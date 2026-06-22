/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import java.time.Duration;

public record ServerConfig(Duration sessionTtl, boolean stateless) {
    public static final ServerConfig DEFAULT = new ServerConfig(Duration.ofSeconds(30), false);

    public static ServerConfig of(Duration sessionTtl) {
        return new ServerConfig(sessionTtl, false);
    }
}
