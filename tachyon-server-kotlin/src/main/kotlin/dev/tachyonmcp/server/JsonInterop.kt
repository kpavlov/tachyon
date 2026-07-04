// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

@PublishedApi
internal val sharedMapper: ObjectMapper = JsonMapper.builder().build()

@PublishedApi
internal fun String.toJsonNode(): JsonNode = sharedMapper.readTree(this)
