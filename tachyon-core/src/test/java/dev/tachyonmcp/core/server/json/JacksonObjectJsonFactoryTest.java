/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.json.JsonObject;
import dev.tachyonmcp.api.json.JsonSchema;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class JacksonObjectJsonFactoryTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final JacksonObjectJsonFactory factory = JacksonObjectJsonFactory.INSTANCE;

    @Test
    void shouldReportObjectNodeAsSourceType() {
        assertThat(factory.sourceType()).isEqualTo(ObjectNode.class);
    }

    @Test
    void shouldRetainSameNodeInstanceForDocumentUnwrap() {
        var node = NODES.objectNode().put("a", 1);
        var document = factory.toJsonDocument(node);
        assertThat(document.unwrap(ObjectNode.class).orElseThrow()).isSameAs(node);
        assertThatJson(document.json()).isEqualTo(node.toString());
    }

    @Test
    void shouldRetainSameNodeInstanceForSchemaUnwrap() {
        var node = NODES.objectNode().put("type", "object");
        var schema = factory.toJsonSchema(node);
        assertThat(schema.unwrap(ObjectNode.class).orElseThrow()).isSameAs(node);
        assertThatJson(schema.json()).isEqualTo(node.toString());
    }

    @Test
    void shouldResolveViaJsonSchemaFrom() {
        var node = NODES.objectNode().put("type", "object");
        var schema = JsonSchema.from(node, ObjectNode.class);
        assertThat(schema.unwrap(ObjectNode.class).orElseThrow()).isSameAs(node);
    }

    @Test
    void shouldResolveViaJsonDocumentFrom() {
        var node = NODES.objectNode().put("a", 1);
        var document = JsonDocument.from(node, ObjectNode.class);
        assertThat(document.unwrap(ObjectNode.class).orElseThrow()).isSameAs(node);
    }

    @Test
    void documentShouldImplementJsonObjectOverTheNode() {
        var nested = NODES.objectNode().put("deep", 1);
        var node = NODES.objectNode();
        node.put("name", "Ada");
        node.put("active", true);
        node.put("age", 32);
        node.set("nothing", NODES.nullNode());
        node.set("address", nested);
        node.set("tags", NODES.arrayNode().add("x").add("y"));

        var document = (JsonObject) factory.toJsonDocument(node);

        assertThat(document.has("name")).isTrue();
        assertThat(document.has("nothing")).isTrue();
        assertThat(document.has("missing")).isFalse();
        assertThat(document.stringValue("name")).isEqualTo("Ada");
        assertThat(document.boolValue("active")).isTrue();
        assertThat(document.intValue("age")).isEqualTo(32);
        assertThat(document.decimalValue("age")).isEqualTo(BigDecimal.valueOf(32));
        assertThat(document.stringOpt("nothing")).isEmpty();
        assertThat(document.stringOpt("missing")).isEmpty();
        assertThat(document.objectValue("address").intValue("deep")).isEqualTo(1);
        assertThat(document.arrayValue("tags").size()).isEqualTo(2);
        assertThat(document.arrayValue("tags").stringValue(0)).isEqualTo("x");
        assertThat(document.asMap()).containsEntry("name", "Ada").containsEntry("age", 32);
    }

    @Test
    void shouldThrowForWrongTypeAccess() {
        var node = NODES.objectNode().put("name", "Ada");
        var document = (JsonObject) factory.toJsonDocument(node);

        assertThatThrownBy(() -> document.intValue("name")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowForNonIntegralNumber() {
        var node = NODES.objectNode().put("price", 3.5);
        var document = (JsonObject) factory.toJsonDocument(node);

        assertThatThrownBy(() -> document.intValue("price")).isInstanceOf(IllegalArgumentException.class);
    }
}
