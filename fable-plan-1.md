# Tachyon observation lifecycle (passive, interceptor-free)

## Context

Tachyon has no telemetry seam on `main` (77b14d01, fetched fresh). PR #288 / branch
`interceptors` (34205fb5, merge-base 25c62b46) adds one — `McpInterceptor`, an
around-advice chain with `proceed()`/`reject()` — plus `integrations/tachyon-otel`
built on top of it. That branch is **not to be merged or cherry-picked**: this task
replaces it with a passive, read-only observation seam that has no control-flow
power, and ships the OTel module on top of *that* instead.

**Why not adapt the interceptor branch:** reviewing it turned up the exact defect
this task warns about. `McpDispatcher`'s interceptor terminal
(`handled()`, diff to `McpDispatcher.java`) calls
`HandlerFutures.joinInterruptibly(handler.handleAsync(context, decoded))` — the
moment *any* interceptor is registered (including the pure-telemetry one), every
async tool/resource/prompt handler is forced onto a blocking join on the dispatch
virtual thread. The branch's own code comment admits a `subscriptions/listen`
handler now parks that thread for the connection's lifetime. This defeats
Tachyon's async-handler contract and is precisely the "blocking async handler
stages solely to keep a trace scope open" anti-pattern the task calls out. It also
lets an interceptor's `chain.reject()` capability suppress `notifications/initialized`
session activation, and classifies `McpOutcome.Success` *before* response
serialization (which can still fail) — three real defects, not just style
disagreements. These are why observation must be structurally incapable of
`proceed()`/`reject()`/result substitution, not merely documented as unsupported.

**What to keep from PR #288** (verified sound): append-only ordered registration
with no `ServiceLoader`; resolving `ServerError.Kind` → wire code exactly once,
inside core, and handing the resolved code to observers rather than re-deriving it;
`opentelemetry-api`-only production dependency with SDK/testing SDK confined to
`test` scope; per-module OTel BOM import (not hoisted to root); bounded span/metric
naming gated to `tools/call`/`prompts/get` only; payload capture off by default,
independently toggleable; copying not-yet-shipped GenAI semconv constants locally
(`McpAttributes`) rather than importing deprecated ones.

## Branch strategy

Work in a fresh worktree off `origin/main` at 77b14d01 (do not touch the current
`main` checkout — it has unrelated untracked `opencode.jsonc`/`package-lock.json` to
leave alone). Branch name: `feat/observability`. Confirm the actual base SHA at
start (`git rev-parse origin/main`) since main may have moved.

## Design note

**Owner/lifetime.** One observation instance per inbound MCP operation (request,
notification, or `initialize`) — independent of JSON-RPC id, so repeated ids across
sessions never collide. Created at the top of `McpDispatcher.dispatchRequestAsync` /
`dispatchNotification` / `dispatchInitializeAsync`, immediately once `method` (and
`id`, if any) are known — before any early-return. Carried as a field on the
already-request-scoped `DefaultDispatchContext` (created fresh per dispatch at
`McpDispatcher.dispatchContext`, already threaded through every
`RpcMethodHandler`/`ToolMethodHandlers`/etc. call) — **not** a `ChannelContext`
attribute (that's connection-scoped and shared across concurrent requests) and
**not** a global map keyed by request id. Raw JSON parse failures (before a method
is even known) are a separate, degenerate ingress fact — no fabricated method/id.

**Phases → one listener, two calls.** Rather than six mandatory event classes, the
dispatcher accumulates phase facts (ingress ok, request recognized, operation
resolved, handler outcome, encode outcome, transport outcome) onto one mutable
internal accumulator as dispatch progresses, and the registered listener is invoked
exactly twice:

```java
// tachyon-core, package dev.tachyonmcp.core.server.observability, @InternalApi
public interface ObservationListener {
    /** Fires once, as early as method(+id) is known, before handler or rejection. */
    ObservationScope start(OperationInfo info);
    /** Fires exactly once, at the operation's terminal boundary (see below). */
    void complete(OperationInfo info, OperationOutcome outcome);
}

public interface ObservationScope {
    void close();                 // same thread it was opened on
    ObservationScope reattach();  // re-open the captured context on a *different* thread once, for the completion callback
}
```

`start` returns a scope so a tracer can `Context.makeCurrent()` for span parentage;
the dispatcher opens it around the *synchronous* portion of handler invocation only
(decode + the call that kicks off `handleAsync`, which for a sync handler is the
whole thing, and for an async handler is just "start the work") and closes it the
moment that call returns — never around an incomplete `CompletionStage`. When the
stage later completes (possibly on a foreign thread), the dispatcher calls
`scope.reattach()` for the duration of the completion callback only, then closes it
and calls `complete()`. This satisfies "attach only while code executes on a
thread, close on the same thread" and "propagate context explicitly across
Tachyon-owned executor hops" without blocking. Framework-async work gets this for
free; arbitrary application-owned executors do not — documented as unsupported
unless the app wraps its own executor with the same explicit pattern.

`OperationOutcome` distinguishes (mirrors §5 of the task, not a public enum
explosion): rejected-before-handler (with the `ServerError`/resolved wire code),
domain success, tool/domain payload failure (`ToolResult.error()`), task
handoff/accepted, JSON-RPC error + resolved code + cause, serialization failure,
cancellation, ignored notification, transport failure/abandonment. Resolved by
reusing `context.responseMapper().error(...)`/existing `ServerError`/`ToolResult`
classification already computed by core — **never re-derived** by the listener.

**Terminal boundary per operation type.**
- Ordinary request (tool/resource/prompt/etc.): ends after response *encoding*
  (including a late serialization failure — new fact PR #288 never captured,
  since it classified success before `encodeResponse` could still throw) —
  `complete()` fires from `McpDispatcher`'s encode call sites. Transport write
  success/failure is a *separate*, best-effort fact attached after the fact via a
  `ChannelFutureListener` (see integration points) — it does not reopen or delay
  `complete()`.
- Notification: ends right after the synchronous handling call returns (including
  `notifications/initialized`, whose activation-before-ack ordering at
  `McpDispatcher.java:351-359` is untouched — observation wraps it, never gates it).
- `tools/call` producing a task handoff: ends at `createTaskResult` mapping
  (`ToolMethodHandlers.mapResult`) — a `TaskHandoff` outcome, not a "success" that
  waits for the task's own lifetime. The task's later `tasks/get`/notifications are
  a distinct, untraced concern per task guidance.
- `subscriptions/listen`: its `RpcMethodHandler` `CompletionStage` spans the whole
  SSE stream (confirmed: `SubscriptionsListenHandler.handleAsync` returns a
  `pending` future that only resolves on disconnect-cancel or `closeAll()`).
  Observation must **not** wait for it. `complete()` fires at SSE establishment
  (the `postStream.started()` branch in `McpOperationHandler.completePostRequest`).
  Stream teardown (`stream.onClose(...)`, already wired at
  `SubscriptionsListenHandler.java:74-77`) reports a second, independent,
  non-blocking "stream ended" fact through its own tiny record — it must not
  reopen, double-end, or resurrect the original operation's observation.

**Payload capture.** A separate opt-in `PayloadCapturePolicy` (independent toggles:
request args, response content, raw message, exception detail; explicit byte
limits) nested under the new `ObservabilityConfig`. Capture only runs when the
policy enables it *and* a listener is registered — metrics-only or disabled-tracing
configurations pay nothing. Captured content is read at its true source
(`ToolRequest`/`ToolResult` at `ToolMethodHandlers`, never `toString()`, routed
through the configured `PayloadSerializer`), redacted/truncated before it reaches
`OperationInfo`, multibyte-safe, and on redaction/parse failure the content is
omitted with a bounded reason — never falls back to the unredacted original.
`_meta.traceparent` extraction is independent of this policy (works with capture
off).

**Registration surface.** New `ServerBuilder.observability(Consumer<ObservabilityConfig.Builder>)`
config group, following the existing `capabilities(...)`/`monitoring(...)` shape
(`ServerBuilder.java`). `ObservabilityConfig` is `@ExperimentalApi` (genuinely new
public surface); `.listener(ObservationListener)` is the one method referencing the
`@InternalApi` bridge type — application code doesn't normally implement
`ObservationListener` itself in v1, only the bundled OTel module does. This keeps
the lifecycle library-owned per the task's explicit instruction, while still
letting `integrations/tachyon-otel` depend on `tachyon-core` at **compile** scope
(not test-scope, unlike PR #288) for that one internal type — the task explicitly
sanctions this narrow cross-module dependency over publishing internals through
`tachyon-api`.

**Disabled-path cost.** Empty listener list ⇒ `McpDispatcher` branches exactly like
`interceptors.isEmpty()` in PR #288 (that part was fine): unchanged fast path,
zero allocation, no executor hop, shared no-op `ObservationScope`/`OperationInfo`
singletons. The `ChannelFutureListener` added for POST-JSON transport-completion
(see below) is skipped entirely when there's no active observation to report to.

**Fault isolation.** Every listener call (`start`/`complete`/scope open/close) is
wrapped by the dispatcher in try/catch that can log (bounded/rate-limited, no raw
payloads) but never rethrows into the real dispatch path, never suppresses/retries
the handler, never rewrites the response, never blocks session activation or
cleanup. One listener's failure doesn't stop a second registered listener (loop
with per-listener isolation, same as PR #288's `InterceptorChain` catch, minus the
outcome-substitution power). `InterruptedException`/fatal `Error` propagate — not
swallowed as "telemetry failure."

## Integration points (file:line, main @ 77b14d01)

- **Seam**: `McpDispatcher.decodeAndHandleAsync` (`tachyon-core/.../server/McpDispatcher.java:295-303`) — already commented as "the seam a future interceptor/chain wraps around." Wrap decode + handle-invocation here; do not touch `RpcMethodHandler`/`ToolMethodHandlers`/etc. themselves.
- **Observation creation**: top of `dispatchRequestAsync` (`McpDispatcher.java:158`), `dispatchNotification` (`:328`), `dispatchInitializeAsync` (`:401`) — before any early return.
- **Early-rejection sites needing a terminal call** (all in `McpDispatcher.java`): malformed params `:171-173`, double-initialize `:174-179`, unknown method (stateless) `:188-191`, missing session header `:195-196`, unknown/expired session `:199-202`, session closed `:210-213`, session initializing+non-ping `:214-217`, unknown method/disabled extension (stateful) `:219-236` (`lookupHandler`).
- **Handler-completion**: `invokeHandlerAsync`'s `.handle((result, ex) -> ...)` (`:280-288`) and `dispatchInitializeAsync`'s equivalent (`:425-432`).
- **Response-encoded fact (new — PR #288 never had this)**: `encodeResponse`/`encodeError` (`:441-458`) and their callers `handleSuccessOrError`/`handleHandlerError` (`:305-326`) — must record encode success *and* the `catch` branch that currently falls back to `encodeError` on serialization failure (`:452-458`... verify exact lines at implementation time).
- **Task handoff**: `ToolMethodHandlers.mapResult` (`tachyon-core/.../features/tools/ToolMethodHandlers.java:167-184`) — `ToolResult.Task` branch, right before `context.responseMapper().createTaskResult(snapshot)`.
- **`notifications/initialized` ordering (must not change)**: `McpDispatcher.java:351-359` (`session.activate()` before `Accepted`), `McpOperationHandler.java:187-197` (activate synchronously before 202).
- **`subscriptions/listen`**: establishment branch `McpOperationHandler.completePostRequest`'s `postStream.started()` path (`McpOperationHandler.java:314-328`); teardown at `SubscriptionsListenHandler.java:74-77` (`stream.onClose`).
- **Transport-completion (POST-JSON path — currently unobserved)**: `McpResponseWriter.sendJsonResponse`'s `ctx.writeAndFlush(response)` (`McpResponseWriter.java:83`) has no listener today; add one mirroring the existing pattern in `PostSseStream.doWriteEvent`/`doWriteComment` (`PostSseStream.java:194-202, 219-221, 232-234` — success/failure, close channel on failure). Keep it constant-time; no payload work on the event loop.
- **`ServerError.Kind` → wire code (reuse, never reimplement)**: `McpResponseMapper.error(ServerError)` in both `v2025_11_25` and `v2026_07_28` codec packages — the 2026 version's `error()` calls `super.error()` then overrides several kinds/HTTP statuses. Observation reads the *result* of this call; it must never switch on `Kind` itself.
- **Registration point**: `ServerBuilder.java` (new `observability(...)` group next to `capabilities`/`monitoring`, `:30-45` area), `DefaultServerBuilder.java` (`build()` around `:307-346`), `ServerEngine.java` (internal accessor, mirrors an eventual `interceptors()`-style accessor pattern), Kotlin: one thin member fn on `TachyonServerBuilder.kt` (no new `*Scope.kt` unless the config genuinely needs a nested block — `ObservabilityConfig`'s payload-capture sub-toggles likely justify one, sized like `MonitoringScope.kt`).

## Module: `integrations/tachyon-otel`

New Maven module, packaging pattern copied from `integrations/tachyon-tasks-temporal/pom.xml`:
parent `tachyon-integrations`; add `<module>tachyon-otel</module>` to
`integrations/pom.xml`; add a `tachyon-otel` entry to `tachyon-bom/pom.xml`
`dependencyManagement`. Module's own `dependencyManagement` imports the OTel BOM
(module-scoped, not root). Compile deps: `tachyon-api`, **`tachyon-core`** (compile
scope — the deliberate narrow-bridge exception, see design note), `opentelemetry-api`,
`opentelemetry-semconv-incubating` (pinned explicit version — alpha). Test-only:
`tachyon-testkit`, `opentelemetry-sdk`, `opentelemetry-sdk-testing`, junit, assertj,
awaitility. **Before writing code**: re-verify current GA versions of
`opentelemetry-bom`/`opentelemetry-semconv-incubating` on Maven Central (PR #288's
1.51.0/1.43.0-alpha may be stale) and re-check the MCP semconv doc
(open-telemetry/semantic-conventions-genai `docs/gen-ai/mcp.md`) for the current
attribute set/naming before porting `McpAttributes`/span-naming logic.

Reuse from PR #288 (logic is sound, just re-host against the new
`ObservationListener` shape instead of `McpInterceptor`): `McpAttributes`'
copy-not-import strategy for unstable GenAI keys; bounded target resolution gated
to `tools/call`/`prompts/get`; `CALLER_FAULT_CODES` set keeping span status `UNSET`
for caller-caused JSON-RPC codes; `mcp.server.operation.duration` histogram, one
measurement per logical completion, no ids in metric dimensions.

**Fix the PR #288 gap**: target identity for span/metric dimensions must come from
the *resolved registry lookup* (the `ToolDescriptor`/`PromptDescriptor` actually
found), not the raw wire `name` param — PR #288's `McpInvocation.targetName()`
echoed the wire value unbounded, which any client can pump with garbage names.
Bound to a fixed "unknown" fallback label when no descriptor resolves.

## Tests

Follow the task's coverage matrix (§10) directly — group scenarios, don't
Cartesian-explode them. Priorities that most need new/adapted E2E fixtures:
parity (observation absent/enabled/no-op/failing produces identical client-visible
responses — including notification order and session effects); the async-handler
non-blocking guarantee (an incomplete `AsyncToolFn` stage must not be joined —
regression test for the exact PR #288 defect, e.g. assert the dispatch thread
returns before a gated `CompletableFuture` is completed, from a second thread,
while observation is enabled); early-rejection coverage (every row in the
integration-points table above); `subscriptions/listen` establishment-vs-teardown
as two independent facts; task handoff as a distinct terminal outcome; fault
isolation (a throwing listener doesn't affect handler execution or a second
listener); payload capture opt-in/redaction/limits; cardinality (many unknown tool
names stay bounded in metric dimensions). Use real `TachyonServer` +
`tachyon-testkit` clients/`JsonRpcResponseAssert` for behavior; `EmbeddedChannel`
only for races not reliably inducible over a socket (disconnect-during-handler,
write-rejection-during-shutdown). Use `io.opentelemetry:opentelemetry-sdk-testing`'s
`InMemorySpanExporter`/`InMemoryMetricReader` for OTel assertions (established
pattern already in `tachyon-otel`'s pom); no Tachyon-specific recorder needed there.
For core-only listener fault-isolation/parity tests, add a small in-memory test
`ObservationListener` fixture in `tachyon-testkit`, shaped like the existing
`TestTaskConnector` (concurrent collections + `List.copyOf` getters + `reset()`).

## Docs

`docs/observability.md`, ~120-180 lines excluding code, linked from `docs/README.md`
(add an "Operate" section, or fold into "Internals" — pick whichever reads better
once the doc exists). Cover: what the lifecycle can/can't prove; minimal Java +
thin Kotlin setup; the two-call listener model and why (span parentage without
blocking); handler/encode/transport completion distinctions; raw vs domain capture
and its opt-in controls; low-cardinality metric policy incl. unknown-target
behavior; fault isolation and SDK ownership; a compact coverage/limitations table
(model PR #288's "Not covered yet" table — outbound `mcp.client` spans, session
duration, W3C traceparent without the OTel agent, etc., re-verified against what
we actually implement).

## Performance & validation gates

Reproducible evidence for: disabled; enabled with a no-op listener; OTel
metrics+traces without payload capture; payload capture on with small/large
inputs — across a sync handler, a delayed async handler, a long-lived
`subscriptions/listen`, and unknown-name traffic. Use an existing benchmark
harness if one exists in the repo (check `tachyon-core`/`e2e` for JMH or similar
before building anything new); otherwise a small reproducible harness, labeled as
coarse. Record JDK/environment/methodology; no fabricated numbers.

Build/lint commands (per `CONTRIBUTING.md`/`Makefile` — note some `make`
clean/package targets delete shared local Maven artifacts, prefer scoped `mvn`
invocations where that matters): `make lint`, `make build`/`make test`, `make ci`
(clean + lint + build + revapi) at completion, plus Kotlin module test
(`mvn test -pl tachyon-kotlin -am`) and the new module's own tests
(`mvn test -pl integrations/tachyon-otel -am`).

## Self-review

Run the task's three-pass loop (§13) before calling this done: correctness trace
of every exit path/ownership transfer; API/perf/security review of new types and
dependencies (challenge every allocation/context hop, confirm no generated wire
model leaks past the boundary, confirm `@InternalApi`/`@ExperimentalApi` are used
correctly); developer-experience pass reading the finished docs cold and
compiling/running both language snippets. Fix and re-run affected gates per
finding rather than batching everything to the end.

## Verification plan (end-to-end)

1. `make lint` and `make build` on the new branch/worktree — record actual output.
2. Targeted new tests first (ATDD: red before the seam exists, green after) via
   `mvn -q test -pl tachyon-core,tachyon-api,tachyon-testkit -am` and
   `mvn -q test -pl integrations/tachyon-otel -am`.
3. Full `make ci` (revapi must show `@InternalApi`/`@ExperimentalApi` correctly
   exempted, no accidental breaking change to stable API).
4. E2E/conformance suite for both protocol versions.
5. Manual sanity: run a minimal Java example server with `.observability(o -> o.listener(McpTelemetryListener.create(sdk)))`, hit it via `tachyon-testkit`/curl, confirm spans/metrics land in the in-memory OTel test exporter and disabled path shows zero overhead in a quick before/after allocation check.
6. Final handoff note per task §14: base SHA + branch, module/API entry points, exact completion semantics, PR #288 lesson retained (async-blocking defect) and designs deliberately avoided, test/build commands with actual results, measured-vs-inferred performance findings, self-review findings and resolutions, remaining limitations/blocked gates.
