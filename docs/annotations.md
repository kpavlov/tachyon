# Annotations

Tachyon's native feature registration is programmatic: `server.tools().register(...)`,
`server.resources().register(...)`, and so on. The `AnnotationProvider` SPI bridges third-party
*annotation* programming models — mcp-java, LangChain4j, Spring AI — onto those same façades, so
you can keep writing `@Tool`-annotated methods from a framework you already use and still get a
Tachyon server underneath.

`ServerBuilder.annotations(...)`, `AnnotationContext`, `AnnotationProvider`, and
`AnnotationRegistrationContext` are `@ExperimentalApi` — the shape may still change.

## The AnnotationProvider interface

Each implementation knows how to inspect one particular annotation framework and translate its
annotated methods into standard Tachyon feature registrations via an `AnnotationRegistrationContext`
— the same `Tools`/`Resources`/`Prompts`/`Completions` façades a manual registration or a
`ServerExtension` would use. Tachyon core never references framework-specific annotations; each
provider lives in its own optional integration module.

```java
public interface AnnotationProvider {
    void register(Object instance, AnnotationRegistrationContext context);
}
```

Implementations are stateless and reusable — the same provider instance may be passed to multiple
objects.

## Register annotated objects

```java
var server = TachyonServer.builder()
    .annotations(a -> a
        .provider(new McpJavaAnnotationProvider())
        .register(new WeatherService())
        .register(new CalculatorService()))
    .build();
```

- Each call to `provider(...)` sets the active provider; subsequent `register(...)` calls dispatch
  through that provider until a new one is set. Multiple providers and multiple objects are
  supported in one chain.
- Calling `.annotations(...)` more than once composes — every configurer runs against the same
  registration context, in call order.
- Registrations execute after the server is constructed but after `withTools`/`withResources`/
  `withPrompts`/`withCompletions` bootstrap registrations, so an annotated method registered under
  the same name as a bootstrap registration wins.
- `register(...)` throws `IllegalStateException` if called before any `provider(...)`.

## Built-in providers

| Module | Provider | Maps |
|---|---|---|
| `tachyon-annotations-mcp-java` | `McpJavaAnnotationProvider` | `@Tool`, `@Resource`, `@ResourceTemplate`, `@Prompt` |
| `tachyon-annotations-langchain4j` | `LangChain4jAnnotationProvider` | `@Tool` only — LangChain4j has no resource/prompt annotations |
| `tachyon-annotations-spring-ai` | `SpringAiAnnotationProvider` | `@McpTool`, `@McpResource` (static or, when the URI contains `{...}`, a template), `@McpPrompt` |

Add the module you need as a dependency; each is independent of the other two. All three live under
`integrations/` in the source tree.

```xml
<dependency>
    <groupId>dev.tachyonmcp</groupId>
    <artifactId>tachyon-annotations-mcp-java</artifactId>
</dependency>
```

All three coerce JSON-RPC arguments to the annotated method's declared parameter type by routing
through the server's configured `PayloadSerializer`/`PayloadDeserializer` (`AnnotationRegistrationContext.payloadSerializer()`/`payloadDeserializer()`), and inject a method parameter of type
`InteractionContext` when the annotated method declares one. A parameter annotated with a
`defaultValue` (e.g. `@P(defaultValue = "10")`, `@ToolArg(defaultValue = "10")`) that's absent from
the incoming arguments is bound to that default, parsed as the parameter's declared type, rather
than `null`.

## Schema generation

`McpJavaAnnotationProvider` describes tool/resource-template/prompt parameters with a scalar-only
mapping (`String`, numeric/boolean primitives and wrappers, and `Optional`/`OptionalInt`/
`OptionalLong`/`OptionalDouble` wrapping one of those) and rejects anything else — a `record`,
`enum`, `List<T>`, or nested POJO parameter — at registration time with a clear exception, rather
than silently misdescribing it as `"string"` or failing only on first invocation.

`LangChain4jAnnotationProvider` and `SpringAiAnnotationProvider` instead delegate tool input-schema
generation to their own framework's schema generator (`dev.langchain4j.agent.tool.ToolSpecifications`
and `org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator`, respectively),
so records, enums, `List<T>`, and nested POJOs are described — and bound — correctly, not rejected.
Both providers still strip parameters neither framework knows about (Tachyon's own
`InteractionContext`) from the generated schema themselves. `SpringAiAnnotationProvider`
additionally rejects, at registration, parameter types under `org.springframework.ai.mcp.annotation`
that aren't one of the three request-context types it actually emulates (`McpSyncRequestContext`,
`McpAsyncRequestContext`, `MetaProvider`) — e.g. `McpMeta` or `McpTransportContext` — instead of
silently resolving them to `null` at invocation.

### Spring AI prompt arguments

`SpringAiAnnotationProvider` reads `@McpArg`'s `name`, `description`, and `required` for
`@McpPrompt` parameters — a renamed argument (`@McpArg(name = "topicName")`) is looked up under
that name both in the advertised `PromptArgument` and when resolving the incoming
`prompts/get` arguments, so the two stay in agreement.

### Spring AI proxies

`SpringAiAnnotationProvider` scans `instance.getClass().getDeclaredMethods()`. If `instance` is a
Spring-managed bean wrapped in a CGLIB proxy — the default for `@Component`/`@Service` beans using
class-based proxying — `getClass()` returns the proxy class, whose declared methods don't carry the
original annotations. Register the unproxied instance, or a bean with proxying disabled
(`proxyTargetClass = false` with an interface, or `@Scope(proxyMode = ScopeMode.NO)`).

## Implement a provider for another framework

```java
public class MyFrameworkAnnotationProvider implements AnnotationProvider {
    @Override
    public void register(Object instance, AnnotationRegistrationContext context) {
        for (Method method : instance.getClass().getDeclaredMethods()) {
            MyTool tool = method.getAnnotation(MyTool.class);
            if (tool == null) continue;
            context.tools().register(
                ToolDescriptor.builder().name(tool.name()).build(),
                (ctx, req) -> ToolResult.text(method.invoke(instance, /* ... */).toString()));
        }
    }
}
```

A well-behaved provider fails fast (throws) on duplicate feature names within the same provider,
invalid annotation combinations, or unsupported method signatures, so callers see the problem at
registration time rather than at first tool call. Tachyon's feature registries don't enforce this
themselves — registering two features under the same name silently replaces the first.

---

**See also:** [Extensions](extensions.md) · [Tools](tools.md) · [Quickstart](quickstart.md)
