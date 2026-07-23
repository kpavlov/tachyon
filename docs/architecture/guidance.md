# Handler Design Guidance

Rules for a new server-feature handler type (tools, resources, prompts, future ones). Read before adding or touching a handler SAM.

## 🎯 Default shape: two interfaces, sync-first

Resources/prompts are the reference — one call shape, two interfaces:

```java
@FunctionalInterface
public interface XHandler {
    XResult handle(InteractionContext ctx, XRequest request) throws Exception;
    default CompletionStage<? extends XResult> handleAsync(InteractionContext ctx, XRequest request) {
        try {
            return CompletableFuture.completedFuture(handle(ctx, request));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}

public interface AsyncXHandler extends XHandler {
    @Override
    CompletionStage<? extends XResult> handleAsync(InteractionContext ctx, XRequest request);
    @Override
    default XResult handle(InteractionContext ctx, XRequest request) throws Exception {
        HandlerFutures.assumeVirtualThread();
        return HandlerFutures.joinInterruptibly(handleAsync(ctx, request));
    }
}
```

Implement `XHandler` for sync, `AsyncXHandler` for async — one override each (see `ResourceHandler`/`AsyncResourceHandler`, `PromptHandler`/`AsyncPromptHandler`). Use this unless there's more than one independent optional-override axis (🏹 below).

## 🐛 Own SAM, `throws Exception` — never raw `java.util.function.*`

`BiFunction`/`Function` can't declare checked exceptions — reusing them forces every I/O handler into `try/catch` boilerplate and drops the real exception type/stacktrace from the dispatcher's error log. Define a purpose-built SAM, `throws Exception` on the sync method:

```java
@FunctionalInterface
public interface XFn {
    XResult apply(InteractionContext ctx, XRequest request) throws Exception;
}
```

`ToolFn` applies this to tools (it replaced raw `BiFunction`) and receives the full `ToolRequest`. The dispatcher already logs/maps thrown exceptions to a JSON-RPC error — a throwing SAM lets a handler use that path instead of hand-rolling it. `ToolRequest.arguments()` exposes the ergonomic `Args`; the request also carries `_meta` so the shape extends later without an interface change.

**Async entry types don't declare `throws Exception`** — errors propagate via a failed `CompletionStage`, matching `AsyncResourceHandler`/`AsyncPromptHandler`. Don't add `throws` there "for symmetry."

## 🪶 Sync-first, virtual-thread contract

Blocking for I/O in `handle`/`read` is intended — handlers run on a server-executor virtual thread:

- `HandlerFutures.assumeVirtualThread()` guardrail at the top of every sync dispatch. Don't remove it.
- Never `synchronized`/native calls in a handler (pins the carrier thread) — use `ReentrantLock`.
- `HandlerFutures.joinInterruptibly(stage)` to block on a `CompletionStage` (restores interrupt flag, unwraps `ExecutionException`). Don't hand-roll `.get()`.

## 🏹 When to reach for a heavier dispatch structure

`ToolHandler`/`AbstractToolHandler` deliberately isn't the two-interface pattern — tools have two **independent** override axes (sync vs async, canonical `Args` vs raw `ToolRequest`) funneled into one `handleAsync(ctx, ToolRequest)`. `AbstractToolHandler` detects which of the 4 overrides a handler supplied via a package-private `NotImplemented` sentinel (thrown as control flow, `fillInStackTrace` no-op'd — near-zero cost), so one class covers sync/async × Args/Request without extra supertypes.

Only use this for >1 independent override axis. A single axis always gets the two-interface pattern — don't default to a sentinel/dual-dispatch base for what one interface pair expresses.

## 🪶 Descriptor bundling: pair vs self-carrying

- **Resources/prompts**: registry takes `(descriptor, handler)` as a pair — handler is a pure function, no descriptor coupling.
- **Tools**: `ToolHandler extends ServerFeature<ToolDescriptor>`, carries its own `descriptor()` — `ToolRegistry.register(ToolHandler)` keys off the handler instance directly.

Default new types to the pair shape. Bundle the descriptor only if the registry needs a single-arg `register(Handler)` overload like tools.

## ⚠️ Naming: split sync/async by name, not overload

`tool`/`asyncTool`, `register`/`registerAsync`, `of`/`ofAsync` — never overload one method name for both sync and async lambda shapes: different shapes under one name throw Java's overload resolution into ambiguity for every lambda caller, and separate names sidestep it. Keep doing that for any new handler's registration API.

**Two async markers, each consistent within its own layer — don't mix them:**
- `ServerBuilder` build-time methods: `async` **prefix** on the noun — `tool`/`asyncTool`, `resource`/`asyncResource`, `prompt`/`asyncPrompt`, `resourceTemplate`/`asyncResourceTemplate`.
- Runtime registries and `XHandler.of…` factories: `Async` **suffix** on the verb — `register`/`registerAsync`, `of`/`ofAsync`.

A new handler type's `ServerBuilder` method follows the prefix style (matching its siblings); its registry and factory methods follow the suffix style. Same feature, two deliberately different layers — not one rule bleeding into the other.

**Interface/SAM naming:**
- `XHandler` — the handler type. Also the lambda-entry SAM when the shape is simple: `ResourceHandler`/`PromptHandler` are plain `@FunctionalInterface`s, so the type doubles as both.
- `AsyncXHandler extends XHandler` — async variant, for single-axis handlers only (`AsyncResourceHandler`, `AsyncPromptHandler`). Abstract `handleAsync`, default `handle` blocks via `HandlerFutures.joinInterruptibly`.
- `XFn` — companion throwing SAM, only when `XHandler` itself isn't lambda-friendly (carries a descriptor, exposes more than one method). Tools need this because `ToolHandler` isn't a `@FunctionalInterface`; `ToolFn` receives the full `ToolRequest`.
- Static factory composition on `XHandler.of…`: base verb `of`, then optional `Async` — `ToolHandler.of(...)` / `ToolHandler.ofAsync(...)`. Both tool factories receive `ToolRequest`; class-based handlers may override the `Args` or `ToolRequest` form.

## 🪶 Registry/facade API naming

- Registry facade interface named as plural: `interface Completions`, `CompletionRegistry extends Completions`, `DefaultCompletionRegistry implements CompletionRegistry`. User-facing API uses facade.
- Build-time `ServerBuilder` methods are declarative nouns: `tool`, `resource`, `prompt`, `resourceTemplate`.
- Runtime feature registries use `register` / `registerAsync` and `unregister`.
- Optional lookup uses `Optional<Descriptor> find(String name)`. Never nullable `get`.
- Descriptor enumeration uses immutable, name-sorted `descriptors()` snapshots.
- Resource templates follow `registerTemplate`, `registerTemplateAsync`, `unregisterTemplate`, `findTemplate`, `templateDescriptors`.
- Keep typed registration methods on each registry. No public generic base registry.
- `TaskRegistry` is excluded. Tasks use runtime lifecycle methods such as `create` and `get`.

## Kotlin adapter shape

- Structured object factories with more than three fields use one canonical type-named receiver builder: `Icon { src = "..."; mimeType = "image/svg+xml" }` (`Annotations` follows this shape despite having three fields — it's nested metadata commonly composed inside other builders). Don't duplicate it with lowercase receiver factories or flat overloads; an owned enclosing DSL may add a singular member such as `argument { }` that delegates to the canonical factory. Required builder fields start nullable and fail with `requireNotNull` in `build()`.
- Type-named receiver factories are `inline`, declare an `EXACTLY_ONCE` contract, and suppress `FunctionName`. Their public builder has an `@PublishedApi internal` constructor and `build()`.
- Keep DSL operations as receiver-class members when the receiver is owned by this module. Use a top-level extension only for types that cannot own the operation. Type-named factories remain top-level when the Java model has no Kotlin companion.
- Java `ServerBuilder` is the implementation source of truth for server construction, descriptor building, validation, and feature registration. Kotlin delegates to it and adds only thin adaptation for suspend lambdas and Kotlin-specific types.
- Do not reimplement Java builder validation, defaulting, or registration collections in Kotlin. Add missing reusable behavior to Java first, then expose it through the Kotlin DSL.
- Expose one Kotlin server-construction surface: `TachyonServerBuilder`. Do not publish Kotlin extensions on the Java `ServerBuilder`; they bypass Kotlin defaults and duplicate autocomplete. Use an internal owned collaborator when thin adaptation would make the public builder too large.
- Keep Kotlin files focused. Once a file exceeds 300 lines, consider splitting it by owned responsibility. Do not split member DSLs into global extensions merely to reduce line count; prefer composition with an internal class.
- Keep required values first and flexible metadata as defaulted named parameters on the common call. Named arguments remove ambiguity; do not hide useful descriptor fields in a registration sub-DSL.
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
    icons: List<Icon>? = null,
    block: suspend TemplateScope.() -> ResourceContents,
)

fun resourceTemplate(
    descriptor: ResourceTemplateDescriptor,
    block: suspend TemplateScope.() -> ResourceContents,
)
```

## ⚠️ `_meta` stays out of the ergonomic surface

`_meta` is the MCP runtime's protocol envelope — `progressToken`, reserved `io.modelcontextprotocol/*` keys, OpenTelemetry trace context — growing every protocol revision; implementations **must not** assume meaning for reserved keys (MCP spec, `_meta` section). Don't add `meta()` to an ergonomic type (`Args` and friends) — invites Hyrum's-law coupling to runtime internals, same failure mode as an `Internal*`-named type users are forced to hold.

A handler needing raw request metadata (progress token, cancellation, task handle) uses `ToolRequest` through a `ToolFn` or an `AbstractToolHandler` request override, not a `_meta` field on the ergonomic `Args` path.

Testing handler dispatch/error mapping: see [`tachyon-development` skill](../../.agents/skills/tachyon-development/SKILL.md).
