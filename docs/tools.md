# Tools — Tachyon MCP Server

Tools are the primary way clients invoke server-side logic. Tachyon validates inputs against JSON Schema 2020-12 and routes calls to your handler.

## Define a tool

### Lambda (simple)

```java
import dev.tachyonmcp.server.features.tools.SyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;

.tool(SyncToolHandler.of("hello", "Say hello", null,
    (ctx, args) -> ToolResult.text("Hello!")))
```

### Class (recommended for complex tools)

```java
import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class WeatherTool extends AbstractSyncToolHandler {
    private static final JsonNode SCHEMA;
    static {
        var s = JsonNodeFactory.instance.objectNode();
        s.put("type", "object");
        s.putObject("properties").putObject("city").put("type", "string");
        s.putArray("required").add("city");
        SCHEMA = s;
    }

    WeatherTool() {
        super(ToolDescriptor.builder("get_weather")
            .description("Get current weather for a city")
            .inputSchema(SCHEMA)
            .build());
    }

    @Override
    public ToolResult handle(McpContext ctx, ToolArgs args) {
        String city = args.string("city");
        return ToolResult.text("☀️ 22°C in " + city);
    }
}
```

Register: `.tool(new WeatherTool())`

### Async tool

```java
import dev.tachyonmcp.server.features.tools.AbstractAsyncToolHandler;
import java.util.concurrent.CompletionStage;

class AsyncWeatherTool extends AbstractAsyncToolHandler {
    AsyncWeatherTool() { super(descriptor); }

    @Override
    public CompletionStage<ToolResult> handleAsync(McpContext ctx, ToolArgs args) {
        return fetchWeather(args.string("city"))
            .thenApply(w -> ToolResult.text(w.summary()));
    }
}
```

## Read arguments

`ToolArgs` wraps the raw `Map<String, JsonNode>` with typed accessors:

| Method                | Returns    |
|-----------------------|------------|
| `args.string("key")`  | `String`   |
| `args.integer("key")` | `int`      |
| `args.bool("key")`    | `boolean`  |
| `args.node("key")`    | `JsonNode` |
| `args.has("key")`     | `boolean`  |

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
