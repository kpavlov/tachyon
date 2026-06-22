---
name: testing
description: Apply when designing and writing tests
---

# Testing guidance

- **Optimize for readability.** Use AssertJ assertions with fluent DSL style
- Add short comments to test methods referring to MCP specification
- Add one-line explaining comments to tests steps **only** when code is not obvious

## End-to-end tests (e2e)

- Use Java MCP SDK client where possible to test server. Fallback to raw http client for tricky cases
- Use `net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson(actual).isEqualTo(expected)` for JSON payload assertions. JsonUnit integrates with AssertJ's fluent DSL. For scenarios with dynamic or timestamped fields, use `.whenIgnoringPaths("path.to.field")` or other options like `IGNORING_ARRAY_ORDER`, `IGNORING_EXTRA_FIELDS`, `TREATING_NULL_AS_ABSENT`.
- Use `// language=JSON` comment before JSON strings in code
