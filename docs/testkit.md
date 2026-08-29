# MCP TestKit

`tachyon-testkit` drives a running Tachyon server from tests: protocol-shaping HTTP clients,
in-process port-0 server helpers, and fluent JSON-RPC assertions.

Version is pinned by the `tachyon-bom` — see [Quickstart](quickstart.md#1-add-the-dependency).

```xml
<dependency>
    <groupId>dev.tachyonmcp</groupId>
    <artifactId>tachyon-testkit</artifactId>
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

`JsonRpcResponseAssert` first selects the JSON-RPC branch, then exposes only assertions valid for
that branch:

```java
import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThat;

var response = client.post("""
    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hi"}}}
    """);

assertThat(response).isSuccess().hasTextContent("echo:hi");
```

Use `hasResult(expected)` or `hasContentExactly(blocks...)` when the complete result or content
array is stable. `hasContent()` requires at least one content block.

Error assertions follow the same staged shape:

```java
import static dev.tachyonmcp.testkit.McpHttpResponseAssert.assertThatResponse;

final HttpResponse<String> response = client.sendRpc("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"missing","arguments":{}}}""");

assertThatResponse(response)
    .isJsonRpcError()
    .hasErrorCode(-32602)
    .hasErrorMessage("Invalid params");
```

### HTTP status and transport rejections

`JsonRpcResponseAssert` reads the body as a JSON-RPC envelope, so it cannot speak for the HTTP
status, nor for a rejection the transport answers in plain text before an envelope exists —
a duplicate MCP header, a protocol version that cannot validate mirrored headers, an oversized
body. `McpHttpResponseAssert` covers both and chains into the JSON-RPC assertions:

```java
import static dev.tachyonmcp.testkit.McpHttpResponseAssert.assertThatResponse;

assertThatResponse(response).hasStatus(200).isSuccess().hasTextContent("echo:hi");
assertThatResponse(response).hasStatus(400).isJsonRpcError().hasId(9).hasErrorCode(-32020);
assertThatResponse(response).isRejectedWith(400, "Duplicate MCP header");
```

Prefer `isRejectedWith` over a bare status check for transport rejections: a JSON-RPC error
carries the same `400`, so the status alone does not prove which layer rejected the request.

## Notifications

Every client captures server-to-client notifications delivered over SSE; await one by method
name, or take a snapshot of everything received so far:

```java
client.awaitNotification("notifications/progress")
    .satisfies(params -> assertThat(params.path("progressToken").asString()).isEqualTo("tok-1"));
```
