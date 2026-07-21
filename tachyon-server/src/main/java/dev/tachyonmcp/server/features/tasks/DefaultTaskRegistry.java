/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.protocol.ProtocolMappers;
import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.McpProtocol;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.config.TasksConfig;
import dev.tachyonmcp.server.domain.ServerErrors;
import dev.tachyonmcp.server.domain.Task;
import dev.tachyonmcp.server.domain.TaskResult;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.features.AbstractRegistry;
import dev.tachyonmcp.server.features.ListRequests;
import dev.tachyonmcp.server.internal.AbstractJanitor;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.server.session.DispatchContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

@InternalApi
public class DefaultTaskRegistry extends AbstractRegistry<TaskDescriptor, TaskEntry> implements TaskRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTaskRegistry.class);
    private static final long TTL_JANITOR_INTERVAL_SECONDS = 30;

    private static final ProtocolResponseMapper mapper =
            Objects.requireNonNull(ProtocolMappers.getMapper("mcp", McpProtocol.VERSION));

    private final ConcurrentHashMap<String, Future<?>> running = new ConcurrentHashMap<>();
    private final ServerEngine server;
    private final TaskIdGenerator taskIdGenerator;
    private final Duration defaultKeepAlive;
    private final @Nullable Duration defaultPollInterval;
    private final AbstractJanitor janitor = new AbstractJanitor("task-janitor") {
        @Override
        protected void sweep() {
            runJanitorSweep();
        }
    };

    public DefaultTaskRegistry(ServerEngine server, TasksConfig config) {
        super(config.pageSize());
        this.taskIdGenerator = DefaultTaskIdGenerator.INSTANCE;
        this.defaultKeepAlive = config.keepAlive();
        this.defaultPollInterval = config.pollInterval();
        this.server = server;
    }

    @Override
    public @Nullable TaskEntry getById(String taskId) {
        return get(taskId);
    }

    @Override
    public void add(TaskEntry entry) {
        addItem(entry);
    }

    @Override
    public boolean remove(String taskId) {
        var entry = get(taskId);
        if (entry == null) {
            return false;
        }
        if (!entry.status().isTerminal()) {
            getAndCancelTask(taskId);
        }
        return removeItem(taskId);
    }

    @Override
    public Task create() {
        return createSessionTask(null, null, null, null);
    }

    @Override
    public Task create(TaskOptions options) {
        var keepAlive = options.keepAlive() != null ? options.keepAlive() : defaultKeepAlive;
        var pollInterval = options.pollInterval() != null ? options.pollInterval() : defaultPollInterval;
        return createTask(options.id(), options.ttl(), options.meta(), null, null, keepAlive, pollInterval);
    }

    @Override
    public TaskEntry createSessionTask(
            @Nullable Duration ttl,
            @Nullable Map<String, JsonNode> meta,
            @Nullable String sessionId,
            @Nullable Object progressToken) {
        return createTask(null, ttl, meta, sessionId, progressToken, defaultKeepAlive, defaultPollInterval);
    }

    private TaskEntry createTask(
            @Nullable String requestedId,
            @Nullable Duration ttl,
            @Nullable Map<String, JsonNode> meta,
            @Nullable String sessionId,
            @Nullable Object progressToken,
            Duration keepAlive,
            @Nullable Duration pollInterval) {
        var id = requestedId != null ? requestedId : taskIdGenerator.generateTaskId(meta, sessionId);
        var descriptor = TaskDescriptor.builder().id(id).build();
        var entry = new TaskEntry(
                descriptor,
                id,
                TaskState.SUBMITTED,
                ttl,
                sessionId,
                progressToken,
                meta,
                keepAlive,
                pollInterval,
                this::fireStatusNotification);
        if (!addItemIfAbsent(entry)) {
            throw new IllegalArgumentException("Task '" + id + "' already exists");
        }
        fireStatusNotification(entry);
        return entry;
    }

    @Override
    public void registerRunning(String taskId, Future<?> future) {
        running.put(taskId, future);
    }

    @Override
    public void unregisterRunning(String taskId) {
        running.remove(taskId);
    }

    public boolean completeTask(String taskId, @Nullable String resultJson) {
        var entry = get(taskId);
        if (entry == null) {
            return false;
        }
        var completed = new TaskResult.Completed(
                resultJson != null ? List.of(TextContent.of(resultJson)) : List.of(), null, null);
        return completeViaEntry(entry, completed);
    }

    public boolean failTask(String taskId, @Nullable String resultJson) {
        var entry = get(taskId);
        if (entry == null) {
            return false;
        }
        var failed =
                new TaskResult.Failed(resultJson != null ? List.of(TextContent.of(resultJson)) : List.of(), null, null);
        return failViaEntry(entry, failed);
    }

    private boolean completeViaEntry(TaskEntry entry, TaskResult.Completed result) {
        if (!entry.complete(result)) {
            return false;
        }
        running.remove(entry.id());
        fireOnChange();
        return true;
    }

    private boolean failViaEntry(TaskEntry entry, TaskResult.Failed result) {
        if (!entry.fail(result)) {
            return false;
        }
        running.remove(entry.id());
        fireOnChange();
        return true;
    }

    public boolean cancelTask(String taskId) {
        var entry = getAndCancelTask(taskId);
        if (entry == null) {
            return false;
        }
        return entry.status() == TaskState.CANCELLED;
    }

    @Nullable
    public TaskEntry getAndCancelTask(String taskId) {
        var entry = get(taskId);
        if (entry == null) {
            return null;
        }
        if (!entry.cancel(null)) {
            return entry;
        }
        var future = running.remove(taskId);
        if (future != null) {
            future.cancel(true);
        }
        fireOnChange();
        return entry;
    }

    public boolean updateStatus(String taskId, TaskState newStatus, @Nullable String statusMessage) {
        Objects.requireNonNull(taskId, "taskId is required");
        Objects.requireNonNull(newStatus, "new status is required");

        final var entry = get(taskId);
        if (entry == null) {
            logger.debug("Task not found for client notification: {}", taskId);
            return false;
        }
        if (statusMessage != null) {
            if (newStatus == TaskState.WORKING && entry.resume(statusMessage)) {
                return true;
            }
            return entry.transitionTo(newStatus, null, statusMessage);
        }
        if (!entry.transitionTo(newStatus)) {
            logger.debug("Invalid status transition from {} to {} for task {}", entry.status(), newStatus, taskId);
            return false;
        }
        return true;
    }

    public void startTtlJanitor() {
        janitor.start(Duration.ofSeconds(TTL_JANITOR_INTERVAL_SECONDS));
    }

    public void stopTtlJanitor() {
        janitor.close();
    }

    void runJanitorSweep() {
        expireStaleTasks();
        dropExpiredResults();
    }

    private void dropExpiredResults() {
        for (var entry : getAll()) {
            if (entry.status().isTerminal() && entry.isResultExpired()) {
                logger.debug("Dropping task result after keepAlive: id={}", entry.id());
                remove(entry.id());
            }
        }
    }

    private void expireStaleTasks() {
        for (var entry : getAll()) {
            if (entry.status().isActive() && entry.isExpired()) {
                logger.info("Task expired: id={}", entry.id());
                var failed = new TaskResult.Failed(List.of(TextContent.of("Task expired")), null, null);
                if (entry.fail(failed)) {
                    fireOnChange();
                }
            }
        }
    }

    private void fireStatusNotification(TaskEntry entry) {
        var params = mapper.taskStatusNotificationParams(entry);
        var sessionId = entry.sessionId();
        if (sessionId != null) {
            server.getSession(sessionId)
                    .ifPresent(s -> server.sendNotification(s, "notifications/tasks/status", params));
            return;
        }
        server.broadcastNotification("notifications/tasks/status", params);
    }

    public void registerHandlers(Map<String, RpcMethodHandler> registry) {
        registry.put("tasks/list", new TasksListHandler(this));
        registry.put("tasks/get", new TasksGetHandler(this));
        registry.put("tasks/cancel", new TasksCancelHandler(this));
        registry.put("tasks/result", new TasksResultHandler(this));
    }

    private record TasksListHandler(DefaultTaskRegistry registry) implements RpcMethodHandler {
        @Override
        public String method() {
            return "tasks/list";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var limit = ListRequests.parseLimit(params);
            var cursor = ListRequests.parseCursor(params);
            var paginated = registry.listItems(limit, cursor);
            if (!paginated.cursorValid()) {
                return ServerErrors.invalidParams("Invalid cursor");
            }
            return context.responseMapper().listTasksResult(paginated.items(), paginated.nextCursor());
        }
    }

    private record TasksGetHandler(Tasks registry) implements RpcMethodHandler {
        @Override
        public String method() {
            return "tasks/get";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var taskId = extractParamId(params);
            if (taskId == null) {
                return ServerErrors.invalidParams("Missing task ID");
            }
            var entry = registry.get(taskId);
            if (entry == null) {
                return ServerErrors.invalidParams("Failed to retrieve task: Task not found");
            }
            return context.responseMapper().getTaskResult(entry);
        }

        private static @Nullable String extractParamId(Object params) {
            if (!(params instanceof Map<?, ?> map)) {
                return null;
            }
            var id = map.get("taskId");
            if (id instanceof String s) {
                return s;
            }
            id = map.get("id");
            if (id instanceof String s) {
                return s;
            }
            return null;
        }
    }

    private record TasksCancelHandler(DefaultTaskRegistry registry) implements RpcMethodHandler {
        @Override
        public String method() {
            return "tasks/cancel";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var taskId = TasksGetHandler.extractParamId(params);
            if (taskId == null) {
                return ServerErrors.invalidParams("Missing task ID");
            }
            final var task = registry.getAndCancelTask(taskId);
            if (task == null) {
                return ServerErrors.invalidParams("Failed to retrieve task: Task not found");
            }
            if (task.status() != TaskState.CANCELLED) {
                return ServerErrors.invalidParams("Task cannot be cancelled in current state: " + task.status());
            }
            return context.responseMapper().cancelTaskResult(task);
        }
    }

    private record TasksResultHandler(DefaultTaskRegistry registry) implements RpcMethodHandler {
        @Override
        public String method() {
            return "tasks/result";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var taskId = TasksGetHandler.extractParamId(params);
            if (taskId == null) {
                return ServerErrors.invalidRequest("Missing task ID");
            }
            var entry = registry.get(taskId);
            if (entry == null) {
                return ServerErrors.invalidRequest("Task not found");
            }
            var status = entry.status();
            if (status == TaskState.CANCELLED) {
                return ServerErrors.invalidRequest("Task was cancelled");
            }
            if (status == TaskState.UNKNOWN) {
                return ServerErrors.invalidRequest("Task is in unknown state");
            }
            if (status.isActive()) {
                try {
                    var result = entry.completion().toCompletableFuture().join();
                    return context.responseMapper().getTaskPayloadResult(result, entry.id());
                } catch (Exception e) {
                    return ServerErrors.invalidRequest("Task result not available: " + e.getMessage());
                }
            }
            var taskResult = entry.result();
            if (taskResult == null) {
                return ServerErrors.invalidRequest("Task result not available");
            }
            return context.responseMapper().getTaskPayloadResult(taskResult, entry.id());
        }
    }
}
