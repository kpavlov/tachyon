/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class ToolRequestTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void builderSetsName() {
        var req = ToolRequest.builder().name("my-tool").build();
        assertThat(req.name()).isEqualTo("my-tool");
    }

    @Test
    void argumentsDefaultsToEmptyMap() {
        var req = ToolRequest.builder().name("t").build();
        assertThat(req.arguments()).isEmpty();
    }

    @Test
    void builderSetsArguments() {
        var args = Map.of("k", JSON.stringNode("v"));
        var req = ToolRequest.builder().name("t").arguments(args).build();
        assertThat(req.arguments()).isEqualTo(args);
    }

    @Test
    void metaIsNullable() {
        var req = ToolRequest.builder().name("t").build();
        assertThat(req.meta()).isNull();
    }

    @Test
    void builderSetsMeta() {
        var meta = Map.of("mk", JSON.stringNode("mv"));
        var req = ToolRequest.builder().name("t").meta(meta).build();
        assertThat(req.meta()).isEqualTo(meta);
    }

    @Test
    void progressTokenIsNullable() {
        var req = ToolRequest.builder().name("t").build();
        assertThat(req.progressToken()).isNull();
    }

    @Test
    void builderSetsProgressToken() {
        var req = ToolRequest.builder().name("t").progressToken(42L).build();
        assertThat(req.progressToken()).isEqualTo(42L);
    }

    @Test
    void cancellationIsNullable() {
        var req = ToolRequest.builder().name("t").build();
        assertThat(req.cancellation()).isNull();
    }

    @Test
    void inputResponsesIsNullable() {
        var req = ToolRequest.builder().name("t").build();
        assertThat(req.inputResponses()).isNull();
    }

    @Test
    void requestStateIsNullable() {
        var req = ToolRequest.builder().name("t").build();
        assertThat(req.requestState()).isNull();
    }

    @Test
    void metaIsAccessibleWhenPresent() {
        Map<String, JsonNode> meta = Map.of("k", JSON.stringNode("v"));
        var req = ToolRequest.builder().name("t").meta(meta).build();
        assertThat(req.meta()).isNotNull();
        assertThat(req.meta().get("k").asString()).isEqualTo("v");
    }
}
