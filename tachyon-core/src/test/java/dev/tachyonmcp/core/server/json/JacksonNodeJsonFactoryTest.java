/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.json.JsonSchema;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class JacksonNodeJsonFactoryTest {

    private final JacksonNodeJsonFactory factory = JacksonNodeJsonFactory.INSTANCE;

    @Test
    void shouldReportJsonNodeAsSourceType() {
        assertThat(factory.sourceType()).isEqualTo(JsonNode.class);
    }

    @Test
    void shouldRetainSameNodeInstanceForDocumentUnwrap() {
        var node = JsonNodeFactory.instance.objectNode().put("a", 1);
        var document = factory.toJsonDocument(node);
        assertThat(document.unwrap(JsonNode.class).orElseThrow()).isSameAs(node);
        assertThatJson(document.json()).isEqualTo(node.toString());
    }

    @Test
    void shouldRetainSameNodeInstanceForSchemaUnwrap() {
        var node = JsonNodeFactory.instance.objectNode().put("type", "object");
        var schema = factory.toJsonSchema(node);
        assertThat(schema.unwrap(JsonNode.class).orElseThrow()).isSameAs(node);
        assertThatJson(schema.json()).isEqualTo(node.toString());
    }

    @Test
    void shouldResolveViaJsonSchemaFrom() {
        var node = JsonNodeFactory.instance.objectNode().put("type", "object");
        var schema = JsonSchema.from(node, JsonNode.class);
        assertThat(schema.unwrap(JsonNode.class).orElseThrow()).isSameAs(node);
    }

    @Test
    void shouldResolveViaJsonDocumentFrom() {
        var node = JsonNodeFactory.instance.objectNode().put("a", 1);
        var document = JsonDocument.from(node, JsonNode.class);
        assertThat(document.unwrap(JsonNode.class).orElseThrow()).isSameAs(node);
    }
}
