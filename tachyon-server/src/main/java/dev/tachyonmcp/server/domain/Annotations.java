/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Optional metadata that clients can use to tailor how content is presented.
 *
 * <p>{@code audience} hints at the intended role (user/assistant), {@code priority} controls
 * ordering, and {@code lastModified} carries an RFC-3339 timestamp of the last modification.
 * All fields are {@code null} when absent — omit the annotation block entirely rather than
 * sending empty values.
 */
public interface Annotations {

    @Nullable
    List<Role> audience();

    @Nullable
    Double priority();

    @Nullable
    String lastModified();

    static Annotations of(@Nullable List<Role> audience, @Nullable Double priority, @Nullable String lastModified) {
        return new DefaultAnnotations(audience, priority, lastModified);
    }
}
