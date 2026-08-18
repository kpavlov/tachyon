# MCP TestKit

`tachyon-testkit` drives a running Tachyon server from tests: protocol-shaping HTTP clients,
in-process port-0 server helpers, and fluent JSON-RPC assertions.

```xml
<dependency>
    <groupId>dev.tachyonmcp</groupId>
    <artifactId>tachyon-testkit</artifactId>
    <version>${tachyon.version}</version>
    <scope>test</scope>
</dependency>
```

## Servers

`McpTestServers.start` builds a port-0 server, registers handlers, and starts it — closing the
transport if anything fails, so a broken test never leaks a listener:

```java
var server = McpTestServers.start(
    b -> b.session(c -> c.enabled(true)),
    s -> s.tools().register(descriptor, handler));
var port = server.port();
```

## Clients

`McpTestClients` builds a raw JSON-over-HTTP client for a protocol version — `Mcp20251125Client`
(session-based, `initialize` handshake) or `Mcp20260728Client` (sessionless, self-describing
requests):

```java
try (var client = McpTestClients.latest(port)) {
    client.post("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""");
}
```

`McpTestClients.builder(port)` skips the manual `initialize()` dance and returns an
already-initialized client for the chosen protocol version:

```java
try (var client = McpTestClients.builder(port).protocolVersion("2025-11-25").build()) {
    client.sendRpc("""{"jsonrpc":"2.0","id":1,"method":"ping"}""");
}
```

Pass a `URI` instead of a port (`McpTestClients.builder(URI.create("https://staging.example.com/mcp"))`)
to drive a remote server instead of a local one.

## Assertions

`JsonRpcResponseAssert` gives AssertJ-style assertions over a JSON-RPC response envelope —
success/error branch, tool content, and JSON-RPC error code/message:

```java
import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThat;

var response = client.post("""
    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hi"}}}
    """);

assertThat(response).hasTextContent("echo:hi");
```

For raw JSON-RPC error responses, use `assertThatJsonRpcResponse(String)` (avoids an ambiguous
overload against AssertJ's own `assertThat(String)`):

```java
import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThatJsonRpcResponse;

var raw = client.sendRpc("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"missing","arguments":{}}}""");

assertThatJsonRpcResponse(raw).hasErrorCode(-32602).hasErrorMessageContaining("missing");
```

## Notifications

Every client captures server-to-client notifications delivered over SSE; await one by method
name, or take a snapshot of everything received so far:

```java
client.awaitNotification("notifications/progress")
    .satisfies(params -> assertThat(params.path("progressToken").asString()).isEqualTo("tok-1"));
```
