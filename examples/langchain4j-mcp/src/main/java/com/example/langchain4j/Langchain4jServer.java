/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.langchain4j;

import dev.tachyonmcp.annotations.langchain4j.LangChain4jAnnotationProvider;
import dev.tachyonmcp.core.server.TachyonServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts an MCP server exposing {@link OrderService}'s {@code @Tool} method via annotations. */
public final class Langchain4jServer {

    private static final Logger log = LoggerFactory.getLogger(Langchain4jServer.class);

    private Langchain4jServer() {}

    /**
     * @param args unused; the bind address comes from {@code HOST}/{@code PORT} (default
     *     {@code localhost:8080}), and {@code ALLOWED_HOST} adds an extra accepted {@code Host}
     *     authority (e.g. a Docker-bridge caller).
     */
    public static void main(String... args) {
        final var server = buildServer(
                System.getenv().getOrDefault("HOST", "localhost"),
                Integer.parseInt(System.getenv().getOrDefault("PORT", "8080")),
                System.getenv("ALLOWED_HOST"),
                new OrderService());
        server.start();
        log.info("Connect your MCP client to http://{}:{}/mcp", server.host(), server.port());
    }

    static TachyonServer buildServer(
            String host, int port, @Nullable String allowedHost, OrderService orderService) {
        return TachyonServer.builder()
                .host(host)
                .port(port)
                .network(n -> {
                    if (allowedHost != null && !allowedHost.isBlank()) {
                        n.allowedHosts(allowedHost);
                    }
                })
                .info(it -> it.name("langchain4j-example")
                        .title("LangChain4j Example")
                        .description("MCP server scanning a LangChain4j @Tool method via annotations")
                        .version("1.0"))
                .annotations(a -> a
                    .withProvider(LangChain4jAnnotationProvider.instance())
                    .register(orderService)
                )
                .build();
    }
}
