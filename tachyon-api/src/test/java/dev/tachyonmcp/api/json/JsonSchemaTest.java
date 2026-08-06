/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JsonSchemaTest {

    @Test
    void generatedLoadsSchemaResourceByClassName() {
        var schema = JsonSchema.generated(GeneratedSchemaFixture.class);

        assertThat(schema.json()).contains("\"required\"", "\"name\"");
    }

    @Test
    void generatedThrowsWhenNoSchemaResourceExists() {
        assertThatThrownBy(() -> JsonSchema.generated(JsonSchemaTest.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("META-INF/kt-schema/schemas");
    }
}
