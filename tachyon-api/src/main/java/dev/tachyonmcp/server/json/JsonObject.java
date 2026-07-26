/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * An immutable, provider-neutral view of a JSON object.
 *
 * <p>Missing properties and JSON {@code null} produce empty optionals. Accessing a property as the
 * wrong type, narrowing a fraction to an integer, or overflowing the requested numeric type throws
 * {@link IllegalArgumentException}. Numeric strings and other scalar types are never coerced.
 *
 * <p>Implementations may retain a provider-specific representation and expose it through {@link
 * #unwrap(Class)}.
 *
 * @author Konstantin Pavlov
 */
public interface JsonObject extends JsonDocument {

    /**
     * Returns whether the object contains {@code name}, including when its value is JSON null.
     *
     * @param name the property name
     * @return true if the property exists
     */
    boolean contains(String name);

    /**
     * Returns the named object, or an empty optional when it is missing or JSON null.
     *
     * @param name the property name
     * @return the object value, or empty if missing or null
     */
    Optional<JsonObject> objectOpt(String name);

    /**
     * Returns the named string, or an empty optional when it is missing or JSON null.
     *
     * @param name the property name
     * @return the string value, or empty if missing or null
     */
    Optional<String> stringOpt(String name);

    /**
     * Returns the named boolean, or an empty optional when it is missing or JSON null.
     *
     * @param name the property name
     * @return the boolean value, or empty if missing or null
     */
    Optional<Boolean> boolOpt(String name);

    /**
     * Returns the named number without precision loss.
     *
     * @param name the property name
     * @return the decimal value, or empty if missing or null
     */
    Optional<BigDecimal> decimalOpt(String name);

    /**
     * Returns the named exact {@code int}.
     *
     * @param name the property name
     * @return the int value, or empty if missing, null, or non-integral
     */
    OptionalInt intOpt(String name);

    /**
     * Returns the named exact {@code long}.
     *
     * @param name the property name
     * @return the long value, or empty if missing, null, or non-integral
     */
    OptionalLong longOpt(String name);

    /**
     * Returns the named value converted to a finite {@code double}.
     *
     * <p>The conversion may lose precision. Use {@link #decimalOpt(String)} when an exact decimal
     * representation is required.
     *
     * @param name the property name
     * @return the double value, or empty if missing or null
     */
    OptionalDouble doubleOpt(String name);

    /**
     * Returns the named required object.
     *
     * @param name the property name
     * @return the object value
     * @throws IllegalArgumentException if the property is missing or null
     */
    default JsonObject objectValue(String name) {
        return objectOpt(name).orElseThrow(() -> missing(name));
    }

    /**
     * Returns the named required string.
     *
     * @param name the property name
     * @return the string value
     * @throws IllegalArgumentException if the property is missing or null
     */
    default String stringValue(String name) {
        return stringOpt(name).orElseThrow(() -> missing(name));
    }

    /**
     * Returns the named required boolean.
     *
     * @param name the property name
     * @return the boolean value
     * @throws IllegalArgumentException if the property is missing or null
     */
    default boolean boolValue(String name) {
        return boolOpt(name).orElseThrow(() -> missing(name));
    }

    /**
     * Returns the named required number without precision loss.
     *
     * @param name the property name
     * @return the decimal value
     * @throws IllegalArgumentException if the property is missing or null
     */
    default BigDecimal decimalValue(String name) {
        return decimalOpt(name).orElseThrow(() -> missing(name));
    }

    /**
     * Returns the named required exact {@code int}.
     *
     * @param name the property name
     * @return the int value
     * @throws IllegalArgumentException if the property is missing or null
     */
    default int intValue(String name) {
        return intOpt(name).orElseThrow(() -> missing(name));
    }

    /**
     * Returns the named required exact {@code long}.
     *
     * @param name the property name
     * @return the long value
     * @throws IllegalArgumentException if the property is missing or null
     */
    default long longValue(String name) {
        return longOpt(name).orElseThrow(() -> missing(name));
    }

    /**
     * Returns the named required finite {@code double}.
     *
     * @param name the property name
     * @return the double value
     * @throws IllegalArgumentException if the property is missing or null
     */
    default double doubleValue(String name) {
        return doubleOpt(name).orElseThrow(() -> missing(name));
    }

    /**
     * Returns the named object or {@code fallback} when it is missing or JSON null.
     *
     * @param name     the property name
     * @param fallback the fallback value
     * @return the object value, or fallback
     */
    default JsonObject objectOr(String name, JsonObject fallback) {
        return objectOpt(name).orElse(fallback);
    }

    /**
     * Returns the named string or {@code fallback} when it is missing or JSON null.
     *
     * @param name     the property name
     * @param fallback the fallback value
     * @return the string value, or fallback
     */
    default String stringOr(String name, String fallback) {
        return stringOpt(name).orElse(fallback);
    }

    /**
     * Returns the named boolean or {@code fallback} when it is missing or JSON null.
     *
     * @param name     the property name
     * @param fallback the fallback value
     * @return the boolean value, or fallback
     */
    default boolean boolOr(String name, boolean fallback) {
        return boolOpt(name).orElse(fallback);
    }

    /**
     * Returns the named decimal or {@code fallback} when it is missing or JSON null.
     *
     * @param name     the property name
     * @param fallback the fallback value
     * @return the decimal value, or fallback
     */
    default BigDecimal decimalOr(String name, BigDecimal fallback) {
        return decimalOpt(name).orElse(fallback);
    }

    /**
     * Returns the named exact {@code int} or {@code fallback} when it is missing or JSON null.
     *
     * @param name     the property name
     * @param fallback the fallback value
     * @return the int value, or fallback
     */
    default int intOr(String name, int fallback) {
        return intOpt(name).orElse(fallback);
    }

    /**
     * Returns the named exact {@code long} or {@code fallback} when it is missing or JSON null.
     *
     * @param name     the property name
     * @param fallback the fallback value
     * @return the long value, or fallback
     */
    default long longOr(String name, long fallback) {
        return longOpt(name).orElse(fallback);
    }

    /**
     * Returns the named finite {@code double} or {@code fallback} when missing or JSON null.
     *
     * @param name     the property name
     * @param fallback the fallback value
     * @return the double value, or fallback
     */
    default double doubleOr(String name, double fallback) {
        return doubleOpt(name).orElse(fallback);
    }

    /**
     * Returns an immutable map representation.
     *
     * @return the map view
     */
    Map<String, Object> asMap();

    /**
     * Creates an immutable object by recursively snapshotting JSON-compatible values.
     *
     * @param values the source map
     * @return a new JSON object
     */
    static JsonObject of(Map<String, ?> values) {
        return new DefaultJsonObject(values);
    }

    /**
     * Returns an empty JSON object.
     *
     * @return the empty instance
     */
    static JsonObject empty() {
        return DefaultJsonObject.EMPTY;
    }

    private static IllegalArgumentException missing(String name) {
        return new IllegalArgumentException("Required JSON property '%s' is missing or null".formatted(name));
    }
}
