/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.jsonrpc;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CallToolResult;
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
    void serializeNotificationAsStringMatchesByteBufVersion() {
        var method = "notifications/message";
        var params = "{\"level\":\"info\",\"data\":\"hello\"}";

        var asString = JsonRpcCodec.serializeNotificationAsString(method, params);
        var asByteBuf = JsonRpcCodec.serializeNotification(method, params);
        var fromByteBuf = asByteBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
        asByteBuf.release();

        assertThat(asString).isEqualTo(fromByteBuf);
    }

    @Test
    void serializeRequestAsStringContainsRequiredFields() {
        var json = JsonRpcCodec.serializeRequestAsString("req-1", "sampling/createMessage", "{\"prompt\":\"hi\"}");

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
    void serializeRequestAsStringMatchesByteBufVersion() {
        var id = "req-42";
        var method = "elicitation/create";
        var params = "{\"requestedSchema\":{}}";

        var asString = JsonRpcCodec.serializeRequestAsString(id, method, params);
        var asByteBuf = JsonRpcCodec.serializeRequest(id, method, params);
        var fromByteBuf = asByteBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
        asByteBuf.release();

        assertThat(asString).isEqualTo(fromByteBuf);
    }

    @Test
    void serializeNotificationAsStringWithNumericId() {
        var json = JsonRpcCodec.serializeRequestAsString(99L, "ping", "{}");

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
}
