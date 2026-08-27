/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package com.example.mcpjava;

import dev.tachyonmcp.annotations.mcpjava.McpJavaAnnotationProvider;
import dev.tachyonmcp.core.server.TachyonServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts an MCP server exposing {@link AnnotatedService}'s mcp-java annotations.
 */
public final class McpJavaServer {

    private static final Logger log = LoggerFactory.getLogger(McpJavaServer.class);

    private McpJavaServer() {
    }

    /**
     * Starts the server on {@code HOST}:{@code PORT}, defaulting to {@code localhost:8080}.
     * Set {@code ALLOWED_HOST} to accept an extra {@code Host} authority (e.g. a Docker-bridge caller).
     */
    public static void main(String... args) {
        final var server = buildServer(
            System.getenv().getOrDefault("HOST", "localhost"),
            Integer.parseInt(System.getenv().getOrDefault("PORT", "8080")),
            System.getenv("ALLOWED_HOST")
        );
        server.start();
        log.info("Connect your MCP client to http://{}:{}/mcp", server.host(), server.port());
    }

    static TachyonServer buildServer(String host, int port, @Nullable String allowedHost) {
        return TachyonServer.builder()
            .host(host)
            .port(port)
            .network(n -> {
                if (allowedHost != null && !allowedHost.isBlank()) {
                    n.allowedHosts(allowedHost);
                }
            })
            .info(it -> it.name("mcp-java-example")
                .title("mcp-java Example")
                .description("MCP server scanning mcp-java annotations")
                .version("1.0"))
            .annotations(a -> a
                .withProvider(McpJavaAnnotationProvider.instance())
                .register(new AnnotatedService()))
            .build();
    }
}
