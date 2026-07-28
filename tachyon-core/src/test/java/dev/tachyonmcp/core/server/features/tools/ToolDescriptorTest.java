/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tools;

import static dev.tachyonmcp.core.test.TestUtils.parseJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
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
    void shouldParseSchemaWithoutObjectType() {
        var schema = "{\"type\":\"array\",\"items\":{\"type\":\"string\"}}";
        var desc = ToolDescriptor.builder()
                .name("array-root-schema")
                .inputSchema(schema)
                .build();
        assertThat(desc.inputSchema()).isNotNull();
        assertThat(parseJson(desc.inputSchema().json()).get("type").asString()).isEqualTo("array");
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
