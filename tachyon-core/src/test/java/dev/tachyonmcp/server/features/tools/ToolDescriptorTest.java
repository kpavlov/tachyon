/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ToolDescriptorTest {

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> ToolDescriptor.builder().name("").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectWhitespaceOnlyName() {
        assertThatThrownBy(() -> ToolDescriptor.builder().name("   ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectMalformedJsonSchema() {
        assertThatThrownBy(() -> ToolDescriptor.builder()
                        .name("bad-tool")
                        .inputSchema("not-json")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-json");
    }

    @Test
    void shouldParseSchemaWithoutObjectType() {
        var schema = "{\"type\":\"array\",\"items\":{\"type\":\"string\"}}";
        var desc = ToolDescriptor.builder()
                .name("array-root-schema")
                .inputSchema(schema)
                .build();
        assertThat(desc.inputSchema()).isNotNull();
        assertThat(desc.inputSchema().get("type").asString()).isEqualTo("array");
    }

    @Test
    void shouldAcceptNullStringSchemas() {
        var desc = ToolDescriptor.builder()
                .name("test")
                .inputSchema((String) null)
                .outputSchema((String) null)
                .build();
        assertThat(desc.inputSchema()).isNull();
        assertThat(desc.outputSchema()).isNull();
    }
}
