/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.protocol.api.json.JsonDocument;
import dev.tachyonmcp.protocol.api.json.JsonSchema;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link JsonSchema}/{@link JsonDocument}'s {@code ServiceLoader}-backed static
 * factories against the providers actually registered in {@code META-INF/services} for this
 * module (Jackson3JsonFactory, JacksonNodeJsonFactory, JacksonObjectJsonFactory).
 */
class JsonSpiDiscoveryTest {

    @Test
    void shouldParseValidJsonStringIntoSchemaViaDiscoveredFactory() {
        assertThatJson(JsonSchema.parse("{\"type\":\"object\"}").json()).isEqualTo("{\"type\":\"object\"}");
    }

    @Test
    void shouldParseValidJsonStringIntoDocumentViaDiscoveredFactory() {
        assertThatJson(JsonDocument.parse("{\"a\":1}").json()).isEqualTo("{\"a\":1}");
    }

    @Test
    void shouldRejectMalformedJsonStringForSchema() {
        assertThatThrownBy(() -> JsonSchema.parse("not-json")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMalformedJsonStringForDocument() {
        assertThatThrownBy(() -> JsonDocument.parse("not-json")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailWithActionableMessageWhenNoSchemaFactoryRegisteredForType() {
        record Unregistered(String value) {}

        assertThatThrownBy(() -> JsonSchema.from(new Unregistered("x"), Unregistered.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unregistered")
                .hasMessageContaining("META-INF/services");
    }

    @Test
    void shouldFailWithActionableMessageWhenNoDocumentFactoryRegisteredForType() {
        record Unregistered(String value) {}

        assertThatThrownBy(() -> JsonDocument.from(new Unregistered("x"), Unregistered.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unregistered")
                .hasMessageContaining("META-INF/services");
    }
}
