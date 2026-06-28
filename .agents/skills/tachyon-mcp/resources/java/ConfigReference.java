/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

import dev.tachyonmcp.server.config.CapabilitiesConfig;
import dev.tachyonmcp.server.config.Mode;
import dev.tachyonmcp.server.config.NetworkConfig;
import dev.tachyonmcp.server.config.SessionConfig;
import java.time.Duration;

/**
 * Configuration builder usage. These builders are passed to
 * TachyonServer.builder().capabilities(cfg -> ...), etc.
 */
final class ConfigReference {

    /** Capabilities — which MCP features to advertise. */
    static CapabilitiesConfig capabilities() {
        return CapabilitiesConfig.builder()
                .toolsMode(Mode.AUTO) // ON when tools registered
                .toolsListChanged(false)
                .resourcesMode(Mode.AUTO) // ON when resources registered
                .resourcesSubscribe(false)
                .resourcesListChanged(false)
                .promptsMode(Mode.AUTO) // ON when prompts registered
                .promptsListChanged(false)
                .tasksList(false)
                .tasksCancel(false)
                .tasksRequests(false)
                .completions(false)
                .logging(false)
                .build();
    }

    /** Convenience defaults. */
    static CapabilitiesConfig convenienceDefaults() {
        return CapabilitiesConfig.builder()
                .tools(true) // Mode.ON, listChanged=true
                .resources(true, true) // Mode.ON, subscribe=true, listChanged=true
                .prompts() // Mode.ON, listChanged=false
                .tasks() // list=true, cancel=false, requests=false
                .completions() // true
                .logging() // true
                .build();
    }

    /** Network — binding and transport config. */
    static NetworkConfig network() {
        return NetworkConfig.builder()
                .host("127.0.0.1")
                .port(8080)
                .endpointPath("/mcp")
                .readerIdleTimeout(Duration.ofSeconds(30))
                .writerIdleTimeout(Duration.ofMinutes(2))
                .maxContentLength(65536) // 64KB
                .allowedOrigins("https://app.example.com")
                .allowNullOrigin(false)
                .allowPrivateNetworks(true)
                .allowedHeaders("X-Custom-Header")
                .build();
    }

    /** Session — connection lifecycle. */
    static SessionConfig session() {
        return SessionConfig.builder()
                .stateless(false)
                .sessionTtl(Duration.ofMinutes(10))
                .build();
    }
}
