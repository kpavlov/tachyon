/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.observability;

import dev.tachyonmcp.api.annotations.InternalApi;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-dispatch accumulator wrapping the registered {@link ObservationListener}s for one {@link
 * OperationInfo}. Carried as a field on {@code DefaultDispatchContext}, one instance per inbound
 * operation — never a {@code ChannelContext} attribute, never a global map keyed by request id.
 *
 * <p>{@link #NONE} is the shared, zero-allocation instance used when no listener is registered;
 * every method on it is a no-op so call sites never need an {@code isEmpty()} branch of their own.
 *
 * <p>Every listener call is fault-isolated here: a throwing {@code start}/{@code complete}/scope
 * call is logged (bounded, no payloads) and never propagates into the real dispatch path. {@link
 * Error} is never caught by a bare {@code catch (Exception)}, and a caught exception raised while
 * the calling thread's interrupt flag is set is rethrown rather than swallowed — a fault in
 * telemetry code is not a fatal-error or interruption suppression license.
 */
@InternalApi
public final class Observation {

    private static final Logger logger = LoggerFactory.getLogger(Observation.class);
    private static final OperationInfo PLACEHOLDER_INFO = new OperationInfo(OperationKind.NOTIFICATION, "", null);

    /** Shared no-op instance for the disabled path: no listeners, no allocation, no executor hop. */
    public static final Observation NONE = new Observation(List.of(), List.of(), PLACEHOLDER_INFO);

    private final List<ObservationListener> listeners;
    private final List<ObservationScope> scopes;
    private final OperationInfo info;
    private final AtomicBoolean completed = new AtomicBoolean();
    private volatile @Nullable OperationOutcome override;

    private Observation(List<ObservationListener> listeners, List<ObservationScope> scopes, OperationInfo info) {
        this.listeners = listeners;
        this.scopes = scopes;
        this.info = info;
    }

    /** Calls {@link ObservationListener#start} on every listener, fault-isolated. */
    public static Observation start(List<ObservationListener> listeners, OperationInfo info) {
        if (listeners.isEmpty()) {
            return NONE;
        }
        var scopes = new ArrayList<ObservationScope>(listeners.size());
        for (var listener : listeners) {
            try {
                var scope = listener.start(info);
                scopes.add(scope != null ? scope : ObservationScope.NOOP);
            } catch (Exception e) {
                fault("listener.start", info.method(), e);
                scopes.add(ObservationScope.NOOP);
            }
        }
        return new Observation(listeners, scopes, info);
    }

    public OperationInfo info() {
        return info;
    }

    /** Whether any listener is registered for this operation — gates payload-capture work. */
    public boolean active() {
        return !listeners.isEmpty();
    }

    /** Tags the terminal outcome as a task handoff, overriding whatever {@link #complete} is later called with. */
    public void markTaskHandoff(String taskId) {
        override = new OperationOutcome.TaskHandoff(taskId);
    }

    /** Tags the terminal outcome as a serialization failure, overriding whatever {@link #complete} is later called with. */
    public void markSerializationFailed(Throwable cause) {
        override = new OperationOutcome.SerializationFailed(cause);
    }

    /** Closes the scopes {@link #start} opened. Called once, on the thread {@link #start} ran on. */
    public void closeStart() {
        closeAll(scopes);
    }

    /** Re-attaches every scope onto the current thread; close the returned list with {@link #closeReattached}. */
    public List<ObservationScope> reattach() {
        if (scopes.isEmpty()) {
            return List.of();
        }
        var reattached = new ArrayList<ObservationScope>(scopes.size());
        for (var scope : scopes) {
            try {
                var r = scope.reattach();
                reattached.add(r != null ? r : ObservationScope.NOOP);
            } catch (Exception e) {
                fault("scope.reattach", info.method(), e);
                reattached.add(ObservationScope.NOOP);
            }
        }
        return reattached;
    }

    /** Closes scopes previously returned by {@link #reattach}. */
    public void closeReattached(List<ObservationScope> reattached) {
        closeAll(reattached);
    }

    /**
     * Calls {@link ObservationListener#complete} on every listener, exactly once per operation.
     * Subsequent calls (e.g. the generic dispatch-completion path firing after a {@code
     * subscriptions/listen} stream already completed at establishment) are silently ignored.
     */
    public void complete(OperationOutcome defaultOutcome) {
        if (listeners.isEmpty()) {
            return;
        }
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        var outcome = override != null ? override : defaultOutcome;
        for (var listener : listeners) {
            try {
                listener.complete(info, outcome);
            } catch (Exception e) {
                fault("listener.complete", info.method(), e);
            }
        }
    }

    private void closeAll(List<ObservationScope> toClose) {
        for (var scope : toClose) {
            try {
                scope.close();
            } catch (Exception e) {
                fault("scope.close", info.method(), e);
            }
        }
    }

    private static void fault(String what, String method, Exception e) {
        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("Observation " + what + " interrupted", e);
        }
        logger.warn("Observation {} failed, method={}", what, method, e);
    }
}
