/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.annotations.spring.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.Args;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.domain.UriTemplateValue;
import dev.tachyonmcp.api.server.features.annotations.AnnotationRegistrationContext;
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.api.server.features.prompts.PromptFn;
import dev.tachyonmcp.api.server.features.prompts.PromptRequest;
import dev.tachyonmcp.api.server.features.prompts.PromptResult;
import dev.tachyonmcp.api.server.features.prompts.Prompts;
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.api.server.features.resources.ResourceFn;
import dev.tachyonmcp.api.server.features.resources.ResourceRequest;
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor;
import dev.tachyonmcp.api.server.features.resources.Resources;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolFn;
import dev.tachyonmcp.api.server.features.tools.ToolRequest;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.api.server.features.tools.Tools;
import dev.tachyonmcp.core.server.json.JacksonPayloadSerde;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

@ExtendWith(MockitoExtension.class)
class SpringAiAnnotationProviderTest {

    @SuppressWarnings("unused")
    static class Fixture {

        @McpTool(
                name = "greet",
                title = "Greeter",
                description = "Greets someone",
                annotations = @McpTool.McpAnnotations(title = "Greeter hint", readOnlyHint = true))
        String greet(@McpToolParam(description = "target name") String who) {
            return "Hello, " + who + "!";
        }

        @McpTool
        int implicit(int n) {
            return n * 2;
        }

        @McpTool(name = "opt")
        String optionalArg(String mandatory, @McpToolParam(required = false) String maybe) {
            return mandatory + "/" + maybe;
        }

        @McpResource(uri = "test://config", name = "cfg", description = "app config")
        String config() {
            return "{}";
        }

        @McpResource(uri = "test://item/{id}", name = "item")
        String item(String id) {
            return "item-" + id;
        }

        @McpPrompt(name = "story", title = "Storyteller", description = "Tells a story")
        String story(String topic) {
            return "A story about " + topic;
        }
    }

    @Mock
    AnnotationRegistrationContext context;

    Tools tools;
    Resources resources;
    Prompts prompts;

    ArgumentCaptor<ToolDescriptor> toolDescriptors;
    ArgumentCaptor<ToolFn> toolFns;
    ArgumentCaptor<ResourceDescriptor> resourceDescriptors;
    ArgumentCaptor<ResourceFn> resourceFns;

    @BeforeEach
    void setUp() {
        tools = mock(Tools.class);
        resources = mock(Resources.class);
        prompts = mock(Prompts.class);
        org.mockito.Mockito.when(context.tools()).thenReturn(tools);
        org.mockito.Mockito.when(context.resources()).thenReturn(resources);
        org.mockito.Mockito.when(context.prompts()).thenReturn(prompts);
        var serde = new JacksonPayloadSerde();
        org.mockito.Mockito.when(context.payloadSerializer()).thenReturn(serde);
        org.mockito.Mockito.when(context.payloadDeserializer()).thenReturn(serde);

        new SpringAiAnnotationProvider().register(new Fixture(), context);

        toolDescriptors = ArgumentCaptor.forClass(ToolDescriptor.class);
        toolFns = ArgumentCaptor.forClass(ToolFn.class);
        verify(tools, times(3)).register(toolDescriptors.capture(), toolFns.capture());

        resourceDescriptors = ArgumentCaptor.forClass(ResourceDescriptor.class);
        resourceFns = ArgumentCaptor.forClass(ResourceFn.class);
        verify(resources).register(resourceDescriptors.capture(), resourceFns.capture());
    }

    private ToolDescriptor tool(String name) {
        return toolDescriptors.getAllValues().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tool not registered: " + name));
    }

    private ToolFn fn(String name) {
        for (int i = 0; i < toolDescriptors.getAllValues().size(); i++) {
            if (toolDescriptors.getAllValues().get(i).name().equals(name)) {
                return toolFns.getAllValues().get(i);
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
    void registersToolWithMetadataHintsAndSchema() {
        ToolDescriptor t = tool("greet");
        assertThat(t.description()).isEqualTo("Greets someone");
        assertThat(t.title()).isEqualTo("Greeter");
        assertThat(t.annotations()).isNotNull();
        assertThat(t.annotations().readOnlyHint()).isTrue();
        assertThat(t.annotations().title()).isEqualTo("Greeter hint");

        String json = t.inputSchema().json();
        assertThat(json).contains("\"who\"");
        assertThat(json).contains("target name");
        assertThat(json).contains("\"required\":[\"who\"]");
    }

    @Test
    void toolNameDerivedFromMethodAndIntegerTypeMapped() {
        ToolDescriptor t = tool("implicit");
        assertThat(t.title()).isNull();
        assertThat(t.description()).isNull();
        assertThat(t.inputSchema().json()).contains("\"n\"");
        assertThat(t.inputSchema().json()).contains("\"integer\"");
    }

    @Test
    void optionalParamsExcludedFromRequired() {
        String json = tool("opt").inputSchema().json();
        assertThat(json).contains("\"mandatory\"");
        assertThat(json).contains("\"maybe\"");
        assertThat(json).contains("\"required\":[\"mandatory\"]");
    }

    @Test
    void toolInvocationPassesArgsAndConvertsResult() throws Exception {
        ToolResult result = fn("greet").apply(mock(InteractionContext.class), request(Map.of("who", "Ada")));
        assertThat(textOf(result)).isEqualTo("Hello, Ada!");

        ToolResult opt =
                fn("opt").apply(mock(InteractionContext.class), request(Map.of("mandatory", "a", "maybe", "b")));
        assertThat(textOf(opt)).isEqualTo("a/b");
    }

    @Test
    void staticResourceRegisteredWithDefaults() throws Exception {
        ResourceDescriptor r = resourceDescriptors.getValue();
        assertThat(r.uri()).isEqualTo("test://config");
        assertThat(r.name()).isEqualTo("cfg");
        assertThat(r.description()).isEqualTo("app config");
        assertThat(r.mimeType()).isEqualTo("text/plain");

        var contents = resourceFns
                .getValue()
                .apply(
                        mock(InteractionContext.class),
                        ResourceRequest.builder().uri("test://config").build());
        assertThat(contents).isInstanceOf(dev.tachyonmcp.api.server.domain.TextResourceContents.class);
        assertThat(((dev.tachyonmcp.api.server.domain.TextResourceContents) contents).text())
                .isEqualTo("{}");
    }

    @Test
    void templatedUriRegistersResourceTemplateAndResolvesParams() throws Exception {
        ArgumentCaptor<ResourceTemplateDescriptor> d = ArgumentCaptor.forClass(ResourceTemplateDescriptor.class);
        verify(resources).registerTemplate(d.capture(), any(ResourceFn.class));
        assertThat(d.getValue().uriTemplate()).isEqualTo("test://item/{id}");

        ArgumentCaptor<ResourceFn> f = ArgumentCaptor.forClass(ResourceFn.class);
        verify(resources).registerTemplate(d.capture(), f.capture());
        var contents = f.getValue()
                .apply(
                        mock(InteractionContext.class),
                        ResourceRequest.builder()
                                .uri("test://item/42")
                                .uriTemplate("test://item/{id}")
                                .params(Map.of("id", new UriTemplateValue.Scalar("42")))
                                .build());
        assertThat(((dev.tachyonmcp.api.server.domain.TextResourceContents) contents).text())
                .isEqualTo("item-42");
    }

    @Test
    void promptRegisteredWithArgumentsAndReturnsUserMessage() throws Exception {
        ArgumentCaptor<PromptDescriptor> d = ArgumentCaptor.forClass(PromptDescriptor.class);
        ArgumentCaptor<PromptFn> f = ArgumentCaptor.forClass(PromptFn.class);
        verify(prompts).register(d.capture(), f.capture());

        PromptDescriptor p = d.getValue();
        assertThat(p.name()).isEqualTo("story");
        assertThat(p.title()).isEqualTo("Storyteller");
        assertThat(p.arguments()).hasSize(1);
        assertThat(p.arguments().getFirst().name()).isEqualTo("topic");

        PromptResult result = f.getValue()
                .apply(
                        mock(InteractionContext.class),
                        new PromptRequest(Args.of(Map.of("topic", "cats")), null, null, null));
        assertThat(result).isInstanceOf(PromptResult.Messages.class);
        TextContent content = (TextContent)
                ((PromptResult.Messages) result).messages().getFirst().content();
        assertThat(content.text()).isEqualTo("A story about cats");
    }
}
