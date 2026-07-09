# Tools — Tachyon MCP Server

Tools are the primary way clients invoke server-side logic. Tachyon validates inputs against JSON Schema 2020-12 and routes calls to your handler.

## Define a tool

### Lambda (simple)

```java
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;

.tool(ToolHandler.of("hello", "Say hello",
    (ctx, args) -> ToolResult.text("Hello!")))
```

Need an input schema? Configure the descriptor with the builder overload. `.inputSchema(...)` /
`.outputSchema(...)` take a raw JSON `String` **or** a Jackson `JsonNode`:

```java
.tool(ToolHandler.of(
    b -> b.name("hello")
        .description("Say hello")
        .inputSchema("""
        {"type":"object","properties":{"name":{"type":"string"}}}
        """),
    (ctx, args) -> ToolResult.text("Hello, " + args.stringOr("name", "world") + "!")))
```

### Class (recommended for complex tools)

Implement `ToolHandler` directly: return a `descriptor()` and override `handle(ctx, ToolArgs)`.

```java
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.runtime.InteractionContext;

class WeatherTool implements ToolHandler {
    private static final String SCHEMA = """
        {"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}
        """;

    @Override
    public ToolDescriptor descriptor() {
        return ToolDescriptor.builder()
            .name("get_weather")
            .description("Get current weather for a city")
            .inputSchema(SCHEMA)
            .build();
    }

    @Override
    public ToolResult handle(InteractionContext ctx, ToolArgs args) {
        String city = args.string("city");
        return ToolResult.text("☀️ 22°C in " + city);
    }
}
```

Register: `.tool(new WeatherTool())`

### Async tool

Blocking handlers run on a virtual thread, so most tools need no async plumbing. When you already
hold a `CompletionStage` (a non-blocking client, another async service), return it directly:
lambda via `ToolHandler.ofAsync`, or override `handleAsync(ctx, ToolArgs)`. Async handlers stay
async — they are not funneled through the blocking path.

```java
import dev.tachyonmcp.server.features.tools.ToolHandler;

.tool(ToolHandler.ofAsync("get_weather_async",
    (ctx, args) -> fetchWeather(args.string("city"))
        .thenApply(w -> ToolResult.text(w.summary()))))
```

### Progress token / full request

`ToolArgs` carries only the parsed arguments. When you need the request `_meta` — a progress token,
input responses — override the request-level method instead: `handle(ctx, ToolRequest)` (sync) or
`ToolHandler.ofRequest(descriptor, (ctx, request) -> ...)` / `handleAsync(ctx, ToolRequest)` (async).

## Read arguments

`ToolArgs` wraps the raw `Map<String, JsonNode>` with typed accessors:

| Method                    | Returns    |
|---------------------------|------------|
| `args.string("key")`      | `String`   |
| `args.intValue("key")`    | `int`      |
| `args.boolValue("key")`   | `boolean`  |
| `args.doubleValue("key")` | `double`   |
| `args.node("key")`        | `JsonNode` |
| `args.has("key")`         | `boolean`  |

`*Or(key, fallback)` and `stringOpt(key)` variants avoid throwing on missing keys.

## Return results

`ToolResult` is a sealed type — pick the right factory:

| Factory                                 | Use case                               |
|-----------------------------------------|----------------------------------------|
| `ToolResult.text(t)`                    | Plain text response                    |
| `ToolResult.error(msg)`                 | Error (`isError = true`)               |
| `ToolResult.blocks(blocks...)`          | Multiple content blocks                |
| `ToolResult.of(payload)`                | POJO → `structuredContent` via Jackson |
| `ToolResult.of(payload, text)`          | Structured + human-readable text       |
| `ToolResult.empty()`                    | No content                             |
| `ToolResult.inputRequired(reqs, state)` | Elicitation request                    |

`structuredContent` must serialize to a JSON **object**; Tachyon throws `IllegalArgumentException` for arrays and primitives.

## Add metadata

```java
return ToolResult.text("done").withMeta("taskId", JSON.textNode("t-123"));
```

Metadata appears in the `_meta` field of the response.

## Jackson note

Tachyon uses **Jackson 3** (`tools.jackson.*`), not Jackson 2. Import `tools.jackson.databind.JsonNode`, not `com.fasterxml.jackson.databind.JsonNode`.

## Kotlin DSL

```kotlin
tool(name = "reverse", description = "Reverse a string") {
    val msg = args.string("message")
    ToolResult.text(msg.reversed())
}
```

### Typed decode/result

```kotlin
@Serializable data class Args(val message: String)
@Serializable data class Reply(val echo: String)

tool("echo", inputSchema = ..., outputSchema = ...) {
    val input = args.decode<Args>()     // via configured serde
    success(Reply(input.message))       // symmetric typed result
}
```

- `args.decode<T>()` — honors configured serde (kotlinx by default, ignores unknown keys)
- `scope.success(value)` / `scope.success(value, text)` — symmetric typed result via configured serializer

See [kotlin.md](kotlin.md) for the full Kotlin DSL reference.

---

**See also:** [Resources](resources.md) · [Tasks](tasks.md) · [Extensions](extensions.md) · [Quickstart](quickstart.md)
