// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.skill

import dev.tachyonmcp.server.domain.Annotations
import dev.tachyonmcp.server.domain.Icon
import dev.tachyonmcp.server.domain.TextResourceContents
import dev.tachyonmcp.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry
import dev.tachyonmcp.server.features.resources.resourceDescriptor

/** Static resource — plain factory. */
fun configDescriptor(): ResourceDescriptor =
    ResourceDescriptor(
        name = "server-config",
        uri = "myapp://config",
        description = "Server configuration",
        mimeType = "application/json",
    )

/** DSL builder — all properties shown. */
fun richDescriptor(): ResourceDescriptor =
    resourceDescriptor(name = "user-profile", uri = "myapp://users/me") {
        description = "Current user profile"
        mimeType = "application/json"
        title = "My Profile"
        annotations = Annotations(priority = 0.5, lastModified = "2026-01-01T00:00:00Z")
        size = 1024.0
        icons = listOf(
            Icon(src = "https://example.com/user.png", mimeType = "image/png", sizes = listOf("32x32"), theme = "blue"),
        )
    }

/** URI template — {param} segments captured at runtime. */
fun userProfileTemplate(): ResourceTemplateEntry =
    ResourceTemplateEntry(
        name = "user-profile",
        uriTemplate = "myapp://users/{userId}/profile",
        description = "User profile data",
        mimeType = "application/json",
    ) { _, uri, params ->
        val userId = params["userId"]
        TextResourceContents(uri = uri, text = """{"userId":"$userId"}""", mimeType = "application/json")
    }

/** URI template — multi-segment with static prefix matching. */
fun forecastTemplate(): ResourceTemplateEntry =
    ResourceTemplateEntry(
        name = "forecast",
        uriTemplate = "weather://forecast/{city}",
        description = "Weather forecast for a city",
        mimeType = "application/json",
    ) { _, uri, params ->
        TextResourceContents(
            uri = uri,
            text = """{"city":"${params["city"]}","temp":22}""",
            mimeType = "application/json",
        )
    }
