# Weather MCP Kotlin Example

Kotlin-DSL port of [`../weather-mcp`](../weather-mcp), demonstrating the Tachyon MCP Server's
Kotlin builder (`tachyon-kotlin`) with the MCP Java SDK 2.0 client in tests.

## Features

- **Tool**: `get-weather` — current weather for a city, with progress notifications and
  elicitation fallback when the city is not found
- **Resource**: `weather://prediction/article` — Markdown article about weather prediction
- **Resource**: `weather://featured/current` — JSON weather snapshot for Tallinn
- **Resource Template**: `weather://current/{city}` — JSON forecast for any city
- **Prompt**: `rewrite-forecast` — rewrites a forecast in a chosen style, with argument
  auto-completion
- **Completions**: city name completion for the resource template, style completion for the
  prompt

## Quickstart

```shell
./mvnw package && \
java -jar target/weather-mcp-kotlin-example.jar
```

## Binding and access from Docker

`HOST` (default `localhost`), `PORT` (default `8080`), and `ALLOWED_HOST` (unset) control the bind
address and which extra `Host` authority the DNS-rebinding guard accepts. To reach the server from
a Docker container:

```shell
export HOST=0.0.0.0 && \
export ALLOWED_HOST=host.docker.internal:8080 && \
java -jar target/weather-mcp-kotlin-example.jar
```

⚠️ `HOST=0.0.0.0` publishes the port on every interface, not just loopback — anything that can
reach your machine can reach the server. Use a specific reachable address instead of `0.0.0.0`
when you can, and keep `ALLOWED_HOST` set so the `Host` check still filters requests.

See [../README.md](../README.md#binding-and-access-from-docker) for the full table.
