---
title: "Completions"
weight: 18
sidebar_order: 18
toc: true
description: |-
  Suggest values for MCP prompt arguments and resource-template variables.
---

Completions help clients suggest values while a user fills a prompt argument or resource-template
variable. Register each handler against the exact prompt name or resource template advertised by
your server.

## Complete a prompt argument

```java
import dev.tachyonmcp.api.server.features.completions.CompletionResult;

server.completions().registerForPrompt("review-code", (context, request) -> {
    if (!request.argumentName().equals("concern")) {
        return CompletionResult.empty();
    }

    var prefix = request.argumentValue().toLowerCase(Locale.ROOT);
    var matches = List.of("clarity", "performance", "security").stream()
            .filter(value -> value.startsWith(prefix))
            .toList();
    return CompletionResult.of(matches);
});
```

`argumentValue()` contains the partial value. `resolvedArguments()` contains sibling arguments the
client has already selected. Return `CompletionResult.empty()` for argument names you don't handle.

## Complete a resource variable

Use the exact URI template string, not the resource name:

```java
server.completions().registerForResourceAsync(
        "weather://current/{city}",
        (context, request) -> cityService.search(request.argumentValue())
                .thenApply(CompletionResult::of));
```

Tachyon returns an empty completion result when no handler matches a reference. It limits the wire
response to 100 values and sets `hasMore` when it truncates a larger result.

## Use the Kotlin DSL

```kotlin
promptCompletion("review-code") {
    if (request.argumentName() != "concern") {
        CompletionResult.empty()
    } else {
        CompletionResult.of(
            listOf("clarity", "performance", "security")
                .filter { it.startsWith(request.argumentValue(), ignoreCase = true) },
        )
    }
}

resourceCompletion("weather://current/{city}") {
    CompletionResult.of(cityService.search(request.argumentValue()))
}
```
