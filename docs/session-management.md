# Session Management

## Stateless Mode

When `stateless(true)` is set in the builder, the server skips all session management:

- `initialize` is handled with no session created; the response omits `MCP-Session-Id`
- All subsequent POST requests are dispatched without session lookup or state checks
- `notifications/initialized` and other session notifications are silently accepted and ignored
- `GET /mcp` opens an SSE stream without a session ID requirement; `Last-Event-ID` is ignored (no event log, no replay)
- `DELETE /mcp` returns 405 — there are no sessions to terminate
- Any request (POST or GET) that includes a `MCP-Session-Id` header returns **404** — the server never issued one
- The session janitor is not started

This mode is suitable for lambda/serverless deployments or single-client scenarios where per-request statelessness is preferred over session continuity.

---

## Stateful Mode (default)

### Session lifecycle

```
CREATE → INITIALIZING → ACTIVE → CLOSED → (janitor removes)
```

- **CREATE**: On `initialize` request. Session stored in `SessionManager`.
- **ACTIVE**: On `notifications/initialized`. Client-server communication allowed.
- **CLOSED**: On `close()` call. Stays in map until janitor sweeps.
- **REMOVED**: Janitor removes `CLOSED` or stale sessions after 30s TTL.

### SSE disconnect ≠ session termination

Per MCP spec, client can reconnect dropped SSE with `Last-Event-ID`. On SSE channel close:
- Connection replaced with `SseConnection.NOOP` (no-op stub)
- `lastActivityNanos` updated — janitor timeout starts
- Session NOT removed — survives for reconnect

Explicit `DELETE /mcp` terminates session immediately.

### GET with unknown session ID

`GET /mcp` with an unrecognised `MCP-Session-Id` returns **400** and does not create a session.
Sessions are created only by `POST /mcp` carrying an `initialize` request.
Accepting unknown IDs on GET was a resource-exhaustion vector.

### SessionManager (interface)

Decouples session lifecycle from server logic. Enables future distributed impl:
- `InMemorySessionManager` (current) — `ConcurrentHashMap`
- Future: `RedisSessionManager` / `JdbcSessionManager`

### SessionJanitor

Scheduled sweep every 5 seconds. Removes:
- `CLOSED` sessions (closed but not yet removed)
- Sessions idle > 30s (`lastActivityNanos < now - 30s`)

| Event | Action | Why |
|-------|--------|-----|
| SSE channel closes | `connection(SseConnection.NOOP)`, `touch()` | Client may reconnect |
| `DELETE /mcp` | `removeSession()` immediately | Explicit teardown |
| Janitor timeout | `removeSession()` | Clean up stale sessions |
| `server.close()` | `manager.close()` removes all | Shutdown |

SessionJanitor runs on the platform thread. It sleeps 5s between sweeps — virtual thread would pin a carrier for nothing.
The sweep itself is a microsecond-scale O(n) iteration (no I/O).
NewSingleThreadScheduledExecutor defaults to platform thread, which is the right choice.

### lastActivityNanos

Updated on: `connection()`, `activate()`. NOT updated on every request (avoids `volatile` write on hot path). Janitor timeout is generous (30s) so the tradeoff is fine.

## Dropped Requirement: No Duplicate Request IDs

**Requirement**: "Request ID MUST NOT be reused within session" (spec `basic/index.mdx:48-49`).

**Decision**: Not enforced — no tracking, no validation.

**Rationale**: Request ID uniqueness is exclusively a client concern. A client reusing an ID creates ambiguity for its own response matching; the server is unaffected (it processes each request independently). No serious JSON-RPC implementation (including MCP reference servers) enforces this. Tracking would require unbounded state per session (or bounded LRU with warts), adding complexity for zero protocol-conformance value.
