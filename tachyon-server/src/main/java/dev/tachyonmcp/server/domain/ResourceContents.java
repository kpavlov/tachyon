/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * The actual content of a resource, either text or binary.
 *
 * <p>Both variants share a {@code uri}, {@code mimeType}, and {@code meta}. The sealed
 * hierarchy lets resource registries and mappers handle each variant explicitly.
 */
public sealed interface ResourceContents permits TextResourceContents, BlobResourceContents {

    String uri();

    @Nullable
    String mimeType();

    @Nullable
    Map<String, JsonNode> meta();
}
