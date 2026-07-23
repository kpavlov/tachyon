/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.jsonrpc;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Annotations;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CallToolResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListResourcesResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Resource;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role;
import dev.tachyonmcp.server.domain.RequestId;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonRpcCodecTest {

    @Test
    void serializeNotificationAsStringContainsRequiredFields() {
        var json = JsonRpcCodec.serializeNotificationAsString("notifications/tools/list_changed", "{}");

        // language=JSON
        assertThatJson(json).isEqualTo("""
            {
              "jsonrpc":"2.0",
              "method":"notifications/tools/list_changed",
              "params": {}
            }
            """);
    }

    @Test
    void serializeRequestAsStringContainsRequiredFields() {
        var json = JsonRpcCodec.serializeRequestAsString(
                RequestId.of("req-1"), "sampling/createMessage", "{\"prompt\":\"hi\"}");

        // language=JSON
        assertThatJson(json).isEqualTo("""
            {
              "jsonrpc":"2.0",
              "id":"req-1",
              "method":"sampling/createMessage",
              "params": {"prompt":"hi"}
            }
            """);
    }

    @Test
    void serializeNotificationAsStringWithNumericId() {
        var json = JsonRpcCodec.serializeRequestAsString(RequestId.of(99L), "ping", "{}");

        // language=JSON
        assertThatJson(json).isEqualTo("""
            {
              "jsonrpc":"2.0",
              "id":99,
              "method":"ping",
              "params": {}
            }
            """);
    }

    @Test
    void toJsonParamsReturnsEmptyObjectForNull() {
        assertThat(JsonRpcCodec.toJsonParams(null)).isEqualTo("{}");
    }

    @Test
    void toJsonParamsReturnsStringUnchanged() {
        assertThat(JsonRpcCodec.toJsonParams("already-serialized")).isEqualTo("already-serialized");
    }

    @Test
    void toJsonParamsSerializesObject() {
        var json = JsonRpcCodec.toJsonParams(java.util.Map.of("key", "value"));
        // language=json
        assertThatJson(json).isEqualTo("""
            {"key":"value"}
            """);
    }

    @Test
    void writeValueAsStringWithProtocolModelReturnsJsonNotToString() {
        var result = CallToolResult.ofText("ok");

        var json = JsonRpcCodec.writeValueAsString(result);

        // language=JSON
        assertThatJson(json).isEqualTo("""
            {"content":[{"type":"text","text":"ok"}]}
            """);
    }

    @Test
    void writeValueAsStringSerializesEnumLists() {
        var annotations = new Annotations(List.of(Role.USER, Role.ASSISTANT), 0.8, "2026-07-23T00:00:00Z");
        var resource = Resource.builder()
                .uri("weather://prediction/article")
                .name("prediction-article")
                .annotations(annotations)
                .build();

        var json = JsonRpcCodec.writeValueAsString(
                ListResourcesResult.builder().resources(List.of(resource)).build());

        // language=JSON
        assertThatJson(json).isEqualTo("""
            {
              "resources": [{
                "uri": "weather://prediction/article",
                "name": "prediction-article",
                "annotations": {
                  "audience": ["user", "assistant"],
                  "priority": 0.8,
                  "lastModified": "2026-07-23T00:00:00Z"
                }
              }]
            }
            """);
    }
}
