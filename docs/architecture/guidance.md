# Handler Design Guidance

Rules for implementing a new server-feature handler type (tools, resources, prompts, future ones). Read before adding or touching a handler SAM.

## 🎯 Default shape: two interfaces, sync-first

Resources/prompts are the reference pattern — one call shape, two interfaces:

```java
@FunctionalInterface
public interface XHandler {
    XResult handle(InteractionContext ctx, /* args */) throws Exception;
    default CompletionStage<? extends XResult> handleAsync(InteractionContext ctx, /* args */) {
        try {
            return CompletableFuture.completedFuture(handle(ctx, /* args */));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}

public interface AsyncXHandler extends XHandler {
    @Override
    CompletionStage<? extends XResult> handleAsync(InteractionContext ctx, /* args */);
    @Override
    default XResult handle(InteractionContext ctx, /* args */) throws Exception {
        HandlerFutures.assumeVirtualThread();
        return HandlerFutures.joinInterruptibly(handleAsync(ctx, /* args */));
    }
}
```

Implement `XHandler` for sync, `AsyncXHandler` for async — one override each (see `ResourceHandler`/`AsyncResourceHandler`, `PromptHandler`/`AsyncPromptHandler`). Use this unless there's more than one independent optional-override axis (🏹 below).

## 🐛 Own SAM, `throws Exception` — never raw `java.util.function.*`

`BiFunction`/`Function` can't declare checked exceptions — reusing them forces every I/O handler into `try/catch` boilerplate and drops the real exception type/stacktrace from the dispatcher's error log. Define a purpose-built SAM instead, `throws Exception` on the sync method:

```java
@FunctionalInterface
public interface XFn {
    XResult apply(InteractionContext ctx, Args args) throws Exception;
}
```

`ToolFn`/`ToolRequestFn` are this applied to tools (fixed 2026-07-18, was raw `BiFunction`). The dispatcher already logs/maps thrown exceptions to a JSON-RPC error — a throwing SAM lets a handler use that path instead of hand-rolling it.

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

`tool`/`asyncTool`, `register`/`registerAsync`, `of`/`ofAsync` — never overload one method name for both sync and async lambda shapes. Different shapes under one name throw Java's overload resolution into ambiguity for every lambda caller. Separate names sidestep it — keep doing that for any new handler's registration API.

**Two async markers, each consistent within its own layer — don't mix them:**
- `ServerBuilder` build-time methods: `async` **prefix** on the noun — `tool`/`asyncTool`, `resource`/`asyncResource`, `prompt`/`asyncPrompt`, `resourceTemplate`/`asyncResourceTemplate`.
- Runtime registries and `XHandler.of…` factories: `Async` **suffix** on the verb — `register`/`registerAsync`, `of`/`ofAsync`.

A new handler type's `ServerBuilder` method follows the prefix style (matching its siblings); its registry and factory methods follow the suffix style. Same feature, two deliberately different layers — not one rule bleeding into the other.

**Interface/SAM naming:**
- `XHandler` — the handler type. Also the lambda-entry SAM when the shape is simple: `ResourceHandler`/`PromptHandler` are plain `@FunctionalInterface`s, so the type doubles as both.
- `AsyncXHandler extends XHandler` — async variant, for single-axis handlers only (`AsyncResourceHandler`, `AsyncPromptHandler`). Abstract `handleAsync`, default `handle` blocks via `HandlerFutures.joinInterruptibly`.
- `XFn` / `XRequestFn` — companion throwing SAM(s), only when `XHandler` itself isn't lambda-friendly (carries a descriptor, exposes more than one method). Tools need this because `ToolHandler` isn't a `@FunctionalInterface` — `ToolFn`/`ToolRequestFn` fill the lambda-entry role instead.
- Static factory composition on `XHandler.of…`: base verb `of`, then optional `Async` right after it, then optional `Request` last — `of` → `ofRequest` → `ofAsync` → `ofAsyncRequest`. Fixed order; don't invent `ofRequestAsync`.

## 🪶 Registry API naming

- Build-time `ServerBuilder` methods are declarative nouns: `tool`, `resource`, `prompt`, `resourceTemplate`.
- Runtime feature registries use `register` / `registerAsync` and `unregister`.
- Optional lookup uses `Optional<Descriptor> find(String name)`. Never nullable `get`.
- Descriptor enumeration uses immutable, name-sorted `descriptors()` snapshots.
- Resource templates follow `registerTemplate`, `registerTemplateAsync`, `unregisterTemplate`, `findTemplate`, `templateDescriptors`.
- Keep typed registration methods on each registry. No public generic base registry.
- `TaskRegistry` is excluded. Tasks use runtime lifecycle methods such as `create` and `get`.

## ⚠️ `_meta` stays out of the ergonomic surface

`_meta` is the MCP runtime's protocol envelope — `progressToken`, reserved `io.modelcontextprotocol/*` keys, OpenTelemetry trace context — growing every protocol revision; implementations **must not** assume meaning for reserved keys (MCP spec, `_meta` section). Don't add `meta()` to an ergonomic type (`Args` and friends) — invites Hyrum's-law coupling to runtime internals, same failure mode as an `Internal*`-named type users are forced to hold.

A handler needing raw request metadata (progress token, cancellation, task handle) uses the raw-request escape hatch (`ToolRequestFn`/`ToolRequest`), not a `_meta` field on the ergonomic path.

Testing handler dispatch/error mapping: see [`tachyon-development` skill](../../.agents/skills/tachyon-development/SKILL.md).
