/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

final class Mcp20251125TestClient extends TestMcpClient {

    Mcp20251125TestClient(int port) {
        super(port);
    }

    @Override
    protected String protocolVersion() {
        return "2025-11-25";
    }
}
