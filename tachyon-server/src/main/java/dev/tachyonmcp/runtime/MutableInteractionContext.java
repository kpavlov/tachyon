/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

import org.jspecify.annotations.Nullable;

/**
 * Extension- and dispatch-facing view of the interaction that adds the lifecycle/session/extension
 * mutators withheld from the handler-facing {@link InteractionContext}. Dispatch code and
 * {@link Extension} implementations receive this type; tool/resource/prompt handlers do not.
 */
public interface MutableInteractionContext extends InteractionContext {

    void setLifecycle(Lifecycle lifecycle);

    void setSession(@Nullable Session session);

    void enableExtension(String extensionId);
}
