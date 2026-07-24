/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import dev.tachyonmcp.annotations.InternalApi;
import org.jspecify.annotations.Nullable;

@InternalApi
public interface ChannelContext extends InteractionContext {

    void setLifecycle(Lifecycle lifecycle);

    void setSession(@Nullable Session session);

    void enableExtension(String extensionId);
}
