# Tachyon MCP — Kotlin DSL

Kotlin coroutine-first DSL for [Tachyon MCP](../README.md). Wraps the Java `ServerBuilder` with suspend tool handlers, type-safe scopes, and idiomatic Kotlin entry points.

## Installation

```xml
<dependency>
    <groupId>dev.tachyonmcp</groupId>
    <artifactId>tachyon-server-kotlin</artifactId>
    <version>1.0.0-beta.8</version>
</dependency>
```

Requires `tachyon-server` on the classpath (included transitively).

Optionally add `org.jetbrains.kotlinx:kotlinx-serialization-json` to unlock `JsonObject`
schemas, `args.decode<T>()`, and `structured(value)` tool results.

## Documentation

Full reference: [docs/kotlin.md](../docs/kotlin.md)

Examples: [`examples/echo-kotlin`](../examples/echo-kotlin/) · [`.agents/skills/tachyon-mcp/resources/kotlin/`](../.agents/skills/tachyon-mcp/resources/kotlin/)
