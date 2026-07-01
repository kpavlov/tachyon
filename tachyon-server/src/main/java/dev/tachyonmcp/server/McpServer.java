/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.runtime.SessionState;
import dev.tachyonmcp.server.config.ServerConfig;
import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.extensions.McpExtension;
import dev.tachyonmcp.server.features.prompts.PromptRegistry;
import dev.tachyonmcp.server.features.resources.ResourceRegistry;
import dev.tachyonmcp.server.features.tasks.TaskRegistry;
import dev.tachyonmcp.server.features.tools.*;
import dev.tachyonmcp.server.handlers.CompletionHandlers;
import dev.tachyonmcp.server.handlers.InitializeHandler;
import dev.tachyonmcp.server.handlers.LoggingHandlers;
import dev.tachyonmcp.server.handlers.PingHandler;
import dev.tachyonmcp.server.session.*;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core MCP server that manages sessions, registries, method handlers, and
 * extension lifecycle. Created via {@link ServerBuilder}.
 */
public class McpServer implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(McpServer.class);

    private final ServerConfig config;
    private final SessionLogRouter router;
    private final SessionManager sessionManager;
    private final MessageRouter messageRouter = new OutboundSseStreamMessageRouter();
    private final AtomicLong eventIdCounter = new AtomicLong(0);
    private final JsonSchemaValidator validator;
    private final ToolRegistry toolRegistry;
    private final ResourceRegistry resourceRegistry;
    private final TaskRegistry taskRegistry;
    private final PromptRegistry promptRegistry;
    private final Map<String, McpMethodHandler> methodHandlers = new ConcurrentHashMap<>();
    final Map<String, LoggingLevel> loggingLevels = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Object, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<McpExtension> extensions;
    private final Map<String, String> extensionMethodOwners = new ConcurrentHashMap<>();
    private final Map<String, McpExtension> extensionsById = new ConcurrentHashMap<>();

    private static final List<ProtocolResponseMapper> RESPONSE_MAPPERS;

    static {
        var mappers = new ArrayList<ProtocolResponseMapper>();
        ServiceLoader.load(ProtocolResponseMapper.class).forEach(mappers::add);
        RESPONSE_MAPPERS = List.copyOf(mappers);
    }

    /** Returns the protocol response mapper for the default MCP version. */
    public ProtocolResponseMapper responseMapper() {
        for (var mapper : RESPONSE_MAPPERS) {
            if (mapper.supports("mcp", "2025-11-25")) {
                return mapper;
            }
        }
        throw new IllegalStateException("No protocol response mapper found");
    }

    /** Returns the server configuration. */
    public ServerConfig config() {
        return config;
    }

    /** Returns {@code true} when running in stateless mode (no session persistence). */
    public boolean isStateless() {
        return config.session().stateless();
    }

    /** Returns the virtual-thread-per-task executor used for handler dispatch. */
    public ExecutorService executor() {
        return virtualThreadExecutor;
    }

    /** Sets the logging level for a session. */
    public void setLoggingLevel(String sessionId, LoggingLevel level) {
        loggingLevels.put(sessionId, level);
    }

    /** Returns the logging level for a session, or {@code null} if not set. */
    public LoggingLevel getLoggingLevel(String sessionId) {
        return loggingLevels.get(sessionId);
    }

    /**
     * Emits a log notification for the given session if the requested level meets
     * the configured threshold.
     */
    public void log(McpSession session, LoggingLevel requested, String logger, Object data) {
        var configured = loggingLevels.get(session.id());
        if (configured == null) return;
        if (!isLevelEnabled(requested, configured)) return;
        var params = Map.of("level", requested.getValue(), "logger", logger, "data", data);
        sendNotification(session, "notifications/message", params);
    }

    private static boolean isLevelEnabled(LoggingLevel requested, LoggingLevel configured) {
        return requested.ordinal() >= configured.ordinal();
    }

    /** Resolves effective capabilities based on configuration and registered features. */
    public ServerCapabilities resolveCapabilities() {
        final var builder = ServerCapabilities.builder();

        final var capabilitiesConfig = config.capabilities();

        builder.logging(capabilitiesConfig.logging());
        builder.completions(capabilitiesConfig.completions());

        if (capabilitiesConfig.tasksList() || capabilitiesConfig.tasksRequests()) {
            builder.tasks(new ServerCapabilities.Tasks(
                    capabilitiesConfig.tasksList(),
                    capabilitiesConfig.tasksCancel(),
                    capabilitiesConfig.tasksRequests()));
        }

        switch (capabilitiesConfig.toolsMode()) {
            case ON -> builder.tools(new ServerCapabilities.Tools(capabilitiesConfig.toolsListChanged()));
            case OFF -> {}
            case AUTO -> {
                if (!toolRegistry.isEmpty()) {
                    builder.tools(new ServerCapabilities.Tools(capabilitiesConfig.toolsListChanged()));
                }
            }
        }

        switch (capabilitiesConfig.resourcesMode()) {
            case ON ->
                builder.resources(new ServerCapabilities.Resources(
                        capabilitiesConfig.resourcesSubscribe(), capabilitiesConfig.resourcesListChanged()));
            case OFF -> {}
            case AUTO -> {
                if (!resourceRegistry.getAll().isEmpty()) {
                    builder.resources(new ServerCapabilities.Resources(
                            capabilitiesConfig.resourcesSubscribe(), capabilitiesConfig.resourcesListChanged()));
                }
            }
        }

        switch (capabilitiesConfig.promptsMode()) {
            case ON -> builder.prompts(new ServerCapabilities.Prompts(capabilitiesConfig.promptsListChanged()));
            case OFF -> {}
            case AUTO -> {
                if (!promptRegistry.getAll().isEmpty()) {
                    builder.prompts(new ServerCapabilities.Prompts(capabilitiesConfig.promptsListChanged()));
                }
            }
        }

        return builder.build();
    }

    public McpServer() {
        this(new InMemorySessionLogRouter(), new InMemorySessionStore(), ServerConfig.DEFAULT, null, List.of());
    }

    public McpServer(SessionLogRouter router, SessionStore sessionStore, JsonSchemaValidator validator) {
        this(router, sessionStore, ServerConfig.DEFAULT, validator, List.of());
    }

    public McpServer(
            SessionLogRouter router,
            SessionStore sessionStore,
            ServerConfig config,
            @Nullable JsonSchemaValidator validator,
            @Nullable List<McpExtension> extensions) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.router = Objects.requireNonNull(router, "router cannot be null");
        this.extensions = extensions != null ? extensions : List.of();
        this.sessionManager = new SessionManager(sessionStore);
        this.validator = validator != null ? validator : new NetworkntJsonSchemaValidator();
        this.toolRegistry = new ToolRegistry(this.validator);
        this.resourceRegistry = new ResourceRegistry(this);
        this.taskRegistry = new TaskRegistry(this);
        this.promptRegistry = new PromptRegistry(this.validator);
        registerDefaults();
        bootstrapExtensions();
        setupChangeListeners(config);
        if (!config.session().stateless()) {
            sessionManager.startJanitor(config.session().sessionTtl());
        }
    }

    private void setupChangeListeners(ServerConfig config) {
        if (config.capabilities().toolsListChanged()) {
            toolRegistry.onChange(() -> broadcastNotification("notifications/tools/list_changed"));
        }
        if (config.capabilities().resourcesListChanged()) {
            resourceRegistry.onChange(() -> broadcastNotification("notifications/resources/list_changed"));
        }
        if (config.capabilities().promptsListChanged()) {
            promptRegistry.onChange(() -> broadcastNotification("notifications/prompts/list_changed"));
        }
        if (config.capabilities().tasksList()) {
            taskRegistry.onChange(() -> broadcastNotification("notifications/tasks/list_changed"));
        }
        taskRegistry.startTtlJanitor();
    }

    void broadcastNotification(String method) {
        broadcastNotification(method, java.util.Map.of());
    }

    /** Sends a notification to all active sessions. */
    public void broadcastNotification(String method, Object params) {
        for (var entry : sessionManager.allSessions()) {
            if (entry.state() == SessionState.ACTIVE) {
                sendNotification(entry, method, params);
            }
        }
    }

    private void registerDefaults() {
        methodHandlers.put("initialize", new InitializeHandler(this, extensions));
        methodHandlers.put("ping", new PingHandler());
        toolRegistry.registerHandlers(methodHandlers);
        resourceRegistry.registerHandlers(methodHandlers);
        taskRegistry.registerHandlers(methodHandlers);
        promptRegistry.registerHandlers(methodHandlers);
        LoggingHandlers.register(methodHandlers);
        CompletionHandlers.register(methodHandlers);
    }

    /** Registers a method handler keyed by its own method name. */
    public void registerHandler(McpMethodHandler handler) {
        methodHandlers.put(handler.method(), handler);
        logger.info("Handler registered: {}", handler.method());
    }

    /** Registers a method handler with an explicit method name. */
    public void registerHandler(String method, McpMethodHandler handler) {
        methodHandlers.put(method, handler);
        logger.info("Handler registered: {}", method);
    }

    /** Returns the handler for a method, or {@code null} if not registered. */
    @Nullable
    public McpMethodHandler getHandler(String method) {
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

    /** Returns the registered extensions. */
    public List<McpExtension> extensions() {
        return Collections.unmodifiableList(extensions);
    }

    /** Returns the extension ID that owns the given method, or {@code null} if none. */
    public @Nullable String extensionForMethod(String method) {
        return extensionMethodOwners.get(method);
    }

    /** Returns {@code true} if the given extension requires the meta envelope for its methods. */
    public boolean extensionRequiresMeta(String extensionId) {
        var ext = extensionsById.get(extensionId);
        return ext != null && ext.requiresMetaEnvelope();
    }

    /** Registers a synchronous tool handler. */
    public void registerTool(SyncToolHandler handler) {
        toolRegistry.register(handler);
        logger.info("Tool registered: {}", handler.name());
    }

    /** Registers an asynchronous tool handler. */
    public void registerTool(AsyncToolHandler handler) {
        toolRegistry.register(handler);
        logger.info("Tool registered: {}", handler.name());
    }

    /** Registers a tool handler. */
    public void registerTool(ToolHandler handler) {
        toolRegistry.register(handler);
        logger.info("Tool registered: {}", handler.descriptor().name());
    }

    /** Returns the descriptor for a tool by name, or {@code null} if not registered. */
    public @Nullable ToolDescriptor getTool(String name) {
        return toolRegistry.getDescriptor(name);
    }

    /** Returns the resource registry. */
    public ResourceRegistry resources() {
        return resourceRegistry;
    }

    /** Returns the prompt registry. */
    public PromptRegistry prompts() {
        return promptRegistry;
    }

    /** Returns the task registry. */
    public TaskRegistry tasks() {
        return taskRegistry;
    }

    /** Creates and registers a new session with the given ID. */
    public McpSession createSession(String sessionId) {
        return sessionManager.createSession(sessionId);
    }

    /** Returns the session with the given ID, if present. */
    public Optional<McpSession> getSession(String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    /** Removes and closes the session with the given ID. */
    public void removeSession(String sessionId) {
        sessionManager.removeSession(sessionId);
    }

    SseEvent appendResponse(McpSession session, SessionEvent.ResponseEvent event) {
        var sseEventId = nextEventId();
        var enriched = new SessionEvent.ResponseEvent(
                event.sessionId(), event.requestId(), event.resultJson(), event.timestamp(), sseEventId);
        router.append(enriched);

        var sseEvent = new SseEvent(String.valueOf(sseEventId), "message", event.resultJson());

        session.send(sseEvent);
        return sseEvent;
    }

    /** Sends a notification to the given session. */
    public void sendNotification(McpSession session, String method, Object params) {
        sendNotification(session, method, params, null);
    }

    /** Sends a notification to the given session, optionally via a bound outbound SSE stream. */
    public void sendNotification(
            McpSession session, String method, @Nullable Object params, @Nullable OutboundSseStream stream) {
        if (session.state() == SessionState.CLOSED) {
            return;
        }
        var paramsStr =
                switch (params) {
                    case String s -> s;
                    case Map<?, ?> m -> JsonRpcCodec.writeValueAsString(m);
                    case List<?> l -> JsonRpcCodec.writeValueAsString(l);
                    case null -> "{}";
                    default -> JsonRpcCodec.writeValueAsString(params);
                };
        var notificationJson = JsonRpcCodec.serializeNotificationAsString(method, paramsStr);

        var sseEventId = nextEventId();
        var notificationEvent = new SessionEvent.NotificationEvent(
                session.id(), method, paramsStr, System.currentTimeMillis(), sseEventId);
        router.append(notificationEvent);

        var sseEvent = new SseEvent(String.valueOf(sseEventId), "message", notificationJson);

        if (stream != null) {
            stream.start();
            stream.writeEvent(sseEvent);
        } else if (!messageRouter.tryRoute(session, sseEvent)) {
            session.send(sseEvent);
        }
    }

    static final Duration PENDING_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    /** Sends a request to the client and returns a future that completes with the response. */
    public CompletableFuture<String> sendRequest(McpSession session, String method, Object params) {
        return sendRequest(session, method, params, null);
    }

    /** Sends a request to the client, optionally via a bound outbound SSE stream, and returns a future. */
    public CompletableFuture<String> sendRequest(
            McpSession session, String method, Object params, @Nullable OutboundSseStream stream) {
        var paramsStr = params instanceof Map || params instanceof List
                ? JsonRpcCodec.writeValueAsString(params)
                : (String) params;
        var requestId = UUID.randomUUID().toString();
        var future = new CompletableFuture<String>();
        registerPendingRequest(requestId, future);

        var requestJson = JsonRpcCodec.serializeRequestAsString(requestId, method, paramsStr);
        var sseEventId = nextEventId();

        var outboundEvent = new SessionEvent.OutboundRequestEvent(
                session.id(), requestId, method, paramsStr, System.currentTimeMillis(), sseEventId);
        router.append(outboundEvent);

        var sseEvent = new SseEvent(String.valueOf(sseEventId), "message", requestJson);

        if (stream != null) {
            stream.start();
            stream.writeEvent(sseEvent);
        } else {
            var routed = messageRouter.tryRoute(session, sseEvent);
            if (!routed) {
                logger.trace(
                        "sendRequest fallback session.send: method={}, session={}, conn={}",
                        method,
                        session.id(),
                        session.connection());
                session.send(sseEvent);
            }
        }
        return future;
    }

    /** Completes a pending client request with the given result JSON. */
    public boolean completePendingRequest(Object requestId, String resultJson) {
        var future = pendingRequests.remove(requestId);
        if (future != null) {
            future.complete(resultJson);
            return true;
        }
        return false;
    }

    /** Fails a pending client request with the given error message. */
    public boolean failPendingRequest(Object requestId, String message) {
        var future = pendingRequests.remove(requestId);
        if (future != null) {
            future.completeExceptionally(new RuntimeException(message));
            return true;
        }
        return false;
    }

    /** Registers a pending request with a timeout. */
    public void registerPendingRequest(Object requestId, CompletableFuture<String> future) {
        pendingRequests.put(requestId, future);
        future.orTimeout(PENDING_REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        future.whenComplete((res, ex) -> {
            if (ex instanceof TimeoutException) {
                var removed = pendingRequests.remove(requestId);
                if (removed != null) {
                    logger.debug(
                            "Pending request timed out after {}s: id={}, pendingCount={}\n{}",
                            PENDING_REQUEST_TIMEOUT.toSeconds(),
                            requestId,
                            pendingRequests.size(),
                            threadDump());
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

    /** Appends an event to the session log. */
    public void appendEvent(SessionEvent event) {
        router.append(event);
    }

    /** Replays session events after the given sequence number. */
    public List<SessionEvent> replay(String sessionId, long lastSeq) {
        return router.replay(sessionId, lastSeq);
    }

    /** Returns and increments the event ID counter. */
    public long nextEventId() {
        return eventIdCounter.incrementAndGet();
    }

    void pumpChronicle(McpSession session) {
        if (!session.connection().isWritable()) {
            return;
        }

        var cursor = session.cursor();
        var lastIndex = router.pump(session.id(), cursor, event -> {
            if (!session.connection().isWritable()) {
                return false;
            }
            var sseEvent = toSseEvent(event);
            if (sseEvent == null) return true;
            return session.send(sseEvent);
        });
        session.cursor(lastIndex);
    }

    Backpressure backpressure(McpSession session) {
        return session.computeBackpressure();
    }

    public static @Nullable SseEvent toSseEvent(SessionEvent event) {
        return switch (event) {
            case SessionEvent.ResponseEvent r ->
                new SseEvent(String.valueOf(r.sseEventId()), "message", r.resultJson());
            case SessionEvent.NotificationEvent n -> {
                var json = JsonRpcCodec.serializeNotificationAsString(n.method(), n.paramsJson());
                yield new SseEvent(String.valueOf(n.sseEventId()), "message", json);
            }
            case SessionEvent.OutboundRequestEvent o -> {
                var json = JsonRpcCodec.serializeRequestAsString(o.requestId(), o.method(), o.paramsJson());
                yield new SseEvent(String.valueOf(o.sseEventId()), "message", json);
            }
            case SessionEvent.RequestEvent ignored -> null;
            case SessionEvent.CancelEvent ignored -> null;
        };
    }

    @Override
    public void close() {
        try {
            logger.info("Shutting down TachyonServer");
            shutdownExtensions();
            virtualThreadExecutor.shutdown();
            try {
                if (!virtualThreadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    virtualThreadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                virtualThreadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            taskRegistry.stopTtlJanitor();
            sessionManager.close();
            router.close();
        } catch (IOException e) {
            logger.debug("Error while shutting down", e);
        }
    }
}
