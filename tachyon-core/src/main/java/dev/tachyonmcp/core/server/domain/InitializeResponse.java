/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.domain;

import dev.tachyonmcp.api.server.config.ServerIdentity;
import dev.tachyonmcp.core.server.ServerCapabilities;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Server response to the {@code initialize} request.
 *
 * @param protocolVersion      the negotiated protocol version string
 * @param capabilities         the server's capabilities
 * @param serverIdentity       server identity metadata
 * @param instructions         optional server-level instructions for the client
 * @param negotiatedExtensions extension-specific negotiated data
 */
public record InitializeResponse(
        String protocolVersion,
        ServerCapabilities capabilities,
        ServerIdentity serverIdentity,
        @Nullable String instructions,
        @Nullable Map<String, JsonNode> negotiatedExtensions) {}
