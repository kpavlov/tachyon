/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol;

import dev.tachyonmcp.api.json.JsonObject;
import dev.tachyonmcp.api.server.config.ServerIdentity;
import dev.tachyonmcp.api.server.domain.InputRequest;
import dev.tachyonmcp.api.server.domain.PromptMessage;
import dev.tachyonmcp.api.server.domain.RequestId;
import dev.tachyonmcp.api.server.domain.ResourceContents;
import dev.tachyonmcp.api.server.domain.ServerCapabilities;
import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.domain.Task;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.features.completions.CompletionResult;
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.protocol.ProtocolRequestMapper.SubscriptionListenRequest;
import dev.tachyonmcp.core.server.domain.InitializeResponse;
import dev.tachyonmcp.core.server.features.tasks.TaskEntry;
import dev.tachyonmcp.core.transport.jsonrpc.JsonRpcError;
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

    /** Maps a protocol-neutral server error to this protocol version's JSON-RPC error payload. */
    JsonRpcError error(ServerError error);

    /** Maps the server discovery response into a protocol-specific shape. */
    default Object discoverResult(
            List<String> supportedVersions,
            ServerCapabilities capabilities,
            ServerIdentity serverIdentity,
            Map<String, JsonObject> registeredExtensions) {
        throw new UnsupportedOperationException("server/discover is not supported by this protocol version");
    }

    /** Maps a completion result into a protocol-specific shape. */
    Object completeResult(CompletionResult result);

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

    /** Maps prompt messages, metadata, and optional description into protocol-specific shape. */
    Object getPromptResult(
            @Nullable String description, List<PromptMessage> messages, @Nullable Map<String, Object> meta);

    /** Maps input-required metadata into protocol-specific shape. */
    Object inputRequiredResult(
            Map<String, ? extends InputRequest> inputRequests,
            @Nullable String requestState,
            @Nullable Map<String, Object> meta);

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

    /**
     * Builds the ack-first {@code notifications/subscriptions/acknowledged} params sent when a new
     * {@code subscriptions/listen} stream is opened.
     */
    default Object subscriptionsAcknowledgedParams(RequestId subscriptionId, SubscriptionListenRequest filter) {
        throw new UnsupportedOperationException("subscriptions/listen is not supported by this protocol version");
    }

    /**
     * Builds the params for a {@code notifications/tools|prompts|resources/list_changed} notification
     * pushed on a {@code subscriptions/listen} stream.
     */
    default Object subscriptionListChangedParams(RequestId subscriptionId) {
        throw new UnsupportedOperationException("subscriptions/listen is not supported by this protocol version");
    }

    /**
     * Builds the params for a {@code notifications/resources/updated} notification pushed on a
     * {@code subscriptions/listen} stream.
     */
    default Object subscriptionResourceUpdatedParams(RequestId subscriptionId, String uri) {
        throw new UnsupportedOperationException("subscriptions/listen is not supported by this protocol version");
    }

    /**
     * Builds the graceful-closure result sent when the server tears down a {@code
     * subscriptions/listen} stream on its own initiative (e.g. shutdown).
     */
    default Object subscriptionsListenGracefulResult(RequestId subscriptionId) {
        throw new UnsupportedOperationException("subscriptions/listen is not supported by this protocol version");
    }
}
