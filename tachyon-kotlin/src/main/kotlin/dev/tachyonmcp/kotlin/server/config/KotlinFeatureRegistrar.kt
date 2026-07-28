// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.server.domain.Annotations
import dev.tachyonmcp.api.server.domain.Icon
import dev.tachyonmcp.api.server.domain.PromptMessage
import dev.tachyonmcp.api.server.domain.ResourceContents
import dev.tachyonmcp.api.server.features.completions.CompletionResult
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor
import dev.tachyonmcp.api.server.features.tools.ToolResult
import dev.tachyonmcp.core.server.ServerBuilder
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import dev.tachyonmcp.kotlin.server.features.completions.promptCompletionFn
import dev.tachyonmcp.kotlin.server.features.completions.resourceCompletionFn
import dev.tachyonmcp.kotlin.server.features.prompts.promptFn
import dev.tachyonmcp.kotlin.server.features.resources.resourceFn
import dev.tachyonmcp.kotlin.server.features.resources.templateFn
import dev.tachyonmcp.kotlin.server.features.tools.toolFn

internal class KotlinFeatureRegistrar(
    private val delegate: ServerBuilder,
    private val runtime: CoroutineRuntime,
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
        delegate.withResources { resources ->
            resources.registerAsync(
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
                resourceFn(name, mimeType, runtime, block),
            )
        }
    }

    fun resource(
        descriptor: ResourceDescriptor,
        block: suspend ResourceScope.() -> ResourceContents,
    ) {
        delegate.withResources {
            it.registerAsync(descriptor, resourceFn(descriptor, runtime, block))
        }
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
        delegate.withResources { resources ->
            resources.registerTemplateAsync(
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
                templateFn(name, mimeType, runtime, block),
            )
        }
    }

    fun resourceTemplate(
        descriptor: ResourceTemplateDescriptor,
        block: suspend TemplateScope.() -> ResourceContents,
    ) {
        delegate.withResources {
            it.registerTemplateAsync(
                descriptor,
                templateFn(descriptor, runtime, block),
            )
        }
    }

    fun tool(
        name: String,
        description: String?,
        inputSchema: JsonSchema?,
        outputSchema: JsonSchema?,
        handler: suspend ToolScope.() -> ToolResult,
    ) {
        delegate.withTools { tools ->
            tools.registerAsync(
                { descriptor ->
                    descriptor
                        .name(name)
                        .description(description)
                        .inputSchema(inputSchema)
                        .outputSchema(outputSchema)
                },
                toolFn(name, runtime, handler),
            )
        }
    }

    fun tool(
        name: String,
        description: String?,
        inputSchema: String,
        outputSchema: String?,
        handler: suspend ToolScope.() -> ToolResult,
    ) {
        delegate.withTools { tools ->
            tools.registerAsync(
                { descriptor ->
                    descriptor
                        .name(name)
                        .description(description)
                        .inputSchema(inputSchema)
                        .outputSchema(outputSchema)
                },
                toolFn(name, runtime, handler),
            )
        }
    }

    fun tool(
        descriptor: ToolDescriptor,
        handler: suspend ToolScope.() -> ToolResult,
    ) {
        delegate.withTools {
            it.registerAsync(descriptor, toolFn(descriptor.name(), runtime, handler))
        }
    }

    fun prompt(
        descriptor: PromptDescriptor,
        handler: suspend PromptScope.() -> List<PromptMessage>,
    ) {
        delegate.withPrompts {
            it.registerAsync(descriptor, promptFn(descriptor, runtime, handler))
        }
    }

    fun promptCompletion(
        promptName: String,
        handler: suspend CompletionScope.() -> CompletionResult,
    ) {
        delegate.withCompletions {
            it.registerForPromptAsync(
                promptName,
                promptCompletionFn(promptName, runtime, handler),
            )
        }
    }

    fun resourceCompletion(
        uriOrTemplate: String,
        handler: suspend CompletionScope.() -> CompletionResult,
    ) {
        delegate.withCompletions {
            it.registerForResourceAsync(
                uriOrTemplate,
                resourceCompletionFn(uriOrTemplate, runtime, handler),
            )
        }
    }
}
