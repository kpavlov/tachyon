/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2026_07_28.codecs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.server.domain.Annotations;
import dev.tachyonmcp.api.server.domain.Icon;
import dev.tachyonmcp.api.server.domain.PromptArgument;
import dev.tachyonmcp.api.server.domain.PromptMessage;
import dev.tachyonmcp.api.server.domain.Role;
import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.domain.ToolAnnotations;
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.CallToolResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.CompleteResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.GetPromptResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ListPromptsResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ListResourceTemplatesResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ListResourcesResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ListToolsResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class McpResponseMapperTest {

    private final McpResponseMapper mapper = new McpResponseMapper();

    @Test
    void resourceNotFoundUsesInvalidParams() {
        var error = mapper.error(new ServerError(ServerError.Kind.RESOURCE_NOT_FOUND, "Resource not found"));

        assertThat(error.code()).isEqualTo(-32602);
    }

    @Test
    void unsupportedProtocolVersionUsesTheModernCode() {
        var error = mapper.error(
                new ServerError(ServerError.Kind.UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version"));

        assertThat(error.code()).isEqualTo(-32022);
    }

    @Test
    void completeResultsUseTheModernDiscriminator() {
        var result = (CompleteResult) mapper.completeResult(List.of("one"), 1.0, false);

        assertThat(result.resultType()).isEqualTo("complete");
        assertThat(result.completion().values()).containsExactly("one");
    }

    @Test
    void toolResultsPreserveArbitraryStructuredValuesAndMetadata() {
        var result = (CallToolResult)
                mapper.callToolResult(ToolResult.of(JsonDocument.of("[1,true]")).withMeta("trace", Map.of("id", 7)));

        assertThat(result.resultType()).isEqualTo("complete");
        assertThat(result.structuredContent())
                .isEqualTo(JsonNodeFactory.instance.arrayNode().add(1).add(true));
        assertThat(result._meta())
                .containsEntry("trace", JsonNodeFactory.instance.objectNode().put("id", 7));
        assertThat(result.content()).hasSize(1);
    }

    @Test
    void promptResultsUseModernContentAndDiscriminator() {
        var result = (GetPromptResult)
                mapper.getPromptResult("Greeting", List.of(PromptMessage.of(Role.USER, TextContent.of("Hello"))));

        assertThat(result.resultType()).isEqualTo("complete");
        assertThat(result.description()).isEqualTo("Greeting");
        assertThat(result.messages()).hasSize(1);
    }

    @Test
    void listResultsPreserveDescriptorAnnotationsIconsAndArguments() {
        var icon = Icon.of("https://example.test/icon.png", "image/png", List.of("16x16"), "light");
        var annotations = Annotations.of(List.of(Role.USER), 0.5, "2026-07-29T00:00:00Z");
        var toolAnnotations = ToolAnnotations.of("Safe", true, false, true, false);
        var tool = ToolDescriptor.builder()
                .name("weather")
                .annotations(toolAnnotations)
                .icons(icon)
                .build();
        var resource = ResourceDescriptor.builder()
                .name("forecast")
                .uri("memory://forecast")
                .annotations(annotations)
                .icons(icon)
                .build();
        var template = ResourceTemplateDescriptor.builder()
                .name("city")
                .uriTemplate("memory://forecast/{city}")
                .annotations(annotations)
                .icons(icon)
                .build();
        var prompt = PromptDescriptor.builder()
                .name("greet")
                .addArguments(PromptArgument.of("name", "Name", "Who to greet", true))
                .icons(List.of(icon))
                .build();

        var toolResult = (ListToolsResult) mapper.listToolsResult(List.of(tool), null);
        var resourceResult = (ListResourcesResult) mapper.listResourcesResult(List.of(resource), null);
        var templateResult = (ListResourceTemplatesResult) mapper.listResourceTemplatesResult(List.of(template), null);
        var promptResult = (ListPromptsResult) mapper.listPromptsResult(List.of(prompt), null);

        assertThat(toolResult.tools().getFirst().annotations().readOnlyHint()).isTrue();
        assertThat(toolResult.tools().getFirst().icons()).hasSize(1);
        assertThat(resourceResult.resources().getFirst().annotations().priority())
                .isEqualTo(0.5);
        assertThat(resourceResult.resources().getFirst().icons()).hasSize(1);
        assertThat(templateResult.resourceTemplates().getFirst().annotations().priority())
                .isEqualTo(0.5);
        assertThat(templateResult.resourceTemplates().getFirst().icons()).hasSize(1);
        assertThat(promptResult.prompts().getFirst().arguments().getFirst().name())
                .isEqualTo("name");
        assertThat(promptResult.prompts().getFirst().icons()).hasSize(1);
    }
}
