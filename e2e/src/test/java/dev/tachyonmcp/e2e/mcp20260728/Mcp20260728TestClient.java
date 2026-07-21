/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e.mcp20260728;

import dev.tachyonmcp.e2e.TestMcpClient;
import dev.tachyonmcp.protocol.mcp.McpHeaderNames;
import java.net.http.HttpRequest;
import tools.jackson.databind.node.ObjectNode;

public final class Mcp20260728TestClient extends TestMcpClient {

    public Mcp20260728TestClient(int port) {
        super(port);
    }

    @Override
    protected String protocolVersion() {
        return "2026-07-28";
    }

    @Override
    protected String requestBody(String body) throws Exception {
        var request = MAPPER.readTree(body);
        if (!(request instanceof ObjectNode requestObject)) {
            throw new IllegalArgumentException("MCP request must be a JSON object");
        }
        var meta = objectField(objectField(requestObject, "params"), "_meta");
        meta.put("io.modelcontextprotocol/protocolVersion", protocolVersion());
        var clientInfo = objectField(meta, "io.modelcontextprotocol/clientInfo");
        if (!clientInfo.hasNonNull("name")) clientInfo.put("name", "test");
        if (!clientInfo.hasNonNull("version")) clientInfo.put("version", "1.0");
        objectField(meta, "io.modelcontextprotocol/clientCapabilities");
        return MAPPER.writeValueAsString(requestObject);
    }

    @Override
    protected void configureRequest(HttpRequest.Builder builder, String body) throws Exception {
        var request = MAPPER.readTree(body);
        var method = request.path("method").asString(null);
        if (method != null) builder.header(McpHeaderNames.MCP_METHOD, method);

        var params = request.path("params");
        var name = params.path("name").asString(null);
        if (name == null) name = params.path("uri").asString(null);
        if (name != null) builder.header(McpHeaderNames.MCP_NAME, name);
    }

    private static ObjectNode objectField(ObjectNode parent, String name) {
        var node = parent.get(name);
        if (node instanceof ObjectNode object) return object;
        var object = MAPPER.createObjectNode();
        parent.set(name, object);
        return object;
    }
}
