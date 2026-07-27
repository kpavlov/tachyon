/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JacksonObjectJsonFactoryTest {

    private final JacksonObjectJsonFactory factory = JacksonObjectJsonFactory.INSTANCE;

    @Test
    void shouldReportJsonObjectAsSourceType() {
        assertThat(factory.sourceType()).isEqualTo(JsonObject.class);
    }

    @Test
    void shouldRetainSameObjectInstanceForDocumentUnwrap() {
        var object = JsonObject.of(Map.of("a", 1));
        var document = factory.toJsonDocument(object);
        assertThat(document.unwrap(JsonObject.class).orElseThrow()).isSameAs(object);
        assertThatJson(document.json()).isEqualTo(object.json());
    }

    @Test
    void shouldRetainSameObjectInstanceForSchemaUnwrap() {
        var object = JsonObject.of(Map.of("type", "object"));
        var schema = factory.toJsonSchema(object);
        assertThat(schema.unwrap(JsonObject.class).orElseThrow()).isSameAs(object);
        assertThatJson(schema.json()).isEqualTo(object.json());
    }

    @Test
    void shouldResolveViaJsonSchemaFrom() {
        var object = JsonObject.of(Map.of("type", "object"));
        var schema = JsonSchema.from(object, JsonObject.class);
        assertThat(schema.unwrap(JsonObject.class).orElseThrow()).isSameAs(object);
    }

    @Test
    void shouldResolveViaJsonDocumentFrom() {
        var object = JsonObject.of(Map.of("a", 1));
        var document = JsonDocument.from(object, JsonObject.class);
        assertThat(document.unwrap(JsonObject.class).orElseThrow()).isSameAs(object);
    }
}
