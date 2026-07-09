/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.McpProtocol;
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
import dev.tachyonmcp.server.domain.*;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.features.tools.ToolResult.InputRequired;
import dev.tachyonmcp.server.features.tools.ToolResult.Success;
import dev.tachyonmcp.server.features.tools.ToolResult.WithMeta;
import dev.tachyonmcp.server.json.JsonUtils;
import dev.tachyonmcp.server.json.RawJson;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

public final class McpResponseMapper implements ProtocolResponseMapper {

    private static final Object EMPTY = new EmptyResult(null, null);

    static {
        CodecRegistry.registerOverride(InputRequiredPayload.class, new InputRequiredPayloadCodec());
    }

    public McpResponseMapper() {}

    @Override
    public boolean supports(String protocolName, String protocolVersion) {
        return "mcp".equalsIgnoreCase(protocolName) && McpProtocol.VERSION.equals(protocolVersion);
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
        Map<String, JsonNode> meta = null;
        ToolResult unwrapped = result;
        if (result instanceof WithMeta(ToolResult inner, Map<String, JsonNode> meta1)) {
            meta = meta1.isEmpty() ? null : meta1;
            unwrapped = inner;
        }
        final var resolvedMeta = meta;
        return switch (unwrapped) {
            case InputRequired ir -> new InputRequiredPayload(ir.inputRequests(), ir.requestState(), resolvedMeta);
            case ToolResult.Error er ->
                new CallToolResult(
                        List.of(McpToolMapper.toProtocolContentBlock(TextContent.of(er.message()))),
                        null,
                        true,
                        resolvedMeta,
                        null);
            case Success s -> wireSuccess(s, resolvedMeta);
            case WithMeta ignored -> throw new AssertionError("WithMeta unwrapped above");
        };
    }

    private Object wireSuccess(Success s, @Nullable Map<String, JsonNode> meta) {
        var blocks = new java.util.ArrayList<>(
                s.content().stream().map(McpToolMapper::toProtocolContentBlock).toList());
        Map<String, JsonNode> structured = null;
        var sv = s.structuredValue();
        if (sv != null) {
            JsonNode node =
                    switch (sv) {
                        case RawJson rj -> JsonUtils.parse(rj.json());
                        case JsonNode n -> n;
                        default -> JsonUtils.parse(JsonUtils.writeString(sv));
                    };
            if (!node.isObject()) {
                throw new IllegalArgumentException(
                        "structuredContent must serialize to a JSON object, got " + node.getNodeType() + ": " + node);
            }
            var objNode = (ObjectNode) node;
            var map = new LinkedHashMap<String, JsonNode>();
            for (var entry : objNode.properties()) {
                map.put(entry.getKey(), entry.getValue());
            }
            structured = map;
            // MCP: a tool returning structured content SHOULD also return the serialized JSON in a
            // text block (backwards-compat). Inject it when the handler supplied no text block.
            var hasText = s.content().stream().anyMatch(c -> c instanceof TextContent);
            if (!hasText) {
                blocks.add(McpToolMapper.toProtocolContentBlock(TextContent.of(objNode.toString())));
            }
        }
        return new CallToolResult(blocks, structured, null, meta, null);
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
    public Object createTaskResult(TaskEntry entry) {
        return McpTaskMapper.toCreateTaskResult(entry);
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

    @Override
    public Object inputRequiredResult(
            Map<String, ? extends InputRequest> inputRequests, @Nullable String requestState) {
        return new InputRequiredPayload(inputRequests, requestState, null);
    }

    private record InputRequiredPayload(
            @Nullable Map<String, ? extends InputRequest> inputRequests,
            @Nullable String requestState,
            @Nullable Map<String, JsonNode> meta) {}

    private static final class InputRequiredPayloadCodec implements Codec<InputRequiredPayload> {

        @Override
        public InputRequiredPayload decode(JsonParser parser) {
            throw new UnsupportedOperationException("server-side only");
        }

        @Override
        public void encode(JsonGenerator gen, InputRequiredPayload value) throws IOException {
            gen.writeStartObject();
            gen.writeStringProperty("resultType", "input_required");
            if (value.inputRequests() != null) {
                gen.writeObjectPropertyStart("inputRequests");
                for (var entry : value.inputRequests().entrySet()) {
                    gen.writeObjectPropertyStart(entry.getKey());
                    writeInputRequest(gen, entry.getValue());
                    gen.writeEndObject();
                }
                gen.writeEndObject();
            }
            if (value.requestState() != null) {
                gen.writeStringProperty("requestState", value.requestState());
            }
            if (value.meta() != null) {
                gen.writeObjectPropertyStart("_meta");
                for (var entry : value.meta().entrySet()) {
                    gen.writeName(entry.getKey());
                    gen.writeRawValue(entry.getValue().toString());
                }
                gen.writeEndObject();
            }
            gen.writeEndObject();
        }

        private static void writeInputRequest(JsonGenerator gen, InputRequest req) throws IOException {
            switch (req) {
                case RpcMethodRequest r -> {
                    gen.writeStringProperty("method", r.method());
                    if (r.params() != null) {
                        gen.writeName("params");
                        CodecRegistry.codecFor(JsonNode.class).encode(gen, r.params());
                    } else {
                        gen.writeObjectPropertyStart("params");
                        gen.writeEndObject();
                    }
                }
                case FormInputRequest f -> {
                    gen.writeStringProperty("method", "elicitation/create");
                    gen.writeName("params");
                    var paramsCodec = CodecRegistry.codecFor(ElicitRequestParams.class);
                    paramsCodec.encode(
                            gen,
                            new ElicitRequestFormParams(
                                    null, f.message(), JsonUtils.writeString(f.requestedSchema()), null, null));
                }
                case UrlInputRequest u -> {
                    gen.writeStringProperty("method", "elicitation/create");
                    gen.writeName("params");
                    var paramsCodec = CodecRegistry.codecFor(ElicitRequestParams.class);
                    paramsCodec.encode(
                            gen,
                            new ElicitRequestURLParams("url", u.message(), u.elicitationId(), u.url(), null, null));
                }
            }
        }
    }
}
