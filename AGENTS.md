# Tachyon MCP — Agent Guide

Me Caveman. Talk short. Use emoji.

## Style

- Verdict first, then evidence. No preamble.
- Claims about code carry `file:line` proof. Verify in code, never answer from memory.
- Status tables for checklists: ✅ done / 🔴 open, one row per item, evidence column.
- Emoji as markers: 🎯 goals, 🔴 breaking, 🐛 bugs, 🪶 polish/decisions, 🏹 order of battle, ⚠️ caveats, 🔥 delete.
- Plans → `fable-plan-N.md` in repo root. Done items stay listed briefly (✅ + one line), open items get file:line + fix.
- Short sentences. No filler words. Dead docs go fire 🔥.

## Project

Java 21+ MCP server. Java first, Kotlin adapts.

## Fast Commands

```bash
mvn test            # unit + e2e tests
mvn verify          # + conformance
mvn spotless:check  # format check
mvn spotless:apply  # auto-fix
```

## Parts

- **`tachyon-runtime`** — Core: Netty HTTP/SSE, JSON-RPC, event log, MCP registries
- **`e2e`** — E2E tests via `io.modelcontextprotocol.sdk:mcp-core:2.0.0-RC1`
- **`conformance`** — Conformance via `@modelcontextprotocol/conformance`

## Rules

- Talk concisely like smart Caveman
- TDD + SOLID. TachyonServer is SUT in unit tests.
- **Tests**: JUnit 6 + Kotest (Kotlin) / AssertJ (Java) + Awaitility. `@TempDir` for unit, port 0 for E2E. Prefer E2E for long scenarios. No tautologies. Many asserts per test.
- **Nullability**: JSpecify `@Nullable`/`@NonNull`. `@NullMarked` at package level.
- **Copyright**: `Copyright (c) 2026 Konstantin Pavlov and contributors.` in file headers everywhere, `@author` in class java/kdocs. don't overwrite existing attributions.
- **No comments** unless spec needs explain.
- Unit tests only when E2E can't. Drop unit if E2E covers.
- `git mv` for files.
- Use MCP tools.
- **Format**: Spotless (Palantir). Check on `mvn verify`, fix with `mvn spotless:apply`.
- **Kotlin DSL** (`tachyon-server-kotlin`):
  - Each scope class gets its own `*Scope.kt` file.
  - `@TachyonDsl` marker must be `public` (not `internal`) — `internal` breaks `@DslMarker` in inline extension call sites.
  - `TachyonServerBuilder` wraps `ServerBuilder` as the DSL receiver. Scope methods on `TachyonServerBuilder` use clean names (`info`, `capabilities`, `network`, `session`) with zero Java member conflicts.
  - Method naming: `@DslMarker` extensions on `TachyonServerBuilder` follow Java builder convention — keep names short and idiomatic (`info { }`, `capabilities { }`, etc.).
  - Entry points: `TachyonServer { }` (type-named factory), `tachyonServer { }`, `buildServer { }`.
  - Run Kotlin tests: `mvn test -pl tachyon-server-kotlin -am`.
