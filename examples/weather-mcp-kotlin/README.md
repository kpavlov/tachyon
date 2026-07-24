# Weather MCP Kotlin Example

Kotlin-DSL port of [`examples/weather`](../weather), demonstrating the Tachyon MCP Server's
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
java -jar target/weather-mcp-kotlin-example-1.0-SNAPSHOT.jar
```
