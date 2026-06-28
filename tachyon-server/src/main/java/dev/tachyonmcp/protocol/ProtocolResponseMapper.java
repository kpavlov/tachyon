/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol;

import dev.tachyonmcp.server.domain.InitializeResponse;
import dev.tachyonmcp.server.domain.InputRequest;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public interface ProtocolResponseMapper {

    boolean supports(String protocolName, String protocolVersion);

    Object emptyResult();

    Object completeResult(List<String> values, @Nullable Double total, @Nullable Boolean hasMore);

    Object initializeResult(InitializeResponse response);

    Object listToolsResult(List<ToolDescriptor> tools, @Nullable String nextCursor);

    <R extends ToolResult> Object callToolResult(R result);

    Object listResourcesResult(List<ResourceDescriptor> resources, @Nullable String nextCursor);

    Object listResourceTemplatesResult(List<ResourceTemplateEntry> templates, @Nullable String nextCursor);

    Object readResourceResult(List<ResourceContents> contents);

    Object listPromptsResult(List<PromptDescriptor> prompts, @Nullable String nextCursor);

    Object getPromptResult(@Nullable String description, List<PromptMessage> messages);

    Object inputRequiredResult(Map<String, ? extends InputRequest> inputRequests, @Nullable String requestState);

    Object listTasksResult(List<TaskEntry> entries, @Nullable String nextCursor);

    Object getTaskResult(TaskEntry entry);

    Object cancelTaskResult(TaskEntry entry);

    Object getTaskPayloadResult(@Nullable JsonNode result);

    Object taskStatusNotificationParams(TaskEntry entry);
}
