/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ToolDescriptorTest {

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> ToolDescriptor.builder("").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectWhitespaceOnlyName() {
        assertThatThrownBy(() -> ToolDescriptor.builder("   ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldParseStringInputSchema() {
        var schema = "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"string\"}}}";
        var desc = ToolDescriptor.builder("test", schema, null).build();
        assertThat(desc.inputSchema()).isNotNull();
        assertThat(desc.inputSchema().get("type").asText()).isEqualTo("object");
        assertThat(desc.outputSchema()).isNull();
    }

    @Test
    void shouldParseStringOutputSchema() {
        var schema = "{\"type\":\"object\",\"properties\":{\"y\":{\"type\":\"integer\"}}}";
        var desc = ToolDescriptor.builder("test", null, schema).build();
        assertThat(desc.inputSchema()).isNull();
        assertThat(desc.outputSchema()).isNotNull();
        assertThat(desc.outputSchema().get("type").asText()).isEqualTo("object");
    }

    @Test
    void shouldParseBothStringSchemas() {
        var input = "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}}}";
        var output = "{\"type\":\"object\",\"properties\":{\"b\":{\"type\":\"number\"}}}";
        var desc = ToolDescriptor.builder("test", input, output).build();
        assertThat(desc.inputSchema().get("properties").get("a").get("type").asText())
                .isEqualTo("string");
        assertThat(desc.outputSchema().get("properties").get("b").get("type").asText())
                .isEqualTo("number");
    }

    @Test
    void shouldRejectMalformedJsonSchema() {
        assertThatThrownBy(() ->
                        ToolDescriptor.builder("bad-tool", "not-json", null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad-tool");
    }

    @Test
    void shouldParseSchemaWithoutObjectType() {
        var schema = "{\"type\":\"array\",\"items\":{\"type\":\"string\"}}";
        var desc = ToolDescriptor.builder("array-root-schema", schema, null).build();
        assertThat(desc.inputSchema()).isNotNull();
        assertThat(desc.inputSchema().get("type").asText()).isEqualTo("array");
    }

    @Test
    void shouldRejectNullStringSchemas() {
        var desc = ToolDescriptor.builder("test", (String) null, (String) null).build();
        assertThat(desc.inputSchema()).isNull();
        assertThat(desc.outputSchema()).isNull();
    }

    @Test
    void shouldProduceSameOutputAsJsonNodeBuilder() {
        var mapper = new JsonMapper();
        var inputJson = "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"string\"}}}";
        var outputJson = "{\"type\":\"object\",\"properties\":{\"y\":{\"type\":\"integer\"}}}";
        var fromString = ToolDescriptor.builder("same", inputJson, outputJson).build();
        var fromNode = ToolDescriptor.builder("same")
                .inputSchema(mapper.readTree(inputJson))
                .outputSchema(mapper.readTree(outputJson))
                .build();
        assertThat(fromString.inputSchema()).isEqualTo(fromNode.inputSchema());
        assertThat(fromString.outputSchema()).isEqualTo(fromNode.outputSchema());
    }
}
