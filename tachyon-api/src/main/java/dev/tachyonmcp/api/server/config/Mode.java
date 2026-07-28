/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.config;

/** Controls whether a capability is enabled, disabled, or auto-detected from registered features. */
public enum Mode {
    /** Enabled only when features of this type are registered. */
    AUTO,
    /** Always enabled. */
    ON,
    /** Always disabled. */
    OFF
}
