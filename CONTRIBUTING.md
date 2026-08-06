# Contributing to Tachyon MCP

## Setup

- JDK 21+
- Maven 3.9+

## Build & test

```bash
mvn test            # unit + e2e tests
mvn verify           # + conformance
mvn spotless:check   # format check
mvn spotless:apply   # auto-fix formatting
```

Coding conventions (TDD, SOLID, nullability, module layout, Kotlin DSL
patterns) live in [CONTRIBUTING.md](CONTRIBUTING.md), [tachyon-development](.agents/skills/tachyon-development), [guidance.md](docs/architecture/guidance.md) -- read it before opening a PR.

Security issues: see [SECURITY.md](SECURITY.md), not a public issue.
