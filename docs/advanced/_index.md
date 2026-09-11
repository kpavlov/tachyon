---
title: "Advanced"
overview_title: "Technical notes"
weight: 80
sidebar_order: 80
toc: true
sidebar_hide: true
build:
  list: never
  render: never
description: |-
  Internal transport implementation notes for Tachyon contributors.
---

Internal notes on transport behavior, kept in the repository for contributors. They are not
part of the product documentation: they name private classes and describe implementation
history rather than a supported API.

- [POST-SSE reconnect and re-delivery](sse-reconnect-redelivery.md)

The behavior a server author can rely on is documented under
[Running Tachyon](../running/).
