/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.json;

import static dev.tachyonmcp.test.TestUtils.parseJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class JsonUtilsTest {

    private static final PayloadSerializer SERDE = new JacksonPayloadSerde();

    @Test
    void valueToObjectNodeNullReturnsNull() {
        assertThat(JsonUtils.valueToObjectNode(null, SERDE)).isNull();
    }

    @Test
    void valueToObjectNodeRawJsonObjectReturnsNode() {
        var result = JsonUtils.valueToObjectNode(RawJson.of("{\"key\":\"val\"}"), SERDE);
        assertThat(result).isNotNull();
        assertThat(result.get("key").asString()).isEqualTo("val");
    }

    @Test
    void valueToObjectNodeRawJsonNonObjectReturnsNull() {
        var result = JsonUtils.valueToObjectNode(RawJson.of("\"string\""), SERDE);
        assertThat(result).isNull();
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
    void serializeStructuredWithRawJsonPassesThrough() {
        var result = ToolResult.of(RawJson.of("{\"x\":1}"), "text");
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
    void serializeStructuredWithPojoWrapsInRawJson() {
        var result = ToolResult.of(new SamplePojo("x", 1), "text");
        var serialized = JsonUtils.serializeStructured(result, SERDE);
        assertThat(serialized).isInstanceOf(ToolResult.Success.class);
        var sv = ((ToolResult.Success) serialized).structuredValue();
        assertThat(sv).isInstanceOf(RawJson.class);
        var parsed = parseJson(((RawJson) Objects.requireNonNull(sv)).json());
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
        assertThat(sv).isInstanceOf(RawJson.class);
    }

    @Test
    void serializeStructuredUnwrapsWithMeta() {
        var inner = ToolResult.of(new SamplePojo("m", 2), "text");
        var withMeta = inner.withMeta("k", parseJson("\"v\""));
        var serialized = JsonUtils.serializeStructured(withMeta, SERDE);
        assertThat(serialized).isInstanceOf(ToolResult.WithMeta.class);
        var innerSerialized = ((ToolResult.WithMeta) serialized).inner();
        var sv = ((ToolResult.Success) innerSerialized).structuredValue();
        assertThat(sv).isInstanceOf(RawJson.class);
    }

    public record SamplePojo(String name, int value) {}
}
