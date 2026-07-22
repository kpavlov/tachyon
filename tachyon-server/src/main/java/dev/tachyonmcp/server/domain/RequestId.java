/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

/**
 * A JSON-RPC 2.0 request/response id: a bare JSON string or number, never an object or array. See
 * {@link #of(Object)} and {@link #ofNullable(Object)} for how raw wire values map to this type.
 */
public sealed interface RequestId permits RequestId.StringValue, RequestId.NumericValue {

    /** An id carrying a string value. */
    record StringValue(java.lang.String value) implements RequestId {
        @Override
        public String toString() {
            return value;
        }
    }

    /** An id carrying a numeric value. */
    record NumericValue(Number value) implements RequestId {
        @Override
        public String toString() {
            return value.toString();
        }
    }

    /**
     * Wraps a raw value as a {@link RequestId}.
     *
     * @param value a {@link CharSequence} or {@link Number}
     * @return the wrapped id
     * @throws IllegalArgumentException if {@code value} is neither a {@link CharSequence} nor a
     *                                  {@link Number}
     */
    static RequestId of(Object value) {
        if (value instanceof CharSequence str) {
            return new RequestId.StringValue(str.toString());
        } else if (value instanceof Number number) {
            return new RequestId.NumericValue(number);
        } else {
            throw new IllegalArgumentException("String or numeric value expected");
        }
    }

    /**
     * Wraps a raw value as a {@link RequestId}, passing through {@code null} — for the JSON-RPC
     * {@code id} field, which is legitimately absent on notifications and some error responses.
     *
     * @param value a {@link CharSequence}, {@link Number}, or {@code null}
     * @return the wrapped id, or {@code null} if {@code value} was {@code null}
     * @throws IllegalArgumentException if {@code value} is non-null and neither a
     *                                  {@link CharSequence} nor a {@link Number}
     */
    static @Nullable RequestId ofNullable(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        return of(value);
    }
}
