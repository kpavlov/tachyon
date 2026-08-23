/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.annotations.mcpjava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.Args;
import dev.tachyonmcp.api.server.domain.BlobResourceContents;
import dev.tachyonmcp.api.server.domain.PromptArgument;
import dev.tachyonmcp.api.server.domain.PromptMessage;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.domain.TextResourceContents;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.resources.ResourceTemplateArg;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpJavaAnnotationProviderTest {

    @SuppressWarnings("unused")
    static class Fixture {

        @Tool(
                name = "greet",
                title = "Greeter",
                description = "Greets someone",
                annotations = @Tool.Annotations(readOnlyHint = true))
        String greet(@ToolArg(name = "who", description = "target name") String who) {
            return "Hello, " + who + "!";
        }

        @Tool
        int implicit(int n) {
            return n * 2;
        }

        @Resource(uri = "test://config", name = "cfg", description = "app config", mimeType = "application/json")
        String config() {
            return "{}";
        }

        @Resource(uri = "test://bytes", name = "raw", mimeType = "application/octet-stream")
        byte[] raw() {
            return new byte[] {1, 2, 3};
        }

        @ResourceTemplate(uriTemplate = "test://item/{id}", name = "item", description = "items by id")
        String item(@ResourceTemplateArg(name = "id") String id) {
            return "item-" + id;
        }

        @Prompt(name = "story", title = "Storyteller", description = "Tells a story")
        PromptMessage story(InteractionContext ctx, @PromptArg(name = "topic", required = false) String topic) {
            return PromptMessage.user("A story about " + topic);
        }
    }

    @Mock
    AnnotationRegistrationContext context;

    Tools tools;
    Resources resources;
    Prompts prompts;

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
        new McpJavaAnnotationProvider().register(new Fixture(), context);
    }

    private ToolDescriptor tool(String name) {
        ArgumentCaptor<ToolDescriptor> d = ArgumentCaptor.forClass(ToolDescriptor.class);
        verify(tools, org.mockito.Mockito.atLeastOnce()).register(d.capture(), any(ToolFn.class));
        return d.getAllValues().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tool not registered: " + name));
    }

    private ResourceDescriptor resource(String name) {
        ArgumentCaptor<ResourceDescriptor> d = ArgumentCaptor.forClass(ResourceDescriptor.class);
        verify(resources, org.mockito.Mockito.atLeastOnce()).register(d.capture(), any(ResourceFn.class));
        return d.getAllValues().stream()
                .filter(r -> r.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("resource not registered: " + name));
    }

    private ResourceFn resourceFn(String name) {
        ArgumentCaptor<ResourceFn> f = ArgumentCaptor.forClass(ResourceFn.class);
        ArgumentCaptor<ResourceDescriptor> d = ArgumentCaptor.forClass(ResourceDescriptor.class);
        verify(resources, org.mockito.Mockito.atLeastOnce()).register(d.capture(), f.capture());
        for (int i = 0; i < d.getAllValues().size(); i++) {
            if (d.getAllValues().get(i).name().equals(name))
                return f.getAllValues().get(i);
        }
        throw new AssertionError("resource fn not found: " + name);
    }

    private ResourceFn templateFn(String name) {
        ArgumentCaptor<ResourceTemplateDescriptor> d = ArgumentCaptor.forClass(ResourceTemplateDescriptor.class);
        ArgumentCaptor<ResourceFn> f = ArgumentCaptor.forClass(ResourceFn.class);
        verify(resources).registerTemplate(d.capture(), f.capture());
        assertThat(d.getValue().name()).isEqualTo(name);
        return f.getValue();
    }

    private ToolFn toolFn(String name) {
        ArgumentCaptor<ToolFn> f = ArgumentCaptor.forClass(ToolFn.class);
        ArgumentCaptor<ToolDescriptor> d = ArgumentCaptor.forClass(ToolDescriptor.class);
        verify(tools, org.mockito.Mockito.atLeastOnce()).register(d.capture(), f.capture());
        for (int i = 0; i < d.getAllValues().size(); i++) {
            if (d.getAllValues().get(i).name().equals(name))
                return f.getAllValues().get(i);
        }
        throw new AssertionError("tool fn not found: " + name);
    }

    private static ToolRequest toolRequest(Map<String, Object> args) {
        return ToolRequest.builder().name("any").arguments(Args.of(args)).build();
    }

    @Test
    void registersToolWithFullMetadata() {
        ToolDescriptor t = tool("greet");
        assertThat(t.description()).isEqualTo("Greets someone");
        assertThat(t.title()).isEqualTo("Greeter");
        JsonSchema schema = t.inputSchema();
        assertThat(schema).isNotNull();
        assertThat(schema.json()).contains("\"who\"");
        assertThat(schema.json()).contains("\"required\":[\"who\"]");
        assertThat(t.annotations()).isNotNull();
        assertThat(t.annotations().readOnlyHint()).isTrue();
    }

    @Test
    void toolFnInvokesMethodAndConvertsTextResult() throws Exception {
        ToolResult result = toolFn("greet").apply(mock(InteractionContext.class), toolRequest(Map.of("who", "Ada")));
        assertThat(result).isInstanceOf(ToolResult.Success.class);
        TextContent text = (TextContent) ((ToolResult.Success) result).content().getFirst();
        assertThat(text.text()).isEqualTo("Hello, Ada!");
    }

    @Test
    void toolNameDerivedFromMethodAndIntegerSchema() {
        ToolDescriptor t = tool("implicit");
        assertThat(t.title()).isNull();
        assertThat(t.inputSchema().json()).contains("\"n\"");
        assertThat(t.inputSchema().json()).contains("\"integer\"");
    }

    @Test
    void resourceRegisteredWithDescriptorAndTextContents() throws Exception {
        ResourceDescriptor r = resource("cfg");
        assertThat(r.uri()).isEqualTo("test://config");
        assertThat(r.mimeType()).isEqualTo("application/json");
        assertThat(r.description()).isEqualTo("app config");

        var contents = resourceFn("cfg")
                .apply(
                        mock(InteractionContext.class),
                        ResourceRequest.builder().uri("test://config").build());
        assertThat(contents).isInstanceOf(TextResourceContents.class);
        assertThat(((TextResourceContents) contents).text()).isEqualTo("{}");
        assertThat(contents.uri()).isEqualTo("test://config");
    }

    @Test
    void byteArrayResourceBecomesBlobContents() throws Exception {
        var contents = resourceFn("raw")
                .apply(
                        mock(InteractionContext.class),
                        ResourceRequest.builder().uri("test://bytes").build());
        assertThat(contents).isInstanceOf(BlobResourceContents.class);
        assertThat(contents.uri()).isEqualTo("test://bytes");
    }

    @Test
    void resourceTemplateRegistersTemplateAndResolvesParams() throws Exception {
        ArgumentCaptor<ResourceTemplateDescriptor> d = ArgumentCaptor.forClass(ResourceTemplateDescriptor.class);
        verify(resources).registerTemplate(d.capture(), any(ResourceFn.class));
        assertThat(d.getValue().uriTemplate()).isEqualTo("test://item/{id}");
        assertThat(d.getValue().description()).isEqualTo("items by id");

        var contents = templateFn("item")
                .apply(
                        mock(InteractionContext.class),
                        ResourceRequest.builder()
                                .uri("test://item/42")
                                .uriTemplate("test://item/{id}")
                                .params(Map.of("id", new UriTemplateValue.Scalar("42")))
                                .build());
        assertThat(((TextResourceContents) contents).text()).isEqualTo("item-42");
    }

    @Test
    void promptRegistersDescriptorAndMessages() throws Exception {
        ArgumentCaptor<PromptDescriptor> d = ArgumentCaptor.forClass(PromptDescriptor.class);
        verify(prompts).register(d.capture(), any(PromptFn.class));
        PromptDescriptor p = d.getValue();

        assertThat(p.name()).isEqualTo("story");
        assertThat(p.title()).isEqualTo("Storyteller");
        assertThat(p.description()).isEqualTo("Tells a story");
        List<PromptArgument> args = p.arguments();
        assertThat(args).hasSize(1);
        assertThat(args.getFirst().name()).isEqualTo("topic");
        assertThat(args.getFirst().required()).isFalse();

        ArgumentCaptor<PromptFn> f = ArgumentCaptor.forClass(PromptFn.class);
        verify(prompts).register(d.capture(), f.capture());
        PromptResult result = f.getValue()
                .apply(
                        mock(InteractionContext.class),
                        new PromptRequest(Args.of(Map.of("topic", "cats")), null, null, null));
        assertThat(result).isInstanceOf(PromptResult.Messages.class);
        List<PromptMessage> messages = ((PromptResult.Messages) result).messages();
        assertThat(messages).hasSize(1);
        TextContent content = (TextContent) messages.getFirst().content();
        assertThat(content.text()).isEqualTo("A story about cats");
    }
}
