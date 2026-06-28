/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.domain;

public sealed interface InputRequest permits FormInputRequest, UrlInputRequest, RpcMethodRequest {}
