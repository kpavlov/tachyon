@file:Suppress("FunctionName")
@file:JvmName("InputRequests")

// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.domain

import dev.tachyonmcp.server.json.toJacksonNode
import dev.tachyonmcp.server.json.toJacksonNodeMap
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode

/**
 * Creates an [RpcMethodRequest] — requests user input by invoking an RPC method.
 *
 * @param method the RPC method to invoke
 * @param params optional parameters for the method; null to omit
 */
public fun RpcMethodRequest(
    method: String,
    params: JsonNode? = null,
): RpcMethodRequest = RpcMethodRequest.of(method, params)

/**
 * Creates an [RpcMethodRequest] using a kotlinx-serialization [JsonObject] params.
 * Requires kotlinx-serialization-json on the classpath.
 */
public fun RpcMethodRequest(
    method: String,
    params: JsonObject?,
): RpcMethodRequest = RpcMethodRequest.of(method, params?.toJacksonNode())

/**
 * Creates a [FormInputRequest] — requests user input via a form described by a JSON schema.
 *
 * @param message         prompt shown to the user
 * @param requestedSchema JSON schema describing the expected form fields
 */
public fun FormInputRequest(
    message: String,
    requestedSchema: Map<String, JsonNode>,
): FormInputRequest = FormInputRequest.of(message, requestedSchema)

/**
 * Creates a [FormInputRequest] using a kotlinx-serialization schema map.
 * Requires kotlinx-serialization-json on the classpath.
 */
@JvmName("formInputRequestWithKxSchema")
public fun FormInputRequest(
    message: String,
    requestedSchema: Map<String, JsonObject>,
): FormInputRequest = FormInputRequest.of(message, requestedSchema.toJacksonNodeMap())

/**
 * Creates a [UrlInputRequest] — requests user input by opening a URL.
 *
 * @param message       prompt shown to the user
 * @param elicitationId identifier linking the response to the elicitation context
 * @param url           URL to open for user input
 */
public fun UrlInputRequest(
    message: String,
    elicitationId: String,
    url: String,
): UrlInputRequest = DefaultUrlInputRequest.of(message, elicitationId, url)
