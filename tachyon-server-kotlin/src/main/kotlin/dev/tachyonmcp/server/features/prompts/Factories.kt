@file:Suppress("FunctionName")
@file:JvmName("PromptDescriptors")

// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.features.prompts

import dev.tachyonmcp.server.domain.Icon
import dev.tachyonmcp.server.domain.PromptArgument
import dev.tachyonmcp.server.json.toJacksonNode
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode

/**
 * Creates a [PromptDescriptor] describing a prompt template.
 *
 * @param name        prompt name (matched against prompt calls)
 * @param description description of the prompt; null to omit
 * @param title       human-readable title; null to omit
 * @param arguments   list of accepted arguments; null for no arguments
 * @param inputSchema JSON schema for the arguments; null to skip schema validation
 * @param icons       list of associated icons; null to omit
 */
public fun PromptDescriptor(
    name: String,
    description: String? = null,
    title: String? = null,
    arguments: List<PromptArgument>? = null,
    inputSchema: JsonNode? = null,
    icons: List<Icon>? = null,
): PromptDescriptor = PromptDescriptor.of(name, description, title, arguments, inputSchema, icons)

/**
 * Creates a [PromptDescriptor] using a [JsonObject] input schema.
 * Requires kotlinx-serialization-json on the classpath.
 */
public fun PromptDescriptor(
    name: String,
    description: String? = null,
    title: String? = null,
    arguments: List<PromptArgument>? = null,
    inputSchema: JsonObject?,
    icons: List<Icon>? = null,
): PromptDescriptor =
    PromptDescriptor.of(
        name,
        description,
        title,
        arguments,
        inputSchema?.toJacksonNode(),
        icons,
    )
