/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.json;

import static dev.tachyonmcp.test.TestUtils.parseJson;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NetworkntJsonSchemaValidatorTest {

    private final NetworkntJsonSchemaValidator validator = new NetworkntJsonSchemaValidator();

    @Test
    void shouldReturnNoErrorsForValidArguments() {
        var schema = parseJson("""
            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
            """);
        var arguments = parseJson("""
            {"name":"John"}
            """);

        assertThat(validator.validate(schema, arguments)).isEmpty();
    }

    @Test
    void shouldReturnErrorWithPathAndKeywordForMissingRequiredProperty() {
        var schema = parseJson("""
            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
            """);
        var arguments = parseJson("{}");

        var errors = validator.validate(schema, arguments);

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().keyword()).isEqualTo("required");
        assertThat(errors.getFirst().message()).contains("name");
    }

    @Test
    void shouldReturnErrorForWrongType() {
        var schema = parseJson("""
            {"type":"object","properties":{"age":{"type":"integer"}}}
            """);
        var arguments = parseJson("""
            {"age":"not a number"}
            """);

        var errors = validator.validate(schema, arguments);

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().keyword()).isEqualTo("type");
    }

    @Test
    void shouldValidateDistinctSchemasIndependently() {
        var nameSchema = parseJson("""
            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
            """);
        var emailSchema = parseJson("""
            {"type":"object","properties":{"email":{"type":"string"}},"required":["email"]}
            """);

        // Same validator instance compiles and caches both schemas; calls must not cross-contaminate.
        assertThat(validator.validate(nameSchema, parseJson("""
            {"name":"John"}
            """))).isEmpty();
        assertThat(validator.validate(emailSchema, parseJson("""
            {"email":"john@example.com"}
            """))).isEmpty();
        assertThat(validator.validate(nameSchema, parseJson("""
            {"email":"john@example.com"}
            """))).hasSize(1);
        assertThat(validator.validate(emailSchema, parseJson("""
            {"name":"John"}
            """))).hasSize(1);
    }

    @Test
    void shouldReuseCompiledSchemaForRepeatedCallsWithEquivalentSchemaContent() {
        var schema1 = parseJson("""
            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
            """);
        var schema2 = parseJson("""
            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
            """);

        assertThat(validator.validate(schema1, parseJson("{}"))).hasSize(1);
        assertThat(validator.validate(schema2, parseJson("{}"))).hasSize(1);
    }
}
