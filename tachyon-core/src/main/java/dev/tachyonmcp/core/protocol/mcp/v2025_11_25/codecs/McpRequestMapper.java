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
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.api.server.features.tools.ToolRequest;
import dev.tachyonmcp.core.protocol.ProtocolRequestMapper;
import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Request mapper for MCP 2025-11-25 and its backward-compatible request shapes.
 *
 * <p>Also parses the 2026-07-28 {@code inputResponses}/{@code requestState} fields (SEP-2322)
 * unconditionally: they're simply absent on 2025-11-25 wire payloads, so this is a no-op for that
 * version. The 2026-07-28 mapper reuses this class as-is (no override needed).
 */
public class McpRequestMapper implements ProtocolRequestMapper {

    private static final String META_LOG_LEVEL_KEY = "io.modelcontextprotocol/logLevel";
    private static final JsonMapper JSON = new JsonMapper();

    @Override
    public PageRequest page(@Nullable Object params) {
        var map = asMap(params);
        var limit = map.get("limit") instanceof Number number ? number.intValue() : 0;
        var cursor = map.get("cursor") instanceof String text ? text : null;
        return new PageRequest(limit, cursor);
    }

    @Override
    public ToolCallRequest callTool(@Nullable Object params, PayloadDeserializer payloadDeserializer) {
        var map = asMap(params);
        var name = requiredString(map, "name", "Missing tool name");
        var arguments = optionalMap(map, "arguments", "Invalid arguments");
        var meta = optionalMap(map, "_meta", "Invalid _meta");
        var inputResponses = optionalMap(map, "inputResponses", "Invalid inputResponses");
        var requestState = optionalString(map, "requestState", "Invalid requestState");
        var task = optionalMap(map, "task", "Invalid task metadata");
        var ttl = task != null && task.get("ttl") instanceof Number value ? Duration.ofMillis(value.longValue()) : null;
        var request = ToolRequest.builder()
                .name(name)
                .arguments(Args.of(arguments, payloadDeserializer))
                .meta(meta)
                .progressToken(progressToken(meta))
                .payloadDeserializer(payloadDeserializer)
                .inputResponses(inputResponses)
                .requestState(requestState)
                .build();
        return new ToolCallRequest(request, task != null, ttl);
    }

    @Override
    public PromptCallRequest getPrompt(@Nullable Object params) {
        var map = asMap(params);
        var name = requiredString(map, "name", "Missing prompt name");
        var arguments = optionalMap(map, "arguments", "Invalid arguments");
        var inputResponses = optionalMap(map, "inputResponses", "Invalid inputResponses");
        var requestState = optionalString(map, "requestState", "Invalid requestState");
        var meta = optionalMap(map, "_meta", "Invalid _meta");
        return new PromptCallRequest(
                name,
                new PromptRequest(
                        arguments != null ? Args.of(arguments) : Args.empty(), inputResponses, requestState, meta));
    }

    @Override
    public ResourceRequest readResource(@Nullable Object params) {
        var map = asMap(params);
        return ResourceRequest.builder()
                .uri(requiredString(map, "uri", "Missing resource URI"))
                .meta(optionalMap(map, "_meta", "Invalid _meta"))
                .inputResponses(optionalMap(map, "inputResponses", "Invalid inputResponses"))
                .requestState(optionalString(map, "requestState", "Invalid requestState"))
                .build();
    }

    @Override
    public CompletionCallRequest complete(@Nullable Object params) {
        var map = asMap(params);
        var ref = requiredMap(map, "ref", "Missing or invalid ref parameter");
        var argument = requiredMap(map, "argument", "Missing or invalid argument parameter");
        var argumentName = requiredString(argument, "name", "argument.name and argument.value are required");
        var argumentValue = requiredString(argument, "value", "argument.name and argument.value are required");
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
    public String taskId(@Nullable Object params) {
        return requiredString(asMap(params), "taskId", "Missing taskId");
    }

    @Override
    public LoggingLevel loggingLevel(@Nullable Object params) {
        var value = requiredString(asMap(params), "level", "Missing level parameter");
        try {
            return LoggingLevel.fromValue(value);
        } catch (IllegalArgumentException e) {
            throw invalidParams("Invalid logging level: " + value);
        }
    }

    @Override
    public InitializeRequest initialize(@Nullable Object params) {
        var capabilities = optionalMap(asMap(params), "capabilities", "Invalid capabilities");
        var extensions = capabilities != null ? optionalMap(capabilities, "extensions", "Invalid extensions") : null;
        if (extensions == null || extensions.isEmpty()) return new InitializeRequest(Map.of());
        var mapped = new LinkedHashMap<String, JsonObject>();
        extensions.forEach((key, value) ->
                mapped.put(key, value instanceof Map<?, ?> map ? JsonObject.of(stringKeyed(map)) : JsonObject.empty()));
        return new InitializeRequest(Map.copyOf(mapped));
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
        var reason = map.get("reason") instanceof String text ? text : null;
        return new CancellationRequest(RequestId.of(rawId), reason);
    }

    @Override
    public @Nullable TaskStatusRequest taskStatus(@Nullable Object params) {
        var map = asMap(params);
        if (!(map.get("taskId") instanceof String taskId)) return null;
        var status = map.get("status");
        var state = "input_required".equals(status)
                ? TaskState.INPUT_REQUIRED
                : "completed".equals(status)
                        ? TaskState.COMPLETED
                        : "failed".equals(status)
                                ? TaskState.FAILED
                                : "cancelled".equals(status) ? TaskState.CANCELLED : TaskState.WORKING;
        var message = map.get("statusMessage") instanceof String text ? text : null;
        return new TaskStatusRequest(taskId, state, message);
    }

    protected Map<String, Object> asMap(@Nullable Object params) {
        if (params == null) return Map.of();
        if (params instanceof Map<?, ?> map) return stringKeyed(map);
        try {
            Map<?, ?> decoded = JSON.convertValue(params, Map.class);
            return stringKeyed(decoded);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
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
