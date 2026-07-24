/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

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

    /** Returns whether the object contains {@code name}, including when its value is JSON null. */
    boolean contains(String name);

    /** Returns the named object, or an empty optional when it is missing or JSON null. */
    Optional<JsonObject> objectOpt(String name);

    /** Returns the named string, or an empty optional when it is missing or JSON null. */
    Optional<String> stringOpt(String name);

    /** Returns the named boolean, or an empty optional when it is missing or JSON null. */
    Optional<Boolean> boolOpt(String name);

    /** Returns the named number without precision loss. */
    Optional<BigDecimal> decimalOpt(String name);

    /** Returns the named exact {@code int}. */
    OptionalInt intOpt(String name);

    /** Returns the named exact {@code long}. */
    OptionalLong longOpt(String name);

    /**
     * Returns the named value converted to a finite {@code double}.
     *
     * <p>The conversion may lose precision. Use {@link #decimalOpt(String)} when an exact decimal
     * representation is required.
     */
    OptionalDouble doubleOpt(String name);

    /** Returns the named required object. */
    default JsonObject objectValue(String name) {
        return objectOpt(name).orElseThrow(() -> missing(name));
    }

    /** Returns the named required string. */
    default String stringValue(String name) {
        return stringOpt(name).orElseThrow(() -> missing(name));
    }

    /** Returns the named required boolean. */
    default boolean boolValue(String name) {
        return boolOpt(name).orElseThrow(() -> missing(name));
    }

    /** Returns the named required number without precision loss. */
    default BigDecimal decimalValue(String name) {
        return decimalOpt(name).orElseThrow(() -> missing(name));
    }

    /** Returns the named required exact {@code int}. */
    default int intValue(String name) {
        return intOpt(name).orElseThrow(() -> missing(name));
    }

    /** Returns the named required exact {@code long}. */
    default long longValue(String name) {
        return longOpt(name).orElseThrow(() -> missing(name));
    }

    /** Returns the named required finite {@code double}. */
    default double doubleValue(String name) {
        return doubleOpt(name).orElseThrow(() -> missing(name));
    }

    /** Returns the named object or {@code fallback} when it is missing or JSON null. */
    default JsonObject objectOr(String name, JsonObject fallback) {
        return objectOpt(name).orElse(fallback);
    }

    /** Returns the named string or {@code fallback} when it is missing or JSON null. */
    default String stringOr(String name, String fallback) {
        return stringOpt(name).orElse(fallback);
    }

    /** Returns the named boolean or {@code fallback} when it is missing or JSON null. */
    default boolean boolOr(String name, boolean fallback) {
        return boolOpt(name).orElse(fallback);
    }

    /** Returns the named decimal or {@code fallback} when it is missing or JSON null. */
    default BigDecimal decimalOr(String name, BigDecimal fallback) {
        return decimalOpt(name).orElse(fallback);
    }

    /** Returns the named exact {@code int} or {@code fallback} when it is missing or JSON null. */
    default int intOr(String name, int fallback) {
        return intOpt(name).orElse(fallback);
    }

    /** Returns the named exact {@code long} or {@code fallback} when it is missing or JSON null. */
    default long longOr(String name, long fallback) {
        return longOpt(name).orElse(fallback);
    }

    /** Returns the named finite {@code double} or {@code fallback} when missing or JSON null. */
    default double doubleOr(String name, double fallback) {
        return doubleOpt(name).orElse(fallback);
    }

    /** Returns an immutable map representation. */
    Map<String, Object> asMap();

    /** Creates an immutable object by recursively snapshotting JSON-compatible values. */
    static JsonObject of(Map<String, ?> values) {
        return new DefaultJsonObject(values);
    }

    /** Returns an empty JSON object. */
    static JsonObject empty() {
        return DefaultJsonObject.EMPTY;
    }

    private static IllegalArgumentException missing(String name) {
        return new IllegalArgumentException("Required JSON property '%s' is missing or null".formatted(name));
    }
}
