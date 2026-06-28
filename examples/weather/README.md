# Weather MCP Example

Demonstrates the Tachyon MCP Server with MCP Java SDK 2.0 client.

## Features

- **Tool**: `get-weather` — returns current weather for a city
- **Resource**: `weather://prediction/article` — markdown article about weather prediction
- **Resource Template**: `weather://forecast/{city}` — JSON forecast for any city
- **Resource**: `weather://current/image` — PNG weather icon (base64 blob)
- **Prompt**: `rewrite-forecast` — rewrites a forecast in a given style

