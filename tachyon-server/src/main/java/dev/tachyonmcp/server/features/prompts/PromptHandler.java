/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.server.domain.PromptMessage;
import java.util.List;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface PromptHandler {

    List<PromptMessage> getMessages(@Nullable String arguments) throws Exception;
}
