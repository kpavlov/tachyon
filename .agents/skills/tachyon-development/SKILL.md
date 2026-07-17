---
name: tachyon-development
description: Apply project specific rules when designing, writing code and tests
---

# Prime directives

- Use acceptance-test-driven development (ATDD). Prefer testing e2e with unit tests for edge conditions. Start e2e tests before prod code.

# Test Rules 🧪

- AssertJ fluent. Short spec ref comment in method.
- E2E over unit. Unit only if E2E can't cover.

## E2E

- Java MCP SDK client. Raw HTTP only for edge cases.
- `JsonUnit` + AssertJ for JSON. Assert full JSON. `whenIgnoringPaths`, `IGNORING_ARRAY_ORDER`, `IGNORING_EXTRA_FIELDS`, `TREATING_NULL_AS_ABSENT`.
- `// language=JSON` before JSON strings.
- Awaitility for polling.
- Kotlin: use kotest-assertions

## JSON Schemas

- Static schema → parse text block. `ObjectMapper.readTree("""...""")` or shared `parseJson(String)` helper. `// language=json` for IDE.
- Imperative `JsonNodeFactory` only for runtime-computed schemas.
