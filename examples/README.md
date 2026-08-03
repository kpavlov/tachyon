# Examples

Standalone, runnable MCP servers built with Tachyon. Each example is its own Maven project with
the Maven wrapper build in, so you can run it without installing anything else — just a JDK 21+.

- [**echo-kotlin**](echo-kotlin) — Minimal server with `echo` and `reverse-echo` tools in Kotlin.
- [**weather-mcp**](weather-mcp) — Java. Full MCP surface: tools, resources, resource templates,
  prompts, completions and elicitation.
- [**weather-mcp-kotlin**](weather-mcp-kotlin) — Kotlin port of `weather-mcp`

Start with **echo-kotlin** to see the smallest viable server, then move to **weather-mcp** (or its
Kotlin twin) for a realistic feature-rich example backed by the Open-Meteo API.

Run a server from its own directory — each example's README has the exact build and run commands.

Looking for the API and docs? See the main [README](../README.md).
