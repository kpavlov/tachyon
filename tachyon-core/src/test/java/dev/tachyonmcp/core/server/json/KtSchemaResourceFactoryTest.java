/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.api.json.JsonSchema;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link KtSchemaResourceFactory} as the {@code JsonSchemaFactory} chain registered for
 * this module: build-time resources resolve, types without a resource resolve to nothing, and
 * {@link JsonSchema#generate(Class)} fails once the chain is exhausted.
 */
class KtSchemaResourceFactoryTest {

    @Test
    void resolvesBuildTimeGeneratedSchemaResource() {
        var schema = JsonSchema.generate(GeneratedSchemaFixture.class);

        assertThat(schema.json()).contains("\"title\": \"fixture\"", "\"name\"");
    }

    @Test
    void generatedFailsWhenChainExhausted() {
        assertThatThrownBy(() -> JsonSchema.generate(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("META-INF/services")
                .hasMessageContaining("META-INF/kt-schema/schemas");
    }
}
