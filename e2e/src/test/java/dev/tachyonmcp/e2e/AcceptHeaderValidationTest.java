/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Guards the MCP {@code Accept}-header rules enforced by {@code AcceptValidationHandler}: POST must
 * accept {@code application/json} and {@code text/event-stream}; GET must accept
 * {@code text/event-stream}. Regression test for the handler having been positioned before the
 * aggregator, where its {@code FullHttpRequest} match never fired and validation was silently
 * skipped.
 */
class AcceptHeaderValidationTest extends AbstractStatelessMcpE2eTest {

    // language=JSON
    private static final String INIT_BODY =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"t\",\"version\":\"1\"}}}";

    private HttpResponse<String> post(@Nullable String accept) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("MCP-Protocol-Version", "2025-11-25")
                .POST(HttpRequest.BodyPublishers.ofString(INIT_BODY));
        if (accept != null) builder.header("Accept", accept);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String accept) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("MCP-Protocol-Version", "2025-11-25")
                .GET();
        builder.header("Accept", accept);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void shouldRejectPostMissingEventStreamAccept() throws Exception {
        var response = post("application/json");
        assertThat(response.statusCode()).isEqualTo(406);
        assertThat(response.body()).contains("text/event-stream");
    }

    @Test
    void shouldRejectPostWithNoAcceptHeader() throws Exception {
        assertThat(post(null).statusCode()).isEqualTo(406);
    }

    @Test
    void shouldRejectGetMissingEventStreamAccept() throws Exception {
        var response = get("application/json");
        assertThat(response.statusCode()).isEqualTo(406);
        assertThat(response.body()).contains("text/event-stream");
    }

    @Test
    void shouldAcceptPostWithBothMediaTypes() throws Exception {
        var response = post("application/json, text/event-stream");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("result");
    }

    @Test
    void shouldAcceptPostWithWildcardSubtypes() throws Exception {
        var response = post("application/*, text/*");
        assertThat(response.statusCode()).isNotEqualTo(406);
    }

    @Test
    void shouldRejectPostWithQZeroOnRequiredType() throws Exception {
        var response = post("application/json;q=0, text/event-stream");
        assertThat(response.statusCode()).isEqualTo(406);
    }

    @Test
    void shouldRejectPostWithPartialMediaTypeMatch() throws Exception {
        var response = post("application/json-seq, text/event-stream");
        assertThat(response.statusCode()).isEqualTo(406);
    }
}
