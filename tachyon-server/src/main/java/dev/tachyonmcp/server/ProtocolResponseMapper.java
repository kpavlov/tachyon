/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.domain.InitializeResponse;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public interface ProtocolResponseMapper {

    ProtocolResponseMapper NOOP = new ProtocolResponseMapper() {
        @Override
        public boolean supports(String protocolName, String protocolVersion) {
            return false;
        }

        @Override
        public Object emptyResult() {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object completeResult(List<String> values, @Nullable Double total, @Nullable Boolean hasMore) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object initializeResult(InitializeResponse response) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object listToolsResult(List<ToolDescriptor> tools, @Nullable String nextCursor) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object callToolResult(ToolResult result) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object listResourcesResult(List<ResourceDescriptor> resources, @Nullable String nextCursor) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object listResourceTemplatesResult(List<ResourceTemplateEntry> templates, @Nullable String nextCursor) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object readResourceResult(List<ResourceContents> contents) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object listPromptsResult(List<PromptDescriptor> prompts, @Nullable String nextCursor) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object getPromptResult(@Nullable String description, List<PromptMessage> messages) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object listTasksResult(List<TaskEntry> entries, @Nullable String nextCursor) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object getTaskResult(TaskEntry entry) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object cancelTaskResult(TaskEntry entry) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object getTaskPayloadResult(@Nullable JsonNode result) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }

        @Override
        public Object taskStatusNotificationParams(TaskEntry entry) {
            throw new UnsupportedOperationException("No ProtocolResponseMapper registered");
        }
    };

    boolean supports(String protocolName, String protocolVersion);

    Object emptyResult();

    Object completeResult(List<String> values, @Nullable Double total, @Nullable Boolean hasMore);

    Object initializeResult(InitializeResponse response);

    Object listToolsResult(List<ToolDescriptor> tools, @Nullable String nextCursor);

    Object callToolResult(ToolResult result);

    Object listResourcesResult(List<ResourceDescriptor> resources, @Nullable String nextCursor);

    Object listResourceTemplatesResult(List<ResourceTemplateEntry> templates, @Nullable String nextCursor);

    Object readResourceResult(List<ResourceContents> contents);

    Object listPromptsResult(List<PromptDescriptor> prompts, @Nullable String nextCursor);

    Object getPromptResult(@Nullable String description, List<PromptMessage> messages);

    Object listTasksResult(List<TaskEntry> entries, @Nullable String nextCursor);

    Object getTaskResult(TaskEntry entry);

    Object cancelTaskResult(TaskEntry entry);

    Object getTaskPayloadResult(@Nullable JsonNode result);

    Object taskStatusNotificationParams(TaskEntry entry);
}
