/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.mcp.v2025_11_25.AbstractStatefulMcpE2eTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
class SseRetryTest extends AbstractStatefulMcpE2eTest {

    @Test
    void sseResponseIncludesRetryField() throws Exception {
        String sessionId;
        try (var client = createTestClient()) {
            sessionId = client.initialize();
        }

        try (var client = createTestClient();
                var subscriber = client.openGetStream(sessionId, null)) {
            var raw = subscriber.awaitRawResponse(body -> body.contains("retry: 3000"), ofSeconds(5));
            assertThat(raw).contains("retry: 3000");
            assertThat(raw).contains("X-Accel-Buffering: no");
        }
    }
}
