# SEP-1686: Tasks — Implementation Notes

SEP status: **Final** (Standards Track). Source: modelcontextprotocol/modelcontextprotocol#1686

---

## What Tasks Are

Tasks are a generic, durable state machine that augments any MCP request (tool call, resource read, prompt get) to support **call-now, fetch-later** patterns. The client generates the task ID; the server tracks execution state and retains results up to a configurable `keepAlive` duration.

Key use cases: long-running tools (minutes to hours), workflow APIs (Step Functions, CI/CD pipelines), multi-agent orchestration, deep research.

> 🔀 **Core → Extension migration.** In `2025-11-25` (Tachyon's current negotiated version) Tasks are a **core** feature advertised via `capabilities.tasks`. In the newer draft they are moving into an **extension** (`io.modelcontextprotocol/tasks`). Tachyon bridges both: a single `TaskRegistry` backs both the core task handlers (for 2025-11-25 clients) and a `TasksExtension` (for draft clients that negotiate it). See `docs/extensions-design.md` → "Tasks as the Reference Extension".

---

## Protocol Summary

### Creating a Task

Client includes `_meta.modelcontextprotocol.io/task` in any request:

```json
{
  "jsonrpc": "2.0", "id": 1, "method": "tools/call",
  "params": {
    "name": "analyze_dataset",
    "arguments": { "dataset": "large_file.csv" },
    "_meta": {
      "modelcontextprotocol.io/task": {
        "taskId": "786512e2-9e0d-44bd-8f29-789f320fe840",
        "keepAlive": 60000
      }
    }
  }
}
```

Server MUST immediately send `notifications/tasks/created` with the task ID in `_meta.modelcontextprotocol.io/related-task`. All subsequent task-related messages carry this same metadata.

### Task Status Lifecycle

```
[*] → submitted → working ⟷ input_required → (completed | failed | cancelled | unknown)
```

- `submitted` — received, queued
- `working` — being processed
- `input_required` — waiting on client input (e.g. elicitation)
- `completed` — done, result available
- `failed` — task-level error (not request-level)
- `cancelled` — cancelled by client
- `unknown` — terminal fallback for unexpected errors

Terminal states cannot transition further. Servers MAY move directly `submitted → completed` for fast operations.

### Methods

| Method | Direction | Description |
|--------|-----------|-------------|
| `tasks/get` | client→server | Poll current task state |
| `tasks/result` | client→server | Retrieve completed task result |
| `tasks/list` | client→server | Paginated list of tasks (cursor-based) |
| `tasks/delete` | client→server | Explicitly delete task and results |

`tasks/result` MUST error if status is not `completed`. `tasks/get` response includes `keepAlive` (actual, may differ from requested), optional `pollFrequency` (ms), and optional `error` field for `failed` state.

> ⚠️ **There is no `tasks/cancel` method in SEP-1686.** Cancellation is performed by the requestor sending a `notifications/cancelled` notification targeting the **original JSON-RPC request ID** of the task-augmented request (§4.8). The receiver SHOULD then move the task to `cancelled` and halt processing. Tachyon's existing `tasks/cancel` request method is a **non-standard extension** to the spec — keep it if clients depend on it, but the standards-compliant path is `notifications/cancelled`.

### Capabilities

The Final SEP-1686 (after PR #1732) requires that a server or client supporting task-augmented requests **MUST declare a `tasks` capability** during `initialize`, structured by request category (e.g. `tasks.requests.tools.call`).

Graceful degradation is a **separate** concern and does NOT mean the capability is optional: a receiver that does *not* support tasks simply ignores `_meta.modelcontextprotocol.io/task` and processes the request normally (returning the result inline). The requestor detects non-support by the absence of `notifications/tasks/created` and falls back to the normal response.

(Note: the SEP's "No Capabilities Declaration" rationale section reflects an earlier design that was superseded by #1732. The capability declaration is now required.)

### Security

- Bind tasks to session/auth context — reject cross-session lookups
- Rate-limit task creation, polling, and result retrieval
- Enforce max `keepAlive` and concurrent task limits
- Prompt clients to delete sensitive task results promptly

---

## Current Tachyon Implementation Gaps

Tachyon has a `TaskRegistry` with basic CRUD — but it diverges from SEP-1686 in several ways.

### ❌ Missing: `SUBMITTED` status

SEP-1686 requires tasks to begin in `submitted`. Tachyon's `TaskStatus` starts in `WORKING`. Add `SUBMITTED` to the enum with transitions `submitted → working | input_required | completed | failed | cancelled | unknown`.

### ❌ Missing: `UNKNOWN` terminal status

Add `UNKNOWN` to `TaskStatus` with no outgoing transitions.

### ❌ Missing: client-generated task IDs

Tachyon currently generates IDs server-side (`UUID.randomUUID()` in `createTask()`). SEP-1686 requires **client-supplied** `taskId` from `_meta.modelcontextprotocol.io/task`. The server creates the task entry using the client-provided ID and rejects duplicates with `-32602`.

### ❌ Missing: `tasks/delete` handler

`TaskRegistry.registerHandlers()` needs a `tasks/delete` handler. Deletion is optional but the method must be routable.

### ❌ Missing: `notifications/tasks/created`

Currently only `notifications/tasks/status` exists. When a task is created (in response to a task-augmented request), server must send `notifications/tasks/created` with `_meta.modelcontextprotocol.io/related-task`.

### ❌ Missing: task augmentation detection in dispatcher

`JsonRpcDispatcher` does not inspect `_meta.modelcontextprotocol.io/task`. Tool call (and other request) dispatch must check for this key, create the task entry (with client ID), send `notifications/tasks/created`, execute the handler asynchronously, and store the result instead of returning it inline.

### ❌ Missing: `keepAlive` and `pollFrequency` fields

`TaskEntry` has `ttl` (double, seconds). SEP-1686 uses `keepAlive` (milliseconds). Rename or add `keepAlive` field. Add `pollFrequency` to `tasks/get` response (server-side hint).

### ❌ Missing: `_meta.modelcontextprotocol.io/related-task` in responses

All `tasks/get`, `tasks/result`, `tasks/list`, `tasks/delete` responses MUST echo the related-task metadata.

### ❌ Missing: `notifications/cancelled` → `cancelled` transition

§4.8: when a `notifications/cancelled` arrives for the original request ID of a task-augmented request, the server SHOULD move the task to `cancelled` and stop processing. Tachyon handles `notifications/cancelled` for pending requests (see `JsonRpcDispatcher.handleCancellation`) but does not link it to task state. Wire cancellation of a task-augmented request to `TaskRegistry.cancelTask()`.

### ⚠️ Non-standard: `tasks/cancel` request method

Tachyon exposes a `tasks/cancel` request handler. This is not part of SEP-1686 (cancellation is via `notifications/cancelled`). Decide whether to keep it as a vendor extension or remove it in favor of the standard path.

### ✅ Already in place

- `tasks/get`, `tasks/list`, `tasks/result` handlers
- `TaskEntry` state machine with terminal state enforcement
- TTL janitor for expiry
- Session-scoped tasks (via `McpServer` context)
- `notifications/tasks/list_changed` broadcast on state change
- Cursor-based pagination via `Registry.list()`
