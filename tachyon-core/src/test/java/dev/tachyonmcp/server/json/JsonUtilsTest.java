/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.json;

import static dev.tachyonmcp.test.TestUtils.parseJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class JsonUtilsTest {

    private static final PayloadSerializer SERDE = new JacksonPayloadSerde();

    @Test
    void valueToObjectNodeNullReturnsNull() {
        assertThat(JsonUtils.valueToObjectNode(null, SERDE)).isNull();
    }

    @Test
    void valueToObjectNodeJsonDocumentObjectReturnsNode() {
        var result = JsonUtils.valueToObjectNode(JsonDocument.of("{\"key\":\"val\"}"), SERDE);
        assertThat(result).isNotNull();
        assertThat(result.get("key").asString()).isEqualTo("val");
    }

    @Test
    void valueToObjectNodeJsonDocumentNonObjectReturnsNull() {
        var result = JsonUtils.valueToObjectNode(JsonDocument.of("\"string\""), SERDE);
        assertThat(result).isNull();
    }

    @Test
    void parseReusesProviderRepresentation() {
        var node = parseJson("{\"key\":\"value\"}");
        var document = new RetainedDocument("{\"ignored\":true}", node);

        assertThat(JsonUtils.parse(document)).isSameAs(node);
    }

    @Test
    void valueToObjectNodeJsonNodeObjectReturnsNode() {
        var node = parseJson("{\"a\":1}");
        var result = JsonUtils.valueToObjectNode(node, SERDE);
        assertThat(result).isSameAs(node);
    }

    @Test
    void valueToObjectNodeJsonNodeNonObjectReturnsNull() {
        var result = JsonUtils.valueToObjectNode(parseJson("\"string\""), SERDE);
        assertThat(result).isNull();
    }

    @Test
    void valueToObjectNodeMapReturnsNode() {
        var result = JsonUtils.valueToObjectNode(Map.of("msg", "hello", "n", 42), SERDE);
        assertThat(result).isNotNull();
        assertThat(result.get("msg").asString()).isEqualTo("hello");
        assertThat(result.get("n").asInt()).isEqualTo(42);
    }

    @Test
    void valueToObjectNodeMapWithJsonNodeValuesReturnsNode() {
        var jsonVal = JsonNodeFactory.instance.stringNode("json-val");
        var result = JsonUtils.valueToObjectNode(Map.of("f1", jsonVal, "f2", "plain"), SERDE);
        assertThat(result).isNotNull();
        assertThat(result.get("f1").asString()).isEqualTo("json-val");
        assertThat(result.get("f2").asString()).isEqualTo("plain");
    }

    @Test
    void valueToObjectNodePojoReturnsNode() {
        var result = JsonUtils.valueToObjectNode(new SamplePojo("test", 42), SERDE);
        assertThat(result).isNotNull();
        assertThat(result.get("name").asString()).isEqualTo("test");
        assertThat(result.get("value").asInt()).isEqualTo(42);
    }

    @Test
    void valueToObjectNodeStringReturnsNull() {
        var result = JsonUtils.valueToObjectNode("just a string", SERDE);
        assertThat(result).isNull();
    }

    @Test
    void serializeStructuredWithJsonDocumentPassesThrough() {
        var result = ToolResult.of(JsonDocument.of("{\"x\":1}"), "text");
        var serialized = JsonUtils.serializeStructured(result, SERDE);
        assertThat(serialized).isSameAs(result);
    }

    @Test
    void serializeStructuredWithJsonNodePassesThrough() {
        var sv = parseJson("{\"y\":2}");
        var result = ToolResult.of(sv, "text");
        var serialized = JsonUtils.serializeStructured(result, SERDE);
        assertThat(serialized).isSameAs(result);
    }

    @Test
    void serializeStructuredWithNullStructuredValuePassesThrough() {
        var result = ToolResult.text("text only");
        var serialized = JsonUtils.serializeStructured(result, SERDE);
        assertThat(serialized).isSameAs(result);
    }

    @Test
    void serializeStructuredWithPojoWrapsInJsonDocument() {
        var result = ToolResult.of(new SamplePojo("x", 1), "text");
        var serialized = JsonUtils.serializeStructured(result, SERDE);
        assertThat(serialized).isInstanceOf(ToolResult.Success.class);
        var sv = ((ToolResult.Success) serialized).structuredValue();
        assertThat(sv).isInstanceOf(JsonDocument.class);
        var parsed = parseJson(((JsonDocument) Objects.requireNonNull(sv)).json());
        assertThat(parsed.get("name").asString()).isEqualTo("x");
        assertThat(parsed.get("value").asInt()).isEqualTo(1);
    }

    @Test
    void serializeStructuredWithMapContainingJsonNodesUsesJackson() {
        var jsonVal = parseJson("{\"inner\":\"val\"}");
        var result = ToolResult.of(Map.of("field", jsonVal), "text");
        var serialized = JsonUtils.serializeStructured(result, SERDE);
        assertThat(serialized).isInstanceOf(ToolResult.Success.class);
        var sv = ((ToolResult.Success) serialized).structuredValue();
        assertThat(sv).isInstanceOf(JsonDocument.class);
    }

    @Test
    void serializeStructuredUnwrapsWithMeta() {
        var inner = ToolResult.of(new SamplePojo("m", 2), "text");
        var withMeta = inner.withMeta("k", "v");
        var serialized = JsonUtils.serializeStructured(withMeta, SERDE);
        assertThat(serialized).isInstanceOf(ToolResult.WithMeta.class);
        var innerSerialized = ((ToolResult.WithMeta) serialized).inner();
        var sv = ((ToolResult.Success) innerSerialized).structuredValue();
        assertThat(sv).isInstanceOf(JsonDocument.class);
    }

    public record SamplePojo(String name, int value) {}

    private record RetainedDocument(String json, JsonNode node) implements JsonDocument {
        @Override
        public <T> Optional<T> unwrap(Class<T> type) {
            return type.isInstance(node) ? Optional.of(type.cast(node)) : Optional.empty();
        }
    }
}
