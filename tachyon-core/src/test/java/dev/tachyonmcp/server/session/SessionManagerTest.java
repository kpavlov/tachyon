/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SessionState;
import dev.tachyonmcp.runtime.SseConnection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

    @Test
    void sweepEvictsExpiredSessions() {
        var store = new InMemorySessionStore();
        var manager = new SessionManager(store);
        var session = manager.createSession("s1");

        manager.sweep(-1); // any idle time exceeds a negative TTL

        assertThat(session.state()).isEqualTo(SessionState.CLOSED);
        assertThat(store.get("s1")).isEmpty();
    }

    @Test
    void sweepKeepsFreshSessions() {
        var store = new InMemorySessionStore();
        var manager = new SessionManager(store);
        var session = manager.createSession("s1");
        session.activate();

        manager.sweep(Long.MAX_VALUE);

        assertThat(session.state()).isEqualTo(SessionState.ACTIVE);
        assertThat(store.get("s1")).contains(session);
    }

    /**
     * The race the conditional remove exists for: the sweep iterates a snapshot containing a
     * stale session while the store already holds a replacement created under the same id
     * (custom SessionIdGenerator scenario). The stale session must be closed, but the
     * replacement must survive the eviction.
     */
    @Test
    void sweepNeverEvictsReplacementCreatedUnderSameId() {
        var stale = new Session("s1", SseConnection.NOOP);
        stale.close(); // CLOSED → sweep will try to evict it

        var backing = new InMemorySessionStore();
        var replacement = new Session("s1", SseConnection.NOOP);
        backing.put("s1", replacement);

        // Store whose iteration still yields the stale session — the janitor mid-race.
        var manager = new SessionManager(new SessionStore() {
            @Override
            public @Nullable Session put(String sessionId, Session session) {
                return backing.put(sessionId, session);
            }

            @Override
            public Optional<Session> get(String sessionId) {
                return backing.get(sessionId);
            }

            @Override
            public Session computeIfAbsent(String sessionId, Function<String, Session> factory) {
                return backing.computeIfAbsent(sessionId, factory);
            }

            @Override
            public Collection<Session> values() {
                return List.of(stale);
            }

            @Override
            public @Nullable Session remove(String sessionId) {
                return backing.remove(sessionId);
            }

            @Override
            public boolean remove(String sessionId, Session expected) {
                return backing.remove(sessionId, expected);
            }

            @Override
            public void close() {
                backing.close();
            }
        });

        manager.sweep(Long.MAX_VALUE);

        assertThat(backing.get("s1")).contains(replacement);
        assertThat(replacement.state()).isNotEqualTo(SessionState.CLOSED);
    }
}
