/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.features.tasks.TaskExecutionEngine;
import dev.tachyonmcp.api.server.features.tasks.TaskExecutionRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskFeature;
import dev.tachyonmcp.api.server.features.tasks.TaskInput;
import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Backward-compatible task engine for work executed inside the Tachyon process.
 *
 * <p>The no-argument constructor owns a virtual-thread-per-task executor. The constructor accepting
 * an executor borrows it and does not close it.
 */
@ExperimentalApi
public final class InProcessTaskExecutionEngine implements TaskExecutionEngine {

    private static final Set<TaskFeature> FEATURES = Set.copyOf(EnumSet.allOf(TaskFeature.class));

    private final ExecutorService executor;
    private final boolean ownsExecutor;

    /** Creates an engine backed by owned virtual threads named with the {@code vt-tasks-} prefix. */
    public InProcessTaskExecutionEngine() {
        this(
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual().name("vt-tasks-", 0).factory()),
                true);
    }

    /**
     * Creates an engine backed by a borrowed executor.
     *
     * @param executor executor used for in-process task work
     */
    public InProcessTaskExecutionEngine(ExecutorService executor) {
        this(executor, false);
    }

    private InProcessTaskExecutionEngine(ExecutorService executor, boolean ownsExecutor) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.ownsExecutor = ownsExecutor;
    }

    @Override
    public Set<TaskFeature> supportedFeatures() {
        return FEATURES;
    }

    @Override
    public TaskSnapshot start(InteractionContext context, TaskExecutionRequest request) {
        throw new IllegalStateException("In-process task engine is not attached to a task registry");
    }

    @Override
    public TaskSnapshot refresh(InteractionContext context, String taskId) {
        throw new IllegalStateException("In-process task engine is not attached to a task registry");
    }

    @Override
    public TaskSnapshot cancel(InteractionContext context, String taskId) {
        throw new IllegalStateException("In-process task engine is not attached to a task registry");
    }

    @Override
    public void submitInput(InteractionContext context, String taskId, TaskInput input) {
        throw new IllegalStateException("In-process task engine is not attached to a task registry");
    }

    ExecutorService executor() {
        return executor;
    }

    @Override
    public void close() {
        if (ownsExecutor) {
            executor.close();
        }
    }
}
