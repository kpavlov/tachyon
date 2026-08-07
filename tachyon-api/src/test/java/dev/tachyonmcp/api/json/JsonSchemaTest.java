/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link JsonSchema#generated(Class)} against the {@link JsonSchemaFactory} chain
 * registered for this module via {@code META-INF/services}: {@link ChainPriorityLowFactory}
 * (priority {@code 0}) and {@link ChainPriorityHighFactory} (priority {@code 10}).
 */
class JsonSchemaTest {

    @Test
    void generatedPrefersTheLowestPriorityFactory() {
        var schema = JsonSchema.generated(ChainPriorityLowFactory.LowTarget.class);

        assertThat(schema.json()).contains("\"low\"");
    }

    @Test
    void generatedContinuesToTheNextFactoryWhenTheLowestReturnsEmpty() {
        var schema = JsonSchema.generated(ChainPriorityHighFactory.HighTarget.class);

        assertThat(schema.json()).contains("\"high\"");
    }

    @Test
    void generatedThrowsWhenEveryFactoryReturnsEmpty() {
        assertThatThrownBy(() -> JsonSchema.generated(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("META-INF/services")
                .hasMessageContaining("META-INF/kt-schema/schemas");
    }
}
