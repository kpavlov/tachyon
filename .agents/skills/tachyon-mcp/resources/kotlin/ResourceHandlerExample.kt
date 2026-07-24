// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.skill

import dev.tachyonmcp.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.kotlin.domain.Annotations
import dev.tachyonmcp.server.kotlin.domain.Icon
import dev.tachyonmcp.server.kotlin.domain.TextResourceContents
import dev.tachyonmcp.server.kotlin.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.features.resources.ResourceHandler
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.server.kotlin.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.server.kotlin.features.resources.resourceDescriptor

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
                    theme = "dark",
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

fun userProfileTemplateHandler(): ResourceHandler =
    ResourceHandler { _, request ->
        val userId = request.params()["userId"]?.scalarValue()
        TextResourceContents(
            uri = request.uri(),
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

fun forecastTemplateHandler(): ResourceHandler =
    ResourceHandler { _, request ->
        val city = request.params()["city"]?.scalarValue()
        TextResourceContents(
            uri = request.uri(),
            mimeType = "application/json",
            text = """{"city":"$city","temp":22}""",
        )
    }
