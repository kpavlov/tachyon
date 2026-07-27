/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link JsonSchema}/{@link JsonDocument}'s {@code ServiceLoader}-backed static
 * factories against the providers actually registered in {@code META-INF/services} for this
 * module (Jackson3JsonFactory, JacksonNodeJsonFactory, JacksonObjectJsonFactory).
 */
class JsonSpiDiscoveryTest {

    @Test
    void shouldParseValidJsonStringViaDiscoveredFactory() {
        assertThatJson(JsonSchema.parse("{\"type\":\"object\"}").json()).isEqualTo("{\"type\":\"object\"}");
        assertThatJson(JsonDocument.parse("{\"a\":1}").json()).isEqualTo("{\"a\":1}");
    }

    @Test
    void shouldRejectMalformedJsonStringViaDiscoveredFactory() {
        assertThatThrownBy(() -> JsonSchema.parse("not-json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonDocument.parse("not-json")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailWithActionableMessageWhenNoFactoryRegisteredForType() {
        record Unregistered(String value) {}

        assertThatThrownBy(() -> JsonSchema.from(new Unregistered("x"), Unregistered.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unregistered")
                .hasMessageContaining("META-INF/services");

        assertThatThrownBy(() -> JsonDocument.from(new Unregistered("x"), Unregistered.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unregistered")
                .hasMessageContaining("META-INF/services");
    }
}
