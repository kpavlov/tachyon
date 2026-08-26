/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import static dev.tachyonmcp.core.server.features.tasks.TasksWorkflowTest.Operation.CANCEL;
import static dev.tachyonmcp.core.server.features.tasks.TasksWorkflowTest.Operation.COMPLETE;
import static dev.tachyonmcp.core.server.features.tasks.TasksWorkflowTest.Operation.FAIL;
import static dev.tachyonmcp.core.server.features.tasks.TasksWorkflowTest.Operation.REQUIRE_INPUT;
import static dev.tachyonmcp.core.server.features.tasks.TasksWorkflowTest.Operation.RESUME;
import static dev.tachyonmcp.core.server.features.tasks.TasksWorkflowTest.Operation.START;
import static dev.tachyonmcp.core.server.features.tasks.TasksWorkflowTest.Operation.UPDATE_MESSAGE;
import static dev.tachyonmcp.core.test.TestUtils.newEngine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.server.domain.FormInputRequest;
import dev.tachyonmcp.api.server.domain.InputRequestBundle;
import dev.tachyonmcp.api.server.domain.Task;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.server.config.TasksConfig;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The task lifecycle as a holder of the {@link Task} interface experiences it: tasks come from a
 * real {@link DefaultTaskRegistry} and every assertion goes through the public API rather than
 * {@link TaskEntry} internals.
 *
 * <p>{@link #operationIsAcceptedOnlyInTheStatesThatAllowIt} is the state x operation matrix: each
 * case drives a fresh task into one state, invokes one operation, and checks whether it was
 * accepted. Expectations are spelled out literally in {@link #legalOperations()} rather than derived
 * from {@link TaskState#canTransitionTo}, because operations carry rules the transition table does
 * not express — {@code start} is {@code SUBMITTED}-only even though {@code INPUT_REQUIRED ->
 * WORKING} is a legal transition, and {@code updateMessage} needs an already-running task.
 */
class TasksWorkflowTest {

    private static final InputRequestBundle NAME_REQUEST = new InputRequestBundle(
            Map.of("user_name", FormInputRequest.of("What is your name?", JsonSchema.objectSchema())), "state-1");

    private final ServerEngine engine = newEngine(b -> {});
    private final DefaultTaskRegistry registry =
            new DefaultTaskRegistry(engine, TasksConfig.builder().build());

    @AfterEach
    void tearDown() {
        engine.close();
    }

    /** Everything a {@link Task} holder can ask for, each reporting whether it was accepted. */
    enum Operation {
        START(task -> task.start("started")),
        UPDATE_MESSAGE(task -> task.updateMessage("halfway")),
        REQUIRE_INPUT(task -> task.requireInput(NAME_REQUEST, "need input")),
        COMPLETE(task -> task.complete(TaskResult.completed(Map.of("ok", true)))),
        FAIL(task -> task.fail(TaskResult.failed("boom"))),
        CANCEL(task -> task.cancel("no longer needed")),
        @SuppressWarnings("for removal")
        RESUME(task -> task.resume("resumed"));

        private final Predicate<Task> invocation;

        Operation(Predicate<Task> invocation) {
            this.invocation = invocation;
        }

        boolean on(Task task) {
            return invocation.test(task);
        }
    }

    /** One row per state, naming exactly the operations it accepts. Anything unnamed is rejected. */
    private static Stream<Arguments> legalOperations() {
        return Stream.of(
                        accepts(TaskState.SUBMITTED, START, RESUME, COMPLETE, FAIL, CANCEL),
                        accepts(TaskState.WORKING, UPDATE_MESSAGE, REQUIRE_INPUT, COMPLETE, FAIL, CANCEL),
                        accepts(TaskState.INPUT_REQUIRED, UPDATE_MESSAGE, COMPLETE, FAIL, CANCEL),
                        accepts(TaskState.COMPLETED),
                        accepts(TaskState.FAILED),
                        accepts(TaskState.CANCELLED))
                .flatMap(row -> row);
    }

    private static Stream<Arguments> accepts(TaskState state, Operation... accepted) {
        Set<Operation> legal =
                accepted.length == 0 ? EnumSet.noneOf(Operation.class) : EnumSet.copyOf(Arrays.asList(accepted));
        return Arrays.stream(Operation.values())
                .map(operation -> Arguments.of(state, operation, legal.contains(operation)));
    }

    @ParameterizedTest(name = "{1} on a {0} task is accepted: {2}")
    @MethodSource("legalOperations")
    void operationIsAcceptedOnlyInTheStatesThatAllowIt(TaskState state, Operation operation, boolean accepted) {
        // Given a task driven to this state through the public API
        var task = taskIn(state);

        // When the operation is invoked
        var wasAccepted = operation.on(task);

        // Then it is accepted only where the lifecycle allows, and a rejection leaves the task alone
        assertThat(wasAccepted).isEqualTo(accepted);
        if (!accepted) {
            assertThat(task.status()).isEqualTo(state);
        }
    }

    @Test
    void taskRunsFromSubmittedToCompletedAndResolvesItsCompletionFuture() {
        // Given a freshly created task, not yet started
        var task = registry.create();
        assertThat(task.status()).isEqualTo(TaskState.SUBMITTED);
        assertThat(task.completion().toCompletableFuture()).isNotDone();

        // When it is started, reports progress in words, and finishes
        assertThat(task.start("importing")).isTrue();
        assertThat(task.status()).isEqualTo(TaskState.WORKING);
        assertThat(task.updateMessage("step 1 of 2")).isTrue();
        assertThat(task.complete(TaskResult.completed(Map.of("imported", 42)))).isTrue();

        // Then it is completed, carries the last message, and its waiters are released
        assertThat(task.status()).isEqualTo(TaskState.COMPLETED);
        assertThat(task.statusMessage()).isEqualTo("step 1 of 2");
        assertThat(task.completion().toCompletableFuture()).isCompleted();
        assertThat(task.result())
                .isEqualTo(task.completion().toCompletableFuture().join());
    }

    @Test
    void taskPausedForInputKeepsItsRequestUntilTheClientAnswers() {
        // Given a running task
        var task = registry.create();
        assertThat(task.status()).isEqualTo(TaskState.SUBMITTED);
        task.start();
        assertThat(task.status()).isEqualTo(TaskState.WORKING);

        // When it asks for input
        assertThat(task.requireInput(NAME_REQUEST, "need more info")).isTrue();

        // Then it parks with the request outstanding
        assertThat(task.status()).isEqualTo(TaskState.INPUT_REQUIRED);
        assertThat(task.statusMessage()).isEqualTo("need more info");
        assertThat(registry.getById(task.id()).pendingInput()).isEqualTo(NAME_REQUEST);

        // And answering it -- the client's tasks/update, the only thing that may resume a pause --
        // puts the task back to work with nothing outstanding
        assertThat(registry.getById(task.id()).submitInput(Map.of("user_name", "Alice")))
                .isNotNull();
        assertThat(task.status()).isEqualTo(TaskState.WORKING);
        assertThat(registry.getById(task.id()).pendingInput()).isNull();
    }

    @Test
    void cancellingATaskReleasesItsWaitersAsCancelledInsteadOfHanging() {
        // Given a running task
        final var task = registry.create();
        assertThat(task.status()).isEqualTo(TaskState.SUBMITTED);
        task.start();
        assertThat(task.status()).isEqualTo(TaskState.WORKING);

        // When it is cancelled
        assertThat(task.cancel("user went away")).isTrue();

        // Then the reason is kept, and anyone awaiting completion sees a cancellation, not a
        // generic failure, and is not left waiting
        assertThat(task.status()).isEqualTo(TaskState.CANCELLED);
        assertThat(task.statusMessage()).isEqualTo("user went away");
        var future = task.completion().toCompletableFuture();
        assertThat(future).isCancelled();
        assertThatThrownBy(future::join).isInstanceOf(CancellationException.class);
    }

    /** Drives a freshly created task into {@code state} using only public {@link Task} calls. */
    private Task taskIn(TaskState state) {
        var task = registry.create();
        switch (state) {
            case SUBMITTED -> {}
            case WORKING -> assertThat(task.start()).isTrue();
            case INPUT_REQUIRED -> {
                assertThat(task.start()).isTrue();
                assertThat(task.requireInput(NAME_REQUEST, null)).isTrue();
            }
            case COMPLETED ->
                assertThat(task.complete(TaskResult.completed(Map.of("ok", true))))
                        .isTrue();
            case FAILED -> assertThat(task.fail(TaskResult.failed("boom"))).isTrue();
            case CANCELLED -> assertThat(task.cancel(null)).isTrue();
            default -> throw new IllegalArgumentException(state + " is not reachable through the Task API");
        }
        assertThat(task.status()).isEqualTo(state);
        return task;
    }
}
