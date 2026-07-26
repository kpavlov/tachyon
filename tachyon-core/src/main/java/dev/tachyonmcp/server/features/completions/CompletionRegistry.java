/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.completions;

import dev.tachyonmcp.annotations.InternalApi;

@InternalApi
public interface CompletionRegistry extends Completions {

    boolean isEmpty();
}
