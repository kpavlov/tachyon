# Tachyon MCP — Agent Guide

## Style

- Me Caveman. Talk short. Use emoji.
- Verdict first, then evidence. No preamble.
- Claims about code carry `file:line` proof. Verify in code, never answer from memory.
- Status tables for checklists: ✅ done / 🔴 open, one row per item, evidence column.
- Emoji as markers: 🎯 goals, 🔴 breaking, 🐛 bugs, 🪶 polish/decisions, 🏹 order of battle, ⚠️ caveats, 🔥 delete.
- Short sentences. No filler words. Dead docs go fire 🔥.

## Project

Java 21+Kotlin 2.2 MCP server. Java first, Kotlin adapts.

## Fast Commands

Use IDE MCP for building/running tests if available. Otherwise:

```bash
mvn test            # unit + e2e tests
mvn verify          # + conformance
mvn spotless:check  # format check
mvn spotless:apply  # auto-fix
```

## Parts

- **`tachyon-runtime`** — Core: Netty HTTP/SSE, JSON-RPC, event log, MCP registries
- **`e2e`** — E2E tests via `io.modelcontextprotocol.sdk:mcp-core:2.0.0`
- **`conformance`** — Conformance via `@modelcontextprotocol/conformance`

## Rules

- TDD + SOLID. TachyonServer is SUT in unit tests.
- **Tests**: JUnit 6 + Kotest (Kotlin) / AssertJ (Java) + Awaitility. `@TempDir` for unit, port 0 for E2E. Prefer E2E, esp. long scenarios; unit only when E2E can't cover, drop unit if E2E already does. No tautologies. Many asserts per test.
- **Nullability**: JSpecify `@Nullable`/`@NonNull`. `@NullMarked` at package level.
- **Copyright**: `Copyright (c) 2026 Konstantin Pavlov and contributors.` in file headers everywhere; don't overwrite existing attributions.
- **No comments in code** unless spec needs explain.
- **Javadocs for public API** - this OSS library for users
- `git mv` for files.
- Use MCP tools.
- **Format**: Check on `make lint`, fix with `make format`.
- **API/Registry design**: see [`docs/architecture/guidance.md`](docs/architecture/guidance.md) before adding/changing a handler SAM (sync/async shape, checked exceptions, descriptor bundling, `_meta`) or naming registry APIs (`ServerBuilder` nouns, `register`/`registerAsync`/`unregister`, `find`, `descriptors()`).
- **Kotlin DSL** (`tachyon-kotlin`):
  - Each scope class gets its own `*Scope.kt` file.
  - `TachyonServerBuilder` wraps `ServerBuilder` as the DSL receiver. Scope methods on `TachyonServerBuilder` use clean names (`info`, `capabilities`, `network`, `session`) with zero Java member conflicts.
  - Method naming: `@DslMarker` extensions on `TachyonServerBuilder` follow Java builder convention — keep names short and idiomatic (`info { }`, `capabilities { }`, etc.).
  - Entry points: `TachyonServer(port) { }` (builds + starts transport), `buildServer { }` (builds only, no transport).
  - Run Kotlin tests: `mvn test -pl tachyon-kotlin -am`.
