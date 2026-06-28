/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.session.McpContext;
import java.util.Map;

@FunctionalInterface
public interface ResourceTemplateHandler {

    ResourceContents read(McpContext context, String uri, Map<String, String> params);
}
