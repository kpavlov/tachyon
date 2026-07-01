/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import tools.jackson.databind.JsonNode;

/** JSON Schema validator backed by {@code networknt/json-schema-validator}. */
public class NetworkntJsonSchemaValidator implements JsonSchemaValidator {

    private final SchemaRegistry registry;

    // Tool/prompt schemas are static and reused across calls; networknt does not cache
    // schemas compiled via getSchema(SchemaLocation, JsonNode), so we cache them ourselves
    // keyed by schema content (JsonNode equals/hashCode are structural in Jackson).
    private final ConcurrentMap<JsonNode, Schema> compiledSchemas = new ConcurrentHashMap<>();

    public NetworkntJsonSchemaValidator() {
        this.registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    }

    @Override
    public List<SchemaValidationError> validate(JsonNode schema, JsonNode arguments) {
        var compiledSchema = compiledSchemas.computeIfAbsent(schema, this::compile);
        var errors = compiledSchema.validate(arguments);
        if (errors.isEmpty()) {
            return List.of();
        }
        return errors.stream()
                .map(error -> new SchemaValidationError(
                        error.getInstanceLocation().toString(), error.getKeyword(), error.getMessage()))
                .toList();
    }

    private Schema compile(JsonNode schema) {
        var location = SchemaLocation.of(
                "urn:tachyon:schema:" + Integer.toHexString(schema.toString().hashCode()));
        return registry.getSchema(location, schema);
    }
}
