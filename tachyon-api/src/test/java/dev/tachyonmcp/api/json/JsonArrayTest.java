/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonArrayTest {

    @Test
    void readsPositionalValuesWithoutNumericCoercion() {
        var array = JsonArray.of(List.of("tachyon", 42, new BigDecimal("1.25"), true, Map.of("name", "child")));

        assertThat(array.size()).isEqualTo(5);
        assertThat(array.isEmpty()).isFalse();
        assertThat(array.stringValue(0)).isEqualTo("tachyon");
        assertThat(array.intValue(1)).isEqualTo(42);
        assertThat(array.decimalValue(2)).isEqualTo(new BigDecimal("1.25"));
        assertThat(array.doubleValue(2)).isEqualTo(1.25);
        assertThat(array.boolValue(3)).isTrue();
        assertThat(array.objectValue(4).stringValue("name")).isEqualTo("child");
    }

    @Test
    void readsNestedArrays() {
        var object = JsonObject.of(Map.of("matrix", List.of(List.of(1, 2), List.of(3, 4))));

        var matrix = object.arrayValue("matrix");
        assertThat(matrix.arrayValue(0).valuesAs(Integer.class)).containsExactly(1, 2);
        assertThat(matrix.arrayValue(1).intValue(1)).isEqualTo(4);
    }

    @Test
    void extractsHomogeneousValuesAs() {
        assertThat(JsonArray.of(List.of("a", "b", "c")).valuesAs(String.class)).containsExactly("a", "b", "c");
        assertThat(JsonArray.of(List.of(1, 2, 3)).valuesAs(Integer.class)).containsExactly(1, 2, 3);
        assertThat(JsonArray.of(List.of(Map.of("k", "v"))).valuesAs(JsonObject.class))
                .singleElement()
                .satisfies(element -> assertThat(element.stringValue("k")).isEqualTo("v"));
    }

    @Test
    void reusesNestedJsonDocuments() {
        var object = JsonObject.of(Map.of("child", Map.of("name", "child"), "items", List.of("item")));
        var objects = JsonArray.of(List.of(Map.of("name", "child")));
        var arrays = JsonArray.of(List.of(List.of("item")));

        assertThat(object.objectValue("child")).isSameAs(object.objectValue("child"));
        assertThat(object.objectValue("child").asMap()).isSameAs(object.asMap().get("child"));
        assertThat(object.arrayValue("items")).isSameAs(object.arrayValue("items"));
        assertThat(objects.valuesAs(JsonObject.class).getFirst())
                .isSameAs(objects.valuesAs(JsonObject.class).getFirst());
        assertThat(arrays.valuesAs(JsonArray.class).getFirst())
                .isSameAs(arrays.valuesAs(JsonArray.class).getFirst());
    }

    @Test
    void readsArrayFromEnclosingObjectPreservingEmptyVsMissing() {
        var object = JsonObject.of(Map.of("tags", List.of("x", "y"), "empty", List.of()));

        assertThat(object.arrayValue("tags").valuesAs(String.class)).containsExactly("x", "y");
        assertThat(object.arrayOpt("empty"))
                .get()
                .extracting(JsonArray::isEmpty)
                .isEqualTo(true);
        assertThat(object.arrayOpt("missing")).isEmpty();
    }

    @Test
    void treatsJsonNullElementAsAbsent() {
        var array = JsonArray.of(Arrays.asList("a", null, "c"));

        assertThat(array.stringOpt(1)).isEmpty();
        assertThat(array.stringOr(1, "fallback")).isEqualTo("fallback");
        assertThatThrownBy(() -> array.stringValue(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[1]");
        assertThatThrownBy(() -> array.valuesAs(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[1]");
        assertThat(array.asList()).containsExactly("a", null, "c");
    }

    @Test
    void readsArrayOrFallsBackOnJsonNull() {
        var fallback = JsonArray.of(List.of("fallback"));
        var array = JsonArray.of(Arrays.asList(List.of("nested"), null));

        assertThat(array.arrayOr(0, fallback).valuesAs(String.class)).containsExactly("nested");
        assertThat(array.arrayOr(1, fallback)).isSameAs(fallback);
    }

    @Test
    void rejectsWrongTypesAndOutOfBounds() {
        var array = JsonArray.of(List.of("not-a-number"));

        assertThatThrownBy(() -> array.intOpt(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[0]");
        assertThatThrownBy(() -> array.stringOpt(1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> array.stringOpt(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> JsonArray.empty().stringOpt(0)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void rejectsUnsupportedElementType() {
        assertThatThrownBy(() -> JsonArray.of(List.of("a")).valuesAs(Thread.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void snapshotsInputAndIsImmutable() {
        var source = new ArrayList<Object>(List.of("before"));
        var array = JsonArray.of(source);
        source.add("after");

        assertThat(array.size()).isEqualTo(1);
        assertThatThrownBy(() -> array.asList().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void serializesToJson() {
        assertThat(JsonArray.of(List.of(1, 2, 3)).json()).isEqualTo("[1,2,3]");
        assertThat(JsonArray.of(List.of("a", true)).json()).isEqualTo("[\"a\",true]");
        assertThat(JsonArray.empty().json()).isEqualTo("[]");
    }
}
