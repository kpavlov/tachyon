/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.transport.netty.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EndpointValidatorHandlerTest {

    private @Nullable EmbeddedChannel channel;

    @AfterEach
    void releaseChannel() {
        if (channel != null) {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/mcp", "/mcp/", "/mcp?client=test", "/mcp/?client=test"})
    void allowsNormalizedEndpoint(String uri) {
        channel = new EmbeddedChannel(new EndpointValidatorHandler("/mcp/"));

        assertThat(channel.writeInbound(request(uri))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/mcp-evil", "/mcp/x/y"})
    void rejectsEndpointPrefixes(String uri) {
        channel = new EmbeddedChannel(new EndpointValidatorHandler("/mcp"));
        var request = request(uri);

        assertThat(channel.writeInbound(request)).isFalse();
        assertThat(request.refCnt()).isZero();
        assertThat(rejectionStatus(channel)).isEqualTo(HttpResponseStatus.NOT_FOUND);
    }

    private static DefaultFullHttpRequest request(String uri) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri);
    }

    private static @Nullable HttpResponseStatus rejectionStatus(EmbeddedChannel channel) {
        Object out = channel.readOutbound();
        try {
            return out instanceof HttpResponse response ? response.status() : null;
        } finally {
            ReferenceCountUtil.release(out);
        }
    }
}
