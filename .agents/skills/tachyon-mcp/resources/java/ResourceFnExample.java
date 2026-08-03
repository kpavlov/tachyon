/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

import dev.tachyonmcp.api.server.domain.BlobResourceContents;
import dev.tachyonmcp.api.server.domain.TextResourceContents;
import dev.tachyonmcp.api.server.features.resources.AsyncResourceFn;
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.api.server.features.resources.ResourceFn;
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor;

import java.util.concurrent.CompletableFuture;

/**
 * Demonstrates static resources and URI-template resource registrations.
 */
final class ResourceFnExample {

    /**
     * Static resource — fixed URI.
     */
    static ResourceFn configHandler() {
        return (ctx, request) ->
            TextResourceContents.of(request.uri(), "{\"mode\":\"production\"}", "application/json");
    }

    static ResourceDescriptor configDescriptor() {
        return ResourceDescriptor.of("server-config", "myapp://config", "Server configuration", "application/json");
    }

    /**
     * Static resource — binary (image, PDF, etc). blob() accepts raw bytes; use the InputStream
     * overload to read directly from a file or classpath resource.
     */
    static ResourceFn imageHandler() {
        return (ctx, request) -> {
            byte[] pngBytes = ResourceFnExample.class.getResourceAsStream("/logo.png").readAllBytes();
            return BlobResourceContents.of(request.uri(), pngBytes, "image/png");
        };
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

    static ResourceFn userProfileTemplateHandler() {
        return (ctx, request) -> {
            var userId = request.params().get("userId").scalarValue();
            return TextResourceContents.of(
                request.uri(), "{\"userId\":\"" + userId + "\"}", "application/json");
        };
    }

    /**
     * URI template — multi-segment with static prefix matching.
     */
    static ResourceTemplateDescriptor forecastTemplateDescriptor() {
        return ResourceTemplateDescriptor.builder()
            .name("forecast")
            .uriTemplate("weather://forecast/{city}")
            .build();
    }

    static ResourceFn forecastTemplateHandler() {
        return (ctx, request) -> TextResourceContents.of(
            request.uri(),
            "{\"city\":\"" + request.params().get("city").scalarValue() + "\",\"temp\":22}",
            "application/json"
        );
    }

    /**
     * Async resource — returns a CompletionStage for non-blocking backends.
     * Blocking handlers run on virtual threads, so prefer plain ResourceFn unless
     * integrating an already-async client.
     */
    static AsyncResourceFn asyncConfigHandler() {
        return (ctx, request) -> CompletableFuture.supplyAsync(
            () -> TextResourceContents.of(request.uri(), "{\"mode\":\"production\"}", "application/json"));
    }
}
