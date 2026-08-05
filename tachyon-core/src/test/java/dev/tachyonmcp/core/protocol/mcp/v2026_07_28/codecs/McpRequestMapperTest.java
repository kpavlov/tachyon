/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2026_07_28.codecs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.PayloadDeserializer;
import java.lang.reflect.Type;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpRequestMapperTest {

    private static final PayloadDeserializer NOOP_DESERIALIZER = new PayloadDeserializer() {
        @Override
        public <T> T deserialize(String json, Type targetType) {
            throw new UnsupportedOperationException("not used by these tests");
        }
    };

    @Test
    void ignoresLegacyTaskAugmentation() {
        var mapper = new McpRequestMapper();

        var result = mapper.callTool(Map.of("name", "greet", "task", "ignored"), NOOP_DESERIALIZER);

        assertThat(mapper.supportsLegacyTaskAugmentation()).isFalse();
        assertThat(result.request().name()).isEqualTo("greet");
        assertThat(result.taskAugmented()).isFalse();
        assertThat(result.taskTtl()).isNull();
    }
}
