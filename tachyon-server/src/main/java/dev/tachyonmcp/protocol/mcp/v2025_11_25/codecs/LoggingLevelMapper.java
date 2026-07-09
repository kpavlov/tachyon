/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

public final class LoggingLevelMapper {

    private LoggingLevelMapper() {}

    public static dev.tachyonmcp.server.domain.LoggingLevel toDomain(
            dev.tachyonmcp.protocol.mcp.v2025_11_25.models.LoggingLevel protocol) {
        return dev.tachyonmcp.server.domain.LoggingLevel.valueOf(protocol.name());
    }

    public static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.LoggingLevel toProtocol(
            dev.tachyonmcp.server.domain.LoggingLevel domain) {
        return dev.tachyonmcp.protocol.mcp.v2025_11_25.models.LoggingLevel.valueOf(domain.name());
    }
}
