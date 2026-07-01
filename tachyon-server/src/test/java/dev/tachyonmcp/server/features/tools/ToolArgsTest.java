/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
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
        var args = ToolArgs.of(Map.of("key", JSON.textNode("val")));
        assertThat(args.isEmpty()).isFalse();
    }

    @Test
    void hasReturnsTrueForExistingKey() {
        var args = ToolArgs.of(Map.of("k", JSON.textNode("v")));
        assertThat(args.has("k")).isTrue();
    }

    @Test
    void hasReturnsFalseForMissingKey() {
        var args = ToolArgs.of(Map.of());
        assertThat(args.has("k")).isFalse();
    }

    @Test
    void stringReturnsValue() {
        var args = ToolArgs.of(Map.of("k", JSON.textNode("hello")));
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
        var args = ToolArgs.of(Map.of("k", JSON.textNode("v")));
        assertThat(args.stringOpt("k")).contains("v");
    }

    @Test
    void stringOptReturnsEmptyWhenMissing() {
        var args = ToolArgs.of(Map.of());
        assertThat(args.stringOpt("k")).isEmpty();
    }

    @Test
    void stringOrReturnsValueWhenPresent() {
        var args = ToolArgs.of(Map.of("k", JSON.textNode("v")));
        assertThat(args.stringOr("k", "def")).isEqualTo("v");
    }

    @Test
    void stringOrReturnsFallbackWhenMissing() {
        var args = ToolArgs.of(Map.of());
        assertThat(args.stringOr("k", "def")).isEqualTo("def");
    }

    @Test
    void nodeReturnsValueWhenPresent() {
        var args = ToolArgs.of(Map.of("k", JSON.textNode("v")));
        assertThat(args.node("k").asText()).isEqualTo("v");
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
        var args = ToolArgs.of(Map.of("k", JSON.textNode("v")));
        assertThat(args.raw("k")).isNotNull();
    }

    @Test
    void rawReturnsNullForDifferentMissingKey() {
        var args = ToolArgs.of(Map.of("a", JSON.textNode("x")));
        assertThat(args.raw("b")).isNull();
    }

    @Test
    void rawReturnsNodeWithText() {
        var args = ToolArgs.of(Map.of("k", JSON.textNode("v")));
        assertThat(args.raw("k").asText()).isEqualTo("v");
    }
}
