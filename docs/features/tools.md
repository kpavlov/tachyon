---
title: "Tools"
weight: 10
sidebar_order: 10
toc: true
description: |-
  Implement MCP tool handlers in Tachyon: sync and async functions, input schemas, structured output, ToolResult, and annotations.
---

Tools are the primary way clients invoke server-side logic. Tachyon validates inputs against JSON Schema 2020-12 and routes calls to your handler.

## Define a tool

### Lambda (simple)

```java
import dev.tachyonmcp.api.server.features.tools.ToolResult;

.withTools(tools -> tools.register(
        tool -> tool.name("hello").description("Say hello"),
        (ctx, request) -> ToolResult.text("Hello!")))
```

Need an input schema? Configure the descriptor with the builder overload. `.inputSchema(...)` /
`.outputSchema(...)` take a raw JSON `String` **or** a Jackson `JsonNode`:

```java
.withTools(tools -> tools.register(
        b -> b.name("hello")
            .description("Say hello")
            .inputSchema("""
            {"type":"object","properties":{"name":{"type":"string"}}}
            """),
        (ctx, request) -> ToolResult.text(
            "Hello, " + request.arguments().stringOr("name", "world") + "!")))
```

### Class (experimental escape hatch)

Prefer descriptor/function registration above. Reach for the experimental class-based escape hatch
only when a lambda cannot express the handler. Then extend
`AbstractToolHandler`: pass the descriptor to the constructor and override `handle(ctx, request)`.
(`ToolHandler` itself declares only `descriptor()` and `handleAsync(ctx, ToolRequest)`;
`AbstractToolHandler` supplies the synchronous request override.)

```java
import dev.tachyonmcp.api.server.features.tools.AbstractToolHandler;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolRequest;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.api.runtime.InteractionContext;

class WeatherTool extends AbstractToolHandler {
    private static final String SCHEMA = """
            {"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}
            """;

    WeatherTool() {
        super(ToolDescriptor.builder()
                .name("get_weather")
                .description("Get current weather for a city")
                .inputSchema(SCHEMA)
                .build());
    }

    @Override
    public ToolResult handle(InteractionContext ctx, ToolRequest request) {
        String city = request.arguments().stringValue("city");
        return ToolResult.text("☀️ 22°C in " + city);
    }
}
```

Register its descriptor and function explicitly:

```java
var weather = new WeatherTool();
server.tools().register(weather.descriptor(), weather::handle);
```

### Async tool

Blocking handlers run on a virtual thread, so most tools need no async plumbing. When you already
hold a `CompletionStage` (a non-blocking client, another async service), return it directly with
`registerAsync`, or override `handleAsync(ctx, request)` on
`AbstractToolHandler`. Async handlers stay async — they are not funneled through the blocking path.

```java
.withTools(tools -> tools.registerAsync(
        tool -> tool.name("get_weather_async"),
        (ctx, request) -> fetchWeather(request.arguments().stringValue("city"))
                .thenApply(w -> ToolResult.text(w.summary()))))
```

### Typed tool (experimental)

Instead of reading arguments key by key, register a tool against an input and an output type.
Tachyon decodes the call arguments into `I` with the configured `PayloadDeserializer` and wraps
your return value as `structuredContent`:

```java
record ForecastRequest(String city, int days) {}
record Forecast(String summary, double highC) {}

.withTools(tools -> tools.register(
        ForecastRequest.class,
        Forecast.class,
        tool -> tool.name("get_forecast").description("Multi-day forecast"),
        (ctx, input) -> new Forecast(lookup(input.city()), highFor(input.city(), input.days()))))
```

`registerAsync(Class, Class, ..., AsyncTypedToolFn)` is the `CompletionStage` twin.

Any schema the descriptor leaves unset is filled in from the matching type via
`JsonSchema.generate(Class)`, which resolves through the registered `JsonSchemaFactory` chain:

| Source | Provided by |
|---|---|
| Build-time schema resource from the kt-schema annotation processor | `tachyon-core` |
| Runtime reflection over the class | `tachyon-kotlin-kt-schema` |

With neither available for a type, `JsonSchema.generate` throws `IllegalStateException`. Declare
`inputSchema`/`outputSchema` on the descriptor yourself and the typed overloads work with no extra
dependency — you still get typed decode and structured output, just not generated schemas.

### Progress token / full request

`register(...)` and `registerAsync(...)` functions receive `ToolRequest`; call
`request.arguments()` for parsed arguments. Class-based handlers receive the same request in
`handle(ctx, ToolRequest)` or `handleAsync(ctx, ToolRequest)`.

## Read arguments

`Args` is the `JsonObject` view of the call arguments, so it carries the same typed accessors:

| Method                    | Returns   |
|---------------------------|-----------|
| `args.stringValue("key")` | `String`  |
| `args.intValue("key")`    | `int`     |
| `args.boolValue("key")`   | `boolean` |
| `args.doubleValue("key")` | `double`  |
| `args.has("key")`         | `boolean` |

`*Or(key, fallback)` and `*Opt(key)` variants avoid throwing on missing keys — `stringOpt`,
`boolOpt`, `intOpt`, `longOpt`, `doubleOpt`, `decimalOpt`, `objectOpt`, `arrayOpt`.

To take the whole argument object at once, use `args.decode(MyArgs.class)` — it runs through the
server's configured `PayloadDeserializer`. To reach the underlying provider value, use
`args.unwrap(JsonNode.class)`.

## Return results

`ToolResult` is a sealed type — pick the right factory:

| Factory                                 | Use case                               |
|-----------------------------------------|----------------------------------------|
| `ToolResult.text(t)`                    | Plain text response                    |
| `ToolResult.error(msg)`                 | Error (`isError = true`)               |
| `ToolResult.content(blocks...)`         | Multiple content blocks                |
| `ToolResult.structured(payload)`        | POJO → `structuredContent`; serialized JSON auto-added as the text block |
| `ToolResult.structured(payload, text)`  | Structured + explicit human-readable text |
| `ToolResult.raw(json, text)`            | Pre-serialized JSON — bypasses the payload serde |
| `ToolResult.empty()`                    | No content                             |
| `ToolResult.task(snapshot)`             | Hand off to a long-running [task](tasks.md) |
| `ToolResult.inputRequired(reqs, state)` | Elicitation request                    |

Under MCP 2026-07-28, `structuredContent`/`outputSchema` may be any JSON value — object, array, or
scalar. Under 2025-11-25, `structuredContent` stays object-only on the wire: a non-object result
still validates against `outputSchema`, but is delivered as the serialized-JSON text block instead
of `structuredContent`. A structured value that fails its declared `outputSchema` is rejected as an
`isError: true` tool result on every protocol version.

See [Client interactions](client-interactions.md) for form elicitation, input-required results,
and the sampling compatibility boundary.

## Add metadata

```java
return ToolResult.text("done").withMeta("taskId", JSON.stringNode("t-123"));
```

Metadata appears in the `_meta` field of the response.

## Mirror an argument into an HTTP header

MCP 2026-07-28 (SEP-2243) lets a tool argument be mirrored into an `Mcp-Param-{Name}` request
header, so load balancers, WAFs and rate limiters can route on it without parsing the JSON body.
Annotate the property with `x-mcp-header`:

```json
{
  "type": "object",
  "properties": {
    "region": {"type": "string", "x-mcp-header": "Region"},
    "query": {"type": "string"}
  }
}
```

A conforming client then sends `Mcp-Param-Region: us-west1`, and the server rejects the call with
`400` / JSON-RPC `-32020` if that header is missing, malformed, or disagrees with the body.

The annotation is validated when the tool is registered — a violation throws
`IllegalArgumentException` rather than being ignored, because an annotation the server skips is a
header an intermediary trusts but nothing ever checks against the body:

| Rule | Detail |
|---|---|
| Non-empty HTTP token | RFC 9110 `1*tchar` — no spaces, colons, control characters, or non-ASCII |
| Unique, ignoring case | Two properties mirroring to one header make it ambiguous |
| Primitive types only | `string`, `integer`, `boolean`. `number` is excluded — its string form is not canonical |
| Top-level properties only | An annotation on a nested property, inside `items`, or behind a `$ref` is rejected rather than silently ignored |

Values must be ASCII; see [Configuration](../running/configuration.md) for the character rules and the
`=?base64?…?=` wrapper for anything else.

> Do not annotate secrets. Mirrored values are visible to every intermediary on the path, and Base64
> is an encoding, not encryption.

## Jackson note

Tachyon uses **Jackson 3** (`tools.jackson.*`), not Jackson 2. Import `tools.jackson.databind.JsonNode`, not `com.fasterxml.jackson.databind.JsonNode`.

## Kotlin DSL

```kotlin
tool(name = "reverse", description = "Reverse a string") {
    val msg = arguments.stringValue("message")
    text(msg.reversed())
}
```

### Typed decode/result

```kotlin
@Serializable data class EchoArgs(val message: String)
@Serializable data class EchoReply(val echo: String)

tool(
    "echo",
    inputSchema = """{"type":"object","properties":{"message":{"type":"string"}}}""",
    outputSchema = """{"type":"object","properties":{"echo":{"type":"string"}}}""",
) {
    val input = arguments.decode<EchoArgs>() // via configured serde
    success(EchoReply(input.message))        // symmetric typed result
}
```

- `arguments.decode<T>()` — honors the configured serde (Jackson by default)
- `scope.success(value)` / `scope.success(value, text)` — symmetric typed result via configured serializer

`typedTool<In, Out>` derives both schemas from the types, so the literals above disappear
entirely. See [typed tools](../kotlin/#typed-tools) and the
[Kotlin DSL](../kotlin/) for the full Kotlin API.
