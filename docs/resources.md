# Resources — Tachyon MCP Server

Resources expose data that AI clients can read. Tachyon supports static URIs, dynamic handlers, and URI templates with parameters.

## Static resource (no handler)

A static resource with a fixed external URI — the client fetches the content directly:

```java
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;

.resource(ResourceDescriptor.of("schema", "https://example.com/schema.json", "JSON Schema", "application/json"))
```

## Static resource with handler

Server-computed content for a fixed URI:

```java
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;

.resource(
    ResourceDescriptor.of("config", "app://config", "Server config", "application/json"),
    (ctx, req) -> TextResourceContents.of(req.uri(), "application/json", """{"env":"prod"}"""))
```

## URI template

Templates match parameterized URIs like `app://users/{id}`. Register after the server starts:

```java
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;

handle.server().resources()
    .addTemplate(ResourceTemplateEntry.of(
        "user-profile",
        "app://users/{id}",
        "User profile by ID",
        "application/json",
        (ctx, uri, params) -> {
            String id = params.get("id");
            return TextResourceContents.of(uri, "application/json", loadUser(id));
        }));
```

## Async handler

Handlers are blocking-first and run on virtual threads — blocking is fine. To integrate
non-blocking services, implement `AsyncResourceHandler` (or `AsyncResourceTemplateHandler`)
and return a `CompletionStage`:

```java
import dev.tachyonmcp.server.features.resources.AsyncResourceHandler;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

AsyncResourceHandler handler = (ctx, req) ->
    httpClient.sendAsync(
            HttpRequest.newBuilder(URI.create(req.uri())).GET().build(),
            BodyHandlers.ofString())
        .thenApply(rsp -> TextResourceContents.of(req.uri(), "application/json", rsp.body()));

.resource(descriptor, handler)
```

Prompts follow the same pattern with `AsyncPromptHandler`. In Kotlin, resource and prompt
lambdas are `suspend` — see [Kotlin DSL](kotlin.md).

## Blob resources

Return binary content with `BlobResourceContents`:

```java
import dev.tachyonmcp.server.domain.BlobResourceContents;

(ctx, req) -> BlobResourceContents.of(req.uri(), "image/png", imageBytes)
```

## Subscribe to changes

Enable subscriptions in capabilities, then notify subscribers when content changes:

```java
.capabilities(cfg -> cfg.resources(true, true))  // subscribe=true, listChanged=true
```

```java
handle.server().resources().notifyUpdated("app://config");
```

## Kotlin DSL

```kotlin
resource(name = "config", uri = "app://config", mimeType = "application/json") {
    TextResourceContents.of(uri, "application/json", """{"env":"prod"}""")
}
```

URI templates use the Java API post-build:

```kotlin
val server = buildServer { /* ... */ }
server.resources().addTemplate(ResourceTemplateEntry.of(...))
```

---

**See also:** [Tools](tools.md) · [Tasks](tasks.md) · [Kotlin DSL](kotlin.md) · [Quickstart](quickstart.md)
