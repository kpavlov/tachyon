/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.JsonSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class MapJsonFactoryTest {

    private final MapJsonFactory factory = MapJsonFactory.INSTANCE;

    @Test
    void shouldReportMapAsSourceType() {
        assertThat(factory.sourceType()).isEqualTo(Map.class);
    }

    @Test
    void shouldBuildComplexSchemaFromMapOfStandardJavaTypes() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("type", "object");
        address.put(
                "properties",
                Map.of(
                        "city", Map.of("type", "string"),
                        "zip", Map.of("type", "string", "pattern", "^[0-9]{5}$")));
        address.put("required", List.of("city"));

        Map<String, Object> score = new LinkedHashMap<>();
        score.put("type", "number");
        score.put("minimum", 0.0);
        score.put("maximum", 100L);

        Map<String, Object> role = new LinkedHashMap<>();
        role.put("type", "string");
        role.put("enum", List.of("ADMIN", "USER", "GUEST"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Map.of("type", "string", "minLength", 1));
        properties.put("age", Map.of("type", "integer", "minimum", 0));
        properties.put("active", Map.of("type", "boolean"));
        properties.put("role", role);
        properties.put("address", address);
        properties.put("scores", Map.of("type", "array", "items", score));
        properties.put("nickname", Map.of("type", List.of("string", "null")));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        map.put("type", "object");
        map.put("additionalProperties", false);
        map.put("required", List.of("name", "age"));
        map.put("properties", properties);
        map.put("oneOf", List.of(Map.of("required", List.of("role")), Map.of("required", List.of("address"))));
        map.put("default", null);

        var schema = factory.toJsonSchema(map).orElseThrow();

        assertThatJson(schema.json())
                // language=json
                .isEqualTo("""
                        {
                          "$schema": "https://json-schema.org/draft/2020-12/schema",
                          "type": "object",
                          "additionalProperties": false,
                          "required": ["name", "age"],
                          "properties": {
                            "name": {"type": "string", "minLength": 1},
                            "age": {"type": "integer", "minimum": 0},
                            "active": {"type": "boolean"},
                            "role": {"type": "string", "enum": ["ADMIN", "USER", "GUEST"]},
                            "address": {
                              "type": "object",
                              "properties": {
                                "city": {"type": "string"},
                                "zip": {"type": "string", "pattern": "^[0-9]{5}$"}
                              },
                              "required": ["city"]
                            },
                            "scores": {
                              "type": "array",
                              "items": {"type": "number", "minimum": 0.0, "maximum": 100}
                            },
                            "nickname": {"type": ["string", "null"]}
                          },
                          "oneOf": [
                            {"required": ["role"]},
                            {"required": ["address"]}
                          ],
                          "default": null
                        }
                        """);
    }

    @Test
    void shouldUnwrapToObjectNode() {
        var schema = factory.toJsonSchema(Map.of("type", "object")).orElseThrow();

        assertThat(schema.unwrap(ObjectNode.class)).isPresent();
    }

    @Test
    void shouldResolveViaJsonSchemaFrom() {
        Map<String, Object> map = Map.of("type", "object");

        var schema = JsonSchema.from(map, factory.sourceType());

        assertThatJson(schema.json()).isEqualTo("{\"type\": \"object\"}");
    }

    @Test
    void shouldResolveViaJsonSchemaFromMapConvenienceOverload() {
        Map<String, Object> map = Map.of("type", "object");

        var schema = JsonSchema.from(map);

        assertThatJson(schema.json()).isEqualTo("{\"type\": \"object\"}");
    }
}
