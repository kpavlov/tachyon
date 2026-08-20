/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.domain.ProgressToken;
import dev.tachyonmcp.api.server.domain.Task;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.features.PaginatedResult;
import dev.tachyonmcp.api.server.features.tasks.TaskDescriptor;
import dev.tachyonmcp.api.server.features.tasks.TaskIdGenerator;
import dev.tachyonmcp.api.server.features.tasks.TaskOptions;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.server.config.TasksConfig;
import dev.tachyonmcp.core.server.features.AbstractRegistry;
import dev.tachyonmcp.core.server.internal.AbstractJanitor;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InternalApi
public class DefaultTaskRegistry extends AbstractRegistry<TaskDescriptor, TaskEntry> implements TaskRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTaskRegistry.class);
    private static final long TTL_JANITOR_INTERVAL_SECONDS = 30;

    private final ConcurrentHashMap<String, Future<?>> running = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TaskResumer> resumers = new ConcurrentHashMap<>();
    private final ServerEngine server;
    private final Clock clock;
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
        this(server, config, Clock.systemUTC());
    }

    public DefaultTaskRegistry(ServerEngine server, TasksConfig config, Clock clock) {
        super(config.pageSize());
        this.taskIdGenerator = DefaultTaskIdGenerator.INSTANCE;
        this.defaultKeepAlive = config.keepAlive();
        this.defaultPollInterval = config.pollInterval();
        this.server = server;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public @Nullable TaskEntry getById(String taskId) {
        return get(taskId);
    }

    PaginatedResult<TaskEntry> listEntries(int limit, @Nullable String cursor) {
        return listItems(limit, cursor);
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
        resumers.remove(taskId);
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
            @Nullable Map<String, Object> meta,
            @Nullable String sessionId,
            @Nullable ProgressToken progressToken) {
        return createTask(null, ttl, meta, sessionId, progressToken, defaultKeepAlive, defaultPollInterval);
    }

    private TaskEntry createTask(
            @Nullable String requestedId,
            @Nullable Duration ttl,
            @Nullable Map<String, Object> meta,
            @Nullable String sessionId,
            @Nullable ProgressToken progressToken,
            Duration keepAlive,
            @Nullable Duration pollInterval) {
        var id = requestedId != null ? requestedId : taskIdGenerator.generateTaskId(meta, sessionId);
        var entry = TaskEntry.builder(id)
                .status(TaskState.SUBMITTED)
                .ttl(ttl)
                .sessionId(sessionId)
                .progressToken(progressToken)
                .meta(meta)
                .keepAlive(keepAlive)
                .pollInterval(pollInterval)
                .statusListener(this::fireStatusNotification)
                .clock(clock)
                .build();
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

    @Override
    public void registerResumer(String taskId, TaskResumer resumer) {
        resumers.put(taskId, resumer);
    }

    @Override
    public void unregisterResumer(String taskId) {
        resumers.remove(taskId);
    }

    @Override
    public @Nullable TaskResumer findResumer(String taskId) {
        return resumers.get(taskId);
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
        resumers.remove(taskId);
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
        if (!entry.transitionTo(newStatus, null, statusMessage)) {
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
                    resumers.remove(entry.id());
                    fireOnChange();
                }
            }
        }
    }

    private void fireStatusNotification(TaskEntry entry) {
        server.notifyTaskStatus(entry);
    }
}
