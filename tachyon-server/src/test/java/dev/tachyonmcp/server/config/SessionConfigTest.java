/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.tachyonmcp.server.session.InMemorySessionLogRouter;
import dev.tachyonmcp.server.session.InMemorySessionStore;
import dev.tachyonmcp.server.session.SessionIdGenerator;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link SessionConfig} defaults (stateless, default id generator) and the fail-fast
 * rejection of session options configured while sessions are disabled — compact constructor
 * resolves value defaults ({@code sessionTtl}, {@code janitorInterval},
 * {@code sessionIdGenerator}) when {@code enabled=true}.
 *
 * @author Konstantin Pavlov
 */
class SessionConfigTest {

    @Test
    void defaultsAreStateless() {
        var config = SessionConfig.builder().build();

        assertThat(config.enabled()).isFalse();
        assertThat(config.sessionIdGenerator()).isNull();
        assertThat(config.sessionStore()).isNull();
        assertThat(config.sessionLogRouter()).isNull();
    }

    @Test
    void resolvesDefaultTtlWhenEnabled() {
        var config = SessionConfig.builder().enabled(true).build();

        assertThat(config.enabled()).isTrue();
        assertThat(config.sessionTtl()).isEqualTo(SessionConfig.DEFAULT_SESSION_TTL);
    }

    @Test
    void resolvesDefaultJanitorIntervalWhenEnabled() {
        var config = SessionConfig.builder().enabled(true).build();

        assertThat(config.janitorInterval()).isEqualTo(SessionConfig.DEFAULT_JANITOR_INTERVAL);
    }

    @Test
    void resolvesDefaultSessionIdGeneratorWhenEnabled() {
        var config = SessionConfig.builder().enabled(true).build();

        assertThat(config.sessionIdGenerator()).isSameAs(SessionIdGenerator.DEFAULT);
    }

    @Test
    void sessionOptionsWhileDisabledFailFast() {
        SessionIdGenerator custom = request -> "custom";

        final var expectedMessage = "Session options require sessions to be enabled — call enabled(true)";

        assertThatIllegalStateException()
                .isThrownBy(() -> SessionConfig.builder()
                        .enabled(false)
                        .sessionIdGenerator(custom)
                        .build())
                .withMessage(expectedMessage);

        assertThatIllegalStateException()
                .isThrownBy(() -> SessionConfig.builder()
                        .enabled(false)
                        .sessionStore(new InMemorySessionStore())
                        .build())
                .withMessage(expectedMessage);

        assertThatIllegalStateException()
                .isThrownBy(() -> SessionConfig.builder()
                        .enabled(false)
                        .sessionLogRouter(new InMemorySessionLogRouter())
                        .build())
                .withMessage(expectedMessage);

        assertThatIllegalStateException()
                .isThrownBy(() -> SessionConfig.builder()
                        .enabled(false)
                        .sessionTtl(Duration.ofSeconds(42))
                        .build())
                .withMessage(expectedMessage);
    }

    @Test
    void sessionOptionsWithEnabledAreAccepted() {
        SessionIdGenerator custom = request -> "custom";

        var config = SessionConfig.builder()
                .enabled(true)
                .sessionIdGenerator(custom)
                .sessionStore(new InMemorySessionStore())
                .build();

        assertThat(config.enabled()).isTrue();
        assertThat(config.sessionIdGenerator()).isSameAs(custom);
        assertThat(config.sessionStore()).isNotNull();
    }
}
