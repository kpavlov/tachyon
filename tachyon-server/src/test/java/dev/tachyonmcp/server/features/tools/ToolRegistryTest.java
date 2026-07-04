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
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TextContent;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.JsonSchemaValidator;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.session.DefaultMcpContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcError;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    // language=json
    private static final JsonNode TEST_SCHEMA = parseJson("""
        {"type":"object","properties":{"message":{"type":"string"}},"required":["message"]}
        """);

    private final ToolRegistry registry = new ToolRegistry(JsonSchemaValidator.noop());

    @Test
    void listToolsReturnsEmptyListWhenNoToolsRegistered() throws Exception {
        var handlers = new HashMap<String, RpcMethodHandler>();
        registry.registerHandlers(handlers);
        var listHandler = handlers.get("tools/list");
        var result = listHandler.handle(DefaultMcpContext.noop(), null);
        assertThat(result).isInstanceOf(ListToolsResult.class);
        assertThat(((ListToolsResult) result).tools()).isEmpty();
    }

    @Test
    void listTools() throws Exception {
        var handlers = new HashMap<String, RpcMethodHandler>();
        registry.registerHandlers(handlers);

        // minimal: only name set; all optional fields absent
        registry.register(new TestTool("minimal-tool", null, null));

        // full: all possible fields set
        // language=json
        var outputSchema = parseJson("""
            {"type":"object","properties":{"result":{"type":"string"}}}
            """);
        var annotations = ToolAnnotations.of(null, true, false, true, false);
        registry.register(
                new AbstractSyncToolHandler(ToolDescriptor.builder("full-tool")
                        .title("Full Tool")
                        .description("Does everything")
                        .inputSchema(TEST_SCHEMA)
                        .outputSchema(outputSchema)
                        .taskSupport(TaskSupport.OPTIONAL)
                        .annotations(annotations)
                        .build()) {
                    @Override
                    public ToolResult handle(InteractionContext context, ToolArgs args) {
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
        var handlers = new HashMap<String, RpcMethodHandler>();
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
        var handlers = new HashMap<String, RpcMethodHandler>();
        registry.registerHandlers(handlers);

        var callHandler = handlers.get("tools/call");

        var result = callHandler.handle(DefaultMcpContext.noop(), Map.of());
        assertThat(result).isInstanceOf(JsonRpcError.class);
        var err = (JsonRpcError) result;
        assertThat(err.code()).isEqualTo(JsonRpcErrors.INVALID_REQUEST);
    }

    @Test
    void callToolWithNullParams() throws Exception {
        var handlers = new HashMap<String, RpcMethodHandler>();
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
            var handlers = new HashMap<String, RpcMethodHandler>();
            registry.registerHandlers(handlers);
            registry.register(new TestTool("echo", "Echo", TEST_SCHEMA));

            var callHandler = handlers.get("tools/call");
            var params = Map.of("name", "echo", "arguments", Map.of("message", "hello"));

            var ctx = DefaultMcpContext.create(Protocols.versions().getFirst(), server);
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
        var handlers = new HashMap<String, RpcMethodHandler>();
        registry.registerHandlers(handlers);
        registry.register(
                new AbstractSyncToolHandler(
                        ToolDescriptor.builder("ts-tool").taskSupport(enumValue).build()) {
                    @Override
                    public ToolResult handle(InteractionContext context, ToolArgs args) {
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
        var handlers = new HashMap<String, RpcMethodHandler>();
        registry.registerHandlers(handlers);
        var icon = Icon.of("https://example.com/tool-icon.png", "image/png", null, null);
        registry.register(
                new AbstractSyncToolHandler(ToolDescriptor.builder("icon-tool")
                        .description("Tool with icon")
                        .icons(List.of(icon))
                        .build()) {
                    @Override
                    public ToolResult handle(InteractionContext context, ToolArgs args) {
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

    @Test
    void getDescriptorReturnsNullForMissingTool() {
        assertThat(registry.getDescriptor("nonexistent")).isNull();
    }

    @Test
    void getDescriptorReturnsDescriptorForRegisteredTool() {
        registry.register(new TestTool("desc-tool", "test desc", null));
        var desc = registry.getDescriptor("desc-tool");
        assertThat(desc).isNotNull();
        assertThat(desc.name()).isEqualTo("desc-tool");
        assertThat(desc.description()).isEqualTo("test desc");
    }

    @Test
    void getAllReturnsAllHandlers() {
        registry.register(new TestTool("t1", null, null));
        registry.register(new TestTool("t2", null, null));
        assertThat(registry.getAll()).hasSize(2);
    }

    @Test
    void isEmptyReturnsTrueWhenEmpty() {
        assertThat(registry.isEmpty()).isTrue();
    }

    @Test
    void isEmptyReturnsFalseWhenNotEmpty() {
        registry.register(new TestTool("t", null, null));
        assertThat(registry.isEmpty()).isFalse();
    }

    @Test
    void listWithZeroLimitUsesDefaultPageSize() {
        registry.register(new TestTool("a", null, null));
        registry.register(new TestTool("b", null, null));
        var result = registry.list(0, null);
        assertThat(result.items()).hasSize(2);
    }

    @Test
    void listWithCursorSkipsPastCursor() {
        registry.register(new TestTool("alpha", null, null));
        registry.register(new TestTool("beta", null, null));
        registry.register(new TestTool("gamma", null, null));
        var result = registry.list(1, "alpha");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().name()).isEqualTo("beta");
    }

    @Test
    void listWithFilterExcludesMismatched() {
        registry.register(new TestTool("keep", null, null));
        registry.register(new TestTool("skip", null, null));
        var result = registry.list(50, null, d -> d.name().startsWith("k"));
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().name()).isEqualTo("keep");
    }

    @Test
    void listWithCustomDefaultLimit() {
        registry.register(new TestTool("a", null, null));
        registry.register(new TestTool("b", null, null));
        var result = registry.list(0, null, 1);
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void listReturnsCursorWhenMoreItemsAvailable() {
        registry.register(new TestTool("a", null, null));
        registry.register(new TestTool("b", null, null));
        var result = registry.list(1, null);
        assertThat(result.nextCursor()).isEqualTo("a");
    }

    @Test
    void listReturnsNullCursorWhenAllItemsReturned() {
        registry.register(new TestTool("a", null, null));
        var result = registry.list(10, null);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void parseLimitFromNumber() {
        var params = Map.<String, Object>of("limit", 5);
        assertThat(ToolRegistry.parseLimit(params)).isEqualTo(5);
    }

    @Test
    void parseLimitFromNonNumberReturnsZero() {
        var params = Map.<String, Object>of("limit", "not-a-number");
        assertThat(ToolRegistry.parseLimit(params)).isZero();
    }

    @Test
    void parseLimitFromNullParamsReturnsZero() {
        assertThat(ToolRegistry.parseLimit(null)).isZero();
    }

    @Test
    void parseLimitFromEmptyMapReturnsZero() {
        assertThat(ToolRegistry.parseLimit(Map.of())).isZero();
    }

    @Test
    void parseCursorFromString() {
        var params = Map.<String, Object>of("cursor", "abc");
        assertThat(ToolRegistry.parseCursor(params)).isEqualTo("abc");
    }

    @Test
    void parseCursorFromNonStringReturnsNull() {
        var params = Map.<String, Object>of("cursor", 123);
        assertThat(ToolRegistry.parseCursor(params)).isNull();
    }

    @Test
    void parseCursorFromNullParamsReturnsNull() {
        assertThat(ToolRegistry.parseCursor(null)).isNull();
    }

    @Test
    void parseCursorFromEmptyMapReturnsNull() {
        assertThat(ToolRegistry.parseCursor(Map.of())).isNull();
    }

    @Test
    void registerThrowsOnNullHandler() {
        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ToolHandler");
    }

    @Test
    void registerThrowsOnNullDescriptor() {
        var handler = new ToolHandler() {
            @Override
            public ToolDescriptor descriptor() {
                return null;
            }

            @Override
            public CompletionStage<? extends ToolResult> handle(InteractionContext context, ToolRequest request) {
                return CompletableFuture.completedFuture(ToolResult.text("x"));
            }
        };
        assertThatThrownBy(() -> registry.register(handler))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ToolDescriptor");
    }

    @Test
    void getReturnsNullForMissingTool() {
        assertThat(registry.get("nonexistent")).isNull();
    }

    @Test
    void removeNonExistentToolDoesNotThrow() {
        registry.remove("nonexistent");
        assertThat(registry.get("nonexistent")).isNull();
    }

    @Test
    void registryResetBetweenTests() {
        assertThat(registry.isEmpty()).isTrue();
    }

    @Test
    void registerHandlersAddsBothMethods() {
        var handlers = new java.util.HashMap<String, RpcMethodHandler>();
        registry.registerHandlers(handlers);
        assertThat(handlers).containsOnlyKeys("tools/list", "tools/call");
    }

    @Test
    void toolsListHandlerMethodName() {
        var handlers = new java.util.HashMap<String, RpcMethodHandler>();
        registry.registerHandlers(handlers);
        assertThat(handlers.get("tools/list").method()).isEqualTo("tools/list");
    }

    @Test
    void toolsCallHandlerMethodName() {
        var handlers = new java.util.HashMap<String, RpcMethodHandler>();
        registry.registerHandlers(handlers);
        assertThat(handlers.get("tools/call").method()).isEqualTo("tools/call");
    }

    @Test
    void asyncToolHandlerCompletesFromSeparateThread() throws Exception {
        var handlers = new HashMap<String, RpcMethodHandler>();
        registry.registerHandlers(handlers);
        var executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "async-tool-pool"));
        registry.register(new AsyncToolHandler() {
            @Override
            public String name() {
                return "async-thread";
            }

            @Override
            public CompletionStage<ToolResult> handleAsync(InteractionContext context, ToolRequest request) {
                return CompletableFuture.supplyAsync(() -> ToolResult.text("from-thread"), executor);
            }

            @Override
            public CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, ToolArgs args) {
                return handleAsync(context, ToolRequest.of(name(), null, null));
            }
        });

        try (var server = TachyonServer.builder().build()) {
            var session = server.createSession("s-async-thread");
            session.activate();
            var callHandler = handlers.get("tools/call");
            var ctx = DefaultMcpContext.create(Protocols.versions().getFirst(), server);
            ctx.setSession(session);
            var params = Map.of("name", "async-thread", "arguments", Map.of());
            var stage = callHandler.handleAsync(ctx, params);
            var result = (CallToolResult) stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(result.content()).isNotEmpty();
            assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("from-thread");
        }
        executor.shutdown();
    }

    @Test
    void asyncToolHandlerInvalidArgumentExceptionMapsToInvalidRequest() throws Exception {
        var handlers = new HashMap<String, RpcMethodHandler>();
        registry.registerHandlers(handlers);
        registry.register(new AsyncToolHandler() {
            @Override
            public String name() {
                return "invalid-arg-async";
            }

            @Override
            public CompletionStage<ToolResult> handleAsync(InteractionContext context, ToolRequest request) {
                return CompletableFuture.failedFuture(new InvalidArgumentException("arg", "bad input"));
            }

            @Override
            public CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, ToolArgs args) {
                return handleAsync(context, ToolRequest.of(name(), null, null));
            }
        });

        try (var server = TachyonServer.builder().build()) {
            var session = server.createSession("s-inv-arg");
            session.activate();
            var callHandler = handlers.get("tools/call");
            var ctx = DefaultMcpContext.create(Protocols.versions().getFirst(), server);
            ctx.setSession(session);
            var params = Map.of("name", "invalid-arg-async", "arguments", Map.of());
            var result = callHandler.handle(ctx, params);
            assertThat(result).isInstanceOf(JsonRpcError.class);
            assertThat(((JsonRpcError) result).code()).isEqualTo(JsonRpcErrors.INVALID_REQUEST);
        }
    }

    @Test
    void shouldFireOnChangeWhenToolReRegistered() {
        registry.register(new TestTool("re-register", null, null));

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.register(new TestTool("re-register", null, null));
        assertThat(callCount).hasValue(1);
    }

    // region: Schema root check

    @Test
    void shouldAcceptNullInputSchema() {
        registry.register(new TestTool("null-input", null, null));
        assertThat(registry.get("null-input")).isNotNull();
    }

    @Test
    void shouldAcceptValidInputSchemaWithTypeObject() {
        registry.register(new TestTool("valid-input", null, TEST_SCHEMA));
        assertThat(registry.get("valid-input")).isNotNull();
    }

    @Test
    void shouldRejectInputSchemaWithWrongRootType() {
        var schema = parseJson("""
            {"type":"string"}
            """);
        assertThatThrownBy(() -> registry.register(new TestTool("bad", null, schema)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputSchema")
                .hasMessageContaining("\"type\": \"object\"");
    }

    @Test
    void shouldRejectInputSchemaWithoutType() {
        var schema = parseJson("""
            {"properties":{}}
            """);
        assertThatThrownBy(() -> registry.register(new TestTool("no-type", null, schema)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputSchema")
                .hasMessageContaining("\"type\": \"object\"")
                .hasMessageContaining("missing \"type\"");
    }

    @Test
    void shouldRejectInputSchemaThatIsNotAnObject() {
        var schema = parseJson("\"just a string\"");
        assertThatThrownBy(() -> registry.register(new TestTool("not-obj", null, schema)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputSchema")
                .hasMessageContaining("\"type\": \"object\"");
    }

    @Test
    void shouldRejectOutputSchemaWithWrongRootType() {
        var outputSchema = parseJson("""
            {"type":"string"}
            """);
        assertThatThrownBy(() -> registry.register(
                        new AbstractSyncToolHandler(ToolDescriptor.builder("bad-output")
                                .outputSchema(outputSchema)
                                .build()) {
                            @Override
                            public ToolResult handle(InteractionContext context, ToolArgs args) {
                                return ToolResult.text("x");
                            }
                        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outputSchema")
                .hasMessageContaining("\"type\": \"object\"");
    }

    @Test
    void shouldAcceptValidOutputSchema() {
        var outputSchema = parseJson("""
            {"type":"object","properties":{"result":{"type":"string"}}}
            """);
        registry.register(
                new AbstractSyncToolHandler(ToolDescriptor.builder("valid-output")
                        .outputSchema(outputSchema)
                        .build()) {
                    @Override
                    public ToolResult handle(InteractionContext context, ToolArgs args) {
                        return ToolResult.text("ok");
                    }
                });
        assertThat(registry.get("valid-output")).isNotNull();
    }

    // endregion

    // region: Structured value conversion for output validation

    @Test
    void shouldConvertPlainJavaMapEntriesBeforeOutputValidation() throws Exception {
        // language=json
        var outputSchema = parseJson("""
            {"type":"object","properties":{"message":{"type":"string"},"count":{"type":"integer"}},"required":["message","count"]}
            """);
        var registryVal = new ToolRegistry(new dev.tachyonmcp.server.NetworkntJsonSchemaValidator());
        var handlers = new HashMap<String, RpcMethodHandler>();
        registryVal.registerHandlers(handlers);
        var handler =
                new AbstractSyncToolHandler(ToolDescriptor.builder("structured-out")
                        .description("test")
                        .outputSchema(outputSchema)
                        .build()) {
                    @Override
                    public ToolResult handle(InteractionContext context, ToolArgs args) {
                        // Map with plain Java values, not JsonNode
                        return ToolResult.of(Map.of("message", "hello", "count", 42), "text fallback");
                    }
                };
        registryVal.register(handler);

        try (var server = TachyonServer.builder().build()) {
            var session = server.createSession("s-struct-out");
            session.activate();
            var callHandler = handlers.get("tools/call");
            var ctx = DefaultMcpContext.create(Protocols.versions().getFirst(), server);
            ctx.setSession(session);
            var params = Map.of("name", "structured-out", "arguments", Map.of());
            var result = callHandler.handle(ctx, params);
            assertThat(result).isInstanceOf(CallToolResult.class);
            // structuredContent should contain both "message" and "count"
            assertThat(((CallToolResult) result).structuredContent()).isNotNull();
            assertThat(((CallToolResult) result).structuredContent()).containsKeys("message", "count");
        }
    }

    @Test
    void shouldConvertMixedJavaAndJsonNodeEntries() throws Exception {
        var registryVal = new ToolRegistry(new dev.tachyonmcp.server.NetworkntJsonSchemaValidator());
        var handlers = new HashMap<String, RpcMethodHandler>();
        registryVal.registerHandlers(handlers);
        var handler = new AbstractSyncToolHandler(
                ToolDescriptor.builder("mixed-out").description("test").build()) {
            @Override
            public ToolResult handle(InteractionContext context, ToolArgs args) {
                var jsonNodeVal = tools.jackson.databind.node.JsonNodeFactory.instance.stringNode("json-val");
                // Mixed map: one JsonNode value, one plain String value
                return ToolResult.of(Map.of("jsonField", jsonNodeVal, "plainField", "plain-val"), "fallback");
            }
        };
        registryVal.register(handler);

        try (var server = TachyonServer.builder().build()) {
            var session = server.createSession("s-mixed");
            session.activate();
            var callHandler = handlers.get("tools/call");
            var ctx = DefaultMcpContext.create(Protocols.versions().getFirst(), server);
            ctx.setSession(session);
            var params = Map.of("name", "mixed-out", "arguments", Map.of());
            var result = callHandler.handle(ctx, params);
            assertThat(result).isInstanceOf(CallToolResult.class);
            assertThat(((CallToolResult) result).structuredContent()).isNotNull();
            assertThat(((CallToolResult) result).structuredContent()).containsOnlyKeys("jsonField", "plainField");
        }
    }

    // endregion

    private static class TestTool extends AbstractSyncToolHandler {

        TestTool(String name, @Nullable String description, @Nullable JsonNode schema) {
            super(ToolDescriptor.builder(name)
                    .description(description)
                    .inputSchema(schema)
                    .build());
        }

        @Override
        public ToolResult handle(InteractionContext context, ToolArgs args) {
            return ToolResult.text("ok");
        }
    }
}
