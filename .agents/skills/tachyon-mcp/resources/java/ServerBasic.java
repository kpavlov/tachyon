/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

import dev.tachyonmcp.server.ServerHandle;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import dev.tachyonmcp.server.features.tools.SyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.json.JacksonPayloadSerde;
import dev.tachyonmcp.server.json.JsonSchemaValidator;
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
        var handle = createServer(8080);
        System.out.println("MCP server on http://localhost:" + handle.port() + "/mcp");
    }

    static ServerHandle createServer(int port) {
        var handle = TachyonServer.builder()
            .info(it -> it.name("demo-server").version("1.0").description("Demo MCP server"))
            .capabilities(c -> c.tools(true).resources(true, true).prompts(true))
            .session(s -> s
                .enabled(true)
                .sessionTtl(ofMinutes(5))
                .janitorInterval(ofSeconds(5))
                .sessionIdGenerator((req) -> "sid_" + UUID.randomUUID())
            )
            .json(j ->
                j
                    // json schema validator
                    .schemaValidator(NetworkntJsonSchemaValidator.INSTANCE)
                    .inputSchemaValidator(NetworkntJsonSchemaValidator.INSTANCE)
                    .outputSchemaValidator(JsonSchemaValidator.NOOP)
                    // serializer
                    .serializer(JacksonPayloadSerde.INSTANCE)
                    .deserializer(JacksonPayloadSerde.INSTANCE)
                    .serde(JacksonPayloadSerde.INSTANCE)
            )
            .runtime(r -> r.shutdownGracePeriod(ofSeconds(5)))
            .network(n -> n.allowedOrigins("*").allowNullOrigin(true))
            .tool(SyncToolHandler.of("ping", null, null, (ctx, args) -> ToolResult.text("pong")))
            .resource(
                ResourceDescriptor.of(
                    "config", "demo://config",
                    "Server configuration", "application/json"),
                (ctx, req) ->
                    TextResourceContents.of(req.uri(), "application/json", "{\"mode\":\"production\"}"))
            .prompt(
                PromptDescriptor.of("greet", "Generates a greeting"),
                args -> List.of(PromptMessage.user("Say hello")))
            .port(port)
            .start();

        handle.server()
            .resources()
            .addTemplate(ResourceTemplateEntry.of(
                "user-profile",
                "demo://users/{userId}/profile",
                "User profile data",
                "application/json",
                (ctx, uri, params) -> {
                    var userId = params.get("userId");
                    return TextResourceContents.of(
                        uri, "application/json", "{\"userId\":\"" + userId + "\",\"name\":\"User\"}");
                }));

        return handle;
    }

    private ServerBasic() {
    }
}
