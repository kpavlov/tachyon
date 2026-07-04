/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class ToolArgsTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void ofNullRawProducesEmptyArgs() {
        var args = ToolArgs.of(null);
        assertThat(args.isEmpty()).isTrue();
    }

    @Test
    void ofNonNullRawProducesNonEmptyArgs() {
        var args = ToolArgs.of(Map.of("key", JSON.stringNode("val")));
        assertThat(args.isEmpty()).isFalse();
    }

    @Test
    void hasReturnsTrueForExistingKey() {
        var args = ToolArgs.of(Map.of("k", JSON.stringNode("v")));
        assertThat(args.has("k")).isTrue();
    }

    @Test
    void hasReturnsFalseForMissingKey() {
        var args = ToolArgs.of(Map.of());
        assertThat(args.has("k")).isFalse();
    }

    @Test
    void stringReturnsValue() {
        var args = ToolArgs.of(Map.of("k", JSON.stringNode("hello")));
        assertThat(args.string("k")).isEqualTo("hello");
    }

    @Test
    void intValueReturnsValue() {
        var args = ToolArgs.of(Map.of("k", JSON.numberNode(42)));
        assertThat(args.intValue("k")).isEqualTo(42);
    }

    @Test
    void boolValueReturnsValue() {
        var args = ToolArgs.of(Map.of("k", JSON.booleanNode(true)));
        assertThat(args.boolValue("k")).isTrue();
    }

    @Test
    void doubleValueReturnsValue() {
        var args = ToolArgs.of(Map.of("k", JSON.numberNode(3.14)));
        assertThat(args.doubleValue("k")).isEqualTo(3.14);
    }

    @Test
    void stringOptReturnsValueWhenPresent() {
        var args = ToolArgs.of(Map.of("k", JSON.stringNode("v")));
        assertThat(args.stringOpt("k")).contains("v");
    }

    @Test
    void stringOptReturnsEmptyWhenMissing() {
        var args = ToolArgs.of(Map.of());
        assertThat(args.stringOpt("k")).isEmpty();
    }

    @Test
    void stringOrReturnsValueWhenPresent() {
        var args = ToolArgs.of(Map.of("k", JSON.stringNode("v")));
        assertThat(args.stringOr("k", "def")).isEqualTo("v");
    }

    @Test
    void stringOrReturnsFallbackWhenMissing() {
        var args = ToolArgs.of(Map.of());
        assertThat(args.stringOr("k", "def")).isEqualTo("def");
    }

    @Test
    void nodeReturnsValueWhenPresent() {
        var args = ToolArgs.of(Map.of("k", JSON.stringNode("v")));
        assertThat(args.node("k").asString()).isEqualTo("v");
    }

    @Test
    void nodeThrowsWhenMissing() {
        var args = ToolArgs.of(Map.of());
        assertThatThrownBy(() -> args.node("k"))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessage("required argument missing");
    }

    @Test
    void nodeThrowsWithArgName() {
        var args = ToolArgs.of(Map.of());
        assertThatThrownBy(() -> args.node("missingArg"))
                .isInstanceOf(InvalidArgumentException.class)
                .satisfies(e ->
                        assertThat(((InvalidArgumentException) e).argName()).isEqualTo("missingArg"));
    }

    @Test
    void rawReturnsNullForMissingKey() {
        var args = ToolArgs.of(Map.of());
        assertThat(args.raw("k")).isNull();
    }

    @Test
    void rawReturnsValueForPresentKey() {
        var args = ToolArgs.of(Map.of("k", JSON.stringNode("v")));
        assertThat(args.raw("k")).isNotNull();
    }

    @Test
    void rawReturnsNullForDifferentMissingKey() {
        var args = ToolArgs.of(Map.of("a", JSON.stringNode("x")));
        assertThat(args.raw("b")).isNull();
    }

    @Test
    void rawReturnsNodeWithText() {
        var args = ToolArgs.of(Map.of("k", JSON.stringNode("v")));
        assertThat(Objects.requireNonNull(args.raw("k")).asString()).isEqualTo("v");
    }

    @Test
    void asMapReturnsUnmodifiableView() {
        java.util.Map<String, JsonNode> raw = new java.util.HashMap<>(Map.of("k", JSON.stringNode("v")));
        var args = ToolArgs.of(raw);
        assertThat(args.asMap()).containsOnlyKeys("k");
        assertThat(args.asMap()).isUnmodifiable();
    }

    @Test
    void boolOrReturnsValueWhenPresent() {
        var args = ToolArgs.of(Map.of("k", JSON.booleanNode(true)));
        assertThat(args.boolOr("k", false)).isTrue();
    }

    @Test
    void boolOrReturnsFallbackWhenMissing() {
        var args = ToolArgs.of(Map.of());
        assertThat(args.boolOr("k", true)).isTrue();
    }

    @Test
    void intOrReturnsValueWhenPresent() {
        var args = ToolArgs.of(Map.of("k", JSON.numberNode(42)));
        assertThat(args.intOr("k", 0)).isEqualTo(42);
    }

    @Test
    void intOrReturnsFallbackWhenMissing() {
        var args = ToolArgs.of(Map.of());
        assertThat(args.intOr("k", 99)).isEqualTo(99);
    }

    @Test
    void doubleOrReturnsValueWhenPresent() {
        var args = ToolArgs.of(Map.of("k", JSON.numberNode(3.14)));
        assertThat(args.doubleOr("k", 0.0)).isEqualTo(3.14);
    }

    @Test
    void doubleOrReturnsFallbackWhenMissing() {
        var args = ToolArgs.of(Map.of());
        assertThat(args.doubleOr("k", 1.5)).isEqualTo(1.5);
    }
}
