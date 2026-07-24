/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.server.json.JsonObject;
import dev.tachyonmcp.server.json.PayloadDeserializer;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class ArgsTest {

    @Test
    void ofNullRawProducesEmptyArgs() {
        var args = Args.of(null);
        assertThat(args.isEmpty()).isTrue();
    }

    @Test
    void ofNonNullRawProducesNonEmptyArgs() {
        var args = Args.of(Map.of("key", "val"));
        assertThat(args.isEmpty()).isFalse();
    }

    @Test
    void containsReturnsTrueForExistingKey() {
        var args = Args.of(Map.of("k", "v"));
        assertThat(args.contains("k")).isTrue();
    }

    @Test
    void containsReturnsFalseForMissingKey() {
        var args = Args.of(Map.of());
        assertThat(args.contains("k")).isFalse();
    }

    @Test
    void stringValueReturnsValue() {
        var args = Args.of(Map.of("k", "hello"));
        assertThat(args.stringValue("k")).isEqualTo("hello");
    }

    @Test
    void intValueReturnsValue() {
        var args = Args.of(Map.of("k", 42));
        assertThat(args.intValue("k")).isEqualTo(42);
    }

    @Test
    void boolValueReturnsValue() {
        var args = Args.of(Map.of("k", true));
        assertThat(args.boolValue("k")).isTrue();
    }

    @Test
    void doubleValueReturnsValue() {
        var args = Args.of(Map.of("k", 3.14));
        assertThat(args.doubleValue("k")).isEqualTo(3.14);
    }

    @Test
    void stringOptReturnsValueWhenPresent() {
        var args = Args.of(Map.of("k", "v"));
        assertThat(args.stringOpt("k")).contains("v");
    }

    @Test
    void stringOptReturnsEmptyWhenMissing() {
        var args = Args.of(Map.of());
        assertThat(args.stringOpt("k")).isEmpty();
    }

    @Test
    void stringOrReturnsValueWhenPresent() {
        var args = Args.of(Map.of("k", "v"));
        assertThat(args.stringOr("k", "def")).isEqualTo("v");
    }

    @Test
    void stringOrReturnsFallbackWhenMissing() {
        var args = Args.of(Map.of());
        assertThat(args.stringOr("k", "def")).isEqualTo("def");
    }

    @Test
    void isAJsonObject() {
        JsonObject args = Args.of(Map.of("nested", Map.of("name", "tachyon")));

        assertThat(args.objectValue("nested").stringValue("name")).isEqualTo("tachyon");
    }

    @Test
    void requiredValueThrowsWhenMissing() {
        var args = Args.of(Map.of());
        assertThatThrownBy(() -> args.stringValue("missingArg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missingArg");
    }

    @Test
    void asMapReturnsUnmodifiableView() {
        var raw = new HashMap<String, Object>(Map.of("k", "v"));
        var args = Args.of(raw);
        assertThat(args.asMap()).containsOnlyKeys("k");
        assertThat(args.asMap()).isUnmodifiable();
    }

    @Test
    void boolOrReturnsValueWhenPresent() {
        var args = Args.of(Map.of("k", true));
        assertThat(args.boolOr("k", false)).isTrue();
    }

    @Test
    void boolOrReturnsFallbackWhenMissing() {
        var args = Args.of(Map.of());
        assertThat(args.boolOr("k", true)).isTrue();
    }

    @Test
    void intOrReturnsValueWhenPresent() {
        var args = Args.of(Map.of("k", 42));
        assertThat(args.intOr("k", 0)).isEqualTo(42);
    }

    @Test
    void intOrReturnsFallbackWhenMissing() {
        var args = Args.of(Map.of());
        assertThat(args.intOr("k", 99)).isEqualTo(99);
    }

    @Test
    void doubleOrReturnsValueWhenPresent() {
        var args = Args.of(Map.of("k", 3.14));
        assertThat(args.doubleOr("k", 0.0)).isEqualTo(3.14);
    }

    @Test
    void doubleOrReturnsFallbackWhenMissing() {
        var args = Args.of(Map.of());
        assertThat(args.doubleOr("k", 1.5)).isEqualTo(1.5);
    }

    @Test
    void supportsLongAndDecimalValues() {
        var args = Args.of(Map.of("long", Long.MAX_VALUE, "decimal", new BigDecimal("1.25")));

        assertThat(args.longValue("long")).isEqualTo(Long.MAX_VALUE);
        assertThat(args.decimalValue("decimal")).isEqualTo(new BigDecimal("1.25"));
    }

    @Test
    void fromRetainsProviderJsonForRawAndDecodedArguments() {
        var provider = new Object();
        var values =
                new RetainedJsonObject("{\"source\":\"provider\"}", JsonObject.of(Map.of("source", "map")), provider);
        var args = Args.from(values, new EchoingPayloadDeserializer());

        assertThat(args.stringValue("source")).isEqualTo("map");
        assertThat(args.rawJson()).isEqualTo("{\"source\":\"provider\"}");
        assertThat(args.decode(String.class)).isEqualTo("{\"source\":\"provider\"}");
        assertThat(args.unwrap(Object.class)).containsSame(provider);
    }

    private record RetainedJsonObject(String json, JsonObject delegate, Object provider) implements JsonObject {

        @Override
        public boolean contains(String name) {
            return delegate.contains(name);
        }

        @Override
        public Optional<JsonObject> objectOpt(String name) {
            return delegate.objectOpt(name);
        }

        @Override
        public Optional<String> stringOpt(String name) {
            return delegate.stringOpt(name);
        }

        @Override
        public Optional<Boolean> boolOpt(String name) {
            return delegate.boolOpt(name);
        }

        @Override
        public Optional<BigDecimal> decimalOpt(String name) {
            return delegate.decimalOpt(name);
        }

        @Override
        public OptionalInt intOpt(String name) {
            return delegate.intOpt(name);
        }

        @Override
        public OptionalLong longOpt(String name) {
            return delegate.longOpt(name);
        }

        @Override
        public OptionalDouble doubleOpt(String name) {
            return delegate.doubleOpt(name);
        }

        @Override
        public Map<String, Object> asMap() {
            return delegate.asMap();
        }

        @Override
        public <T> Optional<T> unwrap(Class<T> type) {
            return type.isInstance(provider) ? Optional.of(type.cast(provider)) : delegate.unwrap(type);
        }
    }

    private static final class EchoingPayloadDeserializer implements PayloadDeserializer {

        @Override
        @SuppressWarnings("unchecked")
        public <T> T deserialize(String json, Type targetType) {
            return (T) json;
        }
    }
}
