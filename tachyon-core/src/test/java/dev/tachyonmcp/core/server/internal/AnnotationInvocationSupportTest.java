/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies {@link AnnotationInvocationSupport}'s schema-type/coercion invariant — every numeric
 * type {@link AnnotationInvocationSupport#jsonSchemaType} advertises as {@code "integer"} or
 * {@code "number"} is a type {@link AnnotationInvocationSupport#coerce} can actually produce —
 * and that {@link AnnotationInvocationSupport#unwrap} recovers an annotated method's real
 * exception from the {@link InvocationTargetException} wrapper.
 *
 * @author Konstantin Pavlov
 */
class AnnotationInvocationSupportTest {

    static Stream<Arguments> numericTypes() {
        return Stream.of(
                Arguments.of(int.class, "integer", Integer.class),
                Arguments.of(Integer.class, "integer", Integer.class),
                Arguments.of(long.class, "integer", Long.class),
                Arguments.of(Long.class, "integer", Long.class),
                Arguments.of(short.class, "integer", Short.class),
                Arguments.of(Short.class, "integer", Short.class),
                Arguments.of(byte.class, "integer", Byte.class),
                Arguments.of(Byte.class, "integer", Byte.class),
                Arguments.of(double.class, "number", Double.class),
                Arguments.of(Double.class, "number", Double.class),
                Arguments.of(float.class, "number", Float.class),
                Arguments.of(Float.class, "number", Float.class));
    }

    @ParameterizedTest
    @MethodSource("numericTypes")
    void schemaTypeAndCoercionAgreeForEveryNumericType(
            Class<?> type, String expectedJsonType, Class<?> expectedWrapper) {
        assertThat(AnnotationInvocationSupport.jsonSchemaType(type)).isEqualTo(expectedJsonType);
        assertThat(AnnotationInvocationSupport.coerce(7, type)).isInstanceOf(expectedWrapper);
    }

    @Test
    void booleanAndStringUseTheirOwnJsonType() {
        assertThat(AnnotationInvocationSupport.jsonSchemaType(boolean.class)).isEqualTo("boolean");
        assertThat(AnnotationInvocationSupport.jsonSchemaType(Boolean.class)).isEqualTo("boolean");
        assertThat(AnnotationInvocationSupport.jsonSchemaType(String.class)).isEqualTo("string");
    }

    @Test
    void unknownTypeDefaultsToStringAndCoercionLeavesValueUnchanged() {
        assertThat(AnnotationInvocationSupport.jsonSchemaType(Thread.class)).isEqualTo("string");
        var raw = new Object();
        assertThat(AnnotationInvocationSupport.coerce(raw, Thread.class)).isSameAs(raw);
    }

    @Test
    void coerceReturnsNullForNullInput() {
        assertThat(AnnotationInvocationSupport.coerce(null, int.class)).isNull();
    }

    @Test
    void coerceLeavesValueAlreadyMatchingTypeUnchanged() {
        String value = "already-typed";
        assertThat(AnnotationInvocationSupport.coerce(value, String.class)).isSameAs(value);
    }

    @Test
    void unwrapReturnsCheckedExceptionCause() {
        var cause = new java.io.IOException("boom");
        var ite = new InvocationTargetException(cause);

        assertThat(AnnotationInvocationSupport.unwrap(ite)).isSameAs(cause);
    }

    @Test
    void unwrapThrowsErrorCauseDirectlySinceErrorIsNotAnException() {
        var cause = new StackOverflowError("boom");
        var ite = new InvocationTargetException(cause);

        assertThatThrownBy(() -> AnnotationInvocationSupport.unwrap(ite)).isSameAs(cause);
    }

    @Test
    void unwrapReturnsTheWrapperItselfWhenCauseIsMissing() {
        var ite = new InvocationTargetException(null);

        assertThat(AnnotationInvocationSupport.unwrap(ite)).isSameAs(ite);
    }

    @Test
    void inputSchemaWrapsPropertiesInAnObjectEnvelopeKeepingDeclarationOrder() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("who", Map.of("type", "string"));
        properties.put("times", Map.of("type", "integer"));

        var json = AnnotationInvocationSupport.inputSchema(properties, List.of("who"))
                .json();

        assertThat(json)
                .contains("\"type\":\"object\"")
                .contains("\"required\":[\"who\"]")
                .containsSubsequence("\"who\"", "\"times\"");
    }

    @Test
    void inputSchemaOmitsRequiredEntirelyWhenNoParameterIsRequired() {
        var json = AnnotationInvocationSupport.inputSchema(Map.of("who", Map.of("type", "string")), List.of())
                .json();

        assertThat(json).contains("\"properties\"").doesNotContain("\"required\"");
    }
}
