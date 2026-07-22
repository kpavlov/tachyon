/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

/**
 * An MCP progress token: an opaque value the client attaches to a request via
 * {@code _meta.progressToken} and the server echoes back on {@code notifications/progress}. Per
 * the MCP spec, a progress token is a bare JSON string or number — never an object or array.
 */
public sealed interface ProgressToken permits ProgressToken.StringValue, ProgressToken.NumericValue {

    /** A progress token carrying a string value. */
    record StringValue(java.lang.String value) implements ProgressToken {
        @Override
        public String toString() {
            return value;
        }
    }

    /** A progress token carrying a numeric value. */
    record NumericValue(Number value) implements ProgressToken {
        @Override
        public String toString() {
            return value.toString();
        }
    }

    /**
     * Wraps a raw value as a {@link ProgressToken}.
     *
     * @param value a {@link CharSequence} or {@link Number}
     * @return the wrapped token
     * @throws IllegalArgumentException if {@code value} is neither a {@link CharSequence} nor a
     *                                  {@link Number}
     */
    static ProgressToken of(Object value) {
        if (value instanceof CharSequence str) {
            return new StringValue(str.toString());
        } else if (value instanceof Number number) {
            return new NumericValue(number);
        } else {
            throw new IllegalArgumentException("String or numeric value expected");
        }
    }
}
