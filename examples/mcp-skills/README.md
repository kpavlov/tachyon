# MCP Skills Java Example

Serves a bundled fictional `elvish-magic` Agent Skill with Tachyon's `SkillsExtension`.
Clients negotiate `io.modelcontextprotocol/skills`, discover it with `skills/list`, then fetch
`skill://elvish-magic/SKILL.md` through `resources/read`.

## Quickstart

The skills extension is currently built from this repository's SNAPSHOT:

```shell
# From the repository root
./mvnw install -pl tachyon-extensions,tachyon-testkit -am -DskipTests
```

Running example
```shell
cd examples/mcp-skills
mvn package
java -jar target/mcp-skills-example.jar
```

Connect an MCP 2026-07-28 client to `http://localhost:8080/mcp` and declare the
`io.modelcontextprotocol/skills` extension.

The included `.mcp.json` registers that endpoint as `elvish-magic-skill-mcp` for clients that load project MCP configuration.
