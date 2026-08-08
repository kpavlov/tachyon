/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.api.json.spi.JsonSchemaFactory;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link JsonSchema#generate(Class)} and {@link JsonSchema#parse(String)} against the
 * {@link JsonSchemaFactory} chain registered for this module via {@code META-INF/services}: {@link
 * ChainPriorityLowFactory} (priority {@code 0}), {@link ChainPriorityHighFactory} (priority {@code
 * 10}) for {@code Class} sources, and {@link StringJsonSchemaFactory} for {@code String} sources.
 */
class JsonSchemaTest {

    @Test
    void generatedPrefersTheLowestPriorityFactory() {
        var schema = JsonSchema.generate(ChainPriorityLowFactory.LowTarget.class);

        assertThat(schema.json()).contains("\"low\"");
    }

    @Test
    void generatedContinuesToTheNextFactoryWhenTheLowestReturnsEmpty() {
        var schema = JsonSchema.generate(ChainPriorityHighFactory.HighTarget.class);

        assertThat(schema.json()).contains("\"high\"");
    }

    @Test
    void generatedThrowsWhenEveryFactoryReturnsEmpty() {
        assertThatThrownBy(() -> JsonSchema.generate(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("META-INF/services")
                .hasMessageContaining("META-INF/kt-schema/schemas");
    }

    @Test
    void parseResolvesValidJsonViaStringProvider() {
        var schema = JsonSchema.parse("{\"type\":\"object\"}");

        assertThat(schema.json()).isEqualTo("{\"type\":\"object\"}");
    }

    @Test
    void parseRejectsMalformedJson() {
        assertThatThrownBy(() -> JsonSchema.parse("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-json");
    }

    @Test
    void parseThrowsWhenNoStringProviderCoversTheSource() {
        assertThatThrownBy(() -> JsonSchema.parse("42"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("java.lang.String")
                .hasMessageContaining("META-INF/services");
    }
}
