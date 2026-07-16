# Stateless Session — Implementation Instructions

## Context

This task implements a **stateless session mode** for the Tachyon MCP server. In stateless mode, the server processes each POST request without creating or persisting a `McpSession`. The GET SSE stream still works (for server→client notifications during a POST), but there is no event replay on reconnect. This is useful for lambda/serverless deployments or single-client scenarios where session persistence is unwanted overhead.

**Work in progress**: `ServerConfig` has already been updated. Everything else is TODO.

---

## Project Layout (relevant files)

```
tachyon-mcp-server/src/main/java/me/kpavlov/tachyon/mcp/server/
  ServerConfig.java                         ← ALREADY UPDATED (see below)
  McpServer.java                            ← needs isStateless()
  builder/
    BuilderState.java                       ← needs stateless field + pass to ServerConfig
    CapabilitiesStep.java                   ← needs stateless() method
  netty/
    McpEndpointHandler.java                 ← needs stateless GET/POST paths
    SseManager.java                         ← needs nullable-session overload, skip replay
    JsonRpcDispatcher.java                  ← needs stateless dispatch paths
```

---

## What Is Already Done

### `ServerConfig.java` — COMPLETE

```java
public record ServerConfig(Duration sessionTtl, boolean stateless) {
    public static final ServerConfig DEFAULT = new ServerConfig(Duration.ofSeconds(30), false);

    public static ServerConfig of(Duration sessionTtl) {
        return new ServerConfig(sessionTtl, false);
    }
}
```

---

## What Needs to Be Done

### 1. `BuilderState.java` — fix broken call + add field

The call `new ServerConfig(sessionTtl)` (line 96) no longer compiles because the record now has 2 fields.

Add `boolean stateless = false;` to the fields, and fix the `build()` method:

```java
// field
boolean stateless = false;

// in build():
var config = new ServerConfig(sessionTtl, stateless);
```

---

### 2. `SessionStep.java` — add `stateless()` method

Add to the "Capabilities" section (alongside `sessionTtl`, `SessionLogRouter`):

```java
public SessionStep stateless(boolean stateless) {
    state.stateless = stateless;
    return this;
}
```

Default is `false` so existing builder chains are unaffected.

If stateless is true then do not allow sessionTtl and SessionLogRouter and others parameters.

---

### 3. `McpServer.java` — store config, expose `isStateless()`

Currently `config` is used only in the constructor (`sessionManager.startJanitor(config.sessionTtl())`) and then discarded. Store it:

```java
private final ServerConfig config;
```

Assign in the 6-arg constructor:

```java
this.config = config;
```

Expose:

```java
public boolean isStateless() {
    return config.stateless();
}
```

don't start session janitor if sessions not supported (stateless)

---

### 4. `JsonRpcDispatcher.java` — stateless dispatch paths

#### 4a. `dispatchInitializeAsync` — skip session creation when stateless

Current code:
```java
var sessionId = generateSessionId();
var session = server.createSession(sessionId);
// ... handler.handle(context, typedParams) ...
return new DispatchResult(encodeResponse(id, result), sessionId);
```

Stateless path: do NOT call `server.createSession()`. Use `ToolContextImpl.noop()` (already used by `dispatchPingAsync`). Return `null` as the sessionId so no `MCP-Session-Id` header is emitted:

```java
private CompletableFuture<DispatchResult> dispatchInitializeAsync(Object id, Object rawParams) {
    return CompletableFuture.supplyAsync(
            () -> {
                logger.info("Client initialize: id={} stateless={}", id, server.isStateless());
                var handler = server.getHandler("initialize");
                if (handler == null) {
                    return errorResult(id, McpErrors.METHOD_NOT_FOUND, "Method not found: initialize");
                }
                var typedParams = convertParams(rawParams, InitializeRequestParams.class);
                if (server.isStateless()) {
                    // Stateless: no session created, no session ID issued
                    try {
                        var result = handler.handle(ToolContextImpl.noop(), typedParams);
                        if (result instanceof McpError error) {
                            return errorResult(id, error.code(), error.message());
                        }
                        return new DispatchResult(encodeResponse(id, result), null);
                    } catch (Exception e) {
                        logger.error("Initialize handler exception (stateless)", e);
                        return errorResult(id, McpErrors.INTERNAL_ERROR, e.getMessage());
                    }
                }
                // Stateful (current logic)
                var sessionId = generateSessionId();
                var session = server.createSession(sessionId);
                try {
                    var context = new ToolContextImpl(session, server);
                    var result = handler.handle(context, typedParams);
                    if (result instanceof McpError error) {
                        return errorResult(id, error.code(), error.message());
                    }
                    return new DispatchResult(encodeResponse(id, result), sessionId);
                } catch (Exception e) {
                    logger.error("Initialize handler exception", e);
                    return errorResult(id, McpErrors.INTERNAL_ERROR, e.getMessage());
                }
            },
            executor);
}
```

#### 4b. `dispatchRequestAsync` — stateless fast-path

After the `METHOD_INITIALIZE` block and before the existing session-ID check, add:

```java
// Stateless mode: no session required, dispatch directly
if (server.isStateless()) {
    return dispatchStatelessAsync(id, method, params, outboundSseStream);
}
```

Then add the method:

```java
private CompletableFuture<DispatchResult> dispatchStatelessAsync(
        Object id, String method, Object params, @Nullable OutboundSseStream outboundSseStream) {
    var handler = server.getHandler(method);
    if (handler == null) {
        var err = McpErrors.methodNotFound("Method not found: " + method);
        return CompletableFuture.completedFuture(errorResult(id, err.code(), err.message()));
    }
    return CompletableFuture.supplyAsync(
            () -> {
                try {
                    var result = OutboundSseStreamMessageRouter.withDispatchContext(
                            null, outboundSseStream, () -> handler.handle(ToolContextImpl.noop(), params));
                    if (result instanceof McpError error) {
                        return errorResult(id, error.code(), error.message());
                    }
                    return new DispatchResult(encodeResponse(id, result), null);
                } catch (Exception e) {
                    logger.error("Stateless handler exception: method={}", method, e);
                    return errorResult(id, McpErrors.INTERNAL_ERROR, e.getMessage());
                }
            },
            executor);
}
```

#### 4c. `dispatchNotification` — stateless path

At the top of `dispatchNotification`, when stateless:
- Skip session lookup (`notifications/initialized` activation is a no-op)
- Still handle `notifications/cancelled` and `notifications/tasks/status` best-effort

The simplest approach: early return `DispatchResult.ACCEPTED` for all notifications in stateless mode, since there is no session state to update:

```java
public DispatchResult dispatchNotification(String method, @Nullable Object params, @Nullable String sessionId) {
    if (server.isStateless()) {
        logger.debug("Stateless notification ignored: {}", method);
        return DispatchResult.ACCEPTED;
    }
    // ... existing code ...
}
```

---

### 5. `McpEndpointHandler.java` — stateless GET and DELETE


#### 5b. `handleGet` — stateless path (no session ID required, no replay)

Replace the current `handleGet`:

```java
private void handleGet(ChannelHandlerContext ctx, FullHttpRequest req, String origin) {
    var lastEventId = req.headers().get(McpHeaderNames.LAST_EVENT_ID);

    if (server.isStateless()) {
        // Stateless: open SSE without session; store connection in channel attribute
        sseManager.openStatelessStream(ctx, origin);
        return;
    }

    var sessionId = req.headers().get(McpHeaderNames.MCP_SESSION_ID);
    if (sessionId == null) {
        sendPlainText(ctx, HttpResponseStatus.BAD_REQUEST, "Missing MCP-Session-Id header", origin);
        return;
    }
    var sessionOpt = server.getSession(sessionId);
    if (sessionOpt.isEmpty()) {
        sendPlainText(ctx, HttpResponseStatus.BAD_REQUEST, "Unknown session: " + sessionId, origin);
        return;
    }
    sseManager.openStream(ctx, sessionOpt.get(), lastEventId, origin);
}
```

#### 5c. `handleDelete` — stateless: return 405

In `handleDelete`, add at the top:
```java
if (server.isStateless()) {
    sendPlainText(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "Session management not available in stateless mode", origin);
    return;
}
```

---

### 6. `SseManager.java` — add `openStatelessStream()`

Add next to the existing `openStream`:

```java
public void openStatelessStream(ChannelHandlerContext ctx, @Nullable String origin) {
    var connection = new NettySseConnection(ctx.channel(), () ->
            logger.debug("Stateless SSE connection closed: {}", ctx.channel().remoteAddress()));

    // Store in channel attribute so PostSseStream notifications can reach this connection
    ctx.channel().attr(McpEndpointHandler.STATELESS_SSE_KEY).set(connection);

    var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream")
            .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
            .set(HttpHeaderNames.CACHE_CONTROL, "no-cache")
            .set(HttpHeaderNames.CONNECTION, "keep-alive");
    if (origin != null) {
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    }
    ctx.write(response);

    // Retry hint — no replay in stateless mode
    ctx.writeAndFlush(
            new DefaultHttpContent(ByteBufUtil.writeUtf8(ctx.alloc(), "retry: " + SSE_RETRY_DELAY_MS + "\n")));

    var eventId = String.valueOf(server.nextEventId());
    var primeSse = new SseEvent(eventId, "message", "");
    connection.send(primeSse);

    logger.debug("Stateless SSE stream opened: {}", ctx.channel().remoteAddress());
}
```

No event replay is attempted — that is intentional.

---

## Compile Verification

**Do NOT run `mvn install`.** Use the reactor build:

```bash
mvn -pl tachyon-mcp-server test-compile
```

Then run unit tests:

```bash
mvn -pl tachyon-mcp-server test
```

After that, check e2e compiles:

```bash
mvn -pl tachyon-mcp-server,e2e test-compile
```

If Spotless fails, run:

```bash
mvn spotless:apply
```

---

## Key Constraints

- **Never run `mvn install`** — use reactor (`-pl`) or root `mvn test-compile` instead.
- All existing stateful behavior is unchanged (default `stateless=false`).
- `ToolContextImpl.noop()` is the right context for stateless dispatch — it already exists (used by `dispatchPingAsync`).
- `OutboundSseStreamMessageRouter.withDispatchContext(null, ...)` — the first arg (sessionId) can be null; verify this compiles. If `null` causes NPE, pass `"stateless"` as a sentinel string.
- `McpEndpointHandler` is `@Sharable` — the `STATELESS_SSE_KEY` `AttributeKey` must be `static final`.
- Import `io.netty.util.AttributeKey` if not already imported.

---

## Builder API (end-user perspective)

After implementation, users can write:

```java
var handle = TachyonServer.builder()
    .stateless(true)          // ← new method on CapabilitiesStep
    .tool(myTool)
    .port(8080)
    .start();
```

Or call it from any step that returns `CapabilitiesStep`:
```java
TachyonServer.builder()
    .name("my-server")
    .stateless(true)
    .port(8080)
    .start();
```

`IdentityStep` also needs to expose a `stateless()` shortcut if users want to skip `CapabilitiesStep` — add the same delegation there (sets `state.stateless`, returns `CapabilitiesStep` or `NetworksStep` as appropriate).
