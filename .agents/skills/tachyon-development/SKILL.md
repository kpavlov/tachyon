---
name: tachyon-development
description: Apply Tachyon MCP project rules when designing, implementing, reviewing, or testing Java and Kotlin server code, MCP protocol behavior, E2E fixtures, concurrency, JSON, and schemas.
---

# Prime directives

- ATDD: prefer E2E, unit only for edge cases E2E can't cover. Start e2e tests before prod code.
- Kotlin API refactors follow the adapter shapes in
  [`docs/architecture/guidance.md`](../../../docs/architecture/guidance.md#kotlin-adapter-shape).
- Java `ServerBuilder` is the implementation source of truth. Kotlin adds only thin adaptation for
  suspend lambdas and Kotlin-specific types; never duplicate validation or registration logic.
- Treat `examples/weather` and `examples/weather-mcp-kotlin` as Rosetta Stone examples. Keep their
  MCP features, metadata, behavior, and coverage functionally identical when changing either one.
- Keep Kotlin source files focused. At more than 300 lines, consider splitting by owned
  responsibility before adding code.

# Test Rules 🧪

- AssertJ fluent. Short spec ref comment in method. JUnit6+JUnit Pioneer annotations. Prefer parametrized tests.
- Handlers that throw: test with a real checked exception thrown directly from the lambda (no try/catch) — exercises the `throws Exception` SAM contract, not just unchecked paths.

## E2E

- Java MCP SDK client. Raw HTTP only for edge cases.
- `JsonUnit` + AssertJ for JSON. Assert full JSON via `assertThatJson(actual).isEqualTo(expected)`, dynamic values (IDs etc.) via `.formatted(...)` — not `inPath(...)` fragments, which can hide a wrong response shape behind passing checks. `whenIgnoringPaths`, `IGNORING_ARRAY_ORDER`, `IGNORING_EXTRA_FIELDS`, `TREATING_NULL_AS_ABSENT` for partial tolerance.
- `// language=json` before JSON strings.
- Awaitility for polling.
- Kotlin: use kotest-assertions

### Shared E2E servers

- Treat shared stateful/stateless singleton servers as production-parity SUTs, not just suite optimizations.
- Keep shared server config/registries immutable after startup. Tests that register or replace features use an isolated `startServer(...)`.
- Make session mode explicit and invariant: stateful fixtures enable sessions; stateless disable them. Don't switch a test's mode just to drop session-ID plumbing.
- JUnit parallel execution is background pressure, not concurrency proof. Add dedicated E2E scenarios coordinating simultaneous clients with barriers/latches, virtual threads, bounded timeouts; never fixed sleeps.
- For stateful concurrency, verify: unique active sessions, parallel requests across sessions, parallel requests within one session, response isolation even with repeated JSON-RPC IDs across sessions, and terminating one session without affecting others.
- For stateless concurrency, verify: parallel initialization returns no session ID, concurrent requests never cross responses or client data.
- Test observable server behavior through real clients. Don't add tests for test helpers.

## JSON Schemas

- Static schema → parse text block. `ObjectMapper.readTree("""...""")` or shared `parseJson(String)` helper. `// language=json` for IDE.
- Imperative `JsonNodeFactory` only for runtime-computed schemas.

## Logging

[Logging policy](https://kpavlov.me/blog/logging-policy/)

| Level | Use |
|---|---|
| ERROR | Immediate action — ops enables Rollbar+PagerDuty alerting |
| WARN | Action needed, can wait to next business day |
| INFO (default on) | Normal-operation info |
| DEBUG (off on PROD, on DEV) | Trace business logic |
| TRACE (off by default) | Raw request/response dump — leaks confidential data if left on |
