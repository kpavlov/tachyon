---
title: "Prompts"
weight: 17
sidebar_order: 17
toc: true
description: |-
  Register MCP prompts, declare arguments, and return messages with Java or Kotlin.
---

Prompts let clients request reusable message templates. A descriptor tells the client which
arguments it can send; the handler turns those arguments into one or more `PromptMessage` values.

## Register a prompt

```java
import dev.tachyonmcp.api.server.domain.PromptArgument;
import dev.tachyonmcp.api.server.domain.PromptMessage;
import dev.tachyonmcp.api.server.features.prompts.PromptResult;
import dev.tachyonmcp.core.server.TachyonServer;

var server = TachyonServer.builder()
        .withPrompts(prompts -> prompts.register(
                prompt -> prompt
                        .name("review-code")
                        .description("Review code for a selected concern")
                        .addArguments(PromptArgument.of(
                                "concern", "Concern", "Security, performance, or clarity", true)),
                (context, request) -> {
                    var concern = request.arguments().stringValue("concern");
                    return PromptResult.messages(PromptMessage.user(
                            "Review this code for " + concern + "."));
                }))
        .port(8080)
        .build();
```

Use `PromptArgument.required()` to tell clients whether an argument is required. Use
`PromptDescriptor.inputSchema()` when you need constraints that the argument list can't express.

## Use asynchronous work

Synchronous prompt handlers run on virtual threads. Register an `AsyncPromptFn` with
`registerAsync(...)` when your dependency already returns `CompletionStage`.

```java
server.prompts().registerAsync(
        descriptor,
        (context, request) -> promptService.create(request.arguments())
                .thenApply(message -> PromptResult.messages(PromptMessage.user(message))));
```

## Use the Kotlin DSL

Kotlin handlers are suspending functions:

```kotlin
prompt(
    name = "review-code",
    description = "Review code for a selected concern",
    arguments = listOf(
        PromptArgument.of("concern", "Concern", "Security, performance, or clarity", true),
    ),
) {
    listOf(PromptMessage.user("Review this code for ${arguments.stringValue("concern")}."))
}
```
