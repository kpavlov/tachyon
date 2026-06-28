/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CallToolResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CompleteResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ElicitRequestFormParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ElicitRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ElicitRequestURLParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.EmptyResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.GetPromptResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.GetTaskPayloadResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListPromptsResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListResourceTemplatesResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListResourcesResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListTasksResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListToolsResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ReadResourceResult;
import dev.tachyonmcp.server.domain.FormInputRequest;
import dev.tachyonmcp.server.domain.InitializeResponse;
import dev.tachyonmcp.server.domain.InputRequest;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.domain.UrlInputRequest;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JsonNode;

public final class McpResponseMapper implements ProtocolResponseMapper {

    private static final Object EMPTY = new EmptyResult(null, null);
    private static final AtomicBoolean CODEC_REGISTERED = new AtomicBoolean();

    public McpResponseMapper() {
        if (CODEC_REGISTERED.compareAndSet(false, true)) {
            CodecRegistry.registerOverride(InputRequiredPayload.class, new InputRequiredPayloadCodec());
        }
    }

    @Override
    public boolean supports(String protocolName, String protocolVersion) {
        return "mcp".equalsIgnoreCase(protocolName) && "2025-11-25".equals(protocolVersion);
    }

    @Override
    public Object emptyResult() {
        return EMPTY;
    }

    @Override
    public Object completeResult(List<String> values, @Nullable Double total, @Nullable Boolean hasMore) {
        return new CompleteResult(
                new CompleteResult.Completion(List.copyOf(Objects.requireNonNull(values, "values")), total, hasMore),
                null,
                null);
    }

    @Override
    public Object initializeResult(InitializeResponse response) {
        var capsBuilder = ServerInfoMapper.toServerCapabilities(response.capabilities());
        if (response.negotiatedExtensions() != null
                && !response.negotiatedExtensions().isEmpty()) {
            capsBuilder.extensions(response.negotiatedExtensions());
        }
        return new InitializeResult(
                response.protocolVersion(),
                capsBuilder.build(),
                ServerInfoMapper.toImplementation(response.serverIdentity()),
                response.instructions(),
                null,
                null);
    }

    @Override
    public Object listToolsResult(List<ToolDescriptor> tools, @Nullable String nextCursor) {
        var protocolTools = tools.stream().map(McpToolMapper::toTool).toList();
        return new ListToolsResult(protocolTools, null, nextCursor, null);
    }

    @Override
    public Object callToolResult(ToolResult result) {
        if (result.inputRequests() != null) {
            return new InputRequiredPayload(result.inputRequests(), result.requestState());
        }
        var protocolContent = result.content().stream()
                .map(McpToolMapper::toProtocolContentBlock)
                .toList();
        return new CallToolResult(protocolContent, result.structuredContent(), result.isError(), result.meta(), null);
    }

    @Override
    public Object listResourcesResult(List<ResourceDescriptor> resources, @Nullable String nextCursor) {
        var protocolResources =
                resources.stream().map(McpResourceMapper::toResource).toList();
        return new ListResourcesResult(protocolResources, null, nextCursor, null);
    }

    @Override
    public Object listResourceTemplatesResult(List<ResourceTemplateEntry> templates, @Nullable String nextCursor) {
        var protocolTemplates =
                templates.stream().map(McpResourceMapper::toResourceTemplate).toList();
        return new ListResourceTemplatesResult(protocolTemplates, null, nextCursor, null);
    }

    @Override
    public Object readResourceResult(List<ResourceContents> contents) {
        var protocolContents = contents.stream()
                .map(ContentBlockMappers::toProtocolResourceContents)
                .toList();
        return new ReadResourceResult(protocolContents, null, null);
    }

    @Override
    public Object listPromptsResult(List<PromptDescriptor> prompts, @Nullable String nextCursor) {
        var protocolPrompts = prompts.stream().map(McpPromptMapper::toPrompt).toList();
        return new ListPromptsResult(protocolPrompts, null, nextCursor, null);
    }

    @Override
    public Object getPromptResult(@Nullable String description, List<PromptMessage> messages) {
        var protocolMessages =
                messages.stream().map(McpPromptMapper::toProtocolMessage).toList();
        return new GetPromptResult(description, protocolMessages, null, null);
    }

    @Override
    public Object listTasksResult(List<TaskEntry> entries, @Nullable String nextCursor) {
        var tasks = entries.stream().map(McpTaskMapper::toTaskProto).toList();
        return new ListTasksResult(tasks, null, nextCursor, null);
    }

    @Override
    public Object getTaskResult(TaskEntry entry) {
        return McpTaskMapper.toGetTaskResult(entry);
    }

    @Override
    public Object cancelTaskResult(TaskEntry entry) {
        return McpTaskMapper.toCancelTaskResult(entry);
    }

    @Override
    public Object taskStatusNotificationParams(TaskEntry entry) {
        return McpTaskMapper.toStatusNotification(entry);
    }

    @Override
    public Object getTaskPayloadResult(@Nullable JsonNode result) {
        if (result == null) {
            return new GetTaskPayloadResult(null, null);
        }
        var additionalProps = new LinkedHashMap<String, JsonNode>();
        additionalProps.put("result", result);
        return new GetTaskPayloadResult(null, additionalProps);
    }

    private record InputRequiredPayload(
            @Nullable Map<String, InputRequest> inputRequests,
            @Nullable String requestState) {}

    private static final class InputRequiredPayloadCodec implements Codec<InputRequiredPayload> {

        @Override
        public InputRequiredPayload decode(JsonParser parser) throws IOException {
            throw new UnsupportedOperationException("server-side only");
        }

        @Override
        public void encode(JsonGenerator gen, InputRequiredPayload value) throws IOException {
            gen.writeStartObject();
            gen.writeStringProperty("resultType", "input_required");
            if (value.inputRequests() != null) {
                gen.writeObjectPropertyStart("inputRequests");
                var paramsCodec = CodecRegistry.codecFor(ElicitRequestParams.class);
                for (var entry : value.inputRequests().entrySet()) {
                    gen.writeObjectPropertyStart(entry.getKey());
                    gen.writeStringProperty("method", "elicitation/create");
                    gen.writeName("params");
                    paramsCodec.encode(gen, toProtocol(entry.getValue()));
                    gen.writeEndObject();
                }
                gen.writeEndObject();
            }
            if (value.requestState() != null) {
                gen.writeStringProperty("requestState", value.requestState());
            }
            gen.writeEndObject();
        }

        private static ElicitRequestParams toProtocol(InputRequest req) {
            if (req instanceof FormInputRequest f) {
                return new ElicitRequestFormParams(null, f.message(), f.requestedSchema(), null, null);
            } else if (req instanceof UrlInputRequest u) {
                return new ElicitRequestURLParams("url", u.message(), u.elicitationId(), u.url(), null, null);
            }
            throw new IllegalArgumentException("Unknown InputRequest type: " + req.getClass());
        }
    }
}
