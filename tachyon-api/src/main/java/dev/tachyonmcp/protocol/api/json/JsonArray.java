/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.protocol.api.json;

import dev.tachyonmcp.protocol.api.annotations.ExperimentalApi;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * An immutable, provider-neutral view of a JSON array.
 *
 * <p>Elements are read by position. A JSON {@code null} element produces an empty optional;
 * accessing an element as the wrong type, narrowing a fraction to an integer, or overflowing the
 * requested numeric type throws {@link IllegalArgumentException}. Numeric strings and other scalar
 * types are never coerced. An index outside {@code [0, size())} throws {@link
 * IndexOutOfBoundsException}.
 *
 * <p>Nested objects and arrays are returned as {@link JsonObject} and {@link JsonArray}. For a bulk
 * typed view of a homogeneous array use {@link #valuesAs(Class)}; for heterogeneous or nested
 * generic shapes decode the enclosing payload with a serde.
 *
 * @author Konstantin Pavlov
 */
public interface JsonArray extends JsonDocument {

    /**
     * Returns the number of elements.
     *
     * @return the element count
     */
    int size();

    /**
     * Returns whether the array has no elements.
     *
     * @return true if empty
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns the element at {@code index} as an object, or an empty optional when it is JSON null.
     *
     * @param index the element index
     * @return the object value, or empty if null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    Optional<JsonObject> objectOpt(int index);

    /**
     * Returns the element at {@code index} as an array, or an empty optional when it is JSON null.
     *
     * @param index the element index
     * @return the array value, or empty if null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    Optional<JsonArray> arrayOpt(int index);

    /**
     * Returns the element at {@code index} as a string, or an empty optional when it is JSON null.
     *
     * @param index the element index
     * @return the string value, or empty if null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    Optional<String> stringOpt(int index);

    /**
     * Returns the element at {@code index} as a boolean, or an empty optional when it is JSON null.
     *
     * @param index the element index
     * @return the boolean value, or empty if null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    Optional<Boolean> boolOpt(int index);

    /**
     * Returns the element at {@code index} as a number without precision loss.
     *
     * @param index the element index
     * @return the decimal value, or empty if null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    Optional<BigDecimal> decimalOpt(int index);

    /**
     * Returns the element at {@code index} as an exact {@code int}.
     *
     * @param index the element index
     * @return the int value, or empty if null
     * @throws IndexOutOfBoundsException if the index is out of range
     * @throws IllegalArgumentException if the value is not a number or is not exactly representable
     *     as an {@code int}
     */
    OptionalInt intOpt(int index);

    /**
     * Returns the element at {@code index} as an exact {@code long}.
     *
     * @param index the element index
     * @return the long value, or empty if null
     * @throws IndexOutOfBoundsException if the index is out of range
     * @throws IllegalArgumentException if the value is not a number or is not exactly representable
     *     as a {@code long}
     */
    OptionalLong longOpt(int index);

    /**
     * Returns the element at {@code index} converted to a finite {@code double}.
     *
     * @param index the element index
     * @return the double value, or empty if null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    OptionalDouble doubleOpt(int index);

    /**
     * Returns the required object element at {@code index}.
     *
     * @param index the element index
     * @return the object value
     * @throws IllegalArgumentException if the element is JSON null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default JsonObject objectValue(int index) {
        return objectOpt(index).orElseThrow(() -> missing(index));
    }

    /**
     * Returns the required array element at {@code index}.
     *
     * @param index the element index
     * @return the array value
     * @throws IllegalArgumentException if the element is JSON null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default JsonArray arrayValue(int index) {
        return arrayOpt(index).orElseThrow(() -> missing(index));
    }

    /**
     * Returns the required string element at {@code index}.
     *
     * @param index the element index
     * @return the string value
     * @throws IllegalArgumentException if the element is JSON null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default String stringValue(int index) {
        return stringOpt(index).orElseThrow(() -> missing(index));
    }

    /**
     * Returns the required boolean element at {@code index}.
     *
     * @param index the element index
     * @return the boolean value
     * @throws IllegalArgumentException if the element is JSON null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default boolean boolValue(int index) {
        return boolOpt(index).orElseThrow(() -> missing(index));
    }

    /**
     * Returns the required number element at {@code index} without precision loss.
     *
     * @param index the element index
     * @return the decimal value
     * @throws IllegalArgumentException if the element is JSON null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default BigDecimal decimalValue(int index) {
        return decimalOpt(index).orElseThrow(() -> missing(index));
    }

    /**
     * Returns the required exact {@code int} element at {@code index}.
     *
     * @param index the element index
     * @return the int value
     * @throws IllegalArgumentException if the element is JSON null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default int intValue(int index) {
        return intOpt(index).orElseThrow(() -> missing(index));
    }

    /**
     * Returns the required exact {@code long} element at {@code index}.
     *
     * @param index the element index
     * @return the long value
     * @throws IllegalArgumentException if the element is JSON null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default long longValue(int index) {
        return longOpt(index).orElseThrow(() -> missing(index));
    }

    /**
     * Returns the required finite {@code double} element at {@code index}.
     *
     * @param index the element index
     * @return the double value
     * @throws IllegalArgumentException if the element is JSON null
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default double doubleValue(int index) {
        return doubleOpt(index).orElseThrow(() -> missing(index));
    }

    /**
     * Returns the object element at {@code index} or {@code fallback} when it is JSON null.
     *
     * @param index    the element index
     * @param fallback the fallback value
     * @return the object value, or fallback
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default JsonObject objectOr(int index, JsonObject fallback) {
        return objectOpt(index).orElse(fallback);
    }

    /**
     * Returns the array element at {@code index} or {@code fallback} when it is JSON null.
     *
     * @param index    the element index
     * @param fallback the fallback value
     * @return the array value, or fallback
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default JsonArray arrayOr(int index, JsonArray fallback) {
        return arrayOpt(index).orElse(fallback);
    }

    /**
     * Returns the string element at {@code index} or {@code fallback} when it is JSON null.
     *
     * @param index    the element index
     * @param fallback the fallback value
     * @return the string value, or fallback
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default String stringOr(int index, String fallback) {
        return stringOpt(index).orElse(fallback);
    }

    /**
     * Returns the boolean element at {@code index} or {@code fallback} when it is JSON null.
     *
     * @param index    the element index
     * @param fallback the fallback value
     * @return the boolean value, or fallback
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default boolean boolOr(int index, boolean fallback) {
        return boolOpt(index).orElse(fallback);
    }

    /**
     * Returns the decimal element at {@code index} or {@code fallback} when it is JSON null.
     *
     * @param index    the element index
     * @param fallback the fallback value
     * @return the decimal value, or fallback
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default BigDecimal decimalOr(int index, BigDecimal fallback) {
        return decimalOpt(index).orElse(fallback);
    }

    /**
     * Returns the exact {@code int} element at {@code index} or {@code fallback} when JSON null.
     *
     * @param index    the element index
     * @param fallback the fallback value
     * @return the int value, or fallback
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default int intOr(int index, int fallback) {
        return intOpt(index).orElse(fallback);
    }

    /**
     * Returns the exact {@code long} element at {@code index} or {@code fallback} when JSON null.
     *
     * @param index    the element index
     * @param fallback the fallback value
     * @return the long value, or fallback
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default long longOr(int index, long fallback) {
        return longOpt(index).orElse(fallback);
    }

    /**
     * Returns the finite {@code double} element at {@code index} or {@code fallback} when JSON null.
     *
     * @param index    the element index
     * @param fallback the fallback value
     * @return the double value, or fallback
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    default double doubleOr(int index, double fallback) {
        return doubleOpt(index).orElse(fallback);
    }

    /**
     * Returns every element coerced to {@code element}.
     *
     * <p>Supported element types: {@link String}, {@link Boolean}, {@link BigDecimal}, {@link
     * Integer}, {@link Long}, {@link Double}, {@link JsonObject}, and {@link JsonArray}. A JSON null, wrong-typed, or
     * overflowing element throws {@link IllegalArgumentException} naming its index. Bean and nested
     * generic element types are not supported — decode the enclosing payload with a serde instead.
     *
     * @param <T>     the element type
     * @param element the element class
     * @return an immutable list of the coerced elements
     * @throws IllegalArgumentException if the element type is unsupported or an element cannot be
     *     coerced
     */
    <T> List<T> valuesAs(Class<T> element);

    /**
     * Returns an immutable list representation.
     *
     * @return the list view
     */
    @ExperimentalApi
    List<Object> asList();

    /**
     * Creates an immutable array by recursively snapshotting JSON-compatible values.
     *
     * @param values the source list
     * @return a new JSON array
     */
    static JsonArray of(List<?> values) {
        return new DefaultJsonArray(values);
    }

    /**
     * Returns an empty JSON array.
     *
     * @return the empty instance
     */
    static JsonArray empty() {
        return DefaultJsonArray.EMPTY;
    }

    private static IllegalArgumentException missing(int index) {
        return new IllegalArgumentException("Required JSON element [%d] is missing or null".formatted(index));
    }
}
