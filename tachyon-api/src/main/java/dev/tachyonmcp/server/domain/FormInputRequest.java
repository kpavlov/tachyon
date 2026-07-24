/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.immutables.value.Value;

/** Requests user input via a form described by a JSON schema. */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public non-sealed interface FormInputRequest extends InputRequest {

    /** Prompt message shown to the user. */
    String message();

    /** JSON schema describing the expected form fields. */
    Map<String, Object> requestedSchema();

    @Value.Check
    default void check() {
        if (message().isBlank()) throw new IllegalArgumentException("message must not be blank");
    }

    static Builder builder() {
        return DefaultFormInputRequest.builder();
    }

    static FormInputRequest of(String message, Map<String, Object> requestedSchema) {
        return DefaultFormInputRequest.of(message, requestedSchema);
    }

    interface Builder {
        Builder message(String message);

        Builder requestedSchema(Map<String, ?> entries);

        FormInputRequest build();
    }
}
