/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.server.domain.PromptMessage;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Generates prompt messages from optional arguments. */
@FunctionalInterface
public interface PromptHandler {

    /** Returns the list of prompt messages for the given argument string. */
    List<PromptMessage> getMessages(@Nullable String arguments) throws Exception;
}
