/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.langchain4j;

import dev.tachyonmcp.annotations.langchain4j.LangChain4jAnnotationProvider;
import dev.tachyonmcp.core.server.TachyonServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts an MCP server exposing {@link OrderService}'s {@code @Tool} method via annotations. */
public final class Langchain4jServer {

    private static final Logger log = LoggerFactory.getLogger(Langchain4jServer.class);

    private Langchain4jServer() {}

    /** @param args unused; the listen port is read from the {@code PORT} environment variable, defaulting to 8080. */
    public static void main(String... args) {
        final var port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        final var server = buildServer(port);
        server.start();
        log.info("Connect your MCP client to http://localhost:{}/mcp", server.port());
    }

    static TachyonServer buildServer(int port) {
        return buildServer(port, new OrderService());
    }

    static TachyonServer buildServer(int port, OrderService orderService) {
        return TachyonServer.builder()
                .port(port)
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
