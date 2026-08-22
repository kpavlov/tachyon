// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.server.domain.PromptMessage
import dev.tachyonmcp.api.server.domain.ResourceContents
import dev.tachyonmcp.api.server.features.completions.CompletionResult
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.api.server.features.tasks.TaskSupport
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
        descriptor: ResourceDescriptor,
        block: suspend ResourceScope.() -> ResourceContents,
    ) {
        delegate.withResources {
            it.registerAsync(descriptor, resourceFn(descriptor, runtime, block))
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
        taskSupport: TaskSupport?,
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
                        .taskSupport(taskSupport)
                },
                toolFn(name, runtime, handler),
            )
        }
    }

    fun tool(
        name: String,
        description: String?,
        inputSchema: String?,
        outputSchema: String?,
        taskSupport: TaskSupport?,
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
                        .taskSupport(taskSupport)
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
