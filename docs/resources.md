# Resources — Tachyon MCP Server

Resources expose data that AI clients can read. Tachyon supports static URIs, dynamic handlers, and URI templates with parameters.

## Static resource

Server-computed content for a fixed URI:

```java
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;

.resource(
    ResourceDescriptor.of("config", "app://config", "Server config", "application/json"),
    (ctx, request) ->
        TextResourceContents.of(request.uri(), """{"env":"prod"}""", "application/json"))
```

## URI template

Templates match parameterized URIs like `app://users/{id}`. Register after the server starts:

```java
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor;
import dev.tachyonmcp.server.domain.UriTemplateValue;

server.resources()
    .registerTemplate(
        ResourceTemplateDescriptor.builder()
            .name("user-profile")
            .uriTemplate("app://users/{id}")
            .description("User profile by ID")
            .mimeType("application/json")
            .build(),
        (ctx, request) -> {
            String id = request.params().get("id").scalarValue();
            return TextResourceContents.of(request.uri(), loadUser(id), "application/json");
        });
```

Static resources and templates use the same `ResourceHandler`. Its `ResourceRequest` contains the
URI, request `_meta`, immutable template parameters, and the nullable original template text.
`uriTemplate()` is null and `params()` is empty for a static resource. `UriTemplate` performs
matching internally. Values are `UriTemplateValue.Scalar` or `UriTemplateValue.Sequence`.
Exploded lists such as `app://files{/segments*}` produce a sequence. Associative maps are not
parsed until MCP defines a variable schema that can disambiguate them.

## Async handler

Handlers are blocking-first and run on virtual threads — blocking is fine. To integrate
non-blocking services, implement `AsyncResourceHandler` and return a `CompletionStage`:

```java
import dev.tachyonmcp.server.features.resources.AsyncResourceHandler;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

AsyncResourceHandler handler = (ctx, request) ->
    httpClient.sendAsync(
            HttpRequest.newBuilder(URI.create(request.uri())).GET().build(),
            BodyHandlers.ofString())
        .thenApply(rsp -> TextResourceContents.of(request.uri(), rsp.body(), "application/json"));

.asyncResource(descriptor, handler)
```

Prompts follow the same pattern with `AsyncPromptHandler`. In Kotlin, resource and prompt
lambdas are `suspend` — see [Kotlin DSL](kotlin.md).

## Blob resources

Return binary content with `BlobResourceContents`:

```java
import dev.tachyonmcp.server.domain.BlobResourceContents;

(ctx, request) -> BlobResourceContents.of(request.uri(), base64Image, "image/png")
```

## Subscribe to changes

Enable subscriptions in capabilities, then notify subscribers when content changes:

```java
.capabilities(cfg -> cfg.resources(true, true))  // subscribe=true, listChanged=true
```

```java
server.resources().notifyResourceUpdated("app://config");
```

## Kotlin DSL

```kotlin
resource(name = "config", uri = "app://config", mimeType = "application/json") {
    TextResourceContents.of(uri, """{"env":"prod"}""", "application/json")
}
```

URI templates use the Java API post-build:

```kotlin
val server = buildServer { /* ... */ }
server.resources().registerTemplate(
    ResourceTemplateDescriptor.builder()
        .name("user-profile")
        .uriTemplate("app://users/{id}")
        .build(),
) { _, request -> /* request.uri(), request.params(), request.meta() */ }
```

---

**See also:** [Tools](tools.md) · [Tasks](tasks.md) · [Kotlin DSL](kotlin.md) · [Quickstart](quickstart.md)
