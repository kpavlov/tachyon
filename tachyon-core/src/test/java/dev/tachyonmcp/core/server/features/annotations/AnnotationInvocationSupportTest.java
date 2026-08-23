/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.annotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.api.server.features.annotations.AnnotationInvocationSupport;
import dev.tachyonmcp.core.server.json.JacksonPayloadSerde;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies {@link AnnotationInvocationSupport}'s schema-type/coercion invariant — every numeric
 * type {@link AnnotationInvocationSupport#jsonSchemaType} advertises as {@code "integer"} or
 * {@code "number"} is a type {@link AnnotationInvocationSupport#coerce} can actually produce —
 * that unsupported types are rejected at registration rather than misdescribed, that {@code
 * Optional}-family parameters coerce correctly, and that {@link AnnotationInvocationSupport#unwrap}
 * recovers an annotated method's real exception from the {@link InvocationTargetException}
 * wrapper.
 *
 * @author Konstantin Pavlov
 */
class AnnotationInvocationSupportTest {

    private static final JacksonPayloadSerde SERDE = new JacksonPayloadSerde();

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
        assertThat(AnnotationInvocationSupport.coerce(7, type, SERDE, SERDE)).isInstanceOf(expectedWrapper);
    }

    @Test
    void booleanAndStringUseTheirOwnJsonType() {
        assertThat(AnnotationInvocationSupport.jsonSchemaType(boolean.class)).isEqualTo("boolean");
        assertThat(AnnotationInvocationSupport.jsonSchemaType(Boolean.class)).isEqualTo("boolean");
        assertThat(AnnotationInvocationSupport.jsonSchemaType(String.class)).isEqualTo("string");
    }

    @Test
    void unsupportedTypeIsRejectedByJsonSchemaType() {
        assertThatThrownBy(() -> AnnotationInvocationSupport.jsonSchemaType(Thread.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Thread");
        assertThatThrownBy(() -> AnnotationInvocationSupport.jsonSchemaType(UUID.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UUID");
    }

    @Test
    void requireBindableRejectsUnsupportedParameterTypeNamingMethodAndParameter() throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod("unsupported", UUID.class);
        var param = method.getParameters()[0];

        assertThatThrownBy(() -> AnnotationInvocationSupport.requireBindable(param, method))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UUID")
                .hasMessageContaining("id")
                .hasMessageContaining("unsupported");
    }

    @Test
    void requireBindableAcceptsSupportedScalarAndOptionalParameters() throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod("supported", String.class, Optional.class, OptionalInt.class);
        for (var param : method.getParameters()) {
            AnnotationInvocationSupport.requireBindable(param, method);
        }
    }

    @Test
    void coerceReturnsNullForNullInput() {
        assertThat(AnnotationInvocationSupport.coerce(null, int.class, SERDE, SERDE))
                .isNull();
    }

    @Test
    void coerceLeavesValueAlreadyMatchingTypeUnchanged() {
        String value = "already-typed";
        assertThat(AnnotationInvocationSupport.coerce(value, String.class, SERDE, SERDE))
                .isSameAs(value);
    }

    @Test
    void coerceWrapsPresentValueIntoOptionalUsingFullGenericType() throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod("supported", String.class, Optional.class, OptionalInt.class);
        var optionalStringType = method.getGenericParameterTypes()[1];

        Object result = AnnotationInvocationSupport.coerce("abc", optionalStringType, SERDE, SERDE);

        assertThat(result).isEqualTo(Optional.of("abc"));
    }

    @Test
    void coerceWrapsPresentValueIntoOptionalInt() {
        Object result = AnnotationInvocationSupport.coerce(7, OptionalInt.class, SERDE, SERDE);

        assertThat(result).isEqualTo(OptionalInt.of(7));
    }

    @Test
    void isOptionalTypeRecognizesAllFourOptionalFamilyTypes() {
        assertThat(AnnotationInvocationSupport.isOptionalType(Optional.class)).isTrue();
        assertThat(AnnotationInvocationSupport.isOptionalType(OptionalInt.class))
                .isTrue();
        assertThat(AnnotationInvocationSupport.isOptionalType(java.util.OptionalLong.class))
                .isTrue();
        assertThat(AnnotationInvocationSupport.isOptionalType(java.util.OptionalDouble.class))
                .isTrue();
        assertThat(AnnotationInvocationSupport.isOptionalType(String.class)).isFalse();
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

    @SuppressWarnings("unused")
    private static final class Fixture {
        void unsupported(UUID id) {}

        void supported(String name, Optional<String> filter, OptionalInt limit) {}
    }
}
