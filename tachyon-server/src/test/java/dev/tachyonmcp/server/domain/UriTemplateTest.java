/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UriTemplateTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("rfc6570Expansions")
    void shouldParseRfc6570Expansion(
            String example, String uriTemplate, String rawUri, Map<String, UriTemplateValue> expectedParameters) {
        // RFC 6570 §§3.2.2-3.2.9.
        var parameters = UriTemplate.create(uriTemplate).parse(rawUri);

        assertThat(parameters).isEqualTo(expectedParameters);
    }

    @Test
    void shouldCopySequenceValues() {
        var values = new java.util.ArrayList<>(List.of("red"));

        var sequence = new UriTemplateValue.Sequence(values);
        values.add("green");

        assertThat(sequence.values()).containsExactly("red").isUnmodifiable();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRfc6570Templates")
    void shouldRejectInvalidOrUnsupportedRfc6570Template(String example, String uriTemplate, String expectedMessage) {
        // RFC 6570 §§2.2-2.4.
        assertThatThrownBy(() -> UriTemplate.create(uriTemplate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonMatchingUris")
    void shouldRejectUriOutsideTemplateExpansion(String example, String uriTemplate, String rawUri) {
        // RFC 6570 §1.4.
        var template = UriTemplate.create(uriTemplate);

        assertThatThrownBy(() -> template.parse(rawUri))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    private static Stream<Arguments> rfc6570Expansions() {
        return Stream.of(
                arguments(
                        "literal-only template", "https://example.com/static", "https://example.com/static", Map.of()),
                arguments(
                        "custom URI scheme",
                        "test://template/{id}/data",
                        "test://template/abc123/data",
                        Map.of("id", "abc123")),
                arguments("level 1 simple value", "{var}", "value", Map.of("var", "value")),
                arguments(
                        "level 1 percent-encoded value",
                        "{hello}",
                        "Hello%20World%21",
                        Map.of("hello", "Hello World!")),
                arguments("level 1 empty value", "{empty}", "", Map.of("empty", "")),
                arguments("level 1 empty value after literal", "/users/{id}", "/users/", Map.of("id", "")),
                arguments("level 2 reserved expansion", "{+path}/here", "/foo/bar/here", Map.of("path", "/foo/bar")),
                arguments(
                        "level 2 reserved expansion in query",
                        "here?ref={+path}",
                        "here?ref=/foo/bar",
                        Map.of("path", "/foo/bar")),
                arguments(
                        "level 2 fragment expansion", "X{#hello}", "X#Hello%20World!", Map.of("hello", "Hello World!")),
                arguments(
                        "level 3 multiple simple variables",
                        "map?{x,y}",
                        "map?1024,768",
                        Map.of("x", "1024", "y", "768")),
                arguments("level 3 label expansion", "X{.x,y}", "X.1024.768", Map.of("x", "1024", "y", "768")),
                arguments("level 3 empty label value", "X{.empty}", "X.", Map.of("empty", "")),
                arguments("level 3 path expansion", "{/x,y}", "/1024/768", Map.of("x", "1024", "y", "768")),
                arguments(
                        "level 3 encoded path value",
                        "{/who,dub}",
                        "/fred/me%2Ftoo",
                        Map.of("who", "fred", "dub", "me/too")),
                arguments("level 3 matrix expansion", "{;x,y}", ";x=1024;y=768", Map.of("x", "1024", "y", "768")),
                arguments(
                        "level 3 empty matrix value", "{;x,empty}", ";x=1024;empty", Map.of("x", "1024", "empty", "")),
                arguments("level 3 query expansion", "{?x,y}", "?x=1024&y=768", Map.of("x", "1024", "y", "768")),
                arguments(
                        "level 3 empty query value", "{?x,empty}", "?x=1024&empty=", Map.of("x", "1024", "empty", "")),
                arguments(
                        "level 3 query continuation",
                        "?fixed=yes{&x,y}",
                        "?fixed=yes&x=1024&y=768",
                        Map.of("x", "1024", "y", "768")),
                arguments("level 4 prefix modifier", "{var:3}", "val", Map.of("var", "val")),
                arguments("level 4 prefix longer than value", "{var:20}", "value", Map.of("var", "value")),
                arguments(
                        "level 4 repeated prefix and full variable",
                        "http://example.com/dictionary/{term:1}/{term}",
                        "http://example.com/dictionary/c/cat",
                        Map.of("term", "cat")),
                arguments(
                        "percent-encoded variable name", "{hello%20world}", "value", Map.of("hello%20world", "value")),
                arguments(
                        "unicode template literal",
                        "https://example.com/café/{item}",
                        "https://example.com/caf%C3%A9/tea",
                        Map.of("item", "tea")),
                arguments("reserved apostrophe literal", "/O'Brien/{id}", "/O'Brien/42", Map.of("id", "42")),
                arguments(
                        "single percent-decode pass",
                        "files/{path}",
                        "files/%252Fetc%252Fpasswd",
                        Map.of("path", "%2Fetc%2Fpasswd")),
                arguments(
                        "encoded separators remain attacker-controlled values",
                        "files/{path}",
                        "files/..%2Fetc%2Fpasswd",
                        Map.of("path", "../etc/passwd")),
                arguments(
                        "encoded null byte remains attacker-controlled value",
                        "items/{id}",
                        "items/foo%00bar",
                        Map.of("id", "foo\u0000bar")),
                arguments("encoded identity character", "svc/{role}", "svc/adm%69n", Map.of("role", "admin")),
                sequenceArguments(
                        "level 4 exploded label list",
                        "X{.colors*}",
                        "X.red.green.blue",
                        "colors",
                        "red",
                        "green",
                        "blue"),
                sequenceArguments(
                        "level 4 exploded path list",
                        "https://example.com/files{/segments*}",
                        "https://example.com/files/a/b%20c",
                        "segments",
                        "a",
                        "b c"),
                sequenceArguments(
                        "encoded path separator stays in list item",
                        "files{/segments*}",
                        "files/a%2Fb/c",
                        "segments",
                        "a/b",
                        "c"),
                sequenceArguments(
                        "level 4 exploded matrix list",
                        "X{;colors*}",
                        "X;colors=red;colors=green",
                        "colors",
                        "red",
                        "green"),
                sequenceArguments(
                        "level 4 exploded query list",
                        "X{?colors*}",
                        "X?colors=red&colors=green",
                        "colors",
                        "red",
                        "green"),
                sequenceArguments(
                        "level 4 exploded query continuation list",
                        "X?fixed=yes{&colors*}",
                        "X?fixed=yes&colors=red&colors=green",
                        "colors",
                        "red",
                        "green"),
                sequenceArguments("empty exploded label list", "X{.colors*}", "X", "colors"),
                sequenceArguments("empty exploded path list", "X{/colors*}", "X", "colors"),
                sequenceArguments("empty exploded matrix list", "X{;colors*}", "X", "colors"),
                sequenceArguments("empty exploded query list", "X{?colors*}", "X", "colors"),
                sequenceArguments("empty exploded continuation list", "X{&colors*}", "X", "colors"));
    }

    private static Stream<Arguments> invalidRfc6570Templates() {
        return Stream.of(
                arguments("empty expression", "{}", "Empty URI template expression"),
                arguments("missing expression close", "{var", "Malformed URI template"),
                arguments("nested expression", "{{var}}", "Malformed URI template"),
                arguments("empty variable", "{var,}", "Empty URI template variable"),
                arguments("empty operator variable", "{?}", "has no variables"),
                arguments("invalid variable name", "{bad-name}", "Invalid URI template variable name"),
                arguments("empty variable name segment", "{bad..name}", "Invalid URI template variable name"),
                arguments("invalid percent-encoded variable name", "{bad%2}", "Invalid URI template variable name"),
                arguments("zero prefix", "{var:0}", "Invalid URI template prefix length"),
                arguments("leading-zero prefix", "{var:01}", "Invalid URI template prefix length"),
                arguments("oversized prefix", "{var:10000}", "Invalid URI template prefix length"),
                arguments("duplicate prefix separator", "{var:2:3}", "Invalid URI template prefix modifier"),
                arguments(
                        "ambiguous simple explode modifier", "{var*}", "cannot be reversed without a variable schema"),
                arguments(
                        "ambiguous reserved explode modifier",
                        "{+var*}",
                        "cannot be reversed without a variable schema"),
                arguments(
                        "ambiguous fragment explode modifier",
                        "{#var*}",
                        "cannot be reversed without a variable schema"),
                arguments("explode with prefix modifier", "{/var:3*}", "cannot combine prefix and explode"),
                arguments("explode with another variable", "{/var*,tail}", "must be the sole variable"),
                arguments("ambiguous reserved variables", "{+x,y}", "cannot be reversed unambiguously"),
                arguments("reserved future operator", "{=var}", "Reserved URI template operator"),
                arguments("invalid percent-encoded literal", "/bad%2", "Invalid percent-encoded"),
                arguments("space in literal", "/bad path/{var}", "Invalid URI template literal"),
                arguments("forbidden literal", "/bad|path/{var}", "Invalid URI template literal"),
                arguments("template exceeds length guard", "x".repeat(1_025), "too long"));
    }

    private static Stream<Arguments> nonMatchingUris() {
        return Stream.of(
                Arguments.of("different literal", "/users/{id}", "/orders/123"),
                Arguments.of("extra path segment", "/users/{id}", "/users/42/extra"),
                Arguments.of("unexpected query string", "/users/{id}", "/users/42?expand=true"),
                Arguments.of("simple value contains reserved slash", "{var}", "one/two"),
                Arguments.of("invalid percent encoding", "/{var}", "/bad%2"),
                Arguments.of("invalid UTF-8", "/{var}", "/%C3%28"),
                Arguments.of("prefix exceeds limit", "{var:3}", "value"),
                Arguments.of(
                        "dot-segment traversal", "app://host/private/{name}", "app://host/public/../private/secret"),
                Arguments.of("repeated variable conflicts", "/{term:1}/{term}", "/c/dog"),
                Arguments.of("query variables use wrong order", "{?x,y}", "?y=768&x=1024"),
                Arguments.of("associative path explode", "{/keys*}", "/semi=%3B/dot=."),
                Arguments.of("associative matrix explode", "{;keys*}", ";semi=%3B;dot=."),
                Arguments.of("associative query explode", "{?keys*}", "?semi=%3B&dot=."),
                Arguments.of("raw URI exceeds length guard", "{var}", "x".repeat(8_193)));
    }

    private static Arguments arguments(
            String example, String uriTemplate, String rawUri, Map<String, String> expectedParameters) {
        var values = new LinkedHashMap<String, UriTemplateValue>();
        expectedParameters.forEach((name, value) -> values.put(name, new UriTemplateValue.Scalar(value)));
        return Arguments.of(example, uriTemplate, rawUri, Map.copyOf(values));
    }

    private static Arguments sequenceArguments(
            String example, String uriTemplate, String rawUri, String name, String... values) {
        return Arguments.of(example, uriTemplate, rawUri, Map.of(name, new UriTemplateValue.Sequence(List.of(values))));
    }

    private static Arguments arguments(String example, String uriTemplate, String expectedMessage) {
        return Arguments.of(example, uriTemplate, expectedMessage);
    }
}
