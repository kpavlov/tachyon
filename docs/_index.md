---
title: "Tachyon MCP Documentation"
weight: 1
sidebar_order: 1
description: |-
  Documentation for Tachyon MCP, a production Model Context Protocol server runtime for Java and
  Kotlin. Start with the quickstart, then dive into tools, resources, tasks, and configuration.
---

Tachyon is an MCP server runtime for Java 21+ built on Netty and virtual threads. It implements
MCP **2025-11-25** and **MCP 2026-07-28** over Streamable HTTP, runs stateless by default, and
passes all official conformance tests for both protocol versions.

## Start here

- [Quickstart](quickstart/) — build a working server in five minutes
- [FAQ](faq/) — answers to common questions
- [Configuration](configuration/) — network, I/O engines, sessions, CORS
- [Observability](observability/) — OpenTelemetry spans, metrics, and payload capture
- [Deployment](deployment/) — bind address, public hostname, containers

## Build MCP features

- [Tools](tools/) — sync/async handlers, input schemas, structured output
- [Resources](resources/) — static URIs, dynamic handlers, URI templates
- [Tasks](tasks/) — long-running operations with an enforced state machine
- [Extensions](extensions/) — negotiable protocol extensions (SEP-2133)
- [Annotations](annotations/) — bridge third-party annotation frameworks
- [JSON and JSON Schema](json/)

## Testing

- [Testkit](testkit/)

## Kotlin

- [Kotlin DSL](kotlin/)
- [Migrating from the Kotlin MCP SDK](migrate-from-kotlin-mcp-to-tachyon/)

## Internals

- [POST-SSE reconnect & re-delivery](sse-reconnect-redelivery/)
