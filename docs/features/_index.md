---
title: "MCP features"
overview_title: "Choose a feature"
weight: 10
sidebar_order: 10
toc: true
description: |-
  Add tools, resources, prompts, completions, client interactions, and long-running tasks.
---

MCP features define what clients can discover and call on your server. Start with the feature that
matches your application behavior; you don't need to enable features you don't use.

## Expose server capabilities

- [Tools](tools/) — run operations with validated arguments and structured results
- [Resources](resources/) — expose text or binary content through fixed and templated URIs
- [Prompts](prompts/) — produce reusable messages from declared arguments
- [Completions](completions/) — suggest prompt argument and resource variable values

## Continue work across interactions

- [Client interactions](client-interactions/) — request user input from an active handler
- [Tasks](tasks/) — connect tool calls to durable work owned by a job or workflow system

Tachyon advertises tools, resources, and prompts automatically after you register them. Configure
capability modes explicitly only when you need dynamic registration or notifications. See
[Configuration](../running/configuration/#capabilities).
