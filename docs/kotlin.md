# Kotlin DSL — Tachyon MCP Server

The `tachyon-kotlin` module wraps `ServerBuilder` with a coroutine-first DSL, suspend tool handlers, and type-safe scope classes.

## Dependency

```xml
<dependency>
    <groupId>dev.tachyonmcp</groupId>
    <artifactId>tachyon-kotlin</artifactId>
    <version>1.0.0-beta.15</version>
</dependency>
```

## Entry points

```kotlin
// Start Netty transport — returns the Kotlin TachyonServer
val server = TachyonServer(port = 8080) { /* configure */ }

// Server logic only, no transport — for testing
val server: TachyonServer = buildServer { /* configure */ }
```

Use `TachyonServerBuilder` through these entry points for Kotlin construction. The Java
`ServerBuilder` remains the implementation source of truth, while the Kotlin DSL adds suspend
handlers and Kotlin-specific types without duplicating registration or validation logic.

## Structured value factories

Kotlin factories use receiver blocks for structured values with more than three fields.
`Annotations` follows the same shape because it is commonly nested inside descriptors:

```kotlin
val annotations = Annotations {
    audience = listOf(Role.USER)
    priority = 0.8
}

val icon = Icon {
    src = "https://example.com/icon.svg"
    mimeType = "image/svg+xml"
    sizes = listOf("any")
    theme = "light"
}
```

Required fields fail fast when the block finishes. Flat overloads remain available for source
compatibility, but new Kotlin code should use receiver factories for `Icon`, `Annotations`,
content objects, and resource, prompt, and tool descriptors.

## Full example

```kotlin
import dev.tachyonmcp.kotlin.server.TachyonServer
import dev.tachyonmcp.api.server.domain.PromptMessage
import dev.tachyonmcp.api.server.domain.TextResourceContents
import dev.tachyonmcp.api.server.features.tools.ToolResult

val server = TachyonServer(port = 8080) {
    info {
        name = "demo-server"
        version = "1.0"
        description = "Demo MCP server"
    }
    capabilities {
        tools { listChanged = true }
        resources {
            subscribe = true
            listChanged = true
        }
        prompts { listChanged = true }
    }
    session {
        enabled = true
        sessionTtl = 5.minutes
        sessionIdGenerator = SessionIdGenerator { _, _ -> "sess_" + Uuid.random().toHexString() }
    }
    tool(name = "ping", description = "Ping the server") {
        ToolResult.text("pong")
    }
    runtime {
        shutdownGracePeriod = 5.seconds
    }
    resource(
        name = "config",
        uri = "demo://config",
        description = "Server configuration",
        mimeType = "application/json",
    ) {
        TextResourceContents {
            uri = this@resource.uri
            text = """{"env":"prod"}"""
            mimeType = "application/json"
        }
    }
    prompt(name = "greet", description = "Greeting prompt") {
        listOf(PromptMessage.user("Say hello, ${arguments ?: "world"}"))
    }
}
```

## Tool handlers

Tool lambdas are `suspend` functions with access to `ToolScope`:

```kotlin
tool(name = "reverse", description = "Reverse a string") {
    // this: ToolScope
    // ctx: InteractionContext, request: ToolRequest
    // arguments: Args, task: Task? — convenience access to request.arguments()/request.task()
    val msg = arguments.stringValue("message")
    text(msg.reversed())
}
```

For the experimental class-based escape hatch, extend `AbstractToolHandler` and override `handle(ctx, request)` (sync) or `handleAsync(ctx, request)` (async).

## Resource & prompt handlers

Resource and prompt lambdas are `suspend` functions too — call suspending APIs directly:

```kotlin
resource(
    name = "config",
    uri = "demo://config",
    description = "Application configuration",
    mimeType = "application/json",
    title = "Configuration",
    annotations = Annotations { priority = 0.8 },
    size = 1024,
    icons = listOf(Icon { src = "https://example.com/config.svg" }),
) {
    // this: ResourceScope — ctx, uri, params, uriTemplate
    val config = fetchConfig()  // suspend call
    TextResourceContents { text = config }
}

prompt(name = "greet", description = "Greeting prompt") {
    // this: PromptScope — ctx, request, arguments
    listOf(PromptMessage.user("Hello, ${arguments ?: "world"}"))
}
```

Inside a resource handler, `TextResourceContents { }` and `BlobResourceContents { }` default `uri`
to the requested URI and `mimeType` to the registered resource MIME type. You can override either
property.

For metadata shared across registrations, pass a prebuilt descriptor:

```kotlin
val descriptor = ResourceDescriptor {
    name = "config"
    uri = "demo://config"
    description = "Application configuration"
    mimeType = "application/json"
    title = "Configuration"
}

resource(descriptor) {
    TextResourceContents { text = fetchConfig() }
}
```

Handlers run in a server-lifecycle coroutine scope on the server executor. They bridge to the Java
registries through asynchronous handlers, without blocking a virtual thread. Closing the server
cancels active Kotlin handlers before shutting down the executor.

### Resource templates

Template metadata stays in named parameters. The trailing `block` handles matched requests:

```kotlin
resourceTemplate(
    name = "user-profile",
    uriTemplate = "user://{userId}/profile",
    description = "User profile template",
    mimeType = "application/json",
    title = "User profile",
    annotations = Annotations { priority = 0.8 },
    icons = listOf(
        Icon {
            src = "https://example.com/user.svg"
            mimeType = "image/svg+xml"
        },
    ),
) {
    TextResourceContents {
        text = """{"id":"${param("userId")}"}"""
    }
}
```

Template handlers use the same contextual defaults. Their text builder also exposes `param(name)`
and `sequence(name)`.

For a descriptor shared across registrations, build it once and use the descriptor overload:

```kotlin
val descriptor = ResourceTemplateDescriptor {
    name = "document"
    uriTemplate = "docs://{path}"
    description = "Documentation"
    mimeType = "text/markdown"
}

resourceTemplate(descriptor) {
    TextResourceContents {
        text = loadDocument(param("path"))
    }
}
```

## Tool schemas

`inputSchema` / `outputSchema` accept three shapes on every registration overload
(`tool(...)` in the DSL, `TachyonServer.registerTool(...)` post-build, `ToolDescriptor { }`):

```kotlin
// Jackson JsonNode
tool("a", inputSchema = jacksonNode) { /* ... */ }

// Raw JSON string — parsed by Tachyon
tool(
    "b",
    inputSchema = """{"type":"object","properties":{"msg":{"type":"string"}}}""",
    outputSchema = """{"type":"object","properties":{"echo":{"type":"string"}}}""",
) { /* ... */ }

// kotlinx.serialization JsonObject — requires kotlinx-serialization-json (optional)
tool("c", inputSchema = buildJsonObject { put("type", "object") }) { /* ... */ }
```

Schema roots are validated at registration time: a schema whose root does not declare
`"type": "object"` fails fast with `IllegalArgumentException` instead of surfacing later
in the MCP client. Tool descriptions longer than 2048 characters log a warning —
clients may truncate them.

## kotlinx.serialization integration

`kotlinx-serialization-json` is an **optional** dependency of `tachyon-kotlin`.
Add it to use `JsonObject` schemas, `arguments.decode<T>()`, and `success(value)`:

```xml
<dependency>
    <groupId>org.jetbrains.kotlinx</groupId>
    <artifactId>kotlinx-serialization-json</artifactId>
</dependency>
```

```kotlin
@Serializable data class EchoArgs(val message: String, val loud: Boolean = false)

@Serializable data class EchoReply(val echo: String)

tool(
    "echo",
    inputSchema = """{"type":"object","properties":{"message":{"type":"string"}}}""",
    outputSchema = """{"type":"object","properties":{"echo":{"type":"string"}}}""",
) {
    val input = arguments.decode<EchoArgs>() // typed decode via configured serde
    success(EchoReply(input.message)) // structuredContent via configured serde
}
```

The Kotlin DSL retains Tachyon's Jackson serde by default. Select kotlinx serialization explicitly:
`json { serde = KxSerializationSerde.Default }`. Configure a strict `Json` via
`json { serde = KxSerializationSerde(Json { ignoreUnknownKeys = false }) }`.
`success(value)` encodes via the configured serde; the value must encode to a JSON
object and pairs with the declared `outputSchema`. For a pre-serialized JSON payload
that bypasses the serde, use `ToolResult.raw(json, text)`.

### Typed decode/result via configured serde

Kotlin extensions bridge the gap between the existing Java typed API and the
configured serde in the Kotlin DSL:

| Method | Routes through | Behaviour |
|---|---|---|
| `request.arguments().decode<T>()` | server-configured `PayloadDeserializer` | Honors custom `Json` config |
| `scope.success(value)` | server-configured `PayloadSerializer` | Deferred serialization at encode time |
| `scope.success(value, text)` | server-configured `PayloadSerializer` | Structured + human-readable text |

`decode<T>` uses `T::class.java → Args.decode(Class<T>)`, which routes
through the deserializer set in `json { serde = ... }`.

```kotlin
@Serializable data class GreetArgs(val name: String, val greeting: String = "Hello")
@Serializable data class GreetReply(val message: String)

tool(name = "greet", inputSchema = ..., outputSchema = ...) {
    val input = arguments.decode<GreetArgs>() // honors configured serde
    success(GreetReply("${input.greeting}, ${input.name}!"), "greeting response")  // symmetric typed result
}
```

## Args accessors

Available via `ToolScope.arguments` (or `PromptScope.arguments`):

| Call | Behaviour |
|---|---|
| `arguments.stringValue("k")` / `intValue` / `boolValue` / `doubleValue` | Required — throws when missing |
| `arguments.stringOrNull("k")` / `intOrNull` / `booleanOrNull` / `doubleOrNull` | Returns `null` when missing |
| `arguments.stringOr("k", "d")` / `int("k", 0)` / `boolean("k", true)` / `double("k", 0.0)` | Falls back to default |
| `arguments.decode<T>()` | typed decode via configured serde (Jackson by default) |

## Scope reference

| Scope | Builder method | Properties |
|---|---|---|
| `ServerInfoScope` | `info { }` | `name`, `version`, `description`, `title`, `instructions` |
| `CapabilitiesScope` | `capabilities { }` | `tools()`, `resources()`, `prompts()`, `tasks()`, `logging()`, `completions()` |
| `NetworkScope` | `network { }` | `host`, `port`, `endpointPath`, `allowedOrigins`, `allowedHeaders`, `allowedHosts`, `maxContentLength` |
| `SessionScope` | `session { }` | `enabled`, `sessionTtl`, `sessionIdGenerator` |
| `RuntimeScope` | `runtime { }` | `shutdownGracePeriod` |
| `ToolScope` | tool lambda | `ctx`, `request`, `arguments`, `task`; `success(v)`, `text(t)`, `fail(msg)`, `content { }` |
| `ResourceScope` | resource lambda | `ctx`, `uri`, `params`, `uriTemplate` |
| `TemplateScope` | resource-template lambda | `ctx`, `uri`, `params`, `uriTemplate`; contextual `TextResourceContents { }` |
| `PromptScope` | prompt lambda | `ctx`, `request`, `arguments` |

## Post-build registration

The Kotlin `TachyonServer` supports suspend tool registration after construction:

```kotlin
val server = buildServer { /* base config */ }
server.registerTool(
    ToolDescriptor {
        name = "echo"
        description = "Echo a message"
    },
) {
    text(arguments.stringValue("msg"))
}
```

## Netty pipeline customization

```kotlin
TachyonServer(port = 8080) {
    pipelineCustomizer {
        addLast("metrics", MetricsHandler())
    }
}
```

## ToolResult factories

| Call | Behaviour |
|---|---|
| `ToolResult.text(t)` | Text content block |
| `ToolResult.error(msg)` | `isError = true` |
| `ToolResult.content(vararg b)` | Multiple content blocks |
| `ToolResult.structured(payload)` | POJO → `structuredContent` via Jackson |
| `ToolResult.structured(payload, text)` | Structured + human-readable text |
| `ToolResult.empty()` | No content |

Inside a tool lambda, prefer the `ToolScope` shortcuts: `text(t)`, `success(v)`, `fail(msg)`.
`fail`, not `error` — a member `error(String)` would shadow Kotlin's stdlib `error()`, silently
turning a thrown `IllegalStateException` into a returned value.

`structuredContent` requires a JSON object shape. Tachyon rejects primitives and arrays at runtime.

With kotlinx-serialization on the classpath, prefer `success(value)` inside a tool lambda —
the configured serde encodes the value into `structuredContent`. Without an explicit `text`
argument, Tachyon emits the serialized JSON as the backwards-compatible text block.

## Testing

Use `TachyonServer(port = 0) { }` for zero-setup E2E tests — it starts Netty on an ephemeral port:

```kotlin
val server = TachyonServer(port = 0) { tool("ping") { ToolResult.text("pong") } }
// server.host() → bound host, server.port() → ephemeral port
```

Run Kotlin tests only:

```bash
mvn test -am -f tachyon-kotlin/pom.xml
```

---

**See also:** [Tools](tools.md) · [Resources](resources.md) · [Tasks](tasks.md) · [Extensions](extensions.md) · [Quickstart](quickstart.md)
