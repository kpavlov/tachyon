// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.skill

import dev.tachyonmcp.server.domain.TextResourceContents
import dev.tachyonmcp.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry

/**
 * Demonstrates static resource URI and template construction.
 */

/** Static resource descriptor — fixed URI. */
fun configDescriptor(): ResourceDescriptor =
    ResourceDescriptor.of(
        "server-config",
        "myapp://config",
        "Server configuration",
        "application/json",
    )

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
