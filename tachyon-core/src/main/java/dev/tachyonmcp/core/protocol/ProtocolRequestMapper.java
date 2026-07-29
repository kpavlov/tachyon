/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol;

import dev.tachyonmcp.api.json.JsonObject;
import dev.tachyonmcp.api.json.PayloadDeserializer;
import dev.tachyonmcp.api.server.domain.LoggingLevel;
import dev.tachyonmcp.api.server.domain.RequestId;
import dev.tachyonmcp.api.server.features.completions.CompletionRequest;
import dev.tachyonmcp.api.server.features.prompts.PromptRequest;
import dev.tachyonmcp.api.server.features.resources.ResourceRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.api.server.features.tools.ToolRequest;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Maps protocol-specific request parameters to protocol-neutral domain requests. */
public interface ProtocolRequestMapper {

    PageRequest page(@Nullable Object params);

    ToolCallRequest callTool(@Nullable Object params, PayloadDeserializer payloadDeserializer);

    PromptCallRequest getPrompt(@Nullable Object params);

    ResourceRequest readResource(@Nullable Object params);

    CompletionCallRequest complete(@Nullable Object params);

    String resourceUri(@Nullable Object params);

    String taskId(@Nullable Object params);

    LoggingLevel loggingLevel(@Nullable Object params);

    InitializeRequest initialize(@Nullable Object params);

    @Nullable
    LoggingLevel permittedLogLevel(@Nullable Object params);

    boolean hasMetaKey(@Nullable Object params, String key);

    @Nullable
    CancellationRequest cancellation(@Nullable Object params);

    @Nullable
    TaskStatusRequest taskStatus(@Nullable Object params);

    record PageRequest(int limit, @Nullable String cursor) {}

    record ToolCallRequest(
            ToolRequest request,
            boolean taskAugmented,
            @Nullable Duration taskTtl) {}

    record PromptCallRequest(String name, PromptRequest request) {}

    record CompletionCallRequest(CompletionReference reference, CompletionRequest request) {}

    sealed interface CompletionReference {
        record Prompt(String name) implements CompletionReference {}

        record Resource(String uri) implements CompletionReference {}
    }

    record InitializeRequest(Map<String, JsonObject> extensions) {}

    record CancellationRequest(
            RequestId requestId, @Nullable String reason) {}

    record TaskStatusRequest(
            String taskId, TaskState state, @Nullable String message) {}
}
