/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class Jackson3JsonFactoryTest {

    private final Jackson3JsonFactory factory = Jackson3JsonFactory.INSTANCE;

    @Test
    void shouldWrapValidJsonStringAsDocument() {
        var document = factory.toJsonDocument("{\"a\":1}");
        assertThat(document.json()).isEqualTo("{\"a\":1}");
    }

    @Test
    void shouldRejectMalformedJsonStringAsDocument() {
        assertThatThrownBy(() -> factory.toJsonDocument("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-json");
    }

    @Test
    void shouldWrapValidJsonStringAsSchema() {
        var schema = factory.toJsonSchema("{\"type\":\"object\"}");
        assertThat(schema.json()).isEqualTo("{\"type\":\"object\"}");
    }

    @Test
    void shouldRejectMalformedJsonStringAsSchema() {
        assertThatThrownBy(() -> factory.toJsonSchema("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-json");
    }

    @Test
    void shouldWrapJsonNodeAsDocument() {
        var node = JsonNodeFactory.instance.objectNode().put("a", 1);
        var document = factory.toJsonDocument(node);
        assertThat(JsonUtils.parse(document)).isEqualTo(node);
    }

    @Test
    void shouldWrapJsonNodeAsSchema() {
        var node = JsonNodeFactory.instance.objectNode().put("type", "object");
        var schema = factory.toJsonSchema(node);
        assertThat(JsonUtils.parse(schema.json())).isEqualTo(node);
    }

    @Test
    void shouldWrapJsonObjectAsDocument() {
        var object = JsonObject.of(Map.of("a", 1));
        var document = factory.toJsonDocument(object);
        assertThat(document.json()).isEqualTo(object.json());
    }

    @Test
    void shouldWrapJsonObjectAsSchema() {
        var object = JsonObject.of(Map.of("type", "object"));
        var schema = factory.toJsonSchema(object);
        assertThat(schema.json()).isEqualTo(object.json());
    }
}
