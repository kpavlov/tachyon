/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.conformance;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.OutboundSseStreamMessageRouter;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.domain.*;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptHandlerResult;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import dev.tachyonmcp.server.features.tools.*;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.awaitility.Awaitility;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

abstract class AbstractConformanceServer {

    // language=json
    private static final JsonNode INPUT_SCHEMA_NO_ARGS = parseJson("""
        {"type": "object", "properties": {}, "additionalProperties": false}
        """);

    // language=json
    private static final JsonNode INPUT_SCHEMA_WITH_PROMPT = parseJson("""
        {
            "type": "object",
            "properties": {"prompt": {"type": "string"}},
            "required": ["prompt"],
            "additionalProperties": false
        }
        """);

    // language=json
    private static final JsonNode INPUT_SCHEMA_WITH_MESSAGE = parseJson("""
        {
            "type": "object",
            "properties": {"message": {"type": "string"}},
            "required": ["message"],
            "additionalProperties": false
        }
        """);

    private static final String MINI_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==";

    private static final String MINI_WAV_BASE64 = "UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=";

    private static final String HMAC_SECRET = "conformance-test-hmac-secret";

    private static JsonNode parseJson(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static FormInputRequest buildFormElicitation(String message, String propName, String propType) {
        var schema = new LinkedHashMap<String, JsonNode>();
        schema.put("type", JsonNodeFactory.instance.stringNode("object"));
        var props = JsonNodeFactory.instance.objectNode();
        props.putObject(propName).put("type", propType);
        schema.put("properties", props);
        schema.put("required", JsonNodeFactory.instance.arrayNode().add(propName));
        return FormInputRequest.of(message, schema);
    }

    private static RpcMethodRequest buildSamplingRequest(String questionText) {
        var messages = JsonNodeFactory.instance.arrayNode();
        var msg = JsonNodeFactory.instance.objectNode();
        msg.put("role", "user");
        var content = msg.putObject("content");
        content.put("type", "text");
        content.put("text", questionText);
        messages.add(msg);
        var params = JsonNodeFactory.instance.objectNode();
        params.set("messages", messages);
        params.put("maxTokens", 100);
        return RpcMethodRequest.of("sampling/createMessage", params);
    }

    private static RpcMethodRequest buildRootsListRequest() {
        return RpcMethodRequest.of("roots/list", JsonNodeFactory.instance.objectNode());
    }

    private static String signState(String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(), "HmacSHA256"));
            var hmac = mac.doFinal(payload.getBytes());
            return payload + "." + Base64.getEncoder().encodeToString(hmac);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean verifyState(String signedState) {
        if (signedState == null) return false;
        var lastDot = signedState.lastIndexOf('.');
        if (lastDot < 0) return false;
        var payload = signedState.substring(0, lastDot);
        return signState(payload).equals(signedState);
    }

    private static JsonNode buildJsonSchema() {
        // language=json
        return parseJson("""
                {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "object",
                    "$defs": {
                        "address": {
                            "$anchor": "addressDef",
                            "type": "object",
                            "properties": {
                                "street": {"type": "string"},
                                "city": {"type": "string"}
                            }
                        }
                    },
                    "properties": {
                        "name": {"type": "string"},
                        "address": {"$ref": "#/$defs/address"},
                        "contactMethod": {"type": "string", "enum": ["phone", "email"]},
                        "phone": {"type": "string"},
                        "email": {"type": "string"}
                    },
                    "allOf": [{"anyOf": [{"required": ["phone"]}, {"required": ["email"]}]}],
                    "if": {
                        "properties": {"contactMethod": {"const": "phone"}},
                        "required": ["contactMethod"]
                    },
                    "then": {"required": ["phone"]},
                    "else": {"required": ["email"]},
                    "additionalProperties": false
                }
                """);
    }

    private static void delay(long millis) {
        Awaitility.await().timeout(millis, TimeUnit.MILLISECONDS);
    }

    protected abstract Server createServer(boolean isStateful);

    Server startServer(boolean isStateful) {
        var srv = createServer(isStateful);
        registerTools(srv);
        registerResources(srv);
        registerPrompts(srv);
        return srv;
    }

    private void registerTools(Server server) {
        server.registerTool(new EchoToolHandler());

        server.registerTool(SyncToolHandler.of(
                "test_simple_text",
                "Returns simple text",
                INPUT_SCHEMA_NO_ARGS,
                (ctx, args) -> ToolResult.text("This is a simple text response for testing.")));

        server.registerTool(SyncToolHandler.of(
                "test_image_content",
                "Returns image content",
                INPUT_SCHEMA_NO_ARGS,
                (ctx, args) -> ToolResult.blocks(ImageContent.of(MINI_PNG_BASE64, "image/png"))));

        server.registerTool(SyncToolHandler.of(
                "test_audio_content",
                "Returns audio content",
                INPUT_SCHEMA_NO_ARGS,
                (ctx, args) -> ToolResult.blocks(AudioContent.of(MINI_WAV_BASE64, "audio/wav"))));

        server.registerTool(SyncToolHandler.of(
                "test_embedded_resource", "Returns embedded resource", INPUT_SCHEMA_NO_ARGS, (ctx, args) -> {
                    var res = TextResourceContents.of(
                            "test://embedded-resource", "text/plain", "This is an embedded resource content.");
                    return ToolResult.blocks(EmbeddedResource.of(res));
                }));

        server.registerTool(SyncToolHandler.of(
                "test_multiple_content_types", "Returns multiple content types", INPUT_SCHEMA_NO_ARGS, (ctx, args) -> {
                    var mixed = TextResourceContents.of(
                            "test://mixed-content-resource", "application/json", "{\"test\":\"data\",\"value\":123}");
                    return ToolResult.blocks(
                            TextContent.of("Multiple content types test:"),
                            ImageContent.of(MINI_PNG_BASE64, "image/png"),
                            EmbeddedResource.of(mixed));
                }));

        server.registerTool(SyncToolHandler.of(
                "test_error_handling",
                "Always returns error",
                INPUT_SCHEMA_NO_ARGS,
                (ctx, args) -> ToolResult.error("This tool intentionally returns an error for testing")));

        server.registerTool(new ToolHandler() {
            @Override
            public ToolDescriptor descriptor() {
                return ToolDescriptor.builder("test_tool_with_progress")
                        .description("Tool with progress notifications")
                        .inputSchema(INPUT_SCHEMA_NO_ARGS)
                        .build();
            }

            @Override
            public CompletionStage<? extends ToolResult> handle(InteractionContext ctx, ToolRequest request) {
                var pt = request.progressToken();
                if (pt != null) {
                    ctx.notifications().progress(pt, 0, 100, "Starting");
                    delay(50);
                    ctx.notifications().progress(pt, 50, 100, "Halfway");
                    delay(50);
                    ctx.notifications().progress(pt, 100, 100, "Complete");
                }
                return CompletableFuture.completedFuture(ToolResult.text("Tool execution completed"));
            }
        });

        server.registerTool(new ToolHandler() {
            @Override
            public ToolDescriptor descriptor() {
                return ToolDescriptor.builder("test_tool_with_logging")
                        .description("Tool with logging")
                        .inputSchema(INPUT_SCHEMA_NO_ARGS)
                        .build();
            }

            @Override
            public CompletionStage<? extends ToolResult> handle(InteractionContext ctx, ToolRequest request) {
                ctx.notifications().info("tachyon.tools", Map.of("message", "Tool execution started"));
                delay(50);
                ctx.notifications().info("tachyon.tools", Map.of("message", "Tool processing data"));
                delay(50);
                ctx.notifications().info("tachyon.tools", Map.of("message", "Tool execution completed"));
                return CompletableFuture.completedFuture(ToolResult.text("Tool execution completed"));
            }
        });

        server.registerTool(SyncToolHandler.of(
                "test_sampling", "Tool that requests sampling", INPUT_SCHEMA_WITH_PROMPT, (ctx, args) -> {
                    var promptOpt = args.stringOpt("prompt");
                    if (promptOpt.isPresent()) {
                        var prompt = promptOpt.get();
                        try {
                            var paramsMap = Map.of(
                                    "messages",
                                    List.of(Map.of(
                                            "role",
                                            "user",
                                            "content",
                                            Map.of("type", "text", "text", prompt.toString()))),
                                    "maxTokens",
                                    100);
                            var responseJson = ctx.sendRequest(
                                            "sampling/createMessage", JsonRpcCodec.writeValueAsString(paramsMap))
                                    .get(2, TimeUnit.SECONDS);
                            var responseObj = (Map<String, Object>) JsonRpcCodec.readValue(responseJson);
                            var content = responseObj.get("content");
                            var text = content instanceof Map<?, ?> cm && "text".equals(cm.get("type"))
                                    ? (String) cm.get("text")
                                    : "";
                            return ToolResult.text(text);
                        } catch (Exception e) {
                            return ToolResult.error("Sampling request failed");
                        }
                    }
                    return ToolResult.text("sampling not fully implemented");
                }));

        server.registerTool(SyncToolHandler.of(
                "test_elicitation", "Tool that requests elicitation", INPUT_SCHEMA_WITH_MESSAGE, (ctx, args) -> {
                    var messageOpt = args.stringOpt("message");
                    if (messageOpt.isPresent()) {
                        var message = messageOpt.get();
                        try {
                            var paramsMap = Map.of(
                                    "mode",
                                    "form",
                                    "message",
                                    message,
                                    "requestedSchema",
                                    Map.of(
                                            "type", "object",
                                            "properties",
                                                    Map.of(
                                                            "username", Map.of("type", "string"),
                                                            "email", Map.of("type", "string")),
                                            "required", List.of("username", "email")));
                            var responseJson = ctx.sendRequest(
                                            "elicitation/create", JsonRpcCodec.writeValueAsString(paramsMap))
                                    .get(2, TimeUnit.SECONDS);
                            var responseObj = (Map<String, Object>) JsonRpcCodec.readValue(responseJson);
                            var action = responseObj.get("action");
                            var content = responseObj.get("content");
                            var text = "User response: " + (action != null ? action : "unknown");
                            if (content instanceof Map<?, ?> cm) text += ", " + cm;
                            return ToolResult.text(text);
                        } catch (Exception e) {
                            return ToolResult.error("Elicitation request failed");
                        }
                    }
                    return ToolResult.text("elicitation not fully implemented");
                }));

        server.registerTool(SyncToolHandler.of(
                "test_elicitation_sep1034_defaults", "Elicitation with defaults", INPUT_SCHEMA_NO_ARGS, (ctx, args) -> {
                    try {
                        var paramsMap = Map.of(
                                "mode", "form",
                                "message", "Please provide your details with defaults",
                                "requestedSchema",
                                        Map.of(
                                                "type",
                                                "object",
                                                "properties",
                                                Map.<String, Object>of(
                                                        "name", Map.of("type", "string", "default", "John Doe"),
                                                        "age", Map.of("type", "integer", "default", 30),
                                                        "score", Map.of("type", "number", "default", 95.5),
                                                        "status",
                                                                Map.of(
                                                                        "type",
                                                                        "string",
                                                                        "enum",
                                                                        List.of("active", "inactive"),
                                                                        "default",
                                                                        "active"),
                                                        "verified", Map.of("type", "boolean", "default", true))));
                        var responseJson = ctx.sendRequest(
                                        "elicitation/create", JsonRpcCodec.writeValueAsString(paramsMap))
                                .get(2, TimeUnit.SECONDS);
                        var responseObj = (Map<String, Object>) JsonRpcCodec.readValue(responseJson);
                        var action = responseObj.get("action");
                        var content = responseObj.get("content");
                        var text = "Defaults " + (action != null ? action : "unknown");
                        if (content instanceof Map<?, ?> cm) text += ", " + cm;
                        return ToolResult.text(text);
                    } catch (Exception e) {
                        return ToolResult.error("Error: " + e.getMessage());
                    }
                }));

        server.registerTool(SyncToolHandler.of(
                "test_elicitation_sep1330_enums", "Elicitation with enums", INPUT_SCHEMA_NO_ARGS, (ctx, args) -> {
                    try {
                        var props = new LinkedHashMap<String, Object>();
                        props.put(
                                "untitledSingle",
                                Map.of("type", "string", "enum", List.of("option1", "option2", "option3")));
                        props.put(
                                "titledSingle",
                                Map.of(
                                        "type",
                                        "string",
                                        "oneOf",
                                        List.of(
                                                Map.of("const", "opt_a", "title", "Option A"),
                                                Map.of("const", "opt_b", "title", "Option B"))));
                        props.put(
                                "legacyEnum",
                                Map.of(
                                        "type",
                                        "string",
                                        "enum",
                                        List.of("val1", "val2"),
                                        "enumNames",
                                        List.of("Value 1", "Value 2")));
                        props.put(
                                "untitledMulti",
                                Map.of(
                                        "type",
                                        "array",
                                        "items",
                                        Map.of("type", "string", "enum", List.of("x", "y", "z"))));
                        props.put(
                                "titledMulti",
                                Map.of(
                                        "type",
                                        "array",
                                        "items",
                                        Map.of(
                                                "type",
                                                "string",
                                                "anyOf",
                                                List.of(
                                                        Map.of("const", "item1", "title", "Item One"),
                                                        Map.of("const", "item2", "title", "Item Two")))));
                        var paramsMap = Map.of(
                                "mode", "form",
                                "message", "Please select your preferences",
                                "requestedSchema", Map.of("type", "object", "properties", props));
                        var responseJson = ctx.sendRequest(
                                        "elicitation/create", JsonRpcCodec.writeValueAsString(paramsMap))
                                .get(2, TimeUnit.SECONDS);
                        var responseObj = (Map<String, Object>) JsonRpcCodec.readValue(responseJson);
                        var action = responseObj.get("action");
                        var content = responseObj.get("content");
                        var text = "Enums " + (action != null ? action : "unknown");
                        if (content instanceof Map<?, ?> cm) text += ", " + cm;
                        return ToolResult.text(text);
                    } catch (Exception e) {
                        return ToolResult.error("Error: " + e.getMessage());
                    }
                }));

        var inputSchema = buildJsonSchema();
        server.registerTool(
                new AbstractSyncToolHandler(ToolDescriptor.builder("json_schema_2020_12_tool")
                        .description("Tool with JSON Schema 2020-12 features")
                        .inputSchema(inputSchema)
                        .build()) {

                    @Override
                    public ToolResult handle(InteractionContext context, ToolArgs arguments) {
                        return ToolResult.text("JSON Schema 2020-12 tool called");
                    }
                });

        server.registerTool(SyncToolHandler.of(
                "test_reconnection",
                "A tool that triggers SSE stream closure to test client reconnection behavior",
                INPUT_SCHEMA_NO_ARGS,
                (ctx, args) -> {
                    var stream = OutboundSseStreamMessageRouter.currentOutboundSseStream();
                    if (stream != null) {
                        stream.start();
                        stream.close();
                    }
                    return ToolResult.text("reconnection triggered");
                }));

        registerInputRequiredTools(server);
    }

    private void registerInputRequiredTools(Server server) {
        server.registerTool(new ToolHandler() {
            @Override
            public ToolDescriptor descriptor() {
                return ToolDescriptor.builder("test_input_required_result_elicitation")
                        .description("SEP-2322 elicitation InputRequiredResult")
                        .inputSchema(INPUT_SCHEMA_NO_ARGS)
                        .build();
            }

            @Override
            public CompletionStage<ToolResult> handle(InteractionContext ctx, ToolRequest request) {
                var inputResponses = request.inputResponses();
                if (inputResponses != null && inputResponses.containsKey("user_name")) {
                    var resp = inputResponses.get("user_name");
                    if (resp != null && resp.isObject()) {
                        var content = resp.path("content");
                        var name = content.has("name") ? content.get("name").asString() : "World";
                        return CompletableFuture.completedFuture(ToolResult.text("Hello, " + name + "!"));
                    }
                }
                return CompletableFuture.completedFuture(ToolResult.inputRequired(
                        Map.of("user_name", buildFormElicitation("What is your name?", "name", "string")), null));
            }
        });

        server.registerTool(new ToolHandler() {
            @Override
            public ToolDescriptor descriptor() {
                return ToolDescriptor.builder("test_input_required_result_sampling")
                        .description("SEP-2322 sampling InputRequiredResult")
                        .inputSchema(INPUT_SCHEMA_NO_ARGS)
                        .build();
            }

            @Override
            public CompletionStage<ToolResult> handle(InteractionContext ctx, ToolRequest request) {
                var inputResponses = request.inputResponses();
                if (inputResponses != null && inputResponses.containsKey("capital_question")) {
                    var resp = inputResponses.get("capital_question");
                    var text = resp != null && resp.path("content").has("text")
                            ? resp.path("content").get("text").asString()
                            : "done";
                    return CompletableFuture.completedFuture(ToolResult.text(text));
                }
                return CompletableFuture.completedFuture(ToolResult.inputRequired(
                        Map.of("capital_question", buildSamplingRequest("What is the capital of France?")), null));
            }
        });

        server.registerTool(new ToolHandler() {
            @Override
            public ToolDescriptor descriptor() {
                return ToolDescriptor.builder("test_input_required_result_list_roots")
                        .description("SEP-2322 roots/list InputRequiredResult")
                        .inputSchema(INPUT_SCHEMA_NO_ARGS)
                        .build();
            }

            @Override
            public CompletionStage<ToolResult> handle(InteractionContext ctx, ToolRequest request) {
                var inputResponses = request.inputResponses();
                if (inputResponses != null && inputResponses.containsKey("client_roots")) {
                    return CompletableFuture.completedFuture(ToolResult.text("Roots received"));
                }
                return CompletableFuture.completedFuture(
                        ToolResult.inputRequired(Map.of("client_roots", buildRootsListRequest()), null));
            }
        });

        server.registerTool(new ToolHandler() {
            @Override
            public ToolDescriptor descriptor() {
                return ToolDescriptor.builder("test_input_required_result_request_state")
                        .description("SEP-2322 requestState round-trip")
                        .inputSchema(INPUT_SCHEMA_NO_ARGS)
                        .build();
            }

            @Override
            public CompletionStage<ToolResult> handle(InteractionContext ctx, ToolRequest request) {
                var inputResponses = request.inputResponses();
                var requestState = request.requestState();
                if (inputResponses != null && inputResponses.containsKey("confirm") && requestState != null) {
                    return CompletableFuture.completedFuture(ToolResult.text("state-ok"));
                }
                return CompletableFuture.completedFuture(ToolResult.inputRequired(
                        Map.of("confirm", buildFormElicitation("Please confirm", "ok", "boolean")),
                        "opaque-server-state"));
            }
        });

        server.registerTool(new ToolHandler() {
            @Override
            public ToolDescriptor descriptor() {
                return ToolDescriptor.builder("test_input_required_result_multiple_inputs")
                        .description("SEP-2322 multiple inputRequests")
                        .inputSchema(INPUT_SCHEMA_NO_ARGS)
                        .build();
            }

            @Override
            public CompletionStage<ToolResult> handle(InteractionContext ctx, ToolRequest request) {
                var inputResponses = request.inputResponses();
                if (inputResponses != null
                        && inputResponses.containsKey("user_name")
                        && inputResponses.containsKey("greeting")
                        && inputResponses.containsKey("client_roots")) {
                    return CompletableFuture.completedFuture(ToolResult.text("All inputs received"));
                }
                var inputRequests = new LinkedHashMap<String, InputRequest>();
                inputRequests.put("user_name", buildFormElicitation("What is your name?", "name", "string"));
                inputRequests.put("greeting", buildSamplingRequest("Generate a greeting"));
                inputRequests.put("client_roots", buildRootsListRequest());
                return CompletableFuture.completedFuture(ToolResult.inputRequired(inputRequests, "multi-input-state"));
            }
        });

        server.registerTool(new ToolHandler() {
            @Override
            public ToolDescriptor descriptor() {
                return ToolDescriptor.builder("test_input_required_result_multi_round")
                        .description("SEP-2322 multi-round InputRequiredResult")
                        .inputSchema(INPUT_SCHEMA_NO_ARGS)
                        .build();
            }

            @Override
            public CompletionStage<ToolResult> handle(InteractionContext ctx, ToolRequest request) {
                var inputResponses = request.inputResponses();
                var requestState = request.requestState();
                if (inputResponses != null && inputResponses.containsKey("step2")) {
                    var color = inputResponses
                            .get("step2")
                            .path("content")
                            .path("color")
                            .asString("unknown");
                    return CompletableFuture.completedFuture(ToolResult.text("Done with color: " + color));
                }
                if (inputResponses != null
                        && inputResponses.containsKey("step1")
                        && "state-round-1".equals(requestState)) {
                    return CompletableFuture.completedFuture(ToolResult.inputRequired(
                            Map.of(
                                    "step2",
                                    buildFormElicitation("Step 2: What is your favorite color?", "color", "string")),
                            "state-round-2"));
                }
                return CompletableFuture.completedFuture(ToolResult.inputRequired(
                        Map.of("step1", buildFormElicitation("Step 1: What is your name?", "name", "string")),
                        "state-round-1"));
            }
        });

        server.registerTool(new ToolHandler() {
            @Override
            public ToolDescriptor descriptor() {
                return ToolDescriptor.builder("test_input_required_result_tampered_state")
                        .description("SEP-2322 tampered requestState rejection")
                        .inputSchema(INPUT_SCHEMA_NO_ARGS)
                        .build();
            }

            @Override
            public CompletionStage<ToolResult> handle(InteractionContext ctx, ToolRequest request) {
                var inputResponses = request.inputResponses();
                var requestState = request.requestState();
                if (inputResponses != null && inputResponses.containsKey("confirm")) {
                    if (requestState == null || !verifyState(requestState)) {
                        throw new IllegalArgumentException("Invalid or tampered requestState");
                    }
                    return CompletableFuture.completedFuture(ToolResult.text("State verified"));
                }
                return CompletableFuture.completedFuture(ToolResult.inputRequired(
                        Map.of("confirm", buildFormElicitation("Please confirm", "ok", "boolean")),
                        signState("tamper-check")));
            }
        });

        server.registerTool(new ToolHandler() {
            @Override
            public ToolDescriptor descriptor() {
                return ToolDescriptor.builder("test_input_required_result_capabilities")
                        .description("SEP-2322 respect client capabilities")
                        .inputSchema(INPUT_SCHEMA_NO_ARGS)
                        .build();
            }

            @Override
            public CompletionStage<ToolResult> handle(InteractionContext ctx, ToolRequest request) {
                var meta = request.meta();
                var capabilities = meta != null ? meta.get("io.modelcontextprotocol/clientCapabilities") : null;
                var hasSampling =
                        capabilities != null && !capabilities.path("sampling").isMissingNode();
                var hasElicitation = capabilities != null
                        && !capabilities.path("elicitation").isMissingNode();
                var inputRequests = new LinkedHashMap<String, InputRequest>();
                if (hasSampling) {
                    inputRequests.put("ai_response", buildSamplingRequest("Generate a helpful response"));
                }
                if (hasElicitation) {
                    inputRequests.put("user_name", buildFormElicitation("What is your name?", "name", "string"));
                }
                if (inputRequests.isEmpty()) {
                    return CompletableFuture.completedFuture(ToolResult.text("No capabilities declared"));
                }
                return CompletableFuture.completedFuture(ToolResult.inputRequired(inputRequests, null));
            }
        });
    }

    private void registerResources(Server server) {
        server.resources()
                .add(
                        ResourceDescriptor.of("hello", "hello://world", "Hello resource", "text/plain"),
                        (ctx, req) -> TextResourceContents.of("hello://world", "text/plain", "Hello, World!"));

        server.resources()
                .add(
                        ResourceDescriptor.of(
                                "static-text", "test://static-text", "Static text resource", "text/plain"),
                        (ctx, req) -> TextResourceContents.of(
                                "test://static-text", "text/plain", "This is static text content for testing."));

        server.resources()
                .add(
                        ResourceDescriptor.of(
                                "static-binary", "test://static-binary", "Static binary resource", "image/png"),
                        (ctx, req) -> BlobResourceContents.of("test://static-binary", "image/png", MINI_PNG_BASE64));

        server.resources()
                .addTemplate(ResourceTemplateEntry.of(
                        "test-template",
                        "test://template/{id}/data",
                        "Test template",
                        "text/plain",
                        (ctx, uri, params) -> TextResourceContents.of(
                                uri, "text/plain", "Resource content for id: " + params.get("id"))));
    }

    private void registerPrompts(Server server) {
        server.prompts()
                .add(PromptDescriptor.of("greeting", "A greeting prompt"), List.of(PromptMessage.user("Hello!")));

        server.prompts()
                .add(
                        PromptDescriptor.of("test_simple_prompt", "A simple test prompt"),
                        List.of(PromptMessage.user("This is a simple test prompt.")));

        server.prompts()
                .add(
                        PromptDescriptor.of("test_prompt_with_arguments", "A parameterized prompt"),
                        args -> List.of(PromptMessage.user("Prompt with arguments: " + args)));

        server.prompts()
                .add(
                        PromptDescriptor.of("test_prompt_with_embedded_resource", "Prompt with embedded resource"),
                        List.of(PromptMessage.user(EmbeddedResource.of(TextResourceContents.of(
                                "test://embedded-resource-content",
                                "text/plain",
                                "Embedded resource content for testing.")))));

        server.prompts()
                .add(
                        PromptDescriptor.of("test_prompt_with_image", "Prompt with image"),
                        List.of(PromptMessage.user(ImageContent.of(MINI_PNG_BASE64, "image/png"))));

        server.prompts()
                .add(
                        PromptDescriptor.of(
                                "test_input_required_result_prompt", "Prompt requiring elicitation input (SEP-2322)"),
                        (args, inputResponses, requestState) -> {
                            if (inputResponses != null && inputResponses.containsKey("user_context")) {
                                return PromptHandlerResult.messages(List.of(PromptMessage.user("Context received")));
                            }
                            return PromptHandlerResult.inputRequired(
                                    Map.of(
                                            "user_context",
                                            buildFormElicitation(
                                                    "What context should the prompt use?", "context", "string")),
                                    null);
                        });
    }

    private static class EchoToolHandler extends AbstractSyncToolHandler {

        // language=json
        private static final JsonNode INPUT_SCHEMA = parseJson("""
            {
                "type": "object",
                "properties": {"message": {"type": "string", "description": "Message to echo"}},
                "required": ["message"]
            }
            """);

        EchoToolHandler() {
            super(ToolDescriptor.builder("echo")
                    .description("Echo back the input message")
                    .inputSchema(INPUT_SCHEMA)
                    .build());
        }

        @Override
        public ToolResult handle(InteractionContext context, ToolArgs arguments) {
            var message = arguments.stringOr("message", "");
            return ToolResult.text(message);
        }
    }
}
