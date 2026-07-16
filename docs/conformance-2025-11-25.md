# Tachyon MCP — Conformance v2025-11-25

**Status**: ✅ 39/39 pass — `InMemoryServerConformanceIT` + `ChronicleServerConformanceIT`  
**Spec**: `modelcontextprotocol/docs/specification/2025-11-25`  
**Runner**: `@modelcontextprotocol/conformance@0.1.16`

---

## Open issues  
🔵 **LOW** — E2: stale session lingers 30s TTL on re-initialize  

---

## Protocol coverage

### Base protocol
- [x] JSON-RPC 2.0 format — `McpJsonCodecTest`
- [x] Request id: string/int, not null
- [x] Response includes same id + `result`
- [x] Error response: `error.code` + `error.message`
- [x] Notification has no id, no response sent

### Streamable HTTP transport
- [x] Single `/mcp` endpoint: POST / GET / DELETE / OPTIONS — `McpEndpointHandlerTest`
- [x] POST → JSON response or 202 for notifications
- [x] POST → SSE with priming event for requests
- [x] Server MAY send notifications/requests before response (lazy SSE upgrade via `PostSseStream`) — `PostSseStreamTest`
- [x] GET → long-lived SSE stream — `SseManagerTest`
- [x] Origin validation → 403 on invalid or missing — `McpEndpointHandlerTest`
- [x] DELETE terminates session
- [x] `MCP-Protocol-Version` header validated → 400 — `ProtocolVersionHandlerTest`
- [x] `MCP-Session-Id` returned on initialize
- [x] Missing session-id (non-init) → 400
- [x] Resumability via `Last-Event-ID` — `SseManagerTest`
- [x] `Accept` header strict validation → 406 — `McpEndpointHandlerTest`
- [x] `retry` field sent before SSE close — `SseRetryE2eTest`
- [x] OPTIONS / CORS headers

### Lifecycle
- [x] Initialize → version + capabilities in response — `McpSdkE2eTest`
- [x] `notifications/initialized` activates session — `JsonRpcDispatcherTest`
- [x] Non-ping before initialized → -32600 — `JsonRpcDispatcherTest`
- [x] All requests rejected after session closed — `JsonRpcDispatcherTest`
- [ ] Server SHOULD NOT send requests before initialized (not enforced)

### Capabilities (declared in initialize response)
- [x] `tools` + `listChanged: true` (when tools registered)
- [x] `resources` + `subscribe` + `listChanged` (when resources registered)
- [x] `prompts` + `listChanged` (when prompts registered)
- [x] `logging: {}` — always declared
- [x] `completions: {}` — always declared
- [x] `tasks` + `list` + `cancel` — always declared

### Tools
- [x] `tools/list` paginated — `ToolRegistryTest`
- [x] `tools/call` → `CallToolResult` — `McpSdkE2eTest`, `ToolCapabilitiesE2eTest`
- [x] Tool error → `isError: true`
- [x] Unknown tool → -32601 — `McpNegativeScenariosE2eTest`
- [x] `listChanged` notification on add/remove — `ToolNotificationsE2eTest`, `ToolRegistryTest`
- [x] `outputSchema` in `tools/list` — `ToolCapabilitiesE2eTest`
- [x] `execution.taskSupport` (forbidden/optional/required) — `ToolCapabilitiesE2eTest`
- [x] Tool name validation (1–128 chars) — `ToolRegistryTest`
- [x] `annotations` field — `ToolCapabilitiesE2eTest`
- [x] Inline notification + logging during tool call — `ToolNotificationsE2eTest`

### Resources
- [x] `resources/list`, `/read`, `/templates/list` — `ResourceRegistryTest`, `ResourceCapabilitiesE2eTest`
- [x] Not found → -32002 — `ResourceCapabilitiesE2eTest`
- [x] `resources/subscribe` + `/unsubscribe` — `ResourceRegistryTest`
- [x] `listChanged` notification on add/remove — `ResourceCapabilitiesE2eTest`
- [x] `updated` notification to subscribers — `ResourceCapabilitiesE2eTest`
- [x] Stale URI evicted when resource updated with new URI — `ResourceRegistryTest`
- [x] Template URI matching — `ResourceRegistry`

### Prompts
- [x] `prompts/list` paginated, `prompts/get` — `PromptRegistryTest`, `McpSdkE2eTest`
- [x] `listChanged` notification — `PromptRegistryTest`
- [x] Invalid name → error — `PromptRegistryTest`
- [x] `title` field in `prompts/list` — `PromptCapabilitiesE2eTest`
- [x] `arguments` field in `prompts/list` — `PromptCapabilitiesE2eTest`

### Logging
- [x] `logging/setLevel` per-session — `LoggingE2eTest`
- [x] `notifications/message` emitted above threshold — `LoggingE2eTest`, `ToolNotificationsE2eTest`
- [ ] Invalid level → -32602 (not tested)
- [ ] Rate limiting (not implemented)

### Completion
- [x] `completion/complete` (prompt + resource ref) — conformance suite

### Ping
- [x] `ping` → empty response, fast-path pre-session — `McpNegativeScenariosE2eTest`, `JsonRpcDispatcherTest`

### Sampling
- [x] `sampling/createMessage` via `sendRequest` — conformance suite

### Elicitation
- [x] `tools-call-elicitation` — conformance suite
- [x] `elicitation-sep1034-defaults` (form + defaults) — conformance suite
- [x] `elicitation-sep1330-enums` — conformance suite
- [ ] URL mode / -32042 error (not implemented)

### Tasks
- [x] Capability declared with `list` + `cancel`
- [x] `tasks/list`, `tasks/get`, `tasks/cancel`, `tasks/result` — `TaskRegistryTest`, `TaskLifecycleE2eTest`
- [x] State machine enforcement + TTL janitor — `TaskRegistryTest`
- [x] `notifications/tasks/status` broadcast — `TaskRegistryTest`
- [x] Stale `byId` entry evicted when task with same name replaced — `TaskRegistryTest`

### Utilities
- [x] Progress notifications (0, 50, 100 hardcoded)
- [x] `notifications/cancelled` — `JsonRpcDispatcherTest`
- [x] Cursor-based pagination across all 4 list methods

### Security & session
- [x] Origin validation: localhost / 127.0.0.1 allowed, others → 403 — `McpEndpointHandlerTest`
- [x] DNS-rebinding — conformance suite
- [x] INITIALIZING → ACTIVE → CLOSED state machine — `JsonRpcDispatcherTest`, `McpSessionTest`
- [x] HOT/WARM/COLD backpressure (logical) — `McpSessionTest`
- [x] Session janitor sweeps every 5s, 30s TTL — `SessionManager`
- [x] SSE disconnect ≠ session removal
- [x] Resumability: priming event, globally unique IDs, `Last-Event-ID` replay — `SseManagerTest`, `SseRetryE2eTest`
- [x] Pending request timeout: 60s with auto-cleanup — `McpServer`
- [x] Concurrent requests, same session (no dedup) ⚠️ E1
- [x] GET with unknown session-id → 400 (not created)
- [x] Backpressure wired: `channelWritabilityChanged` → `setAutoRead(false/true)` — `McpEndpointHandler`
- [x] Max request body 1 MB (`HttpObjectAggregator`) — `McpChannelInitializer`
- [x] Origin validated without per-request allocation — `McpEndpointHandler.isValidOrigin`
