/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonRpcCodecTest {

    @Test
    void serializeNotificationAsStringContainsRequiredFields() {
        var json = JsonRpcCodec.serializeNotificationAsString("notifications/tools/list_changed", "{}");

        assertThat(json).contains("\"jsonrpc\":\"2.0\"");
        assertThat(json).contains("\"method\":\"notifications/tools/list_changed\"");
        assertThat(json).contains("\"params\":{}");
        assertThat(json).doesNotContain("\"id\"");
        assertThat(json).doesNotContain("\"result\"");
        assertThat(json).doesNotContain("\"error\"");
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

        assertThat(json).contains("\"jsonrpc\":\"2.0\"");
        assertThat(json).contains("\"method\":\"sampling/createMessage\"");
        assertThat(json).contains("\"id\":\"req-1\"");
        assertThat(json).contains("\"params\":{\"prompt\":\"hi\"}");
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

        assertThat(json).contains("\"id\":99");
    }
}
