/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.extensions.tools.echo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.extensions.testing.NoopInteractionContext;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class EchoToolHandlerTest {

    private static final InteractionContext NOOP_CTX = new NoopInteractionContext();

    @Test
    void echoReturnsInputMessage() {
        var handler = new EchoToolHandler();
        var args = ToolArgs.of(Map.of("message", JsonNodeFactory.instance.stringNode("hello")));

        var result = (ToolResult.Success) handler.handle(NOOP_CTX, args);

        var text = ((TextContent) result.content().getFirst()).text();
        assertThat(text).isEqualTo("hello");
    }

    @Test
    void echoReturnsEmptyStringWhenMessageMissing() {
        var handler = new EchoToolHandler();
        var args = ToolArgs.of(Map.of());

        var result = (ToolResult.Success) handler.handle(NOOP_CTX, args);

        var text = ((TextContent) result.content().getFirst()).text();
        assertThat(text).isEmpty();
    }
}
