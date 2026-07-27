/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.runtime;

/**
 * A typed, identity-based key for {@link InteractionContext#get(AttributeKey)} /
 * {@link InteractionContext#set(AttributeKey, Object)}.
 *
 * <p>Two keys are equal only if they are the same instance — {@link #of(String)} never interns by
 * name, so unrelated features can never collide on a shared string, and a value can only be
 * retrieved by whoever holds the actual key instance. The {@code name} exists solely for {@link
 * #toString()}/debugging.
 *
 * @param <T> the type of value stored under this key
 * @author Konstantin Pavlov
 */
public final class AttributeKey<T> {

    private final String name;

    private AttributeKey(String name) {
        this.name = name;
    }

    /**
     * Creates a new, distinct key.
     *
     * @param name debug label for the key (does not affect identity)
     * @param <T>  the type of value stored under this key
     * @return a new distinct key instance
     */
    public static <T> AttributeKey<T> of(String name) {
        return new AttributeKey<>(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
