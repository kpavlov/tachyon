/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SseConnection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySessionStoreTest {

    private static Session session(String id) {
        return new Session(id, SseConnection.NOOP);
    }

    @Test
    void conditionalRemoveEvictsOnlyTheExpectedInstance() {
        try (var store = new InMemorySessionStore()) {
            var expired = session("s1");
            store.put("s1", expired);

            // A replacement session appears under the same id (custom SessionIdGenerator scenario)
            // between the janitor's expiry check and its removal.
            var replacement = session("s1");
            store.put("s1", replacement);

            assertThat(store.remove("s1", expired)).isFalse();
            assertThat(store.get("s1")).contains(replacement);

            assertThat(store.remove("s1", replacement)).isTrue();
            assertThat(store.get("s1")).isEmpty();
        }
    }
}
