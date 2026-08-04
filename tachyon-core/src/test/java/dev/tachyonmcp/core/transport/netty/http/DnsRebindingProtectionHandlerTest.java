/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.transport.netty.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DnsRebindingProtectionHandlerTest {

    private static HttpRequest requestWithHost(@Nullable String host) {
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/mcp");
        if (host != null) {
            req.headers().set(HttpHeaderNames.HOST, host);
        }
        return req;
    }

    /** Returns the status of a rejection response written outbound, or {@code null} if none. */
    private static @Nullable HttpResponseStatus rejectionStatus(EmbeddedChannel channel) {
        Object out = channel.readOutbound();
        return out instanceof HttpResponse resp ? resp.status() : null;
    }

    @Test
    void allowsLocalhostHostByDefault() {
        var channel = new EmbeddedChannel(new DnsRebindingProtectionHandler());
        assertThat(channel.writeInbound(requestWithHost("localhost:8096")))
                .as("localhost Host must pass through")
                .isTrue();
        assertThat(rejectionStatus(channel)).isNull();
    }

    @Test
    void rejectsNonLocalhostHostByDefault() {
        var channel = new EmbeddedChannel(new DnsRebindingProtectionHandler());
        channel.writeInbound(requestWithHost("host.docker.internal:8096"));
        assertThat(rejectionStatus(channel)).isEqualTo(HttpResponseStatus.FORBIDDEN);
    }

    @Test
    void allowsHostOnTheAllowlist() {
        var channel = new EmbeddedChannel(new DnsRebindingProtectionHandler(List.of("host.docker.internal:8096")));
        assertThat(channel.writeInbound(requestWithHost("host.docker.internal:8096")))
                .as("an allowlisted Host must pass through")
                .isTrue();
        assertThat(rejectionStatus(channel)).isNull();
    }

    @Test
    void allowlistEntryWithoutPortMatchesAnyPort() {
        var channel = new EmbeddedChannel(new DnsRebindingProtectionHandler(List.of("host.docker.internal")));
        assertThat(channel.writeInbound(requestWithHost("host.docker.internal:8096")))
                .isTrue();
    }

    @Test
    void allowlistIsCaseInsensitive() {
        var channel = new EmbeddedChannel(new DnsRebindingProtectionHandler(List.of("Host.Docker.Internal:8096")));
        assertThat(channel.writeInbound(requestWithHost("host.docker.internal:8096")))
                .isTrue();
    }

    @Test
    void stillRejectsHostsNotOnTheAllowlist() {
        var channel = new EmbeddedChannel(new DnsRebindingProtectionHandler(List.of("host.docker.internal:8096")));
        channel.writeInbound(requestWithHost("evil.example:80"));
        assertThat(rejectionStatus(channel)).isEqualTo(HttpResponseStatus.FORBIDDEN);
    }
}
