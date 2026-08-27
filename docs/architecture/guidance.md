# Handler Design Guidance

Rules for a new server-feature handler type (tools, resources, prompts, future ones). Read before adding or touching a handler SAM.

## 🎯 Protocol boundary: map at the method handler

Generated MCP models, JSON-RPC payloads, and JSON-library nodes are wire types. They stop at the
protocol method handler. A feature registry is domain code: it receives API request objects and
returns API result objects.

```text
wire JSON -> protocol request mapper -> domain request -> registry/feature handler
registry/domain result -> protocol response mapper -> wire JSON
```

- The protocol method handler owns the RPC method name and calls the request mapper before it calls
  a registry. It maps the returned domain result with the negotiated protocol's response mapper.
- A registry must not implement `RpcMethodHandler`, accept `Object params`, inspect raw `Map`
  payloads, invoke `JsonRpcCodec`/a generated codec, or import generated protocol models.
- A request mapper is version-specific. It decodes its generated model and creates the stable API
  domain request (`ToolRequest`, `PromptRequest`, `ResourceRequest`, and so on). It is the only
  place that understands version-specific fields such as `inputResponses` and `requestState`.
- A response mapper is version-specific and converts only domain results/errors to generated wire
  models. It must not reach into a registry to reconstruct request data.
- Keep JSON-library types inside mappers. Pass API JSON abstractions or ordinary domain values
  across the boundary, so a different JSON backend changes a mapper/configuration, not a registry.

`McpDispatcher` and transport code are wire glue and may handle raw JSON-RPC envelopes. This
exception does not extend to feature registries or user-facing feature handlers.

## 🎯 Tasks are protocol connectors, not job executors

Tachyon owns the MCP task representation. It does not own the work represented by a task.
Applications, workflow engines, job queues, and schedulers start and run that work. Tachyon maps
their state and commands to MCP.

| Concern | Owner |
|---|---|
| Starting and running business work | Application or external execution system |
| Retries, timers, durable waits, recovery | Application or external execution system |
| MCP task IDs and wire-version mapping | Tachyon |
| `tasks/get`, `tasks/list`, `tasks/cancel`, `tasks/update` dispatch | Tachyon |
| Authoritative execution state | Application or external execution system |
| Cached MCP projection and terminal-result retention | Tachyon |
| `notifications/tasks/status` | Tachyon, from published projections |
| `notifications/progress` for a task | Tachyon, via `Tasks.reportProgress(taskId, ...)` — ephemeral, not part of `TaskSnapshot` |

### 🔴 Forbidden task ownership

Task code must not:

- accept or retain `Runnable`, `Callable`, handler instances, `DispatchContext`, Netty objects, or
  Java closures as task state;
- expose `Future`, `CompletableFuture`, or `CompletionStage` from `Task`, `Tasks`, or any task SPI;
- submit task work to Tachyon's executor;
- retain a map of running futures or interrupt application work for `tasks/cancel`;
- re-invoke a tool handler after `tasks/update`;
- infer external execution state from whether a local future completed;
- transition an external task merely because Tachyon's local TTL janitor ran.

The server-owned virtual-thread executor remains for serving an MCP request. It may block while a
task engine calls an external system. It must never become the lifetime owner of that external
work.

### 🎯 Target task API

`TaskSnapshot` is an immutable, read-only MCP projection. State changes arrive as complete snapshots,
not mutator calls on a task handle. A monotonically increasing `revision` makes callback and refresh
application idempotent: Tachyon ignores a snapshot whose revision is not newer than the cached one.

The target public model is:

```java
@ExperimentalApi
@Value.Immutable
public interface TaskSnapshot {

    String taskId();
    TaskState status();
    long revision();

    static Builder builder() {
        return DefaultTaskSnapshot.builder();
    }
}

var snapshot = TaskSnapshot.builder()
        .taskId(workflowId)
        .status(TaskState.WORKING)
        .createdAt(observedAt)
        .lastUpdatedAt(observedAt)
        .revision(1)
        .build();
```

The engine is synchronous and blocking by design. Protocol handlers already run on virtual
threads. An implementation may call a database, Temporal, Camunda, or another MCP server without
leaking that client's async type into Tachyon's API.

```java
@ExperimentalApi
public interface TaskExecutionEngine {

    Set<TaskFeature> supportedFeatures();

    @Nullable
    TaskSnapshot refresh(InteractionContext context, String taskId) throws Exception;

    TaskSnapshot cancel(
            InteractionContext context,
            String taskId) throws Exception;

    void submitInput(
            InteractionContext context,
            String taskId,
            TaskInput input) throws Exception;
}

public enum TaskFeature {
    LIST,
    CANCEL,
    REQUESTS
}
```

Declare the engine together with the Tasks capability. Calling `tasks(...)` enables the feature;
there is no root `ServerBuilder.taskExecutionEngine(...)` setter:

```java
CapabilitiesConfig.Builder tasks(TaskExecutionEngine taskExecutionEngine);

CapabilitiesConfig.Builder tasks(
        TaskExecutionEngine taskExecutionEngine,
        boolean list,
        boolean cancel,
        boolean requests);
```

This keeps capability advertisement and its required implementation atomic. Building must reject a
task-producing tool without a configured engine. `noTasks()` remains the explicit opt-out.

The enabled flags must be a subset of `taskExecutionEngine.supportedFeatures()`. Validate after every
`tasks(...)` call and again during `build()`, because later flat setters can change the final config.
Report every missing feature in one `IllegalStateException`:

```text
Task execution engine TemporalTaskExecutionEngine does not support enabled task features: [CANCEL, REQUESTS]
```

`supportedFeatures()` describes optional MCP surfaces. `tasks/get` refresh is the base
`TaskExecutionEngine` contract and therefore needs no feature flag. Return an immutable set. Never probe
support by calling a method and catching `UnsupportedOperationException`.

There is no default or in-process engine. `TaskExecutionEngine` is the user/integration SPI. A
task-producing handler starts external work itself and returns its initial snapshot. Tachyon must
not invent a generic `start` operation because external systems require different start contracts.

Legacy `tasks/list` and blocking `tasks/result` support use a separate optional engine extension. Do
not force session-scoped operations removed by MCP 2026-07-28 into every engine:

```java
@ExperimentalApi
public interface LegacyTaskExecutionEngine extends TaskExecutionEngine {

    PaginatedResult<TaskSnapshot> list(
            InteractionContext context,
            int limit,
            @Nullable String cursor) throws Exception;

    TaskSnapshot awaitResult(
            InteractionContext context,
            String taskId) throws Exception;
}
```

`awaitResult` may block in the external client's supported wait operation. Tachyon must not emulate
it with a local completion future or a polling loop.

The Tasks facade owns only projection publication and lookup:

```java
public interface Tasks {

    TaskSnapshot publish(TaskSnapshot snapshot);

    @Nullable
    TaskSnapshot get(String taskId);

    boolean remove(String taskId);
}
```

`publish` updates the cache, applies retention policy, and emits any negotiated MCP notification.
It does not start work. `refresh` is authoritative for `tasks/get`; a successful snapshot is
published before mapping the response. A refresh failure must not silently invent a state. A cached
snapshot may be used only under an explicit stale-read policy.

Configure one engine while declaring the capability. The engine is an infrastructure
dependency, not a task executor or task-handler registration:

```java
var server = TachyonServer.builder()
        .capabilities(c -> c.tasks(taskExecutionEngine, false, true, true))
        .port(8080)
        .build();
```

Kotlin follows the Java source of truth:

```kotlin
capabilities {
    tasks {
        enabled = true
        list = false
        cancel = true
        requests = true
        executionEngine = taskExecutionEngine
    }
}
```

### 🎯 Task-producing tools

A task-capable tool handler is invoked once. The application starts its external execution and
returns the initial task projection. Tachyon registers that projection and maps it to the negotiated
MCP `CreateTaskResult`. Tachyon never runs the same handler in the background.

The target result shape adds a task branch to `ToolResult`:

```java
var workflowId = workflows.start(request.arguments());
return ToolResult.task(TaskSnapshot.working(workflowId, clock.instant(), 1));
```

For a task-producing call:

1. The tool handler starts or signals the external execution.
2. The handler returns `ToolResult.task(initialSnapshot)`.
3. Tachyon publishes the snapshot and returns the MCP task handle.
4. `tasks/get` calls `TaskExecutionEngine.refresh` and maps the returned snapshot.
5. `tasks/update` forwards `TaskInput`; it does not aggregate answers or resume a handler.
6. `tasks/cancel` calls `TaskExecutionEngine.cancel`; it publishes the returned state only after the
   external owner accepts and reports the cancellation.

The external task ID should be used directly as the MCP task ID when it is stable and safe to
expose. For Temporal, use the workflow ID rather than a run ID so Continue-As-New preserves the
logical task identity. If identifiers cannot be exposed, the connector owns the durable mapping;
Tachyon must not keep an in-memory execution-reference map.

### 🩶 Push is optional; pull is authoritative

External callbacks may call `Tasks.publish(snapshot)` to reduce notification latency. Push does not
replace reconciliation: callbacks can be duplicated, reordered, delayed, or lost while Tachyon is
offline. `tasks/get` always refreshes through the connector before responding unless an explicitly
configured stale-read policy applies.

Task projection retention is not execution control. The janitor may evict an expired cached terminal
projection. It must not cancel, fail, terminate, or otherwise mutate external work.

## 🎯 Default shape: two independent SAMs

Tools, resources, prompts, and completions use one request shape and two unrelated contracts:

```java
@FunctionalInterface
public interface XFn {
    XResult apply(InteractionContext ctx, XRequest request) throws Exception;
}

@FunctionalInterface
public interface AsyncXFn {
    CompletionStage<? extends XResult> apply(InteractionContext ctx, XRequest request);
}
```

Expose them through `register` and `registerAsync`. Adapt sync functions to the registry's internal
async representation inside the implementation. Never model sync and async as subtypes.

## 🐛 Own SAM, `throws Exception` — never raw `java.util.function.*`

`BiFunction`/`Function` can't declare checked exceptions — reusing them forces every I/O handler into `try/catch` boilerplate and drops the real exception type/stacktrace from the dispatcher's error log. Define a purpose-built SAM, `throws Exception` on the sync method:

```java
@FunctionalInterface
public interface XFn {
    XResult apply(InteractionContext ctx, XRequest request) throws Exception;
}
```

`ToolFn` applies this to tools (it replaced raw `BiFunction`) and receives the full `ToolRequest`. The dispatcher already logs/maps thrown exceptions to a JSON-RPC error — a throwing SAM lets a handler use that path instead of hand-rolling it. `ToolRequest.arguments()` exposes the ergonomic `Args`; the request also carries `_meta` so the shape extends later without an interface change.

**Async entry types don't declare `throws Exception`** — errors propagate via a failed `CompletionStage`, matching `AsyncResourceFn`/`AsyncPromptFn`. Don't add `throws` there "for symmetry."

## 🪶 Sync-first, virtual-thread contract

Blocking for I/O in `handle`/`read` is intended — handlers run on a server-executor virtual thread:

- `HandlerFutures.assumeVirtualThread()` guardrail at the top of every sync dispatch. Don't remove it.
- Never `synchronized`/native calls in a handler (pins the carrier thread) — use `ReentrantLock`.
- `HandlerFutures.joinInterruptibly(stage)` to block on a `CompletionStage` (restores interrupt flag, unwraps `ExecutionException`). Don't hand-roll `.get()`.

## 🏹 When to reach for a heavier dispatch structure

Tool registration uses a descriptor/function pair. `ToolFn` and `AsyncToolFn` both receive the full
`ToolRequest`; read parsed arguments via `request.arguments()`. `ToolHandler` and
`AbstractToolHandler` are experimental class-based escape hatches, not registration types.

## 🪶 Descriptor bundling

Registries take `(descriptor, function)` pairs. Keep descriptor metadata separate from executable
behavior. Do not add a single-argument `register(Handler)` overload.

## ⚠️ Naming: split sync/async by name, not overload

Use `register`/`registerAsync`; never overload one method name for both sync and async lambda
shapes. Separate names avoid ambiguous Java lambdas.

Feature registration belongs to the runtime façades. `ServerBuilder.withTools`,
`withResources`, `withPrompts`, and `withCompletions` are bootstrap conveniences that delegate to
those same façade APIs; do not add feature-specific registration overloads to `ServerBuilder`.

**Interface/SAM naming:**
- `XFn` — synchronous throwing SAM. It receives the full request.
- `AsyncXFn` — independent asynchronous SAM returning a `CompletionStage`.

## 🪶 Registry/facade API naming

- Registry facade interface named as plural: `interface Completions`, `CompletionRegistry extends Completions`, `DefaultCompletionRegistry implements CompletionRegistry`. User-facing API uses facade.
- Runtime feature registries use `register` / `registerAsync` and `unregister`.
- Optional lookup uses `Optional<Descriptor> find(String name)`. Never nullable `get`.
- Descriptor enumeration uses immutable, name-sorted `descriptors()` snapshots.
- Resource templates follow `registerTemplate`, `registerTemplateAsync`, `unregisterTemplate`, `findTemplate`, `templateDescriptors`.
- Tool registration accepts `(ToolDescriptor, ToolFn)` or `(ToolDescriptor, AsyncToolFn)` through
  `register` and `registerAsync`. Builder-configurer overloads delegate to these methods.
- `TaskRegistry` is excluded. Tasks use runtime lifecycle methods such as `create` and `get`.

## Kotlin adapter shape

- Structured object factories with more than three fields use one canonical type-named receiver builder: `Icon { src = "..."; mimeType = "image/svg+xml" }` (`Annotations` follows this shape despite having three fields — it's nested metadata commonly composed inside other builders). Don't duplicate it with lowercase receiver factories or flat overloads; an owned enclosing DSL may add a singular member such as `argument { }` that delegates to the canonical factory. Required builder fields start nullable and fail with `requireNotNull` in `build()`.
- Type-named receiver factories are `inline`, declare an `EXACTLY_ONCE` contract, and suppress `FunctionName`. Their public builder has an `@PublishedApi internal` constructor and `build()`.
- Keep DSL operations as receiver-class members when the receiver is owned by this module. Use a top-level extension only for types that cannot own the operation. Type-named factories remain top-level when the Java model has no Kotlin companion.
- Java `ServerBuilder` is the implementation source of truth for server construction and validation. Server feature façades own registration; Kotlin delegates through the builder's `with*` bootstrap conveniences and adds only thin adaptation for suspend lambdas and Kotlin-specific types.
- Do not reimplement Java builder validation, defaulting, or registration collections in Kotlin. Add missing reusable behavior to Java first, then expose it through the Kotlin DSL.
- Expose one Kotlin server-construction surface: `TachyonServerBuilder`. Do not publish Kotlin extensions on the Java `ServerBuilder`; they bypass Kotlin defaults and duplicate autocomplete. Use an internal owned collaborator when thin adaptation would make the public builder too large.
- Keep Kotlin files focused. Once a file exceeds 300 lines, consider splitting it by owned responsibility. Do not split member DSLs into global extensions merely to reduce line count; prefer composition with an internal class.
- Keep required values first and flexible metadata as defaulted named parameters on the common call. Named arguments remove ambiguity; do not hide useful descriptor fields in a registration sub-DSL. Exception: `extensionId` — see the note below the reference shape.
- Keep a value overload accepting the prebuilt descriptor for reuse, testing, and advanced construction.
- Name a trailing behavioral lambda `block`. Do not add a ceremonial `handler {}` or `read {}` wrapper inside another configuration lambda.
- Use a result DSL when it removes repeated request data. Seed contextual defaults such as the requested resource URI and registered MIME type, while allowing explicit overrides.
- A nested result builder must forward handler context used in expressions inside its block, such as URI-template `param` and `sequence` accessors.

Reference shape:

```kotlin
resourceTemplate(
    name = "user-profile",
    uriTemplate = "user://{userId}/profile",
    description = "User profile template",
    mimeType = "application/json",
    title = "User profile",
    annotations = annotations,
    icons = icons,
    meta = mapOf("owner" to "team-x"),
) {
    TextResourceContents {
        text = """{"id":"${param("userId")}"}"""
    }
}
```

Keep both overload families:

```kotlin
fun resourceTemplate(
    name: String,
    uriTemplate: String,
    description: String? = null,
    mimeType: String? = null,
    title: String? = null,
    annotations: Annotations? = null,
    icons: List<Icon> = emptyList(),
    meta: Map<String, Any>? = null,
    block: suspend TemplateScope.() -> ResourceContents,
)

fun resourceTemplate(
    descriptor: ResourceTemplateDescriptor,
    block: suspend TemplateScope.() -> ResourceContents,
)
```

Every field here maps directly onto the corresponding `*Descriptor.Builder` field — add a new
flat param the moment the Java builder gains one, don't let the Kotlin overload drift behind it.

⚠️ **`extensionId` is the one deliberate exception** — never add it as a flat named parameter on
`tool`/`resource`/`resourceTemplate`/`prompt`/`registerTool`. It marks extension-owned features:
`ResourceMethodHandlers`/`PromptMethodHandlers`/`ToolMethodHandlers` filter both `*/list` results
and direct reads to only the extensions the current session negotiated
(`context.isExtensionEnabled`), so a feature carrying an `extensionId` with no matching negotiated
extension becomes silently invisible — a footgun for ordinary server authors who'd never touch it
otherwise. It never appears on the wire either way. The one real caller
(`TasksExtension.java`) sets it via the raw Java `*Descriptor.Builder` in its own bootstrap code —
extension implementations are the intended and only audience. Kotlin still exposes it, but only
on the `*DescriptorScope`/`*Builder` DSL classes (`ToolDescriptorScope`, `ResourceDescriptorScope`,
`ResourceTemplateDescriptorBuilder`, `PromptDescriptorScope`), marked `@ExperimentalApi` to signal
it's for advanced/extension use: `resourceDescriptor(name, uri) { extensionId = MY_EXT_ID }` then
`resource(descriptor) { }` — never `resource(name, uri, extensionId = ...) { }`.

## ⚠️ `_meta` stays out of the ergonomic surface

`_meta` is the MCP runtime's protocol envelope — `progressToken`, reserved `io.modelcontextprotocol/*` keys, OpenTelemetry trace context — growing every protocol revision; implementations **must not** assume meaning for reserved keys (MCP spec, `_meta` section). Don't add `meta()` to an ergonomic type (`Args` and friends) — invites Hyrum's-law coupling to runtime internals, same failure mode as an `Internal*`-named type users are forced to hold.

A function reads arguments and request metadata through `ToolRequest`; subclass the experimental
`AbstractToolHandler` only when a function cannot express the implementation.

## ⚠️ `Optional<T>` vs `@Nullable` — pick by contract, not habit

JSpecify `@Nullable` is the baseline (`@NullMarked` at package level). Reach for `Optional<T>` only when absence is a first-class, must-handle part of the contract — not as a blanket null-replacement. Oracle's own `Optional` docs: primarily a method return type for a clear "no result" case where `null` would likely cause bugs, not a general substitute for every nullable value.

- `@Nullable T` — ordinary nullable state, cache/map-style `get`, DTO fields: `@Nullable String description()`, `@Nullable Value get(Key key)`, `session()`, `sessionId()`.
- `Optional<T>` — a lookup/search where "no result" is the point: `Optional<User> findUserById(id)`, `Optional<Path> resolveConfigFile()`.
- Collections: return empty, never wrap a `List`/`Map` in `@Nullable` or `Optional`.
- Avoid `Optional` for plain field-style getters (`Optional<String> getName()`), setters (`void setName(Optional<String>)`), or class fields — usually just awkward, rarely earns its ceremony.
- Applied here: `InteractionContext.get(AttributeKey<T>)` is `Optional<T>` (map-style lookup, absence is a real branch); `sessionId()` stays `@Nullable String` (ordinary status field, same shape as `lifecycle()`/`session()`).

## 🐛 Context extension state is `AttributeKey<T>`, never `Map<String, Object>`

A stringly-typed attribute bag has two failure modes that compile clean and break at runtime — an unchecked cast on every read (`getAttribute(String)` lets the caller pick any `T`, wrong guess throws `ClassCastException` far from the write site), and silent collision (two unrelated features reusing the same string key overwrite each other). `AttributeKey<T>` fixes both: the key carries `T` so there's no cast at the call site, and keys are identity-based (`AttributeKey.of(name)` returns a distinct object per call, no name-interning registry) so two keys can never collide — retrieval requires holding the actual key instance, not guessing a string. Use this for any future context-carried extension/scratch state; don't reintroduce a generic `Map<String,Object>` surface on a handler-facing type.

Testing handler dispatch/error mapping: see [`tachyon-development` skill](../../.agents/skills/tachyon-development/SKILL.md).
