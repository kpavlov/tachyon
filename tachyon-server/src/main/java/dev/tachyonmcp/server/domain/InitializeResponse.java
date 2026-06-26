/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import dev.tachyonmcp.server.ServerCapabilities;
import dev.tachyonmcp.server.config.ServerIdentity;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public record InitializeResponse(
        String protocolVersion,
        ServerCapabilities capabilities,
        ServerIdentity serverIdentity,
        @Nullable String instructions,
        @Nullable Map<String, JsonNode> negotiatedExtensions) {}
