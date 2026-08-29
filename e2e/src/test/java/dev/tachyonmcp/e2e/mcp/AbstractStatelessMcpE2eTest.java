/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
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
        var multiValued = new LinkedHashMap<String, List<String>>();
        headers.forEach((name, value) -> multiValued.put(name, List.of(value)));
        return postMcpRequest(body, multiValued, true);
    }

    /**
     * Multi-valued variant of {@link #postMcpRequest(String, Map)}: each value becomes its own field
     * line, so a test can send a header twice. Pass {@code includeDefaultProtocolVersion = false} to
     * take over {@code MCP-Protocol-Version} — the only way to duplicate it.
     */
    protected HttpResponse<String> postMcpRequest(
            String body, Map<String, ? extends Iterable<String>> headers, boolean includeDefaultProtocolVersion)
            throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (includeDefaultProtocolVersion) {
            builder.header("MCP-Protocol-Version", "2026-07-28");
        }
        headers.forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
