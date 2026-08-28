/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.api.json.JsonObject;
import dev.tachyonmcp.api.json.PayloadDeserializer;
import dev.tachyonmcp.api.server.domain.Args;
import dev.tachyonmcp.api.server.domain.LoggingLevel;
import dev.tachyonmcp.api.server.domain.ProgressToken;
import dev.tachyonmcp.api.server.domain.RequestId;
import dev.tachyonmcp.api.server.features.completions.CompletionRequest;
import dev.tachyonmcp.api.server.features.prompts.PromptRequest;
import dev.tachyonmcp.api.server.features.resources.ResourceRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskAwaitResultRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskCancelRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskGetRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.api.server.features.tasks.TaskUpdateRequest;
import dev.tachyonmcp.api.server.features.tools.ToolRequest;
import dev.tachyonmcp.core.protocol.ProtocolRequestMapper;
import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.CallToolRequestParams;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.CancelledNotificationParams;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.CompleteRequestParams;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.GetPromptRequestParams;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.InitializeRequestParams;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.PaginatedRequestParams;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.ReadResourceRequestParams;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.TaskStatus;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import dev.tachyonmcp.core.server.json.JsonUtils;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Request mapper for MCP 2025-11-25 and its backward-compatible request shapes.
 *
 * <p>Also parses the 2026-07-28 {@code inputResponses}/{@code requestState} fields (SEP-2322)
 * unconditionally: they're simply absent on 2025-11-25 wire payloads, so this is a no-op for that
 * version. Because these fields aren't part of this version's generated {@code *RequestParams}
 * models, they're read from the raw params map rather than through {@link #convert}.
 */
public class McpRequestMapper implements ProtocolRequestMapper {

    private static final String META_LOG_LEVEL_KEY = "io.modelcontextprotocol/logLevel";
    private static final String META_CLIENT_CAPABILITIES_KEY = "io.modelcontextprotocol/clientCapabilities";

    @Override
    public PageRequest page(@Nullable Object params) {
        var map = asMap(params);
        var limit = map.get("limit") instanceof Number number ? number.intValue() : 0;
        var paginated = convert(map, PaginatedRequestParams.class);
        return new PageRequest(limit, paginated.cursor(), optionalMap(map, "_meta", "Invalid _meta"));
    }

    @Override
    public ToolCallRequest callTool(@Nullable Object params, PayloadDeserializer payloadDeserializer) {
        return callTool(params, payloadDeserializer, true);
    }

    /**
     * Maps a tool call, optionally preserving MCP 2025-11-25's legacy task augmentation fields.
     *
     * <p>{@code task} is excluded before conversion and read from the raw params map instead, even
     * though the generated record declares it: 2026-07-28 payloads may carry a garbage or
     * differently-shaped {@code task} value there since that version ignores the field entirely
     * (see the class javadoc), and converting it as part of {@link CallToolRequestParams} would
     * fail on such payloads regardless of {@code legacyTaskAugmentation}.
     */
    protected final ToolCallRequest callTool(
            @Nullable Object params, PayloadDeserializer payloadDeserializer, boolean legacyTaskAugmentation) {
        var map = asMap(params);
        var callToolMap = new LinkedHashMap<>(map);
        callToolMap.remove("task");
        var callParams = convert(callToolMap, CallToolRequestParams.class);
        var meta = JsonUtils.toObjectMap(callParams._meta());
        var inputResponses = optionalMap(map, "inputResponses", "Invalid inputResponses");
        var requestState = optionalString(map, "requestState", "Invalid requestState");
        var task = legacyTaskAugmentation ? optionalMap(map, "task", "Invalid task metadata") : null;
        var ttl = task != null && task.get("ttl") instanceof Number value ? Duration.ofMillis(value.longValue()) : null;
        var request = ToolRequest.builder()
                .name(required(callParams.name(), "Missing tool name"))
                .arguments(Args.of(JsonUtils.toObjectMap(callParams.arguments()), payloadDeserializer))
                .meta(meta)
                .progressToken(progressToken(meta))
                .payloadDeserializer(payloadDeserializer)
                .inputResponses(inputResponses)
                .requestState(requestState)
                .build();
        return new ToolCallRequest(request, legacyTaskAugmentation && task != null, ttl);
    }

    @Override
    public PromptCallRequest getPrompt(@Nullable Object params) {
        var map = asMap(params);
        var promptParams = convert(map, GetPromptRequestParams.class);
        var inputResponses = optionalMap(map, "inputResponses", "Invalid inputResponses");
        var requestState = optionalString(map, "requestState", "Invalid requestState");
        var arguments = JsonUtils.toObjectMap(promptParams.arguments());
        return new PromptCallRequest(
                required(promptParams.name(), "Missing prompt name"),
                new PromptRequest(
                        arguments != null ? Args.of(arguments) : Args.empty(),
                        inputResponses,
                        requestState,
                        JsonUtils.toObjectMap(promptParams._meta())));
    }

    @Override
    public ResourceRequest readResource(@Nullable Object params) {
        var map = asMap(params);
        var resourceParams = convert(map, ReadResourceRequestParams.class);
        return ResourceRequest.builder()
                .uri(required(resourceParams.uri(), "Missing resource URI"))
                .meta(JsonUtils.toObjectMap(resourceParams._meta()))
                .inputResponses(optionalMap(map, "inputResponses", "Invalid inputResponses"))
                .requestState(optionalString(map, "requestState", "Invalid requestState"))
                .build();
    }

    @Override
    public CompletionCallRequest complete(@Nullable Object params) {
        var map = asMap(params);
        var ref = requiredMap(map, "ref", "Missing or invalid ref parameter");
        var argumentMap = requiredMap(map, "argument", "argument.name and argument.value are required");
        var argument = convert(argumentMap, CompleteRequestParams.Argument.class);
        var argumentName = required(argument.name(), "argument.name and argument.value are required");
        var argumentValue = required(argument.value(), "argument.name and argument.value are required");
        var refType = ref.get("type");
        final CompletionReference reference;
        if ("ref/prompt".equals(refType)) {
            reference =
                    new CompletionReference.Prompt(requiredString(ref, "name", "ref.name is required for ref/prompt"));
        } else if ("ref/resource".equals(refType)) {
            reference = new CompletionReference.Resource(
                    requiredString(ref, "uri", "ref.uri is required for ref/resource"));
        } else {
            throw invalidParams("Unknown ref.type: " + refType);
        }
        var context = optionalMap(map, "context", "Invalid context");
        var resolved = context != null
                ? stringMap(optionalMap(context, "arguments", "Invalid context.arguments"))
                : Map.<String, String>of();
        return new CompletionCallRequest(
                reference,
                CompletionRequest.builder()
                        .argumentName(argumentName)
                        .argumentValue(argumentValue)
                        .resolvedArguments(resolved)
                        .meta(optionalMap(map, "_meta", "Invalid _meta"))
                        .build());
    }

    @Override
    public String resourceUri(@Nullable Object params) {
        return requiredString(asMap(params), "uri", "Missing resource URI");
    }

    @Override
    public TaskGetRequest taskGet(@Nullable Object params) {
        var map = asMap(params);
        return TaskGetRequest.builder()
                .taskId(requiredString(map, "taskId", "Missing taskId"))
                .meta(optionalMap(map, "_meta", "Invalid _meta"))
                .build();
    }

    @Override
    public TaskCancelRequest taskCancel(@Nullable Object params) {
        var map = asMap(params);
        return TaskCancelRequest.builder()
                .taskId(requiredString(map, "taskId", "Missing taskId"))
                .meta(optionalMap(map, "_meta", "Invalid _meta"))
                .build();
    }

    @Override
    @SuppressWarnings("deprecation")
    public TaskAwaitResultRequest taskAwaitResult(@Nullable Object params) {
        var map = asMap(params);
        return TaskAwaitResultRequest.builder()
                .taskId(requiredString(map, "taskId", "Missing taskId"))
                .meta(optionalMap(map, "_meta", "Invalid _meta"))
                .build();
    }

    @Override
    public TaskUpdateRequest taskUpdate(@Nullable Object params) {
        final var map = asMap(params);
        final var taskId = requiredString(map, "taskId", "Missing taskId");
        final var responses = requiredMap(map, "inputResponses", "Missing inputResponses");
        return TaskUpdateRequest.builder()
                .taskId(taskId)
                .inputResponses(responses)
                .meta(optionalMap(map, "_meta", "Invalid _meta"))
                .build();
    }

    @Override
    public LoggingLevel loggingLevel(@Nullable Object params) {
        var value = requiredString(asMap(params), "level", "Missing level parameter");
        try {
            return LoggingLevelMapper.toDomain(
                    dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.LoggingLevel.fromValue(value));
        } catch (IllegalArgumentException e) {
            throw invalidParams("Invalid logging level: " + value);
        }
    }

    @Override
    public InitializeRequest initialize(@Nullable Object params) {
        var initParams = convert(asMap(params), InitializeRequestParams.class);
        var capabilities = initParams.capabilities();
        var extensions = capabilities != null ? JsonUtils.toObjectMap(capabilities.extensions()) : null;
        return new InitializeRequest(mapExtensions(extensions));
    }

    @Override
    public Map<String, JsonObject> declaredExtensions(@Nullable Object params) {
        var map = asMap(params);
        if (!(map.get("_meta") instanceof Map<?, ?> meta)) return Map.of();
        if (!(meta.get(META_CLIENT_CAPABILITIES_KEY) instanceof Map<?, ?> capabilities)) return Map.of();
        if (!(capabilities.get("extensions") instanceof Map<?, ?> extensions)) return Map.of();
        return mapExtensions(extensions);
    }

    /** Maps a raw {@code extensions} object (id -> settings) to the domain {@code Map<String, JsonObject>} shape. */
    private static Map<String, JsonObject> mapExtensions(@Nullable Map<?, ?> extensions) {
        if (extensions == null || extensions.isEmpty()) return Map.of();
        var mapped = new LinkedHashMap<String, JsonObject>();
        stringKeyed(extensions)
                .forEach((key, value) -> mapped.put(
                        key, value instanceof Map<?, ?> map ? JsonObject.of(stringKeyed(map)) : JsonObject.empty()));
        return Map.copyOf(mapped);
    }

    @Override
    public @Nullable LoggingLevel permittedLogLevel(@Nullable Object params) {
        var meta = optionalMap(asMap(params), "_meta", "Invalid _meta");
        if (meta == null || !(meta.get(META_LOG_LEVEL_KEY) instanceof String value)) return null;
        try {
            return LoggingLevel.fromValue(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public boolean hasMetaKey(@Nullable Object params, String key) {
        var meta = optionalMap(asMap(params), "_meta", "Invalid _meta");
        return meta != null && meta.containsKey(key);
    }

    @Override
    public @Nullable CancellationRequest cancellation(@Nullable Object params) {
        var map = asMap(params);
        var rawId = map.get("requestId");
        if (!(rawId instanceof CharSequence) && !(rawId instanceof Number)) return null;
        var cancelParams = convert(map, CancelledNotificationParams.class);
        return new CancellationRequest(RequestId.of(rawId), cancelParams.reason());
    }

    @Override
    public @Nullable TaskStatusRequest taskStatus(@Nullable Object params) {
        var map = asMap(params);
        if (!(map.get("taskId") instanceof String taskId)) return null;
        var state = map.get("status") instanceof String text ? toTaskState(parseTaskStatus(text)) : TaskState.WORKING;
        var message = map.get("statusMessage") instanceof String text ? text : null;
        return new TaskStatusRequest(taskId, state, message);
    }

    /** Parses a {@code status} string via the generated enum, defaulting unknown values to {@code WORKING}. */
    private static TaskStatus parseTaskStatus(String value) {
        try {
            return TaskStatus.fromValue(value);
        } catch (IllegalArgumentException e) {
            return TaskStatus.WORKING;
        }
    }

    private static TaskState toTaskState(TaskStatus status) {
        return switch (status) {
            case INPUT_REQUIRED -> TaskState.INPUT_REQUIRED;
            case COMPLETED -> TaskState.COMPLETED;
            case FAILED -> TaskState.FAILED;
            case CANCELLED -> TaskState.CANCELLED;
            case WORKING -> TaskState.WORKING;
        };
    }

    protected Map<String, Object> asMap(@Nullable Object params) {
        if (params == null) return Map.of();
        if (params instanceof Map<?, ?> map) return stringKeyed(map);
        try {
            Map<?, ?> decoded = JsonUtils.mapper().convertValue(params, Map.class);
            return stringKeyed(decoded);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    /** Deserializes a normalized params map into a generated model, or throws {@code invalid_params}. */
    protected static <T> T convert(Map<String, Object> map, Class<T> type) {
        try {
            return JsonUtils.mapper().convertValue(map, type);
        } catch (RuntimeException e) {
            throw invalidParams("Invalid " + type.getSimpleName());
        }
    }

    /** Requires a field extracted from a generated model to be non-null, or throws {@code invalid_params}. */
    private static <T> T required(@Nullable T value, String message) {
        if (value == null) throw invalidParams(message);
        return value;
    }

    private static Map<String, Object> stringKeyed(Map<?, ?> source) {
        var result = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> {
            if (key instanceof String text) result.put(text, value);
        });
        return result;
    }

    private static String requiredString(Map<String, Object> map, String key, String message) {
        if (map.get(key) instanceof String text) return text;
        throw invalidParams(message);
    }

    private static @Nullable String optionalString(Map<String, Object> map, String key, String message) {
        var value = map.get(key);
        if (value == null) return null;
        if (value instanceof String text) return text;
        throw invalidParams(message);
    }

    private static Map<String, Object> requiredMap(Map<String, Object> map, String key, String message) {
        var value = optionalMap(map, key, message);
        if (value != null) return value;
        throw invalidParams(message);
    }

    private static @Nullable Map<String, Object> optionalMap(Map<String, Object> map, String key, String message) {
        var value = map.get(key);
        if (value == null) return null;
        if (value instanceof Map<?, ?> nested) return stringKeyed(nested);
        throw invalidParams(message);
    }

    private static Map<String, String> stringMap(@Nullable Map<String, Object> map) {
        if (map == null || map.isEmpty()) return Map.of();
        var result = new LinkedHashMap<String, String>();
        map.forEach((key, value) -> {
            if (value instanceof String text) result.put(key, text);
            else throw invalidParams("context.arguments values must be strings");
        });
        return result;
    }

    private static @Nullable ProgressToken progressToken(@Nullable Map<String, Object> meta) {
        if (meta == null) return null;
        var value = meta.get("progressToken");
        if (value instanceof String text) return ProgressToken.of(text);
        if (value instanceof Number number) return ProgressToken.of(number);
        return null;
    }

    private static RequestMappingException invalidParams(String message) {
        return new RequestMappingException(ServerErrors.invalidParams(message));
    }
}
