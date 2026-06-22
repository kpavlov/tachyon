/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.OutboundSseStreamMessageRouter;
import dev.tachyonmcp.server.domain.*;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import dev.tachyonmcp.server.features.tools.*;
import dev.tachyonmcp.server.session.McpContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import dev.tachyonmcp.transport.netty.NettyServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@Tag("conformance")
abstract class AbstractServerConformanceIT {

    private static final JsonNode INPUT_SCHEMA_NO_ARGS = buildSchemaNoArgs();
    private static final JsonNode INPUT_SCHEMA_WITH_PROMPT = buildSchemaWithProp("prompt");
    private static final JsonNode INPUT_SCHEMA_WITH_MESSAGE = buildSchemaWithProp("message");

    private final String conformanceVersion;

    AbstractServerConformanceIT(String conformanceVersion) {
        this.conformanceVersion = conformanceVersion;
    }

    private static JsonNode buildSchemaNoArgs() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        schema.put("additionalProperties", false);
        return schema;
    }

    private static JsonNode buildSchemaWithProp(String propName) {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        props.putObject(propName).put("type", "string");
        schema.putArray("required").add(propName);
        schema.put("additionalProperties", false);
        return schema;
    }

    // 1x1 red pixel PNG as base64
    private static final String MINI_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==";

    // Minimal WAV header + silence
    private static final String MINI_WAV_BASE64 = "UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=";

    protected McpServer server;
    protected NettyServer nettyServer;
    protected int port;

    protected abstract McpServer createServer();

    @BeforeEach
    void setUp() {
        server = createServer();
        registerTools();
        registerResources();
        registerPrompts();
        nettyServer = new NettyServer(0, server);
        port = nettyServer.port();
    }

    private void registerTools() {
        server.registerTool(new EchoToolHandler());

        server.registerTool(SyncToolHandler.of(
                "test_simple_text",
                "Returns simple text",
                INPUT_SCHEMA_NO_ARGS,
                (_, _) -> ToolResult.text("This is a simple text response for testing.")));

        server.registerTool(SyncToolHandler.of(
                "test_image_content",
                "Returns image content",
                INPUT_SCHEMA_NO_ARGS,
                (_, _) -> ToolResult.of(List.of(new ImageContent(MINI_PNG_BASE64, "image/png", null)))));

        server.registerTool(SyncToolHandler.of(
                "test_audio_content",
                "Returns audio content",
                INPUT_SCHEMA_NO_ARGS,
                (_, _) -> ToolResult.of(List.of(new AudioContent(MINI_WAV_BASE64, "audio/wav", null)))));

        server.registerTool(SyncToolHandler.of(
                "test_embedded_resource",
                "Returns embedded resource",
                INPUT_SCHEMA_NO_ARGS,
                (_, _) -> ToolResult.of(List.of(new EmbeddedResource(
                        new TextResourceContents(
                                "test://embedded-resource", "text/plain", "This is an embedded resource content."),
                        null)))));

        server.registerTool(SyncToolHandler.of(
                "test_multiple_content_types",
                "Returns multiple content types",
                INPUT_SCHEMA_NO_ARGS,
                (_, _) -> ToolResult.of(List.of(
                        new TextContent("Multiple content types test:", null),
                        new ImageContent(MINI_PNG_BASE64, "image/png", null),
                        new EmbeddedResource(
                                new TextResourceContents(
                                        "test://mixed-content-resource",
                                        "application/json",
                                        "{\"test\":\"data\",\"value\":123}"),
                                null)))));

        server.registerTool(SyncToolHandler.of(
                "test_error_handling",
                "Always returns error",
                INPUT_SCHEMA_NO_ARGS,
                (_, _) -> ToolResult.error("This tool intentionally returns an error for testing")));

        server.registerTool(new ToolHandler() {
            @Override
            public ToolDescriptor descriptor() {
                return ToolDescriptor.builder("test_tool_with_progress")
                        .description("Tool with progress notifications")
                        .inputSchema(INPUT_SCHEMA_NO_ARGS)
                        .build();
            }

            @Override
            public CompletionStage<ToolResult> handle(ToolRequest request, McpContext ctx) {
                var pt = request.progressToken();
                if (pt != null) {
                    ctx.notifications().progress(pt, 0, 100, "Starting");
                    sleep(50);
                    ctx.notifications().progress(pt, 50, 100, "Halfway");
                    sleep(50);
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
            public CompletionStage<ToolResult> handle(ToolRequest request, McpContext ctx) {
                ctx.notifications().info("tachyon.tools", Map.of("message", "Tool execution started"));
                sleep(50);
                ctx.notifications().info("tachyon.tools", Map.of("message", "Tool processing data"));
                sleep(50);
                ctx.notifications().info("tachyon.tools", Map.of("message", "Tool execution completed"));
                return CompletableFuture.completedFuture(ToolResult.text("Tool execution completed"));
            }
        });

        server.registerTool(SyncToolHandler.of(
                "test_sampling", "Tool that requests sampling", INPUT_SCHEMA_WITH_PROMPT, (ctx, args) -> {
                    if (args instanceof Map<?, ?> map && map.get("prompt") != null) {
                        var prompt = map.get("prompt");
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
                            var responseJson = ctx.server()
                                    .sendRequest("sampling/createMessage", JsonRpcCodec.writeValueAsString(paramsMap))
                                    .get(2, TimeUnit.SECONDS);
                            var responseObj = (Map<String, Object>) JsonRpcCodec.readValue(responseJson);
                            var content = responseObj.get("content");
                            var text = content instanceof Map<?, ?> cm && "text".equals(cm.get("type"))
                                    ? (String) cm.get("text")
                                    : "";
                            return ToolResult.text(text);
                        } catch (Exception e) {
                            return ToolResult.error("Error: " + e.getMessage());
                        }
                    }
                    return ToolResult.text("sampling not fully implemented");
                }));

        server.registerTool(SyncToolHandler.of(
                "test_elicitation", "Tool that requests elicitation", INPUT_SCHEMA_WITH_MESSAGE, (ctx, args) -> {
                    if (args instanceof Map<?, ?> map && map.get("message") != null) {
                        var message = map.get("message");
                        try {
                            var paramsMap = Map.of(
                                    "mode", "form",
                                    "message", message.toString(),
                                    "requestedSchema",
                                            Map.of(
                                                    "type", "object",
                                                    "properties",
                                                            Map.of(
                                                                    "username", Map.of("type", "string"),
                                                                    "email", Map.of("type", "string")),
                                                    "required", List.of("username", "email")));
                            var responseJson = ctx.server()
                                    .sendRequest("elicitation/create", JsonRpcCodec.writeValueAsString(paramsMap))
                                    .get(2, TimeUnit.SECONDS);
                            var responseObj = (Map<String, Object>) JsonRpcCodec.readValue(responseJson);
                            var action = responseObj.get("action");
                            var content = responseObj.get("content");
                            var text = "User response: " + (action != null ? action : "unknown");
                            if (content instanceof Map<?, ?> cm) text += ", " + cm;
                            return ToolResult.text(text);
                        } catch (Exception e) {
                            return ToolResult.error("Error: " + e.getMessage());
                        }
                    }
                    return ToolResult.text("elicitation not fully implemented");
                }));

        server.registerTool(SyncToolHandler.of(
                "test_elicitation_sep1034_defaults", "Elicitation with defaults", INPUT_SCHEMA_NO_ARGS, (ctx, _) -> {
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
                        var responseJson = ctx.server()
                                .sendRequest("elicitation/create", JsonRpcCodec.writeValueAsString(paramsMap))
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
                "test_elicitation_sep1330_enums", "Elicitation with enums", INPUT_SCHEMA_NO_ARGS, (ctx, _) -> {
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
                        var responseJson = ctx.server()
                                .sendRequest("elicitation/create", JsonRpcCodec.writeValueAsString(paramsMap))
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

        var inputSchema = buildJsonSchema2020_12();
        server.registerTool(
                new AbstractSyncToolHandler(ToolDescriptor.builder("json_schema_2020_12_tool")
                        .description("Tool with JSON Schema 2020-12 features")
                        .inputSchema(inputSchema)
                        .build()) {
                    @Override
                    public Object handle(McpContext context, Object arguments) {
                        return ToolResult.text("JSON Schema 2020-12 tool called");
                    }
                });

        server.registerTool(SyncToolHandler.of(
                "test_reconnection",
                "A tool that triggers SSE stream closure to test client reconnection behavior",
                INPUT_SCHEMA_NO_ARGS,
                (_, _) -> {
                    var stream = OutboundSseStreamMessageRouter.currentOutboundSseStream();
                    if (stream != null) {
                        stream.start();
                        stream.close();
                    }
                    return ToolResult.text("reconnection triggered");
                }));
    }

    private static JsonNode buildJsonSchema2020_12() {
        var factory = JsonNodeFactory.instance;

        var addressDef = factory.objectNode();
        addressDef.put("$anchor", "addressDef");
        addressDef.put("type", "object");
        var addressProps = addressDef.putObject("properties");
        addressProps.putObject("street").put("type", "string");
        addressProps.putObject("city").put("type", "string");

        var defs = factory.objectNode();
        defs.set("address", addressDef);

        var properties = factory.objectNode();
        properties.putObject("name").put("type", "string");
        properties.putObject("address").put("$ref", "#/$defs/address");
        properties
                .putObject("contactMethod")
                .put("type", "string")
                .putArray("enum")
                .add("phone")
                .add("email");
        properties.putObject("phone").put("type", "string");
        properties.putObject("email").put("type", "string");

        var phoneRequired = factory.objectNode();
        phoneRequired.putArray("required").add("phone");
        var emailRequired = factory.objectNode();
        emailRequired.putArray("required").add("email");

        var allOf = factory.arrayNode();
        allOf.add(factory.objectNode()
                .set("anyOf", factory.arrayNode().add(phoneRequired).add(emailRequired)));

        var ifSchema = factory.objectNode();
        ifSchema.putObject("properties").putObject("contactMethod").put("const", "phone");
        ifSchema.putArray("required").add("contactMethod");

        var thenSchema = factory.objectNode();
        thenSchema.putArray("required").add("phone");
        var elseSchema = factory.objectNode();
        elseSchema.putArray("required").add("email");

        var schema = factory.objectNode();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.set("$defs", defs);
        schema.set("properties", properties);
        schema.set("allOf", allOf);
        schema.set("if", ifSchema);
        schema.set("then", thenSchema);
        schema.set("else", elseSchema);
        schema.put("additionalProperties", false);
        return schema;
    }

    private void registerResources() {
        server.resources()
                .add(
                        new ResourceDescriptor("hello", "hello://world", "Hello resource", "text/plain"),
                        (_, _) -> new TextResourceContents("hello://world", "text/plain", "Hello, World!"));

        server.resources()
                .add(
                        new ResourceDescriptor(
                                "static-text", "test://static-text", "Static text resource", "text/plain"),
                        (_, _) -> new TextResourceContents(
                                "test://static-text", "text/plain", "This is static text content for testing."));

        server.resources()
                .add(
                        new ResourceDescriptor(
                                "static-binary", "test://static-binary", "Static binary resource", "image/png"),
                        (_, _) -> new BlobResourceContents("test://static-binary", "image/png", MINI_PNG_BASE64));

        server.resources()
                .addTemplate(new ResourceTemplateEntry(
                        "test-template",
                        "test://template/{id}/data",
                        "Test template",
                        "text/plain",
                        id -> new TextResourceContents(
                                "test://template/" + id + "/data", "text/plain", "Resource content for id: " + id)));
    }

    private void registerPrompts() {
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
                        List.of(PromptMessage.user(new EmbeddedResource(
                                new TextResourceContents(
                                        "test://embedded-resource-content",
                                        "text/plain",
                                        "Embedded resource content for testing."),
                                null))));

        server.prompts()
                .add(
                        PromptDescriptor.of("test_prompt_with_image", "Prompt with image"),
                        List.of(PromptMessage.user(new ImageContent(MINI_PNG_BASE64, "image/png", null))));
    }

    @Test
    void serverConformance() throws Exception {
        assumeTrue(ConformanceRunner.isNpxAvailable(), "npx is not available on this system");

        var runner = new ConformanceRunner("http://localhost:" + port + "/mcp", conformanceVersion);
        var start = System.currentTimeMillis();
        var result = runner.runSuite(true, "all");
        var elapsed = System.currentTimeMillis() - start;

        var outputLines = result.outputLines();
        outputLines.forEach(line -> System.out.println("[conformance] " + line));

        var rawFile = Path.of("target", "failsafe-reports", "conformance-raw.log");
        Files.write(rawFile, outputLines);
        System.out.println("[conformance] Raw output saved: " + rawFile.toAbsolutePath());

        var scenarios = ConformanceReportWriter.parseResults(outputLines);
        var baselinePath = Path.of("conformance-baseline.yml");
        var expectedFailures =
                Files.exists(baselinePath) ? ConformanceReportWriter.parseBaseline(baselinePath) : List.<String>of();
        var reportPath = Path.of("target", "failsafe-reports", "TEST-ConformanceReport.xml");
        ConformanceReportWriter.writeReport(reportPath.toAbsolutePath(), scenarios, expectedFailures, elapsed);
        System.out.println("[conformance] Report written: " + reportPath.toAbsolutePath());

        assertThat(result.finished())
                .as("Conformance suite should complete within timeout")
                .isTrue();

        var unexpectedFailures = scenarios.stream()
                .filter(s -> s.failed() > 0)
                .filter(s -> !expectedFailures.contains(s.name()))
                .toList();
        assertThat(unexpectedFailures)
                .as("Unexpected conformance failures:\n" + unexpectedFailures)
                .isEmpty();
    }

    @AfterEach
    void tearDown() {
        nettyServer.close();
        server.close();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class EchoToolHandler extends AbstractSyncToolHandler {

        private static final JsonNode INPUT_SCHEMA = buildEchoSchema();

        private static JsonNode buildEchoSchema() {
            var schema = JsonNodeFactory.instance.objectNode();
            schema.put("type", "object");
            schema.putObject("properties")
                    .putObject("message")
                    .put("type", "string")
                    .put("description", "Message to echo");
            schema.putArray("required").add("message");
            return schema;
        }

        EchoToolHandler() {
            super(ToolDescriptor.builder("echo")
                    .description("Echo back the input message")
                    .inputSchema(INPUT_SCHEMA)
                    .build());
        }

        @Override
        public Object handle(McpContext context, Object arguments) {
            if (!(arguments instanceof Map<?, ?> map)) {
                return ToolResult.text("");
            }
            var message = map.get("message");
            return ToolResult.text(message != null ? message.toString() : "");
        }
    }
}
