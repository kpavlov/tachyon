/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.config;

import java.util.Objects;

/**
 * Aggregated server configuration grouping identity, capabilities, session, network, runtime, and
 * monitoring settings.
 *
 * @param identity     server identity metadata (name, version, etc.)
 * @param capabilities which MCP features are enabled
 * @param session      session lifecycle and persistence settings
 * @param network      transport-level settings (host, port, CORS, etc.)
 * @param runtime      handler-execution runtime settings (shutdown drain, etc.)
 * @param monitoring   diagnostics and observability settings
 */
public record ServerConfig(
        ServerIdentity identity,
        CapabilitiesConfig capabilities,
        SessionConfig session,
        NetworkConfig network,
        RuntimeConfig runtime,
        MonitoringConfig monitoring) {

    static final ServerConfig DEFAULT = new ServerConfig(
            ServerIdentity.DEFAULT,
            CapabilitiesConfig.DEFAULT,
            SessionConfig.STATELESS,
            NetworkConfig.DEFAULT,
            RuntimeConfig.DEFAULT,
            MonitoringConfig.DEFAULT);

    public ServerConfig {
        Objects.requireNonNull(identity, "identity cannot be null");
        Objects.requireNonNull(capabilities, "capabilities cannot be null");
        Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(network, "network cannot be null");
        Objects.requireNonNull(runtime, "runtime cannot be null");
        Objects.requireNonNull(monitoring, "monitoring cannot be null");
    }
}
