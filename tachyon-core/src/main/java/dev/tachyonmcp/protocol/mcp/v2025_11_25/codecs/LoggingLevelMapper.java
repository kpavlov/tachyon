/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.server.domain.LoggingLevel;

/** Maps {@link LoggingLevel} to and from the MCP 2025-11-25 wire model. */
public final class LoggingLevelMapper {

    private LoggingLevelMapper() {}

    /**
     * Maps a protocol logging level to the domain type.
     *
     * @param protocol the protocol logging level
     * @return the domain logging level
     */
    public static LoggingLevel toDomain(dev.tachyonmcp.protocol.mcp.v2025_11_25.models.LoggingLevel protocol) {
        return LoggingLevel.valueOf(protocol.name());
    }

    /**
     * Maps a domain logging level to the protocol type.
     *
     * @param domain the domain logging level
     * @return the protocol logging level
     */
    public static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.LoggingLevel toProtocol(LoggingLevel domain) {
        return dev.tachyonmcp.protocol.mcp.v2025_11_25.models.LoggingLevel.valueOf(domain.name());
    }
}
