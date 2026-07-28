/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.completions;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.features.completions.Completions;

@InternalApi
public interface CompletionRegistry extends Completions {

    boolean isEmpty();
}
