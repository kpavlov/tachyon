/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.example.temporal;

import dev.tachyonmcp.api.server.features.tasks.TaskState;
import io.temporal.workflow.Workflow;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;

/** Example workflow implementation. */
public final class BookingWorkflowImpl implements BookingWorkflow {

    private static final Logger logger = Workflow.getLogger(BookingWorkflowImpl.class);

    private Instant createdAt;
    private TaskState state = TaskState.SUBMITTED;
    private String message = "Submitted";
    private Map<String, Object> result = Map.of();
    private Map<String, Object> input;
    private long revision;
    private Instant observedAt = Instant.EPOCH;

    @Override
    public void book(Map<String, Object> arguments) {
        createdAt = now();
        observedAt = createdAt;
        logger.info("Booking workflow started: fields={}", arguments.keySet());
        transitionTo(TaskState.WORKING, "Booking appointment");
        transitionTo(TaskState.INPUT_REQUIRED, "Approval required");
        logger.info("Booking workflow waiting for approval");
        Workflow.await(() -> input != null);
        var approved = Boolean.TRUE.equals(input.get("approved"));
        result = Map.of("booking", arguments, "confirmed", approved);
        if (approved) {
            transitionTo(TaskState.COMPLETED, "Appointment booked");
            logger.info("Booking workflow completed");
        } else {
            transitionTo(TaskState.REJECTED, "Booking rejected");
            logger.info("Booking workflow rejected");
        }
    }

    @Override
    public TemporalTaskStatus taskStatus() {
        return new TemporalTaskStatus(state, message, createdAt, observedAt, result, revision);
    }

    @Override
    public void provideInput(Map<String, Object> input) {
        logger.info("Booking workflow received input: fields={}", input.keySet());
        this.input = input;
        transitionTo(TaskState.WORKING, "Input accepted: " + input.keySet());
    }

    private void transitionTo(TaskState nextState, String nextMessage) {
        state = nextState;
        message = nextMessage;
        revision++;
        observedAt = now();
    }

    private static Instant now() {
        return Instant.ofEpochMilli(Workflow.currentTimeMillis());
    }
}
