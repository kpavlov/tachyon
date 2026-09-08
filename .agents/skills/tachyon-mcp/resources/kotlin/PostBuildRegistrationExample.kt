// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.skill

import dev.tachyonmcp.kotlin.server.TachyonServer
import dev.tachyonmcp.kotlin.server.buildServer
import dev.tachyonmcp.kotlin.server.features.completions.CompletionResult

/**
 * Post-build registration beyond tools (see `buildWithPostRegistration` in
 * ToolHandlerExample.kt for `registerTool`): resources, resource templates, prompts, and
 * completions all get the same suspend `register*` sugar on an already-built [TachyonServer] —
 * the Kotlin equivalents of the Java runtime server's `server.resources().register(...)`,
 * `server.prompts().register(...)`, and `server.completions().registerForPrompt/Resource(...)`.
 */
fun buildWithFullPostRegistration(): TachyonServer {
    val server = buildServer { }

    server.registerResource(name = "config", uri = "myapp://config") {
        TextResourceContents { text = """{"mode":"demo"}""" }
    }

    server.registerResourceTemplate(name = "user-profile", uriTemplate = "myapp://users/{userId}/profile") {
        TextResourceContents { text = """{"userId":"${param("userId")}"}""" }
    }

    server.registerPrompt(name = "rewrite-forecast", description = "Rewrites a forecast in a chosen style") {
        content { text("Rewrite this forecast in a chosen style.") }
    }

    server.registerPromptCompletion("rewrite-forecast") {
        CompletionResult { values = listOf("plain", "concise", "pirate").filter { it.startsWith(argumentValue) } }
    }

    server.registerResourceCompletion("myapp://users/{userId}/profile") {
        CompletionResult { values = listOf("alice", "bob").filter { it.startsWith(argumentValue) } }
    }

    return server
}
