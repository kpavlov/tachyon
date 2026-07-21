package dev.tachyonmcp.server.domain;

public sealed interface ProgressToken permits ProgressToken.StringValue, ProgressToken.NumericValue {

    record StringValue(java.lang.String value) implements ProgressToken {
        @Override
        public String toString() {
            return value;
        }
    }

    record NumericValue(Number value) implements ProgressToken {
        @Override
        public String toString() {
            return value.toString();
        }
    }

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
