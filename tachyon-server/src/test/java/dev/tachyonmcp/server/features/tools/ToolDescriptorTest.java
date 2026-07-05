/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

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
        assertThat(desc.inputSchema().get("type").asString()).isEqualTo("array");
    }

    @Test
    void shouldAcceptNullStringSchemas() {
        var desc = ToolDescriptor.builder()
                .name("test")
                .inputSchema(null)
                .outputSchema(null)
                .build();
        assertThat(desc.inputSchema()).isNull();
        assertThat(desc.outputSchema()).isNull();
    }
}
