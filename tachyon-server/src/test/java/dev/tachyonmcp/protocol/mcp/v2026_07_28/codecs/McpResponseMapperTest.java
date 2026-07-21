/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2026_07_28.codecs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.domain.ServerError;
import org.junit.jupiter.api.Test;

class McpResponseMapperTest {

    private final McpResponseMapper mapper = new McpResponseMapper();

    @Test
    void resourceNotFoundUsesInvalidParams() {
        var error = mapper.error(new ServerError(ServerError.Kind.RESOURCE_NOT_FOUND, "Resource not found"));

        assertThat(error.code()).isEqualTo(-32602);
    }

    @Test
    void unsupportedProtocolVersionUsesTheModernCode() {
        var error = mapper.error(
                new ServerError(ServerError.Kind.UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version"));

        assertThat(error.code()).isEqualTo(-32022);
    }
}
