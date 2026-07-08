/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.server.domain.PromptMessage;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Generates prompt messages from optional arguments.
 *
 * <p>{@link #getMessages} runs on a virtual thread — blocking for I/O is the intended contract.
 * Never use {@code synchronized} or call native methods (pins the carrier thread).
 * Use {@link java.util.concurrent.locks.ReentrantLock} instead.
 */
@FunctionalInterface
public interface PromptHandler {

    /** Returns the list of prompt messages for the given argument string. */
    List<PromptMessage> getMessages(@Nullable String arguments) throws Exception;
}
