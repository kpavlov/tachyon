/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.api.json.JsonSchema;
import org.junit.jupiter.api.Test;

class FormInputRequestTest {

    @Test
    void ofAcceptsJsonSchemaDirectly() {
        var schema = JsonSchema.unchecked("{\"type\":\"object\"}");

        var request = FormInputRequest.of("Enter details", schema);

        assertThat(request.message()).isEqualTo("Enter details");
        assertThat(request.requestedSchema()).isSameAs(schema);
    }

    @Test
    void builderAcceptsJsonSchemaDirectly() {
        var schema = JsonSchema.objectSchema();

        var request = FormInputRequest.builder()
                .message("Enter details")
                .requestedSchema(schema)
                .build();

        assertThat(request.requestedSchema()).isSameAs(schema);
    }

    @Test
    void checkRejectsBlankMessage() {
        assertThatThrownBy(() -> FormInputRequest.of("   ", JsonSchema.objectSchema()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }

    @Test
    void builderFromCopiesMessageAndSchema() {
        var original = FormInputRequest.of("Enter details", JsonSchema.objectSchema());

        var copy = FormInputRequest.builder().from(original).build();

        assertThat(copy.message()).isEqualTo(original.message());
        assertThat(copy.requestedSchema()).isEqualTo(original.requestedSchema());
    }
}
