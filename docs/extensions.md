# Extensions — Tachyon MCP Server

Extensions add custom MCP methods and negotiate capabilities with clients via the `initialize` handshake. They implement [SEP-2133](https://modelcontextprotocol.io/seps/2133-extensions).

## The McpExtension interface

```java
import dev.tachyonmcp.server.extensions.McpExtension;
import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.session.McpContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import java.util.Map;
import java.util.Set;

public class AuditExtension implements McpExtension {

    @Override
    public String extensionId() {
        return "com.example/audit";  // reverse-DNS format
    }

    @Override
    public Set<String> methods() {
        return Set.of("audit/log");  // methods this extension owns
    }

    @Override
    public void bootstrap(McpServer server) {
        server.registerHandler("audit/log", (ctx, params) -> {
            // handle audit/log method
            return server.responseMapper().emptyResult();
        });
    }

    @Override
    public void onConnectionInit(McpContext ctx, Map<String, JsonNode> clientSettings) {
        // called when a client negotiates this extension
    }

    @Override
    public JsonNode serverSettings() {
        // settings returned to the client during initialize
        return JsonNodeFactory.instance.objectNode()
            .put("version", "1.0");
    }
}
```

## Register an extension

```java
TachyonServer.builder()
    .extension(new AuditExtension())
    .port(8080)
    .start();
```

## How negotiation works

1. Client sends `initialize` with `"extensions": {"com.example/audit": {}}` in capabilities.
2. Tachyon calls `onConnectionInit` for each negotiated extension.
3. `serverSettings()` is returned to the client in `initialize` response under the extension key.
4. Methods declared in `methods()` are only routed for sessions that negotiated the extension.

## Built-in: TasksExtension

`TasksExtension.instance()` is the reference implementation. See [tasks.md](tasks.md) for details.

## Extension shutdown

Override `shutdown()` to release resources when the server stops:

```java
@Override
public void shutdown() {
    scheduler.shutdown();
}
```

---

**See also:** [Tasks](tasks.md) · [Tools](tools.md) · [Quickstart](quickstart.md)
