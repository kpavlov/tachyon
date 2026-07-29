/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2025_11_25.codecs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.core.protocol.ProtocolRequestMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpRequestMapperTest {

    @Test
    void promptAndCompletionRequestsPreserveMetadataInBothProtocolVersions() {
        List<ProtocolRequestMapper> mappers = List.of(
                new McpRequestMapper(), new dev.tachyonmcp.core.protocol.mcp.v2026_07_28.codecs.McpRequestMapper());
        var meta = Map.<String, Object>of("trace", Map.of("id", 7));

        for (var mapper : mappers) {
            var prompt = mapper.getPrompt(Map.of("name", "greet", "_meta", meta));
            var completion = mapper.complete(Map.of(
                    "ref", Map.of("type", "ref/prompt", "name", "greet"),
                    "argument", Map.of("name", "name", "value", "A"),
                    "_meta", meta));

            assertThat(prompt.request().meta()).isEqualTo(meta);
            assertThat(completion.request().meta()).isEqualTo(meta);
        }
    }
}
