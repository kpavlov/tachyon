# Tachyon MCP — Agent Guide

## Project

A Java 25 AI runtime server implementing [MCP spec 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25).

## Quick Commands

```bash
mvn test                               # All unit tests (core + e2e)
mvn verify                             # Unit + e2e + conformance tests tests
mvn spotless:check                     # Check Java/Palantir formatting
mvn spotless:apply                     # Auto-format Java, POM, and Markdown files
```

## Modules

- **`tachyon-runtime`** — Core server: Netty HTTP/SSE transport, JSON-RPC dispatch, event log, MCP
  registries (tools/resources/prompts/tasks)
- **`e2e`** — End-to-end tests using `io.modelcontextprotocol.sdk:mcp-core:2.0.0-RC1`
- **`conformance`** — Conformance tests using `@modelcontextprotocol/conformance`

## Conventions

- Follow TDD and SOLID principles. Ensure MCP protocol logic is covered, TachyonServer is SUT in unit tests.
- **Tests**: JUnit 5 + AssertJ. Unit tests use `@TempDir`; E2E bind to port 0 (MCP SDK client or raw `HttpClient`).
  Prefer higher-level E2E for longer scenarios (less fragile). No tautological tests; don't duplicate a single scenario —
  prefer multiple assertions per test.
- **Nullability**: JSpecify `@Nullable`/`@NonNull` annotations.
- **Copyright**: `/* Copyright (c) 2026 Konstantin Pavlov. */` on every source file.
- **No comments in code** unless explaining non-obvious spec behavior.
- Write unit tests only for cases when E2E tests can't verify. Drop unit tests where E2E counterpart exists
- Use `git mv` to move files to preserve git history
- **Formatting**: Spotless (Palantir Java Format) — runs `check` on `mvn verify`, auto-fix with
  `mvn spotless:apply`.
