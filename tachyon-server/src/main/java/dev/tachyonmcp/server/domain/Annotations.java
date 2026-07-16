/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import java.util.List;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * Optional metadata that clients can use to tailor how content is presented.
 *
 * <p>{@code audience} hints at the intended role (user/assistant), {@code priority} controls
 * ordering, and {@code lastModified} carries an RFC-3339 timestamp of the last modification.
 * All fields are {@code null} when absent — omit the annotation block entirely rather than
 * sending empty values.
 */
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE, typeImmutable = "Default*")
public interface Annotations {

    @Nullable
    List<Role> audience();

    @Nullable
    Double priority();

    @Nullable
    String lastModified();

    @Value.Check
    default void checkPriority() {
        Double priority = priority();
        if (priority != null && (Double.isNaN(priority) || priority < 0.0 || priority > 1.0)) {
            throw new IllegalArgumentException("priority must be in [0.0, 1.0], got: " + priority);
        }
    }

    static DefaultAnnotations.Builder builder() {
        return DefaultAnnotations.builder();
    }

    static Annotations of(@Nullable List<Role> audience, @Nullable Double priority, @Nullable String lastModified) {
        return DefaultAnnotations.builder()
                .audience(audience)
                .priority(priority)
                .lastModified(lastModified)
                .build();
    }
}
