---
name: tachyon-development
description: Apply project specific rules when designing, writing code and tests
metadata:
    author: Konstantin Pavlov
---

# Prime directives

- ATDD: prefer E2E, unit only for edge cases E2E can't cover. Start e2e tests before prod code.

# Test Rules 🧪

- AssertJ fluent. Short spec ref comment in method. JUnit6+JUnit Pioneer annotations. Prefer parametrized tests.
- Handlers that throw: test with a real checked exception thrown directly from the lambda (no try/catch) — exercises the `throws Exception` SAM contract, not just unchecked paths.

## E2E

- Java MCP SDK client. Raw HTTP only for edge cases.
- `JsonUnit` + AssertJ for JSON. Assert full JSON via `assertThatJson(actual).isEqualTo(expected)`, dynamic values (IDs etc.) interpolated via `.formatted(...)` — not `inPath(...)` fragments, which can hide a wrong response shape behind passing-looking checks. `whenIgnoringPaths`, `IGNORING_ARRAY_ORDER`, `IGNORING_EXTRA_FIELDS`, `TREATING_NULL_AS_ABSENT` for partial tolerance.
- `// language=JSON` before JSON strings.
- Awaitility for polling.
- Kotlin: use kotest-assertions

## JSON Schemas

- Static schema → parse text block. `ObjectMapper.readTree("""...""")` or shared `parseJson(String)` helper. `// language=json` for IDE.
- Imperative `JsonNodeFactory` only for runtime-computed schemas.
