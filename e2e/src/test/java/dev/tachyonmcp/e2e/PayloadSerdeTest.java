/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.json.JsonSchemaValidator;
import dev.tachyonmcp.server.json.NetworkntJsonSchemaValidator;
import dev.tachyonmcp.server.json.PayloadSerde;
import java.lang.reflect.Type;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class PayloadSerdeTest extends AbstractMcpE2eTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    // region: Custom serde (Gson) round-trip

    @Test
    void shouldRoundTripWithGsonSerde() throws Exception {
        var gson = new Gson();
        PayloadSerde gsonSerde = new PayloadSerde() {
            @Override
            public String serialize(Object value) {
                return gson.toJson(value);
            }

            @Override
            public <T> T deserialize(String json, Type targetType) {
                return gson.fromJson(json, targetType);
            }
        };

        startServer(it -> it.payloadSerde(gsonSerde)
                .tool(ToolHandler.of(
                        "gson-tool",
                        "Gson tool",
                        (ctx, args) -> ToolResult.of(Map.of("message", "hello from Gson"), "text fallback"))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            // language=json
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"gson-tool","arguments":{}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.body())
                    .inPath("$.result.structuredContent.message")
                    .isEqualTo("hello from Gson");
            assertThatJson(response.body()).inPath("$.result.content[0].text").isEqualTo("text fallback");
        }
    }

    // endregion

    // region: RawJson byte-exact passthrough

    @Test
    void shouldPassthroughRawJson() throws Exception {
        startServer(it -> it.tool(ToolHandler.of(
                "raw-tool", "Raw JSON tool", (ctx, args) -> ToolResult.raw("{\"echo\":\"exact\"}", "raw fallback"))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"raw-tool","arguments":{}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.body())
                    .inPath("$.result.structuredContent.echo")
                    .isEqualTo("exact");
            assertThatJson(response.body()).inPath("$.result.content[0].text").isEqualTo("raw fallback");
        }
    }

    // endregion

    // region: Split validators — input=noop, output=strict

    @Test
    void shouldApplyOutputValidationWithNoopInputValidator() throws Exception {
        var outputSchema = JSON.objectNode();
        outputSchema.put("type", "object");
        var props = outputSchema.putObject("properties");
        var msgProp = props.putObject("message");
        msgProp.put("type", "string");
        var req = outputSchema.putArray("required");
        req.add("message");

        startServer(it -> it.inputSchemaValidator(JsonSchemaValidator.noop())
                .outputSchemaValidator(new NetworkntJsonSchemaValidator())
                .tool(ToolHandler.of(
                        ToolDescriptor.builder()
                                .name("validated-output")
                                .description("Validated output")
                                .outputSchema(outputSchema)
                                .build(),
                        (ctx, args) -> ToolResult.of(Map.of("message", "valid", "extra", 42), "ok"))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"validated-output","arguments":{"message":"test"}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.body())
                    .inPath("$.result.structuredContent.message")
                    .isEqualTo("valid");
        }
    }

    // endregion

    // region: ToolArgs.decode via custom serde (Java API)

    @Test
    void shouldDecodeArgsViaCustomSerde() throws Exception {
        var gson = new Gson();
        PayloadSerde gsonSerde = new PayloadSerde() {
            @Override
            public String serialize(Object value) {
                return gson.toJson(value);
            }

            @Override
            public <T> T deserialize(String json, Type targetType) {
                return gson.fromJson(json, targetType);
            }
        };

        startServer(it -> it.payloadSerde(gsonSerde).tool(ToolHandler.of("decode-tool", "Decode tool", (ctx, args) -> {
            var decoded = args.decode(Map.class);
            return ToolResult.text("decoded: " + decoded);
        })));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"decode-tool","arguments":{"key":"value"}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.body()).inPath("$.result.content[0].text").isEqualTo("decoded: {key=value}");
        }
    }

    // endregion

    // region: Class deserialization delegates through Type

    @Test
    void shouldDelegateClassToTypeDeserialization() throws Exception {
        var gson = new Gson();
        PayloadSerde serde = new PayloadSerde() {
            @Override
            public String serialize(Object value) {
                return gson.toJson(value);
            }

            @Override
            public <T> T deserialize(String json, Type targetType) {
                return gson.fromJson(json, targetType);
            }
        };

        var input = Map.of("name", "test");
        var jsonString = serde.serialize(input);
        assertThat(jsonString).isEqualTo(gson.toJson(input));

        var decoded = serde.deserialize(jsonString, Map.class);
        assertThat(decoded).isEqualTo(input);
    }

    // endregion

    // region: Non-ASCII payload (emoji, cyrillic) survives String round-trip

    @Test
    void shouldRoundTripNonAsciiPayload() throws Exception {
        var serde = new dev.tachyonmcp.server.json.JacksonPayloadSerde();
        var input = Map.of("emoji", "\uD83D\uDE00", "cyrillic", "\u043F\u0440\u0438\u0432\u0435\u0442");

        var json = serde.serialize(input);
        final Map<String, String> decoded = serde.deserialize(json, Map.class);

        assertThat(decoded).isEqualTo(input);
    }

    // endregion

    // region: Jackson serde produces same output as Jackson's writeValueAsString

    @Test
    void shouldMatchJacksonStringOutput() throws Exception {
        var serde = new dev.tachyonmcp.server.json.JacksonPayloadSerde();
        var mapper = new tools.jackson.databind.ObjectMapper();
        var input = Map.of("message", "hello", "count", 42);

        var string = serde.serialize(input);
        var expected = mapper.writeValueAsString(input);

        assertThat(string).isEqualTo(expected);
    }

    // endregion
}
