# Tachyon Testkit

Test harness for driving a running Tachyon MCP server: protocol-shaping HTTP clients and
in-process, port-0 server lifecycle helpers.

```xml
<dependency>
    <groupId>dev.tachyonmcp</groupId>
    <artifactId>tachyon-testkit</artifactId>
    <version>${tachyon.version}</version>
    <scope>test</scope>
</dependency>
```

## Clients

`McpTestClients` builds a client for a protocol version:

```java
try (var client = McpTestClients.latest(port)) {        // 2026-07-28
    var list = client.post("""
        {"jsonrpc":"2.0","id":1,"method":"tools/list"}
    """.trim());
};

try (var client = McpTestClients.forVersion(port, "2025-11-25")) {
    var sessionId = client.initialize();
    client.sendRpc("""
        {"jsonrpc":"2.0","id":2,"method":"ping"}
    """.trim());
};
```

- `Mcp20260728Client` shapes each request for MCP 2026-07-28 automatically: the required
  `_meta` (`io.modelcontextprotocol/protocolVersion` matching the `MCP-Protocol-Version` header,
  `clientInfo`, `clientCapabilities`) plus `Mcp-Method`/`Mcp-Name` headers. Declare negotiated
  extensions with `withExtensions(Map)`; pass extra headers (e.g. `Mcp-Param-*`) via
  `post(body, headers)`.
- `Mcp20251125Client` adds the `initialize`/`notifications/initialized` handshake and session
  tracking.

## Servers

```java
var server = McpTestServers.start(
    b -> b.session(c -> c.enabled(true)),
    s -> s.tools().register(descriptor, handler));   // port 0 -> bound port()
```

`McpTestServers.start`/`startSafely` build, register, start on an ephemeral port, and close the
transport if startup or registration fails so nothing leaks.
