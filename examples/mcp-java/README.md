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

## Binding and access from Docker

`HOST` (default `localhost`), `PORT` (default `8080`), and `ALLOWED_HOST` (unset) control the bind
address and which extra `Host` authority the DNS-rebinding guard accepts. To reach the server from
a Docker container:

```shell
export HOST=0.0.0.0 && \
export ALLOWED_HOST=host.docker.internal:8080 && \
java -jar target/mcp-java-example.jar
```

⚠️ `HOST=0.0.0.0` publishes the port on every interface, not just loopback — anything that can
reach your machine can reach the server. Use a specific reachable address instead of `0.0.0.0`
when you can, and keep `ALLOWED_HOST` set so the `Host` check still filters requests.

See [../README.md](../README.md#binding-and-access-from-docker) for the full table.
