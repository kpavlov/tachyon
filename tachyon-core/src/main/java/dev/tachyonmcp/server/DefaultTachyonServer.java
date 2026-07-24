/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.protocol.Protocol;
import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.McpProtocol;
import dev.tachyonmcp.runtime.Backpressure;
import dev.tachyonmcp.runtime.Notifications;
import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SessionState;
import dev.tachyonmcp.runtime.SseEvent;
import dev.tachyonmcp.server.config.ServerConfig;
import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.domain.RequestId;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.features.completions.Completions;
import dev.tachyonmcp.server.features.completions.DefaultCompletionRegistry;
import dev.tachyonmcp.server.features.prompts.DefaultPromptRegistry;
import dev.tachyonmcp.server.features.prompts.Prompts;
import dev.tachyonmcp.server.features.resources.DefaultResourceRegistry;
import dev.tachyonmcp.server.features.resources.Resources;
import dev.tachyonmcp.server.features.tasks.DefaultTaskRegistry;
import dev.tachyonmcp.server.features.tasks.TaskRegistry;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.features.tasks.Tasks;
import dev.tachyonmcp.server.features.tools.DefaultToolRegistry;
import dev.tachyonmcp.server.features.tools.Tools;
import dev.tachyonmcp.server.handlers.DiscoverHandler;
import dev.tachyonmcp.server.handlers.InitializeHandler;
import dev.tachyonmcp.server.handlers.LoggingHandlers;
import dev.tachyonmcp.server.handlers.PingHandler;
import dev.tachyonmcp.server.internal.NotificationLogSupport;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.server.json.JacksonPayloadSerde;
import dev.tachyonmcp.server.json.JsonSchemaValidator;
import dev.tachyonmcp.server.json.NetworkntJsonSchemaValidator;
import dev.tachyonmcp.server.json.PayloadDeserializer;
import dev.tachyonmcp.server.json.PayloadSerializer;
import dev.tachyonmcp.server.session.SessionEvent;
import dev.tachyonmcp.server.session.SessionEventStore;
import dev.tachyonmcp.server.session.SessionIdGenerator;
import dev.tachyonmcp.server.session.SessionManager;
import dev.tachyonmcp.server.session.SessionStore;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import dev.tachyonmcp.transport.netty.NettyServer;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultTachyonServer implements ServerEngine {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTachyonServer.class);

    private final ServerConfig config;
    private final SessionEventStore sessionEventStore;
    private final SessionManager sessionManager;
    private final OutboundStreamResolver outboundStreamResolver = new OutboundSseStreamMessageRouter();
    private final AtomicLong eventIdCounter = new AtomicLong(0);
    private final DefaultToolRegistry toolRegistry;
    private final DefaultResourceRegistry resourceRegistry;
    private final DefaultTaskRegistry taskRegistry;
    private final DefaultPromptRegistry promptRegistry;
    private final DefaultCompletionRegistry completionRegistry;
    private final Map<String, RpcMethodHandler> methodHandlers = new ConcurrentHashMap<>();
    final Map<String, LoggingLevel> loggingLevels = new ConcurrentHashMap<>();
    final ConcurrentHashMap<RequestId, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final boolean ownsExecutor;
    private final List<ServerExtension> extensions;
    private final Map<String, String> extensionMethodOwners = new ConcurrentHashMap<>();
    private final Map<String, ServerExtension> extensionsById = new ConcurrentHashMap<>();

    private int port;

    @Nullable
    private String host;

    @Nullable
    private Closeable transport;

    private static final List<ProtocolResponseMapper> RESPONSE_MAPPERS;

    static {
        var mappers = new ArrayList<ProtocolResponseMapper>();
        Protocols.list().stream().map(Protocol::responseMapper).forEach(mappers::add);
        RESPONSE_MAPPERS = List.copyOf(mappers);
    }

    @Override
    public ProtocolResponseMapper responseMapper() {
        for (var mapper : RESPONSE_MAPPERS) {
            if (mapper.supports("mcp", McpProtocol.VERSION)) {
                return mapper;
            }
        }
        throw new IllegalStateException("No protocol response mapper found");
    }

    @Override
    public ServerConfig config() {
        return config;
    }

    @Override
    public boolean isStateless() {
        return !config.session().enabled();
    }

    @Override
    public SessionIdGenerator sessionIdGenerator() {
        return config.session().sessionIdGenerator();
    }

    @Override
    public ExecutorService executor() {
        return executor;
    }

    @Override
    public void setLoggingLevel(String sessionId, LoggingLevel level) {
        loggingLevels.put(sessionId, level);
    }

    @Override
    @Nullable
    public LoggingLevel getLoggingLevel(String sessionId) {
        return loggingLevels.get(sessionId);
    }

    /**
     * Resolves the server capabilities advertised by the configured features and registered handlers.
     *
     * @return the resolved server capabilities
     */
    @Override
    public ServerCapabilities resolveCapabilities() {
        final var builder = ServerCapabilities.builder();

        final var capabilitiesConfig = config.capabilities();

        builder.logging(capabilitiesConfig.logging());
        switch (capabilitiesConfig.completions()) {
            case ON -> builder.completions(true);
            case OFF -> builder.completions(false);
            case AUTO -> builder.completions(!completionRegistry.isEmpty());
        }

        var hasTaskAugmentedTools = toolRegistry.getAll().stream()
                .anyMatch(h ->
                        h.descriptor().taskSupport() != null && h.descriptor().taskSupport() != TaskSupport.FORBIDDEN);
        var tasksConfig = capabilitiesConfig.tasks();
        if (tasksConfig.enabled() || hasTaskAugmentedTools) {
            builder.tasks(new ServerCapabilities.Tasks(
                    tasksConfig.list(), tasksConfig.cancel(), tasksConfig.requests() || hasTaskAugmentedTools));
        }

        var toolsConfig = capabilitiesConfig.tools();
        switch (toolsConfig.mode()) {
            case ON -> builder.tools(new ServerCapabilities.Tools(toolsConfig.listChanged()));
            case OFF -> {}
            case AUTO -> {
                if (!toolRegistry.isEmpty()) {
                    builder.tools(new ServerCapabilities.Tools(toolsConfig.listChanged()));
                }
            }
        }

        var resourcesConfig = capabilitiesConfig.resources();
        switch (resourcesConfig.mode()) {
            case ON ->
                builder.resources(
                        new ServerCapabilities.Resources(resourcesConfig.subscribe(), resourcesConfig.listChanged()));
            case OFF -> {}
            case AUTO -> {
                if (!resourceRegistry.isEmpty()) {
                    builder.resources(new ServerCapabilities.Resources(
                            resourcesConfig.subscribe(), resourcesConfig.listChanged()));
                }
            }
        }

        var promptsConfig = capabilitiesConfig.prompts();
        switch (promptsConfig.mode()) {
            case ON -> builder.prompts(new ServerCapabilities.Prompts(promptsConfig.listChanged()));
            case OFF -> {}
            case AUTO -> {
                if (!promptRegistry.isEmpty()) {
                    builder.prompts(new ServerCapabilities.Prompts(promptsConfig.listChanged()));
                }
            }
        }

        return builder.build();
    }

    DefaultTachyonServer(
            ExecutorService executor,
            boolean ownsExecutor,
            SessionEventStore sessionEventStore,
            SessionStore sessionStore,
            ServerConfig config,
            @Nullable JsonSchemaValidator inputValidator,
            @Nullable JsonSchemaValidator outputValidator,
            @Nullable PayloadSerializer payloadSerializer,
            @Nullable PayloadDeserializer payloadDeserializer,
            @Nullable List<ServerExtension> extensions) {
        this.executor = executor;
        this.ownsExecutor = ownsExecutor;
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.sessionEventStore = Objects.requireNonNull(sessionEventStore, "sessionEventStore cannot be null");
        this.port = config.network().port();
        this.extensions = extensions != null ? extensions : List.of();
        this.sessionManager = new SessionManager(sessionStore);
        final JsonSchemaValidator inputValidator1 =
                inputValidator != null ? inputValidator : new NetworkntJsonSchemaValidator();
        final JsonSchemaValidator outputValidator1 = outputValidator != null ? outputValidator : inputValidator1;
        var defaultSerde = new JacksonPayloadSerde();
        final PayloadSerializer payloadSerializer1 = payloadSerializer != null ? payloadSerializer : defaultSerde;
        final PayloadDeserializer payloadDeserializer1 =
                payloadDeserializer != null ? payloadDeserializer : defaultSerde;
        var caps = config.capabilities();
        this.toolRegistry = new DefaultToolRegistry(
                inputValidator1, outputValidator1, payloadSerializer1, payloadDeserializer1, caps.tools());
        this.resourceRegistry = new DefaultResourceRegistry(this, caps.resources());
        this.taskRegistry = new DefaultTaskRegistry(this, caps.tasks());
        this.promptRegistry = new DefaultPromptRegistry(inputValidator1, caps.prompts());
        this.completionRegistry = new DefaultCompletionRegistry(caps.completions());
        registerDefaults();
        bootstrapExtensions();
        setupChangeListeners(config);
        if (config.session().enabled()) {
            var session = config.session();
            assert session.sessionTtl() != null : "compact ctor fills ttl when enabled";
            assert session.janitorInterval() != null : "compact ctor fills janitor when enabled";
            sessionManager.startJanitor(session.sessionTtl(), session.janitorInterval());
        }
    }

    static ExecutorService defaultExecutorForBuilder() {
        return defaultExecutor();
    }

    private static ExecutorService defaultExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("tachyon-", 0).factory());
    }

    /**
     * Called by ServerBuilder after transport bind to set the actual bound host, port, and transport.
     */
    void bind(Closeable transport, String host, int port) {
        this.transport = transport;
        this.host = host;
        this.port = port;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public String host() {
        return host != null ? host : config().network().host();
    }

    private void setupChangeListeners(ServerConfig config) {
        var caps = config.capabilities();
        if (caps.tools().listChanged()) {
            toolRegistry.onChange(() -> broadcastNotification("notifications/tools/list_changed"));
        }
        if (caps.resources().listChanged()) {
            resourceRegistry.onChange(() -> broadcastNotification("notifications/resources/list_changed"));
        }
        if (caps.prompts().listChanged()) {
            promptRegistry.onChange(() -> broadcastNotification("notifications/prompts/list_changed"));
        }
        if (caps.tasks().list()) {
            taskRegistry.onChange(() -> broadcastNotification("notifications/tasks/list_changed"));
        }
        taskRegistry.startTtlJanitor();
    }

    void broadcastNotification(String method) {
        broadcastNotification(method, java.util.Map.of());
    }

    @Override
    public void broadcastNotification(String method, Object params) {
        var paramsStr = JsonRpcCodec.toJsonParams(params);
        var notificationJson = JsonRpcCodec.serializeNotificationAsString(method, paramsStr);
        for (var entry : sessionManager.allSessions()) {
            if (entry.state() == SessionState.ACTIVE) {
                sendSerializedNotification(entry, method, paramsStr, notificationJson, null);
            }
        }
    }

    private void registerDefaults() {
        methodHandlers.put("initialize", new InitializeHandler(this, extensions));
        methodHandlers.put("server/discover", new DiscoverHandler(this));
        methodHandlers.put("ping", new PingHandler());
        toolRegistry.registerHandlers(methodHandlers);
        resourceRegistry.registerHandlers(methodHandlers);
        taskRegistry.registerHandlers(methodHandlers);
        promptRegistry.registerHandlers(methodHandlers);
        completionRegistry.registerHandlers(methodHandlers);
        if (config.capabilities().logging()) {
            LoggingHandlers.register(methodHandlers);
        }
    }

    @Override
    public void registerHandler(RpcMethodHandler handler) {
        methodHandlers.put(handler.method(), handler);
        logger.debug("Handler registered: {}", handler.method());
    }

    @Override
    public void registerHandler(String method, RpcMethodHandler handler) {
        methodHandlers.put(method, handler);
        logger.debug("Handler registered: {}", method);
    }

    @Override
    @Nullable
    public RpcMethodHandler getHandler(String method) {
        return methodHandlers.get(method);
    }

    private void bootstrapExtensions() {
        for (var ext : extensions) {
            for (var method : ext.methods()) {
                extensionMethodOwners.put(method, ext.extensionId());
            }
            extensionsById.put(ext.extensionId(), ext);
            ext.bootstrap(this);
        }
    }

    private void shutdownExtensions() {
        for (var ext : extensions) {
            try {
                ext.shutdown();
            } catch (Exception e) {
                logger.warn("Extension shutdown error: {}", ext.extensionId(), e);
            }
        }
    }

    @Override
    public List<ServerExtension> extensions() {
        return Collections.unmodifiableList(extensions);
    }

    @Override
    public @Nullable String extensionForMethod(String method) {
        return extensionMethodOwners.get(method);
    }

    @Override
    public boolean extensionRequiresMeta(String extensionId) {
        var ext = extensionsById.get(extensionId);
        return ext != null && ext.requiresMetaEnvelope();
    }

    @Override
    public Tools tools() {
        return toolRegistry;
    }

    @Override
    public Resources resources() {
        return resourceRegistry;
    }

    @Override
    public Prompts prompts() {
        return promptRegistry;
    }

    @Override
    public Completions completions() {
        return completionRegistry;
    }

    @Override
    public Tasks tasks() {
        return taskRegistry;
    }

    @Override
    public TaskRegistry tasksRegistry() {
        return taskRegistry;
    }

    private final Notifications serverNotifications = this::broadcastLog;

    @Override
    public Notifications notifications() {
        return serverNotifications;
    }

    /**
     * Broadcasts a structured MCP log to every active session whose configured threshold admits the
     * level. No-op when the server does not advertise the {@code logging} capability. The wire form
     * is serialized once and reused across recipients.
     */
    public void broadcastLog(LoggingLevel level, @Nullable String logger, @Nullable Object data) {
        if (!config.capabilities().logging()) {
            return;
        }
        var paramsStr = JsonRpcCodec.toJsonParams(NotificationLogSupport.logParams(level, logger, data));
        var notificationJson = JsonRpcCodec.serializeNotificationAsString(NotificationLogSupport.LOG_METHOD, paramsStr);
        for (var session : sessionManager.allSessions()) {
            if (session.state() != SessionState.ACTIVE) {
                continue;
            }
            var threshold = loggingLevels.getOrDefault(session.id(), LoggingLevel.INFO);
            if (level.ordinal() < threshold.ordinal()) {
                continue;
            }
            sendSerializedNotification(session, NotificationLogSupport.LOG_METHOD, paramsStr, notificationJson, null);
        }
    }

    @Override
    public Session createSession(String sessionId) {
        return sessionManager.createSession(sessionId);
    }

    @Override
    public Optional<Session> getSession(String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    @Override
    public void removeSession(String sessionId) {
        sessionManager.removeSession(sessionId);
    }

    SseEvent appendResponse(Session session, SessionEvent.ResponseEvent event) {
        var sseEventId = nextEventId();
        var enriched = new SessionEvent.ResponseEvent(
                event.sessionId(), event.requestId(), event.resultJson(), event.timestamp(), sseEventId, null);
        sessionEventStore.append(enriched);

        var sseEvent = new SseEvent(String.valueOf(sseEventId), "message", event.resultJson());

        session.send(sseEvent);
        return sseEvent;
    }

    @Override
    public void sendNotification(Session session, String method, Object params) {
        sendNotification(session, method, params, null);
    }

    @Override
    public void sendNotification(
            Session session, String method, @Nullable Object params, @Nullable OutboundSseStream stream) {
        var paramsStr = JsonRpcCodec.toJsonParams(params);
        var notificationJson = JsonRpcCodec.serializeNotificationAsString(method, paramsStr);
        sendSerializedNotification(session, method, paramsStr, notificationJson, stream);
    }

    private void sendSerializedNotification(
            Session session,
            String method,
            String paramsStr,
            String notificationJson,
            @Nullable OutboundSseStream stream) {
        if (session.state() == SessionState.CLOSED) {
            return;
        }
        var target = stream != null ? stream : outboundStreamResolver.resolve(session);
        var streamKey = target != null ? target.streamKey() : null;
        var sseEventId = nextEventId();
        var notificationEvent = new SessionEvent.NotificationEvent(
                session.id(), method, paramsStr, System.currentTimeMillis(), sseEventId, streamKey);
        sessionEventStore.append(notificationEvent);

        var sseEvent = new SseEvent(ServerEngine.wireEventId(sseEventId, streamKey), "message", notificationJson);

        if (target != null) {
            target.start();
            target.writeEvent(sseEvent);
        } else {
            session.send(sseEvent);
        }
    }

    @Override
    public CompletableFuture<String> sendRequest(Session session, String method, Object params) {
        return sendRequest(session, method, params, null);
    }

    /**
     * Sends a JSON-RPC request to a session and tracks its response.
     *
     * @param session the session that receives the request
     * @param method  the JSON-RPC method name
     * @param params  the request parameters
     * @param stream  the outbound stream to use, or {@code null} to resolve one for the session
     * @return a future completed with the response JSON, or completed exceptionally if the request fails
     */
    @Override
    public CompletableFuture<String> sendRequest(
            Session session, String method, Object params, @Nullable OutboundSseStream stream) {
        final var paramsStr = JsonRpcCodec.toJsonParams(params);
        final var requestId = RequestId.of(UUID.randomUUID().toString());
        final var future = new CompletableFuture<String>();
        registerPendingRequest(requestId, future);

        final var requestJson = JsonRpcCodec.serializeRequestAsString(requestId, method, paramsStr);
        final var target = stream != null ? stream : outboundStreamResolver.resolve(session);
        final var streamKey = target != null ? target.streamKey() : null;
        final var sseEventId = nextEventId();

        final var outboundEvent = new SessionEvent.OutboundRequestEvent(
                session.id(), requestId, method, paramsStr, System.currentTimeMillis(), sseEventId, streamKey);
        sessionEventStore.append(outboundEvent);

        final var sseEvent = new SseEvent(ServerEngine.wireEventId(sseEventId, streamKey), "message", requestJson);

        if (target != null) {
            target.start();
            target.writeEvent(sseEvent);
        } else {
            logger.trace(
                    "sendRequest fallback session.send: method={}, session={}, conn={}",
                    method,
                    session.id(),
                    session.connection());
            session.send(sseEvent);
        }
        return future;
    }

    @Override
    public boolean completePendingRequest(@Nullable RequestId requestId, String resultJson) {
        // ConcurrentHashMap#remove throws NPE on a null key; a null id (malformed client
        // response, no JSON-RPC id) can never match a pending request, since registered ids are
        // always server-generated and non-null.
        if (requestId == null) {
            return false;
        }
        var future = pendingRequests.remove(requestId);
        if (future != null) {
            future.complete(resultJson);
            return true;
        }
        return false;
    }

    @Override
    public boolean failPendingRequest(@Nullable RequestId requestId, String message) {
        if (requestId == null) {
            return false;
        }
        var future = pendingRequests.remove(requestId);
        if (future != null) {
            future.completeExceptionally(new RuntimeException(message));
            return true;
        }
        return false;
    }

    @Override
    public void registerPendingRequest(RequestId requestId, CompletableFuture<String> future) {
        pendingRequests.put(requestId, future);
        var timeout = config.runtime().requestTimeout();
        future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        future.whenComplete((res, ex) -> {
            if (ex instanceof TimeoutException) {
                var removed = pendingRequests.remove(requestId);
                if (removed != null) {
                    logger.debug(
                            "Pending request timed out after {}s: id={}, pendingCount={}",
                            timeout.toSeconds(),
                            requestId,
                            pendingRequests.size());
                    if (logger.isTraceEnabled()) {
                        logger.trace("{}", threadDump());
                    }
                }
            }
        });
    }

    private static String threadDump() {
        var sb = new StringBuilder("Thread dump:\n");
        Thread.getAllStackTraces().forEach((t, frames) -> {
            sb.append("  ")
                    .append(t.getName())
                    .append(" [")
                    .append(t.getState())
                    .append("]");
            if (t.isVirtual()) sb.append(" (virtual)");
            sb.append('\n');
            for (var frame : frames) {
                sb.append("    at ").append(frame).append('\n');
            }
        });
        return sb.toString();
    }

    @Override
    public void appendEvent(SessionEvent event) {
        sessionEventStore.append(event);
    }

    @Override
    public List<SessionEvent> replay(String sessionId, long lastSeq) {
        return sessionEventStore.replay(sessionId, lastSeq);
    }

    @Override
    public long nextEventId() {
        return eventIdCounter.incrementAndGet();
    }

    void drainEvents(Session session) {
        if (!session.connection().isWritable()) {
            return;
        }

        var cursor = session.cursor();
        var lastIndex = sessionEventStore.drain(session.id(), cursor, event -> {
            if (!session.connection().isWritable()) {
                return false;
            }
            var sseEvent = ServerEngine.toSseEvent(event);
            if (sseEvent == null) return true;
            return session.send(sseEvent);
        });
        session.cursor(lastIndex);
    }

    Backpressure backpressure(Session session) {
        return session.computeBackpressure();
    }

    @Override
    public void close() {
        if (transport instanceof NettyServer netty) {
            netty.stopAccepting();
        }
        try {
            logger.info("Shutting down TachyonMCP Server");
            shutdownExtensions();
            if (ownsExecutor) {
                executor.shutdown();
                try {
                    var grace = config.runtime().shutdownGracePeriod();
                    if (!executor.awaitTermination(grace.toMillis(), TimeUnit.MILLISECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            taskRegistry.stopTtlJanitor();
            sessionManager.close();
            sessionEventStore.close();
        } catch (IOException e) {
            logger.debug("Error while shutting down", e);
        } finally {
            if (transport instanceof NettyServer netty) {
                netty.close();
            } else if (transport != null) {
                try {
                    transport.close();
                } catch (IOException e) {
                    logger.debug("Error closing transport", e);
                }
            }
        }
    }
}
