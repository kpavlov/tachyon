/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

import dev.tachyonmcp.server.domain.BlobResourceContents;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.resources.AsyncResourceHandler;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceHandler;
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor;

import java.util.concurrent.CompletableFuture;

/**
 * Demonstrates static resources and URI-template resource registrations.
 */
final class ResourceHandlerExample {

    /**
     * Static resource — fixed URI.
     */
    static ResourceHandler configHandler() {
        return ResourceHandler.of((ctx, uri) ->
            TextResourceContents.of(uri, "{\"mode\":\"production\"}", "application/json"));
    }

    static ResourceDescriptor configDescriptor() {
        return ResourceDescriptor.of("server-config", "myapp://config", "Server configuration", "application/json");
    }

    /**
     * Static resource — binary (image, PDF, etc).
     */
    static ResourceHandler imageHandler() {
        return ResourceHandler.of((ctx, uri) ->
            BlobResourceContents.of(uri, "iVBORw0KGgoAAAANS...", "image/png"));
    }

    /**
     * URI template — {param} segments captured at runtime.
     */
    static ResourceTemplateDescriptor userProfileTemplateDescriptor() {
        return ResourceTemplateDescriptor.builder()
            .name("user-profile")
            .uriTemplate("myapp://users/{userId}/profile")
            .description("User profile data")
            .mimeType("application/json")
            .build();
    }

    static ResourceHandler userProfileTemplateHandler() {
        return (ctx, uri, params, uriTemplate) -> {
            var userId = params.get("userId").scalarValue();
            return TextResourceContents.of(uri, "{\"userId\":\"" + userId + "\"}", "application/json");
        };
    }

    /**
     * URI template — multi-segment with static prefix matching.
     */
    static ResourceTemplateDescriptor forecastTemplateDescriptor() {
        return ResourceTemplateDescriptor.of(
            "forecast", //name
            "weather://forecast/{city}" //uriTemplate
        );
    }

    static ResourceHandler forecastTemplateHandler() {
        return (ctx, uri, params, uriTemplate) -> TextResourceContents.of(
            uri, "{\"city\":\"" + params.get("city").scalarValue() + "\",\"temp\":22}", "application/json"
        );
    }

    /**
     * Async resource — returns a CompletionStage for non-blocking backends.
     * Blocking handlers run on virtual threads, so prefer plain ResourceHandler unless
     * integrating an already-async client.
     */
    static AsyncResourceHandler asyncConfigHandler() {
        return ResourceHandler.ofAsync((ctx, uri) -> CompletableFuture.supplyAsync(
            () -> TextResourceContents.of(uri, "{\"mode\":\"production\"}", "application/json")));
    }
}
