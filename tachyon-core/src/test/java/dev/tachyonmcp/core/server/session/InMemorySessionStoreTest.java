/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.core.runtime.SessionState;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemorySessionStoreTest {

    @Test
    void replacementGenerationRejectsStaleWritesAndTermination() {
        try (var store = new InMemorySessionStore()) {
            var firstKey = new SessionKey("s1", "g1");
            var first = store.create(firstKey, Instant.parse("2026-09-09T12:00:00Z"));
            var active = new SessionSnapshot(
                    first.key(),
                    SessionState.ACTIVE,
                    "2025-11-25",
                    first.enabledExtensionIds(),
                    null,
                    first.expiresAt(),
                    first.revision() + 1);

            assertThat(store.compareAndSet(first, active)).isTrue();
            assertThat(store.find("s1")).contains(active);

            var replacement = store.create(new SessionKey("s1", "g2"), Instant.parse("2026-09-09T13:00:00Z"));

            assertThat(store.compareAndSet(active, active)).isFalse();
            assertThat(store.touch(firstKey, Instant.parse("2026-09-09T14:00:00Z")))
                    .isFalse();
            assertThat(store.terminate(firstKey)).isFalse();
            assertThat(store.find("s1")).contains(replacement);
            assertThat(store.touch(replacement.key(), Instant.parse("2026-09-09T14:00:00Z")))
                    .isTrue();
            assertThat(store.find("s1")).hasValueSatisfying(touched -> {
                assertThat(touched.expiresAt()).isEqualTo(Instant.parse("2026-09-09T14:00:00Z"));
                assertThat(touched.revision()).isEqualTo(replacement.revision() + 1);
            });
            assertThat(store.terminate(replacement.key())).isTrue();
            assertThat(store.find("s1")).isEmpty();
        }
    }
}
