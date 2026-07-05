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
    ResourceDescriptor.of(
        "server-config",
        "myapp://config",
        "Server configuration",
        "application/json",
    )

/** DSL builder — all properties shown. */
fun richDescriptor(): ResourceDescriptor =
    resourceDescriptor(name = "user-profile", uri = "myapp://users/me") {
        description = "Current user profile"
        mimeType = "application/json"
        title = "My Profile"
        annotations = Annotations.of(null, 0.5, "2026-01-01T00:00:00Z")
        size = 1024.0
        icons = listOf(
            Icon.of("https://example.com/user.png", "image/png", listOf("32x32"), "blue"),
        )
    }

/** URI template — {param} segments captured at runtime. */
fun userProfileTemplate(): ResourceTemplateEntry =
    ResourceTemplateEntry.of(
        "user-profile",
        "myapp://users/{userId}/profile",
        "User profile data",
        "application/json",
    ) { _, uri, params ->
        val userId = params["userId"]
        TextResourceContents.of(uri, "application/json", """{"userId":"$userId"}""")
    }

/** URI template — multi-segment with static prefix matching. */
fun forecastTemplate(): ResourceTemplateEntry =
    ResourceTemplateEntry.of(
        "forecast",
        "weather://forecast/{city}",
        "Weather forecast for a city",
        "application/json",
    ) { _, uri, params ->
        TextResourceContents.of(
            uri,
            "application/json",
            """{"city":"${params["city"]}","temp":22}""",
        )
    }
