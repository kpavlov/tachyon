/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features;

import dev.tachyonmcp.server.session.DispatchContext;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * Static utilities for common CompletionStage patterns.
 */
public final class HandlerFutures {

    private HandlerFutures() {}

    /**
     * Joins a CompletionStage via blocking {@code .get()}, restoring interrupt and unwrapping
     * ExecutionException. For use on virtual threads where blocking is expected.
     */
    public static <T> T joinInterruptibly(CompletionStage<T> stage) throws Exception {
        try {
            return stage.toCompletableFuture().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new CompletionException(cause);
        }
    }

    /**
     * Wraps a CompletionStage with an isDone fast-path and CompletionException unwrapping:
     * if already complete, runs the transformer inline (Runnable::run); otherwise hops to the
     * server executor. The transformer receives the unwrapped result and any exception cause
     * (CompletionException already unwrapped).
     *
     * <p>TOCTOU note: a stage completing after the isDone check merely takes the executor hop
     * — benign, not a race.
     */
    public static CompletionStage<Object> completeOn(
            CompletionStage<?> stage,
            DispatchContext ctx,
            BiFunction<@Nullable Object, @Nullable Throwable, Object> transformer) {
        var done = stage.toCompletableFuture().isDone();
        var executor = done
                ? (java.util.concurrent.Executor) Runnable::run
                : ctx.server().executor();
        return stage.handleAsync(
                (result, e) -> {
                    if (e != null) {
                        var cause = e instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : e;
                        return transformer.apply(null, cause);
                    } else {
                        return transformer.apply(result, null);
                    }
                },
                executor);
    }
}
