# MCP Protocol TLDR (2025-11-25)

Quick reference for the Model Context Protocol. See `conformance-2025-11-25.md` for deviation analysis.

---

## Transport — Streamable HTTP

```
POST /mcp   → client sends JSON-RPC, server responds JSON or SSE
GET  /mcp   → client opens SSE stream for server→client messages
DELETE /mcp → session teardown
OPTIONS /mcp → CORS preflight
```

**Headers**: `MCP-Protocol-Version` (req for POST), `MCP-Session-Id` (after init), `Accept`, `Origin`.

**Session ID**: Generated server-side on initialize, returned in `MCP-Session-Id` response header. State: `INITIALIZING → ACTIVE → CLOSED`.

**SSE stream**: Priming event `event: id\ndata: {eventId}\n\n`, then server→client messages. After final JSON-RPC response, server MAY terminate stream. Client can resume via `Last-Event-ID`.

---

## JSON-RPC Shapes

| Type | Has `id` | Response expected | Method |
|------|----------|-------------------|--------|
| Request | ✅ | ✅ (result or error) | `"method": "<name>"` |
| Notification | ❌ | ❌ → 202 | `"method": "notifications/..."` |
| Response | N/A | N/A | `"result": {...}` or `"error": {"code": <int>, "message": <str>}` |

### Standard JSON-RPC error codes

| Code | Meaning | When |
|------|---------|------|
| -32700 | Parse error | Malformed JSON |
| -32600 | Invalid request | Bad params/missing fields |
| -32601 | Method not found | Unknown method |
| -32602 | Invalid params | Wrong args to known method |
| -32603 | Internal error | Unexpected server error |
| -32002 | Resource not found | Non-existent resource URI |
| -32042 | Elicitation required | URL elicitation before tool call |

---

## Lifecycle

1. Client sends `initialize` with protocol version + client info + capabilities
2. Server responds with `InitializeResult { protocolVersion, capabilities, serverInfo }`
3. Client sends `notifications/initialized`
4. Session becomes `ACTIVE` — normal operation begins
5. Client may `DELETE /mcp` to close session

---

## Server Capabilities Declaration

Declared in `initialize` response `capabilities`:

| Capability | Value | Meaning |
|------------|-------|---------|
| `experimental` | `{}` | Custom extensions |
| `logging` | `{}` | Supports `logging/setLevel` + messages |
| `completions` | `{}` | Supports `completion/complete` |
| `prompts` | `{listChanged: bool}` | Supports `prompts/list`, `prompts/get` |
| `resources` | `{subscribe: bool, listChanged: bool}` | Supports `resources/list/read/subscribe` |
| `tools` | `{listChanged: bool}` | Supports `tools/list`, `tools/call` |
| `tasks` | `{list: {}, cancel: {}}` | Supports `tasks/list/get/cancel/result` |

---

## Server Methods (requests from client to server)

| Method | Returns | Notes |
|--------|---------|-------|
| `initialize` | `InitializeResult` | Must be first |
| `ping` | `EmptyResult {}` | Health check |
| `logging/setLevel` | `EmptyResult {}` | Per-session level |
| `completion/complete` | `CompleteResult` | Argument autocomplete |
| `tools/list` | `ListToolsResult` | Paginated |
| `tools/call` | `CallToolResult` | Can request sampling/elicitation from client |
| `resources/list` | `ListResourcesResult` | Paginated |
| `resources/read` | `ReadResourceResult` | Text or blob |
| `resources/templates/list` | `ListResourceTemplatesResult` | |
| `resources/subscribe` | `EmptyResult {}` | Watch resource for changes |
| `resources/unsubscribe` | `EmptyResult {}` | |
| `prompts/list` | `ListPromptsResult` | Paginated |
| `prompts/get` | `GetPromptResult` | May invoke prompt resolver for dynamic args |
| `tasks/list` | `ListTasksResult` | Paginated |
| `tasks/get` | `GetTaskResult` | Query single task |
| `tasks/cancel` | `CancelTaskResult` | Cancel a task |
| `tasks/result` | `GetTaskPayloadResult` / error | Retrieve completed/failed task payload |

---

## Server Notifications (client → server, fire-and-forget → 202)

| Notification | Notes |
|-------------|-------|
| `notifications/initialized` | Sent after initialize response received; activates session |
| `notifications/cancelled` | Cancels a previously-issued request from server (e.g. sampling, elicitation) |
| `notifications/roots/list_changed` | Client root list changed |
| `notifications/tasks/status` | Client informs server of task status changes |

---

## Server→Client Requests (on GET SSE stream)

The server can send these **requests** to the client while processing a tool call:

| Request | Purpose |
|---------|---------|
| `sampling/createMessage` | Ask client to call LLM on server's behalf |
| `elicitation/create` | Ask client for user input (form/URL mode) |
| `tasks/get` | Server can query client for task info |
| `tasks/result` | Server can request task result from client |
| `tasks/list` | Server can list tasks on client |
| `tasks/cancel` | Server can request cancellation of a client task |

**Key pattern**: A `tools/call` handler that needs LLM or user input must:
1. Send `sampling/createMessage` or `elicitation/create` as a JSON-RPC **request** on the session's SSE stream
2. Wait for the client's **response** on POST
3. Use that response data in the tool's `CallToolResult`

---

## Server→Client Notifications (on GET SSE stream)

| Notification | When |
|-------------|------|
| `notifications/message` | Logging output (respects `logging/setLevel`) |
| `notifications/progress` | Progress of long operations (matches `progressToken` from request `_meta`) |
| `notifications/resources/list_changed` | Resource list changed |
| `notifications/resources/updated` | Subscribed resource content changed |
| `notifications/tools/list_changed` | Tool list changed |
| `notifications/prompts/list_changed` | Prompt list changed |
| `notifications/tasks/status` | Task state transitions |

---

## Pagination Pattern

Request: `{ limit: int, cursor: string }`  
Response: `{ ...items..., nextCursor: string }`

`cursor` is opaque — server encodes last-seen item. `limit` is maximum to return. If `nextCursor` is absent/empty, there are no more results.

---

## Cancellation

- Either side can send `notifications/cancelled` with `{ requestId, reason? }`
- `requestId` must match a previously-issued request in the same direction
- Tasks use `tasks/cancel` request, not `notifications/cancelled`
- `initialize` MUST NOT be cancelled
