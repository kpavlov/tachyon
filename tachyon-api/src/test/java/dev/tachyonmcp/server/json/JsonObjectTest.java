/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonObjectTest {

    @Test
    void readsJsonValuesWithoutNumericCoercion() {
        var object = JsonObject.of(Map.of(
                "decimal",
                new BigDecimal("12.50"),
                "int",
                42,
                "long",
                Long.MAX_VALUE,
                "double",
                1.25,
                "string",
                "tachyon",
                "boolean",
                true,
                "nested",
                Map.of("name", "child")));

        assertThat(object.decimalOpt("decimal")).contains(new BigDecimal("12.50"));
        assertThat(object.intOpt("int")).hasValue(42);
        assertThat(object.longOpt("long")).hasValue(Long.MAX_VALUE);
        assertThat(object.doubleOpt("double")).hasValue(1.25);
        assertThat(object.stringOpt("string")).contains("tachyon");
        assertThat(object.boolOpt("boolean")).contains(true);
        assertThat(object.objectOpt("nested").flatMap(value -> value.stringOpt("name")))
                .contains("child");
    }

    @Test
    void returnsEmptyForMissingAndNullValues() {
        var values = new LinkedHashMap<String, Object>();
        values.put("null", null);
        var object = JsonObject.of(values);

        assertThat(object.contains("missing")).isFalse();
        assertThat(object.contains("null")).isTrue();
        assertThat(object.decimalOpt("missing")).isEmpty();
        assertThat(object.intOpt("null")).isEmpty();
        assertThat(object.longOpt("missing")).isEmpty();
        assertThat(object.doubleOpt("null")).isEmpty();
        assertThat(object.stringOpt("missing")).isEmpty();
        assertThat(object.boolOpt("null")).isEmpty();
        assertThat(object.objectOpt("missing")).isEmpty();
    }

    @Test
    void rejectsWrongTypesFractionsAndOverflow() {
        var object = JsonObject.of(Map.of(
                "string", "42",
                "fraction", new BigDecimal("1.5"),
                "overflow", new BigDecimal("2147483648")));

        assertThatThrownBy(() -> object.intOpt("string"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("string");
        assertThatThrownBy(() -> object.intOpt("fraction"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fraction");
        assertThatThrownBy(() -> object.intOpt("overflow"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overflow");
    }

    @Test
    void copiesInputRecursively() {
        var nested = new LinkedHashMap<String, Object>();
        nested.put("name", "before");
        var values = new LinkedHashMap<String, Object>();
        values.put("nested", nested);

        var object = JsonObject.of(values);
        nested.put("name", "after");
        values.put("added", true);

        assertThat(object.objectOpt("nested").flatMap(value -> value.stringOpt("name")))
                .contains("before");
        assertThat(object.contains("added")).isFalse();
        assertThatThrownBy(() -> object.asMap().put("added", true)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void providesFallbacks() {
        var object = JsonObject.of(Map.of(
                "string",
                "tachyon",
                "boolean",
                true,
                "decimal",
                new BigDecimal("1.25"),
                "int",
                2,
                "long",
                3L,
                "double",
                4.5,
                "nested",
                Map.of("name", "child")));

        assertThat(object.stringOr("string", "fallback")).isEqualTo("tachyon");
        assertThat(object.stringOr("missing", "fallback")).isEqualTo("fallback");
        assertThat(object.boolOr("boolean", false)).isTrue();
        assertThat(object.boolOr("missing", true)).isTrue();
        assertThat(object.intOr("missing", 1)).isEqualTo(1);
        assertThat(object.longOr("missing", 2L)).isEqualTo(2L);
        assertThat(object.doubleOr("missing", 3.0)).isEqualTo(3.0);
        assertThat(object.decimalOr("missing", BigDecimal.TEN)).isEqualTo(BigDecimal.TEN);
    }

    @Test
    void providesRequiredValues() {
        var object = JsonObject.of(Map.of(
                "string",
                "tachyon",
                "boolean",
                true,
                "decimal",
                new BigDecimal("1.25"),
                "int",
                2,
                "long",
                3L,
                "double",
                4.5,
                "nested",
                Map.of("name", "child")));

        assertThat(object.stringValue("string")).isEqualTo("tachyon");
        assertThat(object.boolValue("boolean")).isTrue();
        assertThat(object.decimalValue("decimal")).isEqualTo(new BigDecimal("1.25"));
        assertThat(object.intValue("int")).isEqualTo(2);
        assertThat(object.longValue("long")).isEqualTo(3L);
        assertThat(object.doubleValue("double")).isEqualTo(4.5);
        assertThat(object.objectValue("nested").stringValue("name")).isEqualTo("child");
    }

    @Test
    void requiredValueRejectsMissingProperty() {
        var object = JsonObject.empty();

        assertThatThrownBy(() -> object.stringValue("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void unwrapsRetainedRepresentationOnlyForMatchingType() {
        var object = JsonObject.of(Map.of("name", "tachyon"));

        assertThat(object.unwrap(Map.class)).contains(object.asMap());
        assertThat(object.unwrap(String.class)).isEmpty();
    }

    @Test
    void encodesMapBackedObjectsAsJsonDocuments() {
        var values = new LinkedHashMap<String, Object>();
        values.put("text", "quote: \"\n");
        values.put("nested", Map.of("number", new BigDecimal("1.25")));
        values.put("items", java.util.Arrays.asList(true, null));

        assertThat(JsonObject.of(values).json())
                .isEqualTo("{\"text\":\"quote: \\\"\\n\",\"nested\":{\"number\":1.25},\"items\":[true,null]}");
    }
}
