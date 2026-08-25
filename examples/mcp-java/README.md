# mcp-java MCP Example

Demonstrates Tachyon's `McpJavaAnnotationProvider`: a plain Java service using mcp-java
annotations, with no Tachyon imports in the service itself.

## Features

- **Tool**: `add` — accepts scalar arguments and returns their sum.
- **Resource**: `app://config` — serves static JSON configuration.
- **Resource template**: `app://greeting/{name}` — creates a greeting for a URI parameter.
- **Prompt**: `welcome` — creates a user message from a prompt argument.

## Quickstart

The annotation integration is not published in a release yet. Install the Tachyon SNAPSHOT
locally first from the repository root:

```shell
./mvnw install -pl tachyon-api,tachyon-core,integrations/tachyon-annotations-mcp-java -am -DskipTests
```

Then build and run the example:

```shell
mvn package
java -jar target/mcp-java-example.jar
```

The server listens on `http://localhost:8080/mcp` by default. Set `HOST` and `PORT` to change it.
