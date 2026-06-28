/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

import dev.tachyonmcp.server.domain.BlobResourceContents;
import dev.tachyonmcp.server.domain.ReadResourceRequest;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceHandler;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import dev.tachyonmcp.server.session.McpContext;

/**
 * Demonstrates static resources and URI-template resource registrations.
 */
final class ResourceHandlerExample {

    /**
     * Static resource — fixed URI.
     */
    static ResourceHandler configHandler() {
        return (McpContext ctx, ReadResourceRequest req) ->
            TextResourceContents.of(req.uri(), "application/json", "{\"mode\":\"production\"}");
    }

    static ResourceDescriptor configDescriptor() {
        return ResourceDescriptor.of("server-config", "myapp://config", "Server configuration", "application/json");
    }

    /**
     * Static resource — binary (image, PDF, etc).
     */
    static ResourceHandler imageHandler() {
        return (McpContext ctx, ReadResourceRequest req) ->
            BlobResourceContents.of(req.uri(), "image/png", "iVBORw0KGgoAAAANS...");
    }

    /**
     * URI template — {param} segments captured at runtime.
     */
    static ResourceTemplateEntry userProfileTemplate() {
        return ResourceTemplateEntry.of(
            "user-profile",
            "myapp://users/{userId}/profile",
            "User profile data",
            "application/json",
            (McpContext ctx, String uri, java.util.Map<String, String> params) -> {
                var userId = params.get("userId");
                return TextResourceContents.of(uri, "application/json", "{\"userId\":\"" + userId + "\"}");
            });
    }

    /**
     * URI template — multi-segment with static prefix matching.
     */
    static ResourceTemplateEntry forecastTemplate() {
        return ResourceTemplateEntry.of(
            "forecast",
            "weather://forecast/{city}",
            "Weather forecast for a city",
            "application/json",
            (ctx, uri, params) -> TextResourceContents.of(
                uri, "application/json",
                "{\"city\":\"" + params.get("city") + "\",\"temp\":22}"));
    }
}
