---
title: "Client interactions"
weight: 19
sidebar_order: 19
toc: true
description: |-
  Request user input from a handler and understand Tachyon's sampling compatibility boundary.
---

An MCP server can ask its connected client for user input. Tachyon exposes typed form elicitation
through `InteractionContext.client()`. Sampling is a legacy protocol feature ([SEP-2577](https://modelcontextprotocol.io/seps/2577-deprecate-roots-sampling-and-logging)) and isn't part of the
current, non-deprecated API guidance.

## Request form input

Call `context.client().elicitation().create(...)` from a tool, resource, or prompt handler. Define
the accepted response with a restricted JSON Schema whose top-level properties are primitive
values.

```java
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.runtime.ElicitationRequest;
import dev.tachyonmcp.api.server.features.tools.ToolResult;

var schema = JsonSchema.unchecked("""
        {
          "type": "object",
          "properties": {
            "city": { "type": "string" }
          },
          "required": ["city"]
        }
        """);

server.tools().register(
        tool -> tool.name("choose-city"),
        (context, request) -> {
            var result = context.client().elicitation().create(
                    new ElicitationRequest("Choose a forecast city", schema)).join();

            return switch (result.action()) {
                case ACCEPT -> ToolResult.text(
                        "Selected " + result.content().stringValue("city"));
                case DECLINE -> ToolResult.error("City selection declined");
                case CANCEL -> ToolResult.error("City selection cancelled");
            };
        });
```

The returned action is `ACCEPT`, `DECLINE`, or `CANCEL`. `content()` is present only for `ACCEPT`.
Check the action before reading it.

The client must advertise form elicitation and keep a bidirectional connection available for the
round trip. Treat rejection, disconnection, and request timeout as normal handler failure paths.

## Return an input-required result

Tools and prompts can instead return an input-required result. This ends the current handler call
with one or more input requests rather than waiting for an immediate client response.

```java
import dev.tachyonmcp.api.server.domain.FormInputRequest;

return ToolResult.inputRequired(
        Map.of("city", FormInputRequest.of("Choose a forecast city", schema)),
        "forecast-draft-42");
```

Use the optional state string as an opaque correlation value. For long-running task workflows,
put the same `InputRequestBundle` on an `INPUT_REQUIRED` task snapshot and accept the submitted
values through `TaskConnector.update(...)`. See [Tasks](tasks.md).

## Sampling status

MCP 2026-07-28 deprecates `sampling/createMessage` through SEP-2577. Tachyon retains
`ClientContext.sampling()` only for compatibility with older clients, and both it and
`SamplingService` are deprecated Java APIs.

Don't add sampling to new integrations. An application that must support an older MCP client
should isolate sampling behind a version-specific adapter and plan its removal.

