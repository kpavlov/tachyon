/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/**
 * Test helper for invoking handlers on a virtual thread.
 *
 * <p>Tool handlers assert they run on a virtual thread (the dispatch contract). Unit tests that
 * exercise a handler in isolation run on the JUnit platform thread, so they must hop onto a
 * virtual thread first.
 */
public final class VirtualThreads {

    private VirtualThreads() {}

    /**
     * Runs {@code task} on a virtual thread and returns its result, unwrapping the cause of any
     * failure so callers see the original checked exception.
     */
    public static <T> T runInVirtualThread(Callable<T> task) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                return executor.submit(task).get();
            } catch (ExecutionException e) {
                switch (e.getCause()) {
                    case Exception ex -> throw ex;
                    case Error err -> throw err;
                    case null, default -> throw e;
                }
            }
        }
    }
}
