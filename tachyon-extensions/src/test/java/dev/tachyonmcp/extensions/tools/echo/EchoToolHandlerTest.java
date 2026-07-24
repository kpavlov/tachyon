/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.extensions.tools.echo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.Args;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.NoopInteractionContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EchoToolHandlerTest {

    private static final InteractionContext NOOP_CTX = NoopInteractionContext.INSTANCE;

    @Test
    void echoReturnsInputMessage() {
        var handler = new EchoToolHandler();
        var args = Args.of(Map.of("message", "hello"));

        var result = (ToolResult.Success) handler.handle(NOOP_CTX, args);

        var text = ((TextContent) result.content().getFirst()).text();
        assertThat(text).isEqualTo("hello");
    }

    @Test
    void echoReturnsEmptyStringWhenMessageMissing() {
        var handler = new EchoToolHandler();
        var args = Args.empty();

        var result = (ToolResult.Success) handler.handle(NOOP_CTX, args);

        var text = ((TextContent) result.content().getFirst()).text();
        assertThat(text).isEmpty();
    }
}
