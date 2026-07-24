/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import static dev.tachyonmcp.test.TestUtils.newEngine;
import static dev.tachyonmcp.test.VirtualThreads.runInVirtualThread;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ReadResourceRequestParams;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.config.ResourcesConfig;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.session.DefaultDispatchContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ResourceRequestDispatchTest {

    @Test
    void shouldPassRequestMetaToResourceHandler() throws Exception {
        var server = newEngine(builder -> {});
        var registry =
                new DefaultResourceRegistry(server, ResourcesConfig.builder().build());
        var handlers = new HashMap<String, RpcMethodHandler>();
        registry.registerHandlers(handlers);
        var captured = new AtomicReference<ResourceRequest>();
        registry.register(
                ResourceDescriptor.of("meta-request", "test://meta-request", null, "text/plain"), (ctx, request) -> {
                    captured.set(request);
                    return TextResourceContents.of(request.uri(), "meta", "text/plain");
                });
        Map<String, JsonNode> meta = Map.of("trace-id", new ObjectMapper().readTree("\"trace-42\""));

        runInVirtualThread(() -> handlers.get("resources/read")
                .handle(
                        DefaultDispatchContext.stateless(server),
                        ReadResourceRequestParams.builder()
                                .uri("test://meta-request")
                                ._meta(meta)
                                .build()));

        assertThat(captured.get().uri()).isEqualTo("test://meta-request");
        assertThat(captured.get().params()).isEmpty();
        assertThat(captured.get().uriTemplate()).isNull();
        assertThat(captured.get().meta()).isEqualTo(Map.of("trace-id", "trace-42"));
    }
}
