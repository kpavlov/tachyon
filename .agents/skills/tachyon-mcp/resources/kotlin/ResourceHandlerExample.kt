// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.skill

import dev.tachyonmcp.server.domain.Annotations
import dev.tachyonmcp.server.domain.Icon
import dev.tachyonmcp.server.domain.TextResourceContents
import dev.tachyonmcp.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.server.features.resources.ResourceTemplateHandler
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
        size = 1024
        icons =
            listOf(
                Icon(
                    src = "https://example.com/user.png",
                    mimeType = "image/png",
                    sizes = listOf("32x32"),
                    theme = "blue",
                ),
            )
    }

/** URI template — {param} segments captured at runtime. */
fun userProfileTemplateDescriptor(): ResourceTemplateDescriptor =
    ResourceTemplateDescriptor
        .builder()
        .name("user-profile")
        .uriTemplate("myapp://users/{userId}/profile")
        .description("User profile data")
        .mimeType("application/json")
        .build()

fun userProfileTemplateHandler(): ResourceTemplateHandler =
    ResourceTemplateHandler { _, uri, params ->
        val userId = params["userId"]
        TextResourceContents(
            uri = uri,
            mimeType = "application/json",
            text = """{"userId":"$userId"}""",
        )
    }

/** URI template — multi-segment with static prefix matching. */
fun forecastTemplateDescriptor(): ResourceTemplateDescriptor =
    ResourceTemplateDescriptor
        .builder()
        .name("forecast")
        .uriTemplate("weather://forecast/{city}")
        .description("Weather forecast for a city")
        .mimeType("application/json")
        .build()

fun forecastTemplateHandler(): ResourceTemplateHandler =
    ResourceTemplateHandler { _, uri, params ->
        TextResourceContents(
            uri = uri,
            mimeType = "application/json",
            text = """{"city":"${params["city"]}","temp":22}""",
        )
    }
