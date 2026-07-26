/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import dev.tachyonmcp.kotlin.server.features.completions.promptCompletionHandler
import dev.tachyonmcp.kotlin.server.features.completions.resourceCompletionHandler
import dev.tachyonmcp.kotlin.server.features.prompts.promptHandler
import dev.tachyonmcp.kotlin.server.features.resources.resourceHandler
import dev.tachyonmcp.kotlin.server.features.resources.templateHandler
import dev.tachyonmcp.kotlin.server.features.tools.toolFn
import dev.tachyonmcp.server.ServerBuilder
import dev.tachyonmcp.server.domain.Annotations
import dev.tachyonmcp.server.domain.Icon
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.completions.CompletionResult
import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.json.JsonSchema

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
                resourceHandler(name, mimeType, runtime, block),
            )
        }
    }

    fun resource(
        descriptor: ResourceDescriptor,
        block: suspend ResourceScope.() -> ResourceContents,
    ) {
        delegate.withResources {
            it.registerAsync(descriptor, resourceHandler(descriptor, runtime, block))
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
                templateHandler(name, mimeType, runtime, block),
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
                templateHandler(descriptor, runtime, block),
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
            it.registerAsync(descriptor, promptHandler(descriptor, runtime, handler))
        }
    }

    fun promptCompletion(
        promptName: String,
        handler: suspend CompletionScope.() -> CompletionResult,
    ) {
        delegate.withCompletions {
            it.registerForPromptAsync(
                promptName,
                promptCompletionHandler(promptName, runtime, handler),
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
                resourceCompletionHandler(uriOrTemplate, runtime, handler),
            )
        }
    }
}
