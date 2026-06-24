/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.config;

import java.util.Objects;

public record ServerConfig(
        ServerIdentity identity, CapabilitiesConfig capabilities, SessionConfig session, NetworkConfig network) {

    public static final ServerConfig DEFAULT = new ServerConfig(
            ServerIdentity.DEFAULT, CapabilitiesConfig.DEFAULT, SessionConfig.DEFAULT, NetworkConfig.DEFAULT);

    public ServerConfig {
        Objects.requireNonNull(identity, "identity cannot be null");
        Objects.requireNonNull(capabilities, "capabilities cannot be null");
        Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(network, "network cannot be null");
    }
}
