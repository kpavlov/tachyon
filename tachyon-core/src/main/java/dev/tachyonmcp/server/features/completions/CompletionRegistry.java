/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.completions;

import dev.tachyonmcp.protocol.api.annotations.InternalApi;
import dev.tachyonmcp.protocol.api.server.features.completions.Completions;

@InternalApi
public interface CompletionRegistry extends Completions {

    boolean isEmpty();
}
