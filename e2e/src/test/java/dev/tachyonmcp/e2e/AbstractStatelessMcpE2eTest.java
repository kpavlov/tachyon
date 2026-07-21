/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public abstract class AbstractStatelessMcpE2eTest extends AbstractMcpE2eTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Override
    protected final SessionMode sessionMode() {
        return SessionMode.STATELESS;
    }

    @Override
    protected void startDefaultServer() {
        var h = SharedStatelessE2eServer.ensureStarted();
        this.server = h;
        this.port = h.port();
        this.usingCustomServer = false;
    }

    /**
     * POSTs a raw MCP 2026-07-28 request: {@code Content-Type}, {@code Accept}, and
     * {@code MCP-Protocol-Version} are applied automatically, {@code headers} layers on top
     * (e.g. {@code Mcp-Method}/{@code Mcp-Name}/{@code Mcp-Param-*}).
     */
    protected HttpResponse<String> postMcpRequest(String body, Map<String, String> headers) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2026-07-28")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
