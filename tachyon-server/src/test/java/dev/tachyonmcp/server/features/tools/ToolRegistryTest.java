/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import static dev.tachyonmcp.test.TestUtils.parseJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CallToolResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListToolsResult;
import dev.tachyonmcp.server.JsonSchemaValidator;
import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.session.DefaultMcpContext;
import dev.tachyonmcp.server.session.McpContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcError;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

class ToolRegistryTest {

    private static final JsonNode TEST_SCHEMA = parseJson("""
        {"type":"object","properties":{"message":{"type":"string"}},"required":["message"]}
        """);

    private final ToolRegistry registry = new ToolRegistry(JsonSchemaValidator.noop());

    @Test
    void listToolsReturnsEmptyListWhenNoToolsRegistered() throws Exception {
        var handlers = new HashMap<String, McpMethodHandler>();
        registry.registerHandlers(handlers);
        var listHandler = handlers.get("tools/list");
        var result = listHandler.handle(DefaultMcpContext.noop(), null);
        assertThat(result).isInstanceOf(ListToolsResult.class);
        assertThat(((ListToolsResult) result).tools()).isEmpty();
    }

    @Test
    void listTools() throws Exception {
        var handlers = new HashMap<String, McpMethodHandler>();
        registry.registerHandlers(handlers);

        // minimal: only name set; all optional fields absent
        registry.register(new TestTool("minimal-tool", null, null));

        // full: all possible fields set
        var outputSchema = parseJson("""
            {"type":"object","properties":{"result":{"type":"string"}}}
            """);
        var annotations = ToolAnnotations.of(null, true, false, true, false);
        registry.register(
                new AbstractSyncToolHandler<>(ToolDescriptor.builder("full-tool")
                        .title("Full Tool")
                        .description("Does everything")
                        .inputSchema(TEST_SCHEMA)
                        .outputSchema(outputSchema)
                        .taskSupport(TaskSupport.OPTIONAL)
                        .annotations(annotations)
                        .build()) {
                    @Override
                    public ToolResult handle(McpContext context, Map<String, JsonNode> arguments) {
                        return ToolResult.text("ok");
                    }
                });

        var listResult = (ListToolsResult) handlers.get("tools/list").handle(DefaultMcpContext.noop(), null);
        assertThat(listResult.tools()).hasSize(2);

        var minimal = listResult.tools().stream()
                .filter(t -> "minimal-tool".equals(t.name()))
                .findFirst()
                .orElseThrow();
        assertThat(minimal.title()).isNull();
        assertThat(minimal.description()).isNull();
        assertThat(minimal.inputSchema())
                .isEqualTo(parseJson("""
                {"type":"object"}
                """)); // defaulted by McpToolMapper when handler returns null
        assertThat(minimal.outputSchema()).isNull();
        assertThat(minimal.execution()).isNull();

        var full = listResult.tools().stream()
                .filter(t -> "full-tool".equals(t.name()))
                .findFirst()
                .orElseThrow();
        assertThat(full.title()).isEqualTo("Full Tool");
        assertThat(full.description()).isEqualTo("Does everything");
        assertThat(full.inputSchema()).isEqualTo(TEST_SCHEMA);
        assertThat(full.outputSchema()).isEqualTo(outputSchema);
        assertThat(full.execution()).isNotNull();
        assertThat(full.execution().taskSupport()).isEqualTo("optional");
        assertThat(full.annotations()).isNotNull();
        assertThat(full.annotations().readOnlyHint()).isEqualTo(annotations.readOnlyHint());
        assertThat(full.annotations().destructiveHint()).isEqualTo(annotations.destructiveHint());
        assertThat(full.annotations().idempotentHint()).isEqualTo(annotations.idempotentHint());
        assertThat(full.annotations().openWorldHint()).isEqualTo(annotations.openWorldHint());
    }

    @Test
    void callToolNotFound() throws Exception {
        var handlers = new HashMap<String, McpMethodHandler>();
        registry.registerHandlers(handlers);

        var callHandler = handlers.get("tools/call");
        var params = Map.<String, Object>of("name", "nonexistent");

        var result = callHandler.handle(DefaultMcpContext.noop(), params);
        assertThat(result).isInstanceOf(JsonRpcError.class);
        var err = (JsonRpcError) result;
        assertThat(err.code()).isEqualTo(JsonRpcErrors.METHOD_NOT_FOUND);
    }

    @Test
    void callToolMissingName() throws Exception {
        var handlers = new HashMap<String, McpMethodHandler>();
        registry.registerHandlers(handlers);

        var callHandler = handlers.get("tools/call");

        var result = callHandler.handle(DefaultMcpContext.noop(), Map.of());
        assertThat(result).isInstanceOf(JsonRpcError.class);
        var err = (JsonRpcError) result;
        assertThat(err.code()).isEqualTo(JsonRpcErrors.INVALID_REQUEST);
    }

    @Test
    void callToolWithNullParams() throws Exception {
        var handlers = new HashMap<String, McpMethodHandler>();
        registry.registerHandlers(handlers);

        var callHandler = handlers.get("tools/call");

        var result = callHandler.handle(DefaultMcpContext.noop(), null);
        assertThat(result).isInstanceOf(JsonRpcError.class);
        var err = (JsonRpcError) result;
        assertThat(err.code()).isEqualTo(JsonRpcErrors.INVALID_REQUEST);
    }

    @Test
    void callToolReturnsResult() throws Exception {
        try (var server = TachyonServer.builder().build()) {
            var session = server.createSession("test");
            session.activate();
            var handlers = new HashMap<String, McpMethodHandler>();
            registry.registerHandlers(handlers);
            registry.register(new TestTool("echo", "Echo", TEST_SCHEMA));

            var callHandler = handlers.get("tools/call");
            var params = Map.of("name", "echo", "arguments", Map.of("message", "hello"));

            var ctx = new DefaultMcpContext(Protocols.versions().get(0), server);
            ctx.setSession(session);
            var result = callHandler.handle(ctx, params);
            assertThat(result).isInstanceOf(CallToolResult.class);
        }
    }

    /**
     * Tool name validation follows
     * <a href="https://modelcontextprotocol.io/seps/986-specify-format-for-tool-names">SEP-986</a>:
     * 1-64 characters, case-sensitive, alphanumeric plus underscore, dash, dot, forward slash.
     */
    @ParameterizedTest
    @MethodSource("validToolNames")
    void shouldAcceptValidNameOnRegister(String name) {
        registry.register(SyncToolHandler.of(name, null, null, (ctx, args) -> ToolResult.text("ok")));
        assertThat(registry.get(name)).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("invalidToolNames")
    void shouldRejectInvalidNameOnRegister(String name) {
        assertThatThrownBy(() ->
                        registry.register(SyncToolHandler.of(name, null, null, (ctx, args) -> ToolResult.text("ok"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> validToolNames() {
        return Stream.of(
                Arguments.of("valid-name"),
                Arguments.of("valid_name"),
                Arguments.of("valid.name"),
                Arguments.of("valid/name"),
                Arguments.of("VALID_NAME"),
                Arguments.of("tool123"),
                Arguments.of("admin.tools.list"),
                Arguments.of("user-profile/update"),
                Arguments.of("DATA_EXPORT_v2"),
                Arguments.of("a"),
                Arguments.of("a" + "b".repeat(63)));
    }

    private static Stream<Arguments> invalidToolNames() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of("has space"),
                Arguments.of("has,comma"),
                Arguments.of("has@at"),
                Arguments.of("has#hash"),
                Arguments.of("has!bang"),
                Arguments.of("has%percent"),
                Arguments.of("has^caret"),
                Arguments.of("has&and"),
                Arguments.of("has*star"),
                Arguments.of("has(open"),
                Arguments.of("has)close"),
                Arguments.of("has[open"),
                Arguments.of("has]close"),
                Arguments.of("has{open"),
                Arguments.of("has}close"),
                Arguments.of("has;semi"),
                Arguments.of("has'quote"),
                Arguments.of("has\"quote"),
                Arguments.of("has<lt"),
                Arguments.of("has>gt"),
                Arguments.of("has?qmark"),
                Arguments.of("has+plus"),
                Arguments.of("has=eq"),
                Arguments.of("has~tilde"),
                Arguments.of("has`backtick"),
                Arguments.of("has\\backslash"),
                Arguments.of("a" + "b".repeat(64)));
    }

    @ParameterizedTest
    @CsvSource({"FORBIDDEN,forbidden", "OPTIONAL,optional", "REQUIRED,required"})
    void taskSupportSerializesToWireValue(TaskSupport enumValue, String wireValue) throws Exception {
        var handlers = new HashMap<String, McpMethodHandler>();
        registry.registerHandlers(handlers);
        registry.register(
                new AbstractSyncToolHandler<>(
                        ToolDescriptor.builder("ts-tool").taskSupport(enumValue).build()) {
                    @Override
                    public ToolResult handle(McpContext context, Map<String, JsonNode> arguments) {
                        return ToolResult.text("ok");
                    }
                });

        var result = (ListToolsResult) handlers.get("tools/list").handle(DefaultMcpContext.noop(), null);
        var tool = result.tools().stream()
                .filter(t -> "ts-tool".equals(t.name()))
                .findFirst()
                .orElseThrow();
        assertThat(tool.execution()).isNotNull();
        assertThat(tool.execution().taskSupport()).isEqualTo(wireValue);
    }

    @Test
    void shouldFireOnChangeWhenToolAdded() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.register(new TestTool("new-tool", null, null));

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldFireOnChangeWhenExistingToolRemoved() {
        registry.register(new TestTool("removable-tool", null, null));

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.remove("removable-tool");

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldNotFireOnChangeWhenRemovingNonExistentTool() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.remove("does-not-exist");

        assertThat(callCount).hasValue(0);
    }

    @Test
    void shouldMapIconsFromDescriptorToProtocolModel() throws Exception {
        var handlers = new HashMap<String, McpMethodHandler>();
        registry.registerHandlers(handlers);
        var icon = Icon.of("https://example.com/tool-icon.png", "image/png", null, null);
        registry.register(
                new AbstractSyncToolHandler<>(ToolDescriptor.builder("icon-tool")
                        .description("Tool with icon")
                        .icons(List.of(icon))
                        .build()) {
                    @Override
                    public ToolResult handle(McpContext context, Map<String, JsonNode> arguments) {
                        return ToolResult.text("ok");
                    }
                });

        var listResult = (ListToolsResult) handlers.get("tools/list").handle(DefaultMcpContext.noop(), null);
        var tool = listResult.tools().stream()
                .filter(t -> "icon-tool".equals(t.name()))
                .findFirst()
                .orElseThrow();

        assertThat(tool.icons()).isNotNull().hasSize(1);
        assertThat(tool.icons().getFirst().src()).isEqualTo("https://example.com/tool-icon.png");
        assertThat(tool.icons().getFirst().mimeType()).isEqualTo("image/png");
    }

    private static class TestTool extends AbstractSyncToolHandler<ToolResult> {

        TestTool(String name, @Nullable String description, @Nullable JsonNode schema) {
            super(ToolDescriptor.builder(name)
                    .description(description)
                    .inputSchema(schema)
                    .build());
        }

        @Override
        public ToolResult handle(McpContext context, Map<String, JsonNode> arguments) {
            return ToolResult.text("ok");
        }
    }
}
