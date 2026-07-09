# Migrating from the Kotlin MCP SDK to Tachyon

You have an MCP server on `io.modelcontextprotocol:kotlin-sdk` and its hand-rolled Ktor
transport. Tachyon owns the transport, sessions, and validation for you — but the port
touches every layer, and a few swaps are silent behavior changes if you copy mechanically.

This guide is the map: the type-for-type translation, plus the three regressions that don't
fail to compile and won't show up until a client hits them.

## The translation at a glance

| Concern | Kotlin MCP SDK | Tachyon (Kotlin) |
|---|---|---|
| Package root | `io.modelcontextprotocol.kotlin.sdk.*` | `dev.tachyonmcp.server.*` |
| JSON node type | kotlinx `JsonElement` / `McpJson` | **Jackson 3** `tools.jackson.databind.JsonNode` |
| Server + HTTP | `Server(...)` + you wire Ktor, sessions, reaper | `TachyonServer(port) { ... }` — transport & sessions built in |
| Identity | `Implementation(name, version, title, websiteUrl, icons)` | `info { name; version; title; websiteUrl; icons.add(...) }` |
| Icon | `sdk.types.Icon` | `dev.tachyonmcp.server.domain.Icon(src, mimeType, sizes, theme)` |
| Register a tool | `server.addTool(name, desc, inputSchema: ToolSchema, outputSchema, handler)` | `server.registerTool(name, desc, inputSchema, outputSchema) { }` |
| Handler receiver | `suspend ClientConnection.(CallToolRequest) -> CallToolResult` | `suspend ToolScope.() -> ToolResult` |
| Read arguments | `request.arguments: JsonObject` | `args: ToolArgs` (typed accessors) |
| Tool result | `CallToolResult(content = listOf(TextContent(x)), isError)` | `ToolResult.text(x)` / `.error(x)` / `success(v)` |
| Schema type | `ToolSchema(properties, required)` | `String` **or** Jackson `JsonNode` **or** kotlinx `JsonObject` |
| Logging | `server.sendLoggingMessage(LoggingMessageNotification(...))` | `server.log(session, level, logger, data: JsonNode)` |
| Log level | `LoggingLevel.Info/Debug/Error` (PascalCase) | `LoggingLevel.INFO/DEBUG/ERROR` (UPPER) |
| Resources | `server.addResources(...)` per file | `server.resources().add(descriptor, handler)` / `.addTemplate(...)` |
| Elicitation | `Server.userFeedbackElicitor(sessionId)` | `InteractionContext.userFeedbackElicitor()` (via `ToolScope.ctx`) |
| JSON config | `McpJson` | `KxSerializationSerde(json = yourJson)` in `json { serde = ... }` |

## 1. Dependencies

Swap the SDK for Tachyon's Kotlin module. Keep `kotlinx-serialization-json` — it's an
*optional* Tachyon dependency, and you need it for `JsonObject` schemas and
`args.decode<T>()`. Jackson 3 (`tools.jackson.*`) arrives transitively.

```toml
# out: io.modelcontextprotocol:kotlin-sdk
tachyon-server-kotlin = { module = "dev.tachyonmcp:tachyon-server-kotlin", version.ref = "tachyon" }
```

## 2. Server and transport — where most of the code disappears

The SDK hands you a `Server` and leaves the streamable-HTTP transport, session lifecycle, and
reaping to you. `TachyonServer { }` is the whole thing:

```kotlin
val handle = TachyonServer(port = mcpPort) {
    info {
        name = "example-server"
        title = "Example MCP"
        version = appVersion
        websiteUrl = "https://example.com/docs"
        icons.add(logoIcon)
    }
    capabilities {
        tools(listChanged = true)
        resources(subscribe = false, listChanged = true)
        logging = true
    }
    json { serde = KxSerializationSerde(json = yourJson) }   // reuse your kotlinx Json
    network {
        host = "127.0.0.1"
        allowNullOrigin = true
        allowedOrigins.add("*")
    }
    session { enabled = true; sessionTtl = 10.minutes }
}

handle.server()   // dev.tachyonmcp.server.Server — register tools/resources here
handle.port()
handle.close()    // wire to your app's stop hook
```

The identity block is easy to under-fill. `info { }` supports `title`, `websiteUrl`, and
`icons` — port all of them, not just `name`/`version`:

```kotlin
import dev.tachyonmcp.server.domain.Icon

val logoIcon = javaClass.getResourceAsStream("/logo-32x32.png")!!.use { s ->
    Icon(
        src = "data:image/png;base64,${Base64.Mime.encode(s.readAllBytes())}",
        sizes = listOf("32x32"),
        mimeType = "image/png",
    )
}
```

> The SDK's `ServerOptions.timeout` has no equivalent and needs none — it never extended MCP
> host per-call limits anyway. Long-running tools still need `wait=false` polling or a
> log-file side channel.

## 3. Tools

`addTool` becomes `registerTool` (import from `dev.tachyonmcp.server.features.tools`). The
handler receiver changes from `ClientConnection.(CallToolRequest)` to `ToolScope`, and it
returns a `ToolResult`. `registerTool` returns the `Server`, so registrations chain.

```kotlin
import dev.tachyonmcp.server.features.tools.registerTool

server.registerTool(
    name = "search",
    description = "Search the index.",
    inputSchema = """{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}""",
    outputSchema = SearchResult::class.jsonSchemaString,   // nullable
) { // this: ToolScope
    val query = args.string("query")
    ToolResult.text(json.encodeToString(SearchResult.serializer(), run(query)))
}
```

A thin wrapper that assembles the `{"type":"object","properties":…,"required":…}` envelope and
warns when a description exceeds 2048 chars (clients truncate there) pays off across many tools.

## 4. Arguments — `ToolArgs`

`ToolScope.args` replaces digging through a `JsonObject`:

| Old                                                 | New                                                                                  |
|-----------------------------------------------------|--------------------------------------------------------------------------------------|
| `arguments["k"]?.jsonPrimitive?.content` (required) | `args.string("k")` — throws if missing                                               |
| same, optional                                      | `args.stringOrNull("k")`                                                             |
| with a default                                      | `args.stringOr("k", "d")`                                                            |
| `…?.int` etc.                                       | `args.intValue/boolValue/doubleValue` (+ `…OrNull`, `…(k, default)`)                 |
| raw element                                         | `args.raw("k"): JsonNode?` → `.asString()`                                           |
| membership                                          | `args.has("k")`                                                                      |
| decode whole object                                 | `args.decode<MyArgs>()` (via configured serde; kotlinx default ignores unknown keys) |

## 5. Results — `ToolResult`

```kotlin
CallToolResult(content = listOf(TextContent(json)))               → ToolResult.text(json)
CallToolResult(content = listOf(TextContent(json)), isError=true) → ToolResult.error(json)
ToolResult.raw(structuredJson, textFallback)                    // pre-serialized JSON, skips serde
success(EchoReply(...))                                         // configured serde → structuredContent
ToolResult.of(pojo)                                             // Jackson: POJO → structuredContent
```

## 6. Schemas — three shapes, one gotcha

`inputSchema`/`outputSchema` accept a raw **String**, a Jackson **`JsonNode`**, or a kotlinx
**`JsonObject`** on every overload. The `outputSchema` string overload arrived in
**1.0.0-beta.8** — if you wrote a project-side shim for it against an earlier beta, delete the
shim now.

- **Jackson 3**: `tools.jackson.databind.JsonNode`, *not* `com.fasterxml.jackson…`. Convert a
  kotlinx `JsonObject` once with `ObjectMapper().readTree(obj.toString())`.
- The root must declare `"type":"object"` — validated at *registration*, so a typo in a raw
  string fails at boot, not at call time.
- Generating schema strings from `@Serializable` types (e.g. `KClass.jsonSchemaString` from a
  schema-generator library) drops straight into the String overload and kills the
  data-class-vs-schema drift the SDK's `ToolSchema` also invited.

## 7. Logging

The `LoggingMessageNotification` wrapper is gone; call `server.log(...)` with a `Session`.
Note the enum casing and that data is a **Jackson** node.

```kotlin
// before: server.sendLoggingMessage(LoggingMessageNotification(...LoggingLevel.Info, data = McpJson...))
server.log(session, LoggingLevel.INFO, loggerName, entry.toJacksonNode())

fun Entry.toJacksonNode(): JsonNode = ObjectMapper().readTree(Json.encodeToString(serializer(), this))
```

`Info/Debug/Error` → `INFO/DEBUG/ERROR`. Logging is session-scoped — get the `Session` from the
interaction/subscription context, not a global send.

## 8. Resources

```kotlin
// concrete, per-item resource (shows up in resources/list)
server.resources().add(
    ResourceDescriptor(name = uri, uri = uri, title = label, description = desc, mimeType = "text/markdown"),
) { _, request ->
    TextResourceContents.of(request.uri(), "text/markdown", read(request.uri()) ?: error("not found"))
}

// URI template (shows up in resources/templates/list)
server.resources().addTemplate(
    ResourceTemplateEntry.of("docs", "example://docs/{path}", "Docs", "text/markdown") { _, uri, _ ->
        TextResourceContents.of(uri, "text/markdown", read(uri) ?: error("not found"))
    },
)
```

`list_changed` fires automatically when the registry changes (given `listChanged = true`) — drop
any manual `broadcastNotification("notifications/resources/list_changed", …)`. Use the resource
**`name`** as your remove key; make it unique (the URI is a safe choice — bare filenames collide
across directories, and `remove(name)` won't find a stale entry keyed differently).

## 9. Elicitation

The elicitor now hangs off `InteractionContext` (available as `ToolScope.ctx`) instead of a
`Server` + `sessionId` pair:

```kotlin
// before: server.userFeedbackElicitor(sessionId)
val elicitor = ctx.userFeedbackElicitor()   // inside a tool handler
```

## Checklist

- [ ] Swap dependency; keep `kotlinx-serialization-json` if you use decode/structured/JsonObject schemas.
- [ ] Delete the custom Ktor transport/session code — `TachyonServer { }` replaces it.
- [ ] Port full identity: `title`, `websiteUrl`, `icons` — not just `name`/`version`.
- [ ] `addTool` → `registerTool`; receiver → `ToolScope`; arguments → `args.*`; results → `ToolResult`.
- [ ] Fix Jackson imports to `tools.jackson.*`; convert kotlinx schemas with `readTree(...)`.
- [ ] Re-emit `required` on every input schema.
- [ ] `LoggingLevel.Info` → `INFO`; route logs through `server.log(session, …)`.
- [ ] Register concrete resources if clients call `resources/list`; drop manual list-changed broadcasts.
- [ ] Elicitation → `ctx.userFeedbackElicitor()`.
- [ ] Re-audit every input-validation check the SDK path enforced — confirm it still runs against a trusted value.

Full API reference: [`docs/tools.md`](tools.md), [`docs/resources.md`](resources.md), and
[`docs/kotlin.md`](kotlin.md). 
Hit a rough edge? [Open an issue](https://github.com/kpavlov/tachyon/issues/new) on the Tachyon repo.
