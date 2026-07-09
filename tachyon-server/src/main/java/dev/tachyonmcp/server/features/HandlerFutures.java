/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

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
}
