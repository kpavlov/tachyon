/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.domain;

/** A request for additional user input during a tool call or prompt get. */
public sealed interface InputRequest permits FormInputRequest, UrlInputRequest, RpcMethodRequest {}
