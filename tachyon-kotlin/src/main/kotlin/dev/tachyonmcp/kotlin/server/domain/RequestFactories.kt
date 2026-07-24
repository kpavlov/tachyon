@file:Suppress("FunctionName")
@file:JvmName("InputRequests")

// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.kotlin.server.domain

import dev.tachyonmcp.kotlin.server.json.toJacksonNode
import dev.tachyonmcp.kotlin.server.json.toJacksonNodeMap
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
): dev.tachyonmcp.server.domain.RpcMethodRequest =
    dev.tachyonmcp.server.domain.RpcMethodRequest
        .of(method, params)

/**
 * Creates an [RpcMethodRequest] using a kotlinx-serialization [JsonObject] params.
 * Requires kotlinx-serialization-json on the classpath.
 */
public fun RpcMethodRequest(
    method: String,
    params: JsonObject,
): dev.tachyonmcp.server.domain.RpcMethodRequest =
    dev.tachyonmcp.server.domain.RpcMethodRequest.of(
        method,
        params.toJacksonNode(),
    )

/**
 * Creates a [FormInputRequest] — requests user input via a form described by a JSON schema.
 *
 * @param message         prompt shown to the user
 * @param requestedSchema JSON schema describing the expected form fields
 */
public fun FormInputRequest(
    message: String,
    requestedSchema: Map<String, JsonNode>,
): dev.tachyonmcp.server.domain.FormInputRequest =
    dev.tachyonmcp.server.domain.FormInputRequest
        .of(message, requestedSchema)

/**
 * Creates a [FormInputRequest] using a kotlinx-serialization schema map.
 * Requires kotlinx-serialization-json on the classpath.
 */
@JvmName("formInputRequestWithKxSchema")
public fun FormInputRequest(
    message: String,
    requestedSchema: Map<String, JsonObject>,
): dev.tachyonmcp.server.domain.FormInputRequest =
    dev.tachyonmcp.server.domain.FormInputRequest.of(
        message,
        requestedSchema.toJacksonNodeMap(),
    )

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
): dev.tachyonmcp.server.domain.UrlInputRequest =
    dev.tachyonmcp.server.domain.UrlInputRequest
        .of(message, elicitationId, url)
