/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.BlobResourceContents;
import dev.tachyonmcp.server.domain.ReadResourceRequest;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.resources.AsyncResourceHandler;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceHandler;
import dev.tachyonmcp.server.features.resources.ResourceTemplate;
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor;

/**
 * Demonstrates static resources and URI-template resource registrations.
 */
final class ResourceHandlerExample {

    /**
     * Static resource — fixed URI.
     */
    static ResourceHandler configHandler() {
        return (InteractionContext ctx, ReadResourceRequest req) ->
            TextResourceContents.of(req.uri(), "application/json", "{\"mode\":\"production\"}");
    }

    static ResourceDescriptor configDescriptor() {
        return ResourceDescriptor.of("server-config", "myapp://config", "Server configuration", "application/json");
    }

    /**
     * Static resource — binary (image, PDF, etc).
     */
    static ResourceHandler imageHandler() {
        return (InteractionContext ctx, ReadResourceRequest req) ->
            BlobResourceContents.of(req.uri(), "image/png", "iVBORw0KGgoAAAANS...");
    }

    /**
     * URI template — {param} segments captured at runtime.
     */
    static ResourceTemplate userProfileTemplate() {
        return ResourceTemplate.of(
            ResourceTemplateDescriptor.builder()
                .name("user-profile")
                .uriTemplate("myapp://users/{userId}/profile")
                .description("User profile data")
                .mimeType("application/json")
                .build(),
            (InteractionContext ctx, String uri, java.util.Map<String, String> params) -> {
                var userId = params.get("userId");
                return TextResourceContents.of(uri, "application/json", "{\"userId\":\"" + userId + "\"}");
            });
    }

    /**
     * URI template — multi-segment with static prefix matching.
     */
    static ResourceTemplate forecastTemplate() {
        return ResourceTemplate.of(
            ResourceTemplateDescriptor.of(
                "forecast",//name
                "weather://forecast/{city}" //uriTemplate
            ),
            (ctx, uri, params) -> TextResourceContents.of(
                uri, "application/json",
                "{\"city\":\"" + params.get("city") + "\",\"temp\":22}"));
    }

    /**
     * Async resource — returns a CompletionStage for non-blocking backends.
     * Blocking handlers run on virtual threads, so prefer plain ResourceHandler unless
     * integrating an already-async client.
     */
    static AsyncResourceHandler asyncConfigHandler() {
        return (ctx, req) -> java.util.concurrent.CompletableFuture.supplyAsync(
            () -> TextResourceContents.of(req.uri(), "application/json", "{\"mode\":\"production\"}"));
    }
}
