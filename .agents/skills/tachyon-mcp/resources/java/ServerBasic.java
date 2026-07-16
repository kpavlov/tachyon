/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.json.JacksonPayloadSerde;
import dev.tachyonmcp.server.json.NetworkntJsonSchemaValidator;

import java.util.List;
import java.util.UUID;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

/**
 * Complete MCP server example with tool, resource, template, and prompt.
 */
public final class ServerBasic {

    public static void main(String... args) {
        var server = createServer(8080);
        System.out.println("MCP server on http://localhost:" + server.port() + "/mcp");
    }

    static TachyonServer createServer(int port) {
        var server = TachyonServer.builder()
            .info(it -> it.name("demo-server").version("1.0").description("Demo MCP server"))
            .capabilities(c -> c.tools(true).resources(true, true).prompts(true))
            .session(s -> s
                .enabled(true)
                .sessionTtl(ofMinutes(5))
                .janitorInterval(ofSeconds(5))
                .sessionIdGenerator((req) -> "sid_" + UUID.randomUUID())
            )
            .json(j ->
                j.inputSchemaValidator(NetworkntJsonSchemaValidator.INSTANCE)
                    .serde(JacksonPayloadSerde.INSTANCE)
            )
            .runtime(r -> r.shutdownGracePeriod(ofSeconds(5)))
            .network(n -> n.allowedOrigins("*").allowNullOrigin(true))
            .tool(ToolHandler.of(b -> b.name("ping"), (ctx, args) -> ToolResult.text("pong")))
            .resource(
                ResourceDescriptor.of(
                    "config", "demo://config",
                    "Server configuration", "application/json"),
                (ctx, uri, params, uriTemplate) ->
                    TextResourceContents.of(uri, "application/json", "{\"mode\":\"production\"}"))
            .prompt(
                PromptDescriptor.of("greet", "Generates a greeting"),
                List.of(PromptMessage.user("Say hello")))
            .resourceTemplate(builder -> builder
                    .name("user-profile")
                    .uriTemplate("demo://users/{userId}/profile")
                    .description("User profile data")
                    .mimeType("application/json"),
                (ctx, uri, params, uriTemplate) -> {
                    var userId = params.get("userId").scalarValue();
                    return TextResourceContents.of(
                        uri, "application/json", "{\"userId\":\"" + userId + "\",\"name\":\"User\"}");
                })
            .port(port)
            .start();

        return server;
    }

    private ServerBasic() {
    }
}
