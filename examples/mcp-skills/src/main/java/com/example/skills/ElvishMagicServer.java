/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package com.example.skills;

import dev.tachyonmcp.core.server.TachyonServer;
import dev.tachyonmcp.extensions.skills.ClasspathSkillsRegistry;
import dev.tachyonmcp.extensions.skills.SkillsExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves bundled fictional Elvish Agent Skills over MCP.
 */
public final class ElvishMagicServer {

    private static final Logger log = LoggerFactory.getLogger(ElvishMagicServer.class);

    private ElvishMagicServer() {
    }

    /**
     * Starts the server on {@code PORT}, or port 8080 when unset.
     *
     * @param args unused
     */
    public static void main(String... args) {
        var server = buildServer(
            System.getenv().getOrDefault("HOST", "localhost"),
            Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"))
        );
        server.start();
        log.info("Elvish skills await at http://{}:{}/mcp", server.host(), server.port());
    }

    static TachyonServer buildServer(String host, int port) {
        return TachyonServer.builder()
            .host(host)
            .port(port)
            .info(info -> info.name("elvish-magic")
                .title("Elvish Magic Library")
                .description("MCP server for fictional Elvish Agent Skills")
                .version("1.0"))
            .withExtensions(SkillsExtension.builder()
                .cacheScope("public")
                .registry(new ClasspathSkillsRegistry("skills"))
                .build())
            .build();
    }
}
