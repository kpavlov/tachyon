# Weather MCP Example

Demonstrates the Tachyon MCP Server with MCP Java SDK 2.0 client.

## Features

- **Tool**: `get-weather` — returns current weather for a city
- **Resource**: `weather://prediction/article` — Markdown article about weather prediction
- **Resource Template**: `weather://forecast/{city}` — JSON forecast for any city
- **Resource**: `weather://current/image` — PNG weather icon (base64 blob)
- **Prompt**: `rewrite-forecast` — rewrites a forecast in a given style

## Quickstart

Override the bind address/port with `HOST`/`PORT` env vars (default `localhost:8080`):

```shell
./mvnw package -DskipTests -q && \
export HOST=127.0.0.1 && \
export PORT=8080 && \
java -jar target/weather-example.jar
```

By default only localhost/loopback requests are accepted (DNS-rebinding protection). To allow
another `Host` header — e.g. a Docker-bridge caller using `host.docker.internal` — set
`ALLOWED_HOST`:

```shell
export HOST=0.0.0.0 && \
export ALLOWED_HOST=host.docker.internal:8080 && \
java -jar target/weather-example.jar
```

⚠️ `HOST=0.0.0.0` publishes the port on every interface, not just loopback — anything that can
reach your machine can reach the server. Use a specific reachable address instead of `0.0.0.0`
when you can, and keep `ALLOWED_HOST` set so the `Host` check still filters requests.
