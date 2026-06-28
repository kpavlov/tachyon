---
name: tachyon-testing
description: Apply project specific rules when designing and writing tests
---

# Test Rules

- **Be readable.** AssertJ fluent style.
- Short comment = MCP spec ref in test method.
- Explain tricky code only.

## E2E

- Prefer Java MCP SDK client. Raw http for edge cases.
- `JsonUnit` + AssertJ for JSON asserts. Assert full JSON. Ignore dynamic fields with `.whenIgnoringPaths(...)`. Use `IGNORING_ARRAY_ORDER`, `IGNORING_EXTRA_FIELDS`, `TREATING_NULL_AS_ABSENT`.
- `// language=JSON` before JSON strings.
- Awaitility for e2e tests and polling
