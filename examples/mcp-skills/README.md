# MCP Skills Java Example

Serves a bundled fictional `elvish-magic` Agent Skill with Tachyon's `SkillsExtension`.
Clients can fetch `skill://elvish-magic/SKILL.md` through the base `resources/read` method without
extension negotiation. Clients negotiate `io.modelcontextprotocol/skills` to use `skills/list`,
`skills/get`, and `resources/directory/read`.

## Quickstart

The skills extension is currently built from this repository's SNAPSHOT:

```shell
# From the repository root
./mvnw install -pl tachyon-extensions,tachyon-testkit -am -DskipTests -Djacoco.skip=true
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
