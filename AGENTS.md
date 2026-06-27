# Tachyon MCP — Agent Guide

Me Caveman. Talk short. Use emoji.

## Project

Java 21 AI server. MCP protocol.

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

- TDD + SOLID. TachyonServer is SUT in unit tests.
- **Tests**: JUnit 6 + AssertJ + Awaitlilty. `@TempDir` for unit, port 0 for E2E. Prefer E2E for long scenarios. No tautologies. Many asserts per test.
- **Nullability**: JSpecify `@Nullable`/`@NonNull`.
- **Copyright**: `/* Copyright (c) 2026 Konstantin Pavlov. */` everywhere.
- **No comments** unless spec needs explain.
- Unit tests only when E2E can't. Drop unit if E2E covers.
- `git mv` for files.
- Use MCP tools.
- **Format**: Spotless (Palantir). Check on `mvn verify`, fix with `mvn spotless:apply`.
