/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.annotations.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.Args;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.features.annotations.AnnotationRegistrationContext;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolFn;
import dev.tachyonmcp.api.server.features.tools.ToolRequest;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.api.server.features.tools.Tools;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LangChain4jAnnotationProviderTest {

    static class Fixture {

        boolean sawContext;

        @Tool("Adds numbers")
        int add(@P(name = "a", description = "first operand") int a, @P(value = "second", required = false) Integer b) {
            return a + (b == null ? 0 : b);
        }

        @Tool(name = "noop")
        void noop(InteractionContext ctx) {
            sawContext = ctx != null;
        }
    }

    @Mock
    AnnotationRegistrationContext context;

    Tools tools;

    ArgumentCaptor<ToolDescriptor> descriptors;
    ArgumentCaptor<ToolFn> fns;

    @BeforeEach
    void setUp() {
        tools = mock(Tools.class);
        org.mockito.Mockito.when(context.tools()).thenReturn(tools);
        new LangChain4jAnnotationProvider().register(new Fixture(), context);

        descriptors = ArgumentCaptor.forClass(ToolDescriptor.class);
        fns = ArgumentCaptor.forClass(ToolFn.class);
        verify(tools, times(2)).register(descriptors.capture(), fns.capture());
    }

    private ToolDescriptor tool(String name) {
        return descriptors.getAllValues().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tool not registered: " + name));
    }

    private ToolFn fn(String name) {
        for (int i = 0; i < descriptors.getAllValues().size(); i++) {
            if (descriptors.getAllValues().get(i).name().equals(name)) {
                return fns.getAllValues().get(i);
            }
        }
        throw new AssertionError("fn not found: " + name);
    }

    private static ToolRequest request(Map<String, Object> args) {
        return ToolRequest.builder().name("any").arguments(Args.of(args)).build();
    }

    private static String textOf(ToolResult result) {
        assertThat(result).isInstanceOf(ToolResult.Success.class);
        return ((TextContent) ((ToolResult.Success) result).content().getFirst()).text();
    }

    @Test
    void registersToolsFromAnnotatedMethods() {
        assertThat(tool("add")).isNotNull();
        assertThat(tool("noop")).isNotNull();
    }

    @Test
    void toolDescriptionComesFromValueAttribute() {
        assertThat(tool("add").description()).isEqualTo("Adds numbers");
        assertThat(tool("noop").description()).isNull();
    }

    @Test
    void inputSchemaReflectsParamsRequiredAndDescriptions() {
        String json = tool("add").inputSchema().json();
        assertThat(json).contains("\"a\"");
        assertThat(json).contains("\"b\"");
        assertThat(json).contains("first operand");
        assertThat(json).contains("\"required\":[\"a\"]");
        assertThat(json).doesNotContain("\"required\":[\"b\"");
    }

    @Test
    void invokingAddPassesNamedArguments() throws Exception {
        ToolResult result = fn("add").apply(mock(InteractionContext.class), request(Map.of("a", 3, "b", 4)));
        assertThat(textOf(result)).isEqualTo("7");
    }

    @Test
    void missingOptionalArgumentPassesNull() throws Exception {
        ToolResult result = fn("add").apply(mock(InteractionContext.class), request(Map.of("a", 3)));
        assertThat(textOf(result)).isEqualTo("3");
    }

    @Test
    void interactionContextInjectedAndExcludedFromSchema() throws Exception {
        Fixture fixture = new Fixture();
        new LangChain4jAnnotationProvider().register(fixture, context);

        ArgumentCaptor<ToolDescriptor> d = ArgumentCaptor.forClass(ToolDescriptor.class);
        ArgumentCaptor<ToolFn> f = ArgumentCaptor.forClass(ToolFn.class);
        verify(tools, times(4)).register(d.capture(), f.capture());

        ToolDescriptor noop = null;
        ToolFn noopFn = null;
        for (int i = 0; i < d.getAllValues().size(); i++) {
            if (d.getAllValues().get(i).name().equals("noop")) {
                noop = d.getAllValues().get(i);
                noopFn = f.getAllValues().get(i);
            }
        }
        assertThat(noop).isNotNull();
        assertThat(noop.inputSchema().json()).contains("\"properties\":{}");

        ToolResult result = noopFn.apply(org.mockito.Mockito.mock(InteractionContext.class), request(Map.of()));
        assertThat(result).isInstanceOf(ToolResult.Success.class);
        assertThat(textOf(result)).isEqualTo("Success");
        assertThat(fixture.sawContext).isTrue();
    }
}
