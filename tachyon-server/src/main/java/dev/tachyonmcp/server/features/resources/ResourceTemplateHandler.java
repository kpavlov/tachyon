/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ResourceContents;
import java.util.Map;

@FunctionalInterface
public interface ResourceTemplateHandler {

    ResourceContents read(InteractionContext context, String uri, Map<String, String> params);
}
