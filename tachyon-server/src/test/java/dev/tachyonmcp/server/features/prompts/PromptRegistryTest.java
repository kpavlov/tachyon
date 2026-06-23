/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.GetPromptResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListPromptsResult;
import dev.tachyonmcp.server.JsonSchemaValidator;
import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.PromptArgument;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.session.DefaultMcpContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcError;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromptRegistryTest {

    private final PromptRegistry registry = new PromptRegistry(JsonSchemaValidator.noop());
    private final HashMap<String, McpMethodHandler> handlers = new HashMap<>();

    @BeforeEach
    void setUp() {
        registry.registerHandlers(handlers);
    }

    @Test
    void shouldReturnEmptyListWhenNoPromptsRegistered() throws Exception {
        var result = handlers.get("prompts/list").handle(DefaultMcpContext.noop(), null);

        assertThat(result).isInstanceOf(ListPromptsResult.class);
        assertThat(((ListPromptsResult) result).prompts()).isEmpty();
    }

    @Test
    void shouldReturnErrorWhenPromptNotFound() throws Exception {
        var result = handlers.get("prompts/get").handle(DefaultMcpContext.noop(), Map.of("name", "nonexistent"));

        assertThat(result).isInstanceOf(JsonRpcError.class);
        assertThat(((JsonRpcError) result).code()).isEqualTo(JsonRpcErrors.INVALID_REQUEST);
    }

    @Test
    void shouldReturnErrorWhenPromptNameMissing() throws Exception {
        var result = handlers.get("prompts/get").handle(DefaultMcpContext.noop(), Map.of());

        assertThat(result).isInstanceOf(JsonRpcError.class);
    }

    @Test
    void shouldReturnPromptByName() throws Exception {
        registry.add(PromptDescriptor.of("greeting", "A greeting prompt"), List.of(PromptMessage.user("Hello!")));

        var result = handlers.get("prompts/get").handle(DefaultMcpContext.noop(), Map.of("name", "greeting"));

        assertThat(result).isInstanceOf(GetPromptResult.class);
        assertThat(((GetPromptResult) result).description()).isEqualTo("A greeting prompt");
    }

    @Test
    void shouldReturnAllRegisteredPrompts() throws Exception {
        registry.add(PromptDescriptor.of("prompt-1", "First prompt"), List.of());
        registry.add(PromptDescriptor.of("prompt-2", "Second prompt"), List.of());

        var result = (ListPromptsResult) handlers.get("prompts/list").handle(DefaultMcpContext.noop(), null);

        assertThat(result.prompts()).hasSize(2);
    }

    @Test
    void shouldFireOnChangeWhenPromptAdded() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.add(PromptDescriptor.of("greeting", "A greeting prompt"), List.of());

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldFireOnChangeWhenExistingPromptRemoved() {
        registry.add(PromptDescriptor.of("greeting", "A greeting prompt"), List.of());

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.remove("greeting");

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldNotFireOnChangeWhenRemovingNonExistentPrompt() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.remove("does-not-exist");

        assertThat(callCount).hasValue(0);
    }

    @Test
    void shouldReplacePromptAndFireOnChangeWhenAddedWithSameName() {
        registry.add(PromptDescriptor.of("greeting", "Original"), List.of());

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.add(PromptDescriptor.of("greeting", "Updated"), List.of());

        assertThat(callCount).hasValue(1);
        assertThat(registry.get("greeting").descriptor().description()).isEqualTo("Updated");
        assertThat(registry.getAll()).hasSize(1);
    }

    @Test
    void shouldMapAllDescriptorFieldsToProtocolModel() throws Exception {
        var icon = Icon.of("https://example.com/prompt-icon.svg", "image/svg+xml", null, null);
        var arg = PromptArgument.of("topic", null, "The topic", true);
        var descriptor =
                PromptDescriptor.of("full-prompt", "Full description", "Full Title", List.of(arg), null, List.of(icon));
        registry.add(descriptor, List.of(PromptMessage.user("Hello")));

        var result = (dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListPromptsResult)
                handlers.get("prompts/list").handle(DefaultMcpContext.noop(), null);
        var prompt = result.prompts().getFirst();

        assertThat(prompt.name()).isEqualTo("full-prompt");
        assertThat(prompt.description()).isEqualTo("Full description");
        assertThat(prompt.title()).isEqualTo("Full Title");
        assertThat(prompt.arguments()).hasSize(1);
        assertThat(prompt.arguments().getFirst().name()).isEqualTo("topic");
        assertThat(prompt.arguments().getFirst().required()).isTrue();
        assertThat(prompt.icons()).isNotNull().hasSize(1);
        assertThat(prompt.icons().getFirst().src()).isEqualTo("https://example.com/prompt-icon.svg");
    }

    @Test
    void shouldUseDynamicHandlerForMessages() throws Exception {
        registry.add(
                PromptDescriptor.of("dynamic", "Dynamic prompt"), args -> List.of(PromptMessage.user("args=" + args)));

        var result = (GetPromptResult)
                handlers.get("prompts/get").handle(DefaultMcpContext.noop(), Map.of("name", "dynamic"));

        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().getFirst().content().toString()).contains("args=");
    }
}
