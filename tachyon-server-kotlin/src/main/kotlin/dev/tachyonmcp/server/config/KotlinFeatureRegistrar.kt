// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.ServerBuilder
import dev.tachyonmcp.server.domain.Annotations
import dev.tachyonmcp.server.domain.Icon
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.server.features.prompts.promptHandler
import dev.tachyonmcp.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.server.features.resources.resourceHandler
import dev.tachyonmcp.server.features.resources.templateHandler
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.features.tools.toolFn
import tools.jackson.databind.JsonNode

internal class KotlinFeatureRegistrar(
    private val delegate: ServerBuilder,
) {
    fun resource(
        name: String,
        uri: String,
        description: String?,
        mimeType: String?,
        title: String?,
        annotations: Annotations?,
        size: Long?,
        icons: List<Icon>?,
        block: suspend ResourceScope.() -> ResourceContents,
    ) {
        delegate.resource(
            { descriptor ->
                descriptor
                    .name(name)
                    .uri(uri)
                    .description(description)
                    .mimeType(mimeType)
                    .title(title)
                    .annotations(annotations)
                    .size(size)
                    .icons(icons)
            },
            resourceHandler(name, mimeType, block),
        )
    }

    fun resource(
        descriptor: ResourceDescriptor,
        block: suspend ResourceScope.() -> ResourceContents,
    ) {
        delegate.resource(descriptor, resourceHandler(descriptor, block))
    }

    fun resourceTemplate(
        name: String,
        uriTemplate: String,
        description: String?,
        mimeType: String?,
        title: String?,
        annotations: Annotations?,
        icons: List<Icon>?,
        block: suspend TemplateScope.() -> ResourceContents,
    ) {
        delegate.resourceTemplate(
            { descriptor ->
                descriptor
                    .name(name)
                    .uriTemplate(uriTemplate)
                    .description(description)
                    .mimeType(mimeType)
                    .title(title)
                    .annotations(annotations)
                    .icons(icons)
            },
            templateHandler(name, mimeType, block),
        )
    }

    fun resourceTemplate(
        descriptor: ResourceTemplateDescriptor,
        block: suspend TemplateScope.() -> ResourceContents,
    ) {
        delegate.resourceTemplate(descriptor, templateHandler(descriptor, block))
    }

    fun tool(
        name: String,
        description: String?,
        inputSchema: JsonNode?,
        outputSchema: JsonNode?,
        handler: suspend ToolScope.() -> ToolResult,
    ) {
        delegate.tool(
            { descriptor ->
                descriptor
                    .name(name)
                    .description(description)
                    .inputSchema(inputSchema)
                    .outputSchema(outputSchema)
            },
            toolFn(name, handler),
        )
    }

    fun tool(
        name: String,
        description: String?,
        inputSchema: String,
        outputSchema: String?,
        handler: suspend ToolScope.() -> ToolResult,
    ) {
        delegate.tool(
            { descriptor ->
                descriptor
                    .name(name)
                    .description(description)
                    .inputSchema(inputSchema)
                    .outputSchema(outputSchema)
            },
            toolFn(name, handler),
        )
    }

    fun tool(
        descriptor: ToolDescriptor,
        handler: suspend ToolScope.() -> ToolResult,
    ) {
        delegate.tool(descriptor, toolFn(descriptor.name(), handler))
    }

    fun prompt(
        descriptor: PromptDescriptor,
        handler: suspend PromptScope.() -> List<PromptMessage>,
    ) {
        delegate.prompt(descriptor, promptHandler(descriptor, handler))
    }
}
