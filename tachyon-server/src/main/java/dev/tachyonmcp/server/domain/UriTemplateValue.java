/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import java.util.List;
import java.util.Objects;

/**
 * Value recovered from an RFC 6570 URI template.
 *
 * <p>Associative values are intentionally unsupported until MCP defines variable schemas that can
 * distinguish them from sequences.
 */
public sealed interface UriTemplateValue permits UriTemplateValue.Scalar, UriTemplateValue.Sequence {

    default String scalarValue() {
        if (this instanceof Scalar s) {
            return s.value;
        } else if (this instanceof Sequence seq) {
            if (seq.values.size() > 1) {
                throw new IllegalStateException("Sequence has more than one element");
            } else {
                return seq.values.getFirst();
            }
        } else {
            throw new UnsupportedOperationException("unsupported value");
        }
    }

    default Scalar asScalar() {
        if (this instanceof Scalar s) {
            return s;
        } else {
            return new Scalar(scalarValue());
        }
    }

    default Sequence asSequence() {
        if (this instanceof Sequence s) {
            return s;
        } else {
            return new Sequence(List.of(((Scalar) this).value));
        }
    }

    /**
     * A scalar template variable.
     */
    record Scalar(String value) implements UriTemplateValue {
        public Scalar {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * An exploded list template variable.
     */
    record Sequence(List<String> values) implements UriTemplateValue {
        public Sequence {
            Objects.requireNonNull(values, "values");
            values = List.copyOf(values);
        }
    }
}
