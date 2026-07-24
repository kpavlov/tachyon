/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NetworkntJsonSchemaValidatorTest {

    private final NetworkntJsonSchemaValidator validator = new NetworkntJsonSchemaValidator();

    @Test
    void shouldReturnNoErrorsForValidArguments() {
        var schema = JsonSchema.of("""
            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
            """);
        var arguments = JsonDocument.of("""
            {"name":"John"}
            """);

        assertThat(validator.validate(schema, arguments)).isEmpty();
    }

    @Test
    void shouldReturnErrorWithPathAndKeywordForMissingRequiredProperty() {
        var schema = JsonSchema.of("""
            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
            """);
        var arguments = JsonDocument.of("{}");

        var errors = validator.validate(schema, arguments);

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().keyword()).isEqualTo("required");
        assertThat(errors.getFirst().message()).contains("name");
    }

    @Test
    void shouldReturnErrorForWrongType() {
        var schema = JsonSchema.of("""
            {"type":"object","properties":{"age":{"type":"integer"}}}
            """);
        var arguments = JsonDocument.of("""
            {"age":"not a number"}
            """);

        var errors = validator.validate(schema, arguments);

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().keyword()).isEqualTo("type");
    }

    @Test
    void shouldValidateDistinctSchemasIndependently() {
        var nameSchema = JsonSchema.of("""
            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
            """);
        var emailSchema = JsonSchema.of("""
            {"type":"object","properties":{"email":{"type":"string"}},"required":["email"]}
            """);

        // Same validator instance compiles and caches both schemas; calls must not cross-contaminate.
        assertThat(validator.validate(nameSchema, JsonDocument.of("""
            {"name":"John"}
            """))).isEmpty();
        assertThat(validator.validate(emailSchema, JsonDocument.of("""
            {"email":"john@example.com"}
            """))).isEmpty();
        assertThat(validator.validate(nameSchema, JsonDocument.of("""
            {"email":"john@example.com"}
            """))).hasSize(1);
        assertThat(validator.validate(emailSchema, JsonDocument.of("""
            {"name":"John"}
            """))).hasSize(1);
    }

    @Test
    void shouldReuseCompiledSchemaForRepeatedCallsWithEquivalentSchemaContent() {
        var schema1 = JsonSchema.of("""
            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
            """);
        var schema2 = JsonSchema.of("""
            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
            """);

        assertThat(validator.validate(schema1, JsonDocument.of("{}"))).hasSize(1);
        assertThat(validator.validate(schema2, JsonDocument.of("{}"))).hasSize(1);
    }

    @Test
    void shouldSupportBooleanSchemas() {
        assertThat(validator.validate(JsonSchema.of("true"), JsonDocument.of("{\"value\":1}")))
                .isEmpty();
        assertThat(validator.validate(JsonSchema.of("false"), JsonDocument.of("{\"value\":1}")))
                .hasSize(1);
    }
}
