package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

public sealed interface RequestId permits RequestId.StringValue, RequestId.NumericValue {

    record StringValue(java.lang.String value) implements RequestId {
        @Override
        public String toString() {
            return value;
        }
    }

    record NumericValue(Number value) implements RequestId {
        @Override
        public String toString() {
            return value.toString();
        }
    }

    static RequestId of(Object value) {
        if (value instanceof CharSequence str) {
            return new RequestId.StringValue(str.toString());
        } else if (value instanceof Number number) {
            return new RequestId.NumericValue(number);
        } else {
            throw new IllegalArgumentException("String or numeric value expected");
        }
    }

    static @Nullable RequestId ofNullable(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        return of(value);
    }
}
