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

    @Override
    public void book(Map<String, Object> arguments) {
        createdAt = now();
        logger.info("Booking workflow started: fields={}", arguments.keySet());
        state = TaskState.WORKING;
        message = "Booking appointment";
        revision++;
        state = TaskState.INPUT_REQUIRED;
        message = "Approval required";
        revision++;
        logger.info("Booking workflow waiting for approval");
        Workflow.await(() -> input != null);
        result = Map.of("booking", arguments, "confirmed", true);
        state = TaskState.COMPLETED;
        message = "Appointment booked";
        revision++;
        logger.info("Booking workflow completed");
    }

    @Override
    public TemporalTaskStatus taskStatus() {
        var observedAt = createdAt != null ? now() : Instant.EPOCH;
        return new TemporalTaskStatus(state, message, createdAt, observedAt, result, revision);
    }

    @Override
    public void provideInput(Map<String, Object> input) {
        logger.info("Booking workflow received input: fields={}", input.keySet());
        this.input = input;
        state = TaskState.WORKING;
        message = "Input accepted: " + input.keySet();
        revision++;
    }

    private static Instant now() {
        return Instant.ofEpochMilli(Workflow.currentTimeMillis());
    }
}
