/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol;

import dev.tachyonmcp.server.ServerCapabilities;
import dev.tachyonmcp.server.config.ServerIdentity;
import dev.tachyonmcp.server.domain.InitializeResponse;
import dev.tachyonmcp.server.domain.InputRequest;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.domain.Task;
import dev.tachyonmcp.server.domain.TaskResult;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor;
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Maps domain objects to protocol-specific response shapes.
 *
 * <p>Each protocol version (e.g. MCP 2025-11-25) provides its own implementation
 * registered via {@link java.util.ServiceLoader}.
 */
public interface ProtocolResponseMapper {

    /** Returns {@code true} when this mapper handles the given protocol family and version. */
    boolean supports(String protocolName, String protocolVersion);

    /** Returns the protocol-specific empty result sent for methods that return no data. */
    Object emptyResult();

    /** Maps the server discovery response into a protocol-specific shape. */
    default Object discoverResult(
            List<String> supportedVersions, ServerCapabilities capabilities, ServerIdentity serverIdentity) {
        throw new UnsupportedOperationException("server/discover is not supported by this protocol version");
    }

    /** Builds a completion result from a list of candidate values with optional pagination. */
    Object completeResult(List<String> values, @Nullable Double total, @Nullable Boolean hasMore);

    /** Maps the server's initialize response into protocol-specific shape. */
    Object initializeResult(InitializeResponse response);

    /** Maps a paginated list of tool descriptors into protocol-specific shape. */
    Object listToolsResult(List<ToolDescriptor> tools, @Nullable String nextCursor);

    /** Maps a tool call result into protocol-specific shape. */
    Object callToolResult(ToolResult result);

    /** Maps a paginated list of resource descriptors into protocol-specific shape. */
    Object listResourcesResult(List<ResourceDescriptor> resources, @Nullable String nextCursor);

    /** Maps a paginated list of resource template entries into protocol-specific shape. */
    Object listResourceTemplatesResult(List<ResourceTemplateDescriptor> templates, @Nullable String nextCursor);

    /** Maps a resource contents list (from a read operation) into protocol-specific shape. */
    Object readResourceResult(List<ResourceContents> contents);

    /** Maps a paginated list of prompt descriptors into protocol-specific shape. */
    Object listPromptsResult(List<PromptDescriptor> prompts, @Nullable String nextCursor);

    /** Maps prompt messages plus optional description into protocol-specific shape. */
    Object getPromptResult(@Nullable String description, List<PromptMessage> messages);

    /** Maps input-required metadata into protocol-specific shape. */
    Object inputRequiredResult(Map<String, ? extends InputRequest> inputRequests, @Nullable String requestState);

    /** Maps a paginated list of task entries into protocol-specific shape. */
    Object listTasksResult(List<TaskEntry> entries, @Nullable String nextCursor);

    /** Maps a single task entry (get result) into protocol-specific shape. */
    Object getTaskResult(Task entry);

    /** Maps a newly created task entry into a CreateTaskResult (for task-augmented requests). */
    Object createTaskResult(TaskEntry entry);

    /** Maps a cancelled task entry into protocol-specific shape. */
    Object cancelTaskResult(TaskEntry entry);

    /** Maps a task's terminal result into the tasks/result payload — a {@code CallToolResult}. */
    Object getTaskPayloadResult(@Nullable TaskResult result, String taskId);

    /** Builds the params object for a tasks/status notification from a task entry. */
    Object taskStatusNotificationParams(TaskEntry entry);
}
