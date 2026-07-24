/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.protocol.Protocol;
import org.jspecify.annotations.Nullable;

@InternalApi
public interface ChannelContext extends InteractionContext {

    Protocol protocol();

    @Override
    default String protocolVersion() {
        return protocol().versionString();
    }

    @Nullable
    Session session();

    @Override
    default @Nullable String sessionId() {
        var session = session();
        return session == null ? null : session.id();
    }

    void setLifecycle(Lifecycle lifecycle);

    void setSession(@Nullable Session session);

    void enableExtension(String extensionId);
}
