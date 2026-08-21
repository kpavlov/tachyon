/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.handlers;

import static dev.tachyonmcp.core.test.TestUtils.newEngine;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.core.protocol.Protocols;
import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.server.features.subscriptions.SubscriptionRegistry;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import dev.tachyonmcp.core.server.session.DefaultDispatchContext;
import org.junit.jupiter.api.Test;

class SubscriptionsListenHandlerTest {

    private final ServerEngine server = newEngine(b -> {});
    private final SubscriptionsListenHandler handler = new SubscriptionsListenHandler(new SubscriptionRegistry(server));

    @Test
    void decodeRejectsProtocolWithoutSubscriptionsListenSupport() {
        // SEP-2575: subscriptions/listen is 2026-07-28-only; 2025-11-25 has no such method.
        var legacyProtocol = Protocols.list().stream()
                .filter(p -> p.versionString().equals("2025-11-25"))
                .findFirst()
                .orElseThrow();
        var context = DefaultDispatchContext.create(legacyProtocol, server);

        assertThatThrownBy(() -> handler.decode(context, null))
                .isInstanceOf(RequestMappingException.class)
                .extracting(e -> ((RequestMappingException) e).error().kind())
                .isEqualTo(ServerError.Kind.METHOD_NOT_FOUND);
    }

    @Test
    void handleThrowsBecauseTheHandlerIsStreamBased() {
        var context = DefaultDispatchContext.stateless(server);

        assertThatThrownBy(() -> handler.handle(context, null))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("subscriptions/listen is stream-based");
    }
}
