/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.Codec;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.*;
import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.features.Registry;
import dev.tachyonmcp.server.features.tools.ToolRegistry;
import dev.tachyonmcp.server.session.McpContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

public class TaskRegistry extends Registry<TaskEntry> {

    private static final Logger logger = LoggerFactory.getLogger(TaskRegistry.class);
    private static final long TTL_JANITOR_INTERVAL_SECONDS = 30;

    private final ConcurrentHashMap<String, TaskEntry> byId = new ConcurrentHashMap<>();
    private final McpServer server;
    private volatile @Nullable ScheduledExecutorService ttlJanitor;

    public TaskRegistry(McpServer server) {
        this.server = server;
    }

    @Override
    public void add(TaskEntry item) {
        var previous = get(item.name());
        super.add(item);
        if (previous != null && !previous.id().equals(item.id())) {
            byId.remove(previous.id());
        }
        byId.put(item.id(), item);
    }

    @Override
    public void remove(String name) {
        var removed = get(name);
        super.remove(name);
        if (removed != null) {
            byId.remove(removed.id());
        }
    }

    public @Nullable TaskEntry getById(String id) {
        return byId.get(id);
    }

    public TaskEntry createTask(String name, @Nullable String description) {
        return createTask(name, description, 0.0);
    }

    public TaskEntry createTask(String name, @Nullable String description, double ttl) {
        return createTask(TaskDescriptor.of(name, description), ttl);
    }

    public TaskEntry createTask(TaskDescriptor descriptor) {
        return createTask(descriptor, 0.0);
    }

    public TaskEntry createTask(TaskDescriptor descriptor, double ttl) {
        var id = UUID.randomUUID().toString();
        var entry = new TaskEntry(descriptor, id, TaskState.WORKING, ttl);
        add(entry);
        fireStatusNotification(entry);
        return entry;
    }

    public boolean cancelTask(String taskId) {
        var entry = byId.get(taskId);
        if (entry == null) {
            return false;
        }
        if (!entry.transitionTo(TaskState.CANCELLED)) {
            return false;
        }
        fireStatusNotification(entry);
        fireOnChange();
        return true;
    }

    public boolean completeTask(String taskId, @Nullable String resultJson) {
        var entry = byId.get(taskId);
        if (entry == null) {
            return false;
        }
        if (!entry.transitionTo(TaskState.COMPLETED)) {
            return false;
        }
        entry.resultJson(resultJson);
        fireStatusNotification(entry);
        fireOnChange();
        return true;
    }

    public boolean failTask(String taskId, @Nullable String resultJson) {
        var entry = byId.get(taskId);
        if (entry == null) {
            return false;
        }
        if (!entry.transitionTo(TaskState.FAILED)) {
            return false;
        }
        entry.resultJson(resultJson);
        fireStatusNotification(entry);
        fireOnChange();
        return true;
    }

    public boolean updateStatusFromClientNotification(
            String taskId, TaskStatus wireStatus, @Nullable String statusMessage) {
        var entry = byId.get(taskId);
        if (entry == null) {
            logger.debug("Task not found for client notification: {}", taskId);
            return false;
        }
        var internalStatus = TaskBindings.toInternalStatus(wireStatus);
        if (!entry.transitionTo(internalStatus)) {
            logger.debug("Invalid status transition from {} to {} for task {}", entry.status(), internalStatus, taskId);
            return false;
        }
        if (statusMessage != null) {
            entry.resultJson(statusMessage);
        }
        fireStatusNotification(entry);
        return true;
    }

    private final AtomicBoolean ttlJanitorStarted = new AtomicBoolean();

    public void startTtlJanitor() {
        if (!ttlJanitorStarted.compareAndSet(false, true)) {
            return;
        }
        var executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "task-ttl-janitor");
            t.setDaemon(true);
            return t;
        });
        ttlJanitor = executor;
        executor.scheduleAtFixedRate(
                this::expireStaleTasks, TTL_JANITOR_INTERVAL_SECONDS, TTL_JANITOR_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stopTtlJanitor() {
        var executor = ttlJanitor;
        if (executor != null) {
            executor.shutdown();
            ttlJanitor = null;
        }
    }

    private void expireStaleTasks() {
        for (var entry : byId.values()) {
            if (entry.status().isActive() && entry.isExpired()) {
                logger.info("Task expired: id={}, name={}", entry.id(), entry.name());
                if (entry.transitionTo(TaskState.FAILED)) {
                    entry.resultJson("{\"error\":\"Task expired\"}");
                    fireStatusNotification(entry);
                    fireOnChange();
                }
            }
        }
    }

    private void fireStatusNotification(TaskEntry entry) {
        var params = new TaskStatusNotificationParams(
                null,
                entry.id(),
                TaskBindings.toWireStatus(entry.status()),
                null,
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttl(),
                null);
        server.broadcastNotification("notifications/tasks/status", params);
    }

    public void registerHandlers(Map<String, McpMethodHandler> registry) {
        registry.put("tasks/list", new TasksListHandler(this));
        registry.put("tasks/get", new TasksGetHandler(this));
        registry.put("tasks/cancel", new TasksCancelHandler(this));
        registry.put("tasks/result", new TasksResultHandler(this));
    }

    private record TasksListHandler(Registry<TaskEntry> registry) implements McpMethodHandler {

        @Override
        public String method() {
            return "tasks/list";
        }

        @Override
        public Object handle(McpContext context, Object params) {
            var limit = ToolRegistry.parseLimit(params);
            var cursor = ToolRegistry.parseCursor(params);
            var paginated = registry.list(limit, cursor);

            var tasks = new ArrayList<Task>();
            for (var entry : paginated.items()) {
                tasks.add(TaskBindings.toTaskProto(entry));
            }
            return new ListTasksResult(tasks, null, paginated.nextCursor(), null);
        }
    }

    private record TasksGetHandler(TaskRegistry registry) implements McpMethodHandler {

        @Override
        public String method() {
            return "tasks/get";
        }

        @Override
        public Object handle(McpContext context, Object params) {
            var taskId = extractParamId(params);
            if (taskId == null) {
                return JsonRpcErrors.invalidRequest("Missing task ID");
            }
            var entry = registry.getById(taskId);
            if (entry == null) {
                return JsonRpcErrors.invalidRequest("Task not found");
            }
            return TaskBindings.toGetTaskResult(entry);
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

    private record TasksCancelHandler(TaskRegistry registry) implements McpMethodHandler {

        @Override
        public String method() {
            return "tasks/cancel";
        }

        @Override
        public Object handle(McpContext context, Object params) {
            var taskId = TasksGetHandler.extractParamId(params);
            if (taskId == null) {
                return JsonRpcErrors.invalidRequest("Missing task ID");
            }
            if (!registry.cancelTask(taskId)) {
                var entry = registry.getById(taskId);
                if (entry == null) {
                    return JsonRpcErrors.invalidRequest("Task not found");
                }
                return JsonRpcErrors.invalidRequest("Task cannot be cancelled in current state: " + entry.status());
            }
            var entry = registry.getById(taskId);
            return TaskBindings.toCancelTaskResult(entry);
        }
    }

    private record TasksResultHandler(TaskRegistry registry) implements McpMethodHandler {

        @Override
        public String method() {
            return "tasks/result";
        }

        @Override
        public Object handle(McpContext context, Object params) {
            var taskId = TasksGetHandler.extractParamId(params);
            if (taskId == null) {
                return JsonRpcErrors.invalidRequest("Missing task ID");
            }
            var entry = registry.getById(taskId);
            if (entry == null) {
                return JsonRpcErrors.invalidRequest("Task not found");
            }
            var status = entry.status();
            if (status.isActive()) {
                return JsonRpcErrors.invalidRequest("Task is not yet completed: " + status);
            }
            if (status == TaskState.CANCELLED) {
                return JsonRpcErrors.invalidRequest("Task was cancelled");
            }
            var resultJson = entry.resultJson();
            if (resultJson == null) {
                return JsonRpcErrors.invalidRequest("Task result not available");
            }
            try (var p = Codec.FACTORY.createParser(JsonRpcCodec.TREE_READ_CONTEXT, resultJson)) {
                p.nextToken();
                var resultNode = JsonRpcCodec.readTreeValue(p);
                var additionalProps = new LinkedHashMap<String, JsonNode>();
                additionalProps.put("result", resultNode);
                return new GetTaskPayloadResult(null, additionalProps);
            } catch (Exception e) {
                logger.debug("Failed to parse task result JSON: {}", e.getMessage());
                return JsonRpcErrors.invalidRequest("Failed to parse task result");
            }
        }
    }
}
