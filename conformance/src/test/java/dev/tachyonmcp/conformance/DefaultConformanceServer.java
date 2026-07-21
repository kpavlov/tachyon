/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.conformance;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.features.tools.AbstractToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class DefaultConformanceServer extends AbstractConformanceServer {

    @Override
    protected ServerEngine createServer(boolean isStateful) {
        return (ServerEngine) TachyonServer.builder()
                .capabilities(c -> c.logging())
                .session(s -> s.enabled(isStateful))
                .network(n -> n.host("localhost"))
                .build();
    }

    /**
     * Tools scoped to the latest stable conformance suite (protocol version 2025-11-25) that the
     * draft suite no longer exercises: logging, sampling, elicitation, and the SEP-1034/SEP-1330
     * elicitation defaults/enums scenarios.
     */
    @Override
    protected void registerVersionSpecificTools(ServerEngine server) {
        server.tools()
                .register(
                        new AbstractToolHandler(ToolDescriptor.builder()
                                .name("test_tool_with_logging")
                                .description("Tool with logging")
                                .inputSchema(INPUT_SCHEMA_NO_ARGS)
                                .build()) {
                            @Override
                            public ToolResult handle(InteractionContext ctx, ToolRequest request) {
                                ctx.notifications().info("tachyon.tools", Map.of("message", "Tool execution started"));
                                delay(50);
                                ctx.notifications().info("tachyon.tools", Map.of("message", "Tool processing data"));
                                delay(50);
                                ctx.notifications()
                                        .info("tachyon.tools", Map.of("message", "Tool execution completed"));
                                return ToolResult.text("Tool execution completed");
                            }
                        });

        server.tools()
                .register(ToolHandler.of(
                        b -> b.name("test_sampling")
                                .description("Tool that requests sampling")
                                .inputSchema(INPUT_SCHEMA_WITH_PROMPT),
                        (ctx, request) -> {
                            var promptOpt = request.arguments().stringOpt("prompt");
                            if (promptOpt.isPresent()) {
                                var prompt = promptOpt.get();
                                try {
                                    Map<String, Object> paramsMap = Map.of(
                                            "messages",
                                            List.of(Map.of(
                                                    "role", "user", "content", Map.of("type", "text", "text", prompt))),
                                            "maxTokens",
                                            100);
                                    var responseJson = ctx.sendRequest(
                                                    "sampling/createMessage",
                                                    JsonRpcCodec.writeValueAsString(paramsMap))
                                            .get(2, TimeUnit.SECONDS);
                                    var responseObj = (Map<String, Object>) JsonRpcCodec.readValue(responseJson);
                                    var content = responseObj.get("content");
                                    var text = content instanceof Map<?, ?> cm && "text".equals(cm.get("type"))
                                            ? (String) cm.get("text")
                                            : "";
                                    return ToolResult.text(text);
                                } catch (Exception e) {
                                    return ToolResult.error("Sampling request failed");
                                }
                            }
                            return ToolResult.text("sampling not fully implemented");
                        }));

        server.tools()
                .register(ToolHandler.of(
                        b -> b.name("test_elicitation")
                                .description("Tool that requests elicitation")
                                .inputSchema(INPUT_SCHEMA_WITH_MESSAGE),
                        (ctx, request) -> {
                            var messageOpt = request.arguments().stringOpt("message");
                            if (messageOpt.isPresent()) {
                                var message = messageOpt.get();
                                try {
                                    var paramsMap = Map.of(
                                            "mode",
                                            "form",
                                            "message",
                                            message,
                                            "requestedSchema",
                                            Map.of(
                                                    "type",
                                                    "object",
                                                    "properties",
                                                    Map.of(
                                                            "username", Map.of("type", "string"),
                                                            "email", Map.of("type", "string")),
                                                    "required",
                                                    List.of("username", "email")));
                                    var responseJson = ctx.sendRequest(
                                                    "elicitation/create", JsonRpcCodec.writeValueAsString(paramsMap))
                                            .get(2, TimeUnit.SECONDS);
                                    var responseObj = (Map<String, Object>) JsonRpcCodec.readValue(responseJson);
                                    var action = responseObj.get("action");
                                    var content = responseObj.get("content");
                                    var text = "User response: " + (action != null ? action : "unknown");
                                    if (content instanceof Map<?, ?> cm) text += ", " + cm;
                                    return ToolResult.text(text);
                                } catch (Exception e) {
                                    return ToolResult.error("Elicitation request failed");
                                }
                            }
                            return ToolResult.text("elicitation not fully implemented");
                        }));

        server.tools()
                .register(ToolHandler.of(
                        b -> b.name("test_elicitation_sep1034_defaults")
                                .description("Elicitation with defaults")
                                .inputSchema(INPUT_SCHEMA_NO_ARGS),
                        (ctx, request) -> {
                            try {
                                var paramsMap = Map.of(
                                        "mode",
                                        "form",
                                        "message",
                                        "Please provide your details with defaults",
                                        "requestedSchema",
                                        Map.of(
                                                "type",
                                                "object",
                                                "properties",
                                                Map.<String, Object>of(
                                                        "name",
                                                        Map.of("type", "string", "default", "John Doe"),
                                                        "age",
                                                        Map.of("type", "integer", "default", 30),
                                                        "score",
                                                        Map.of("type", "number", "default", 95.5),
                                                        "status",
                                                        Map.of(
                                                                "type",
                                                                "string",
                                                                "enum",
                                                                List.of("active", "inactive"),
                                                                "default",
                                                                "active"),
                                                        "verified",
                                                        Map.of("type", "boolean", "default", true))));
                                var responseJson = ctx.sendRequest(
                                                "elicitation/create", JsonRpcCodec.writeValueAsString(paramsMap))
                                        .get(2, TimeUnit.SECONDS);
                                var responseObj = (Map<String, Object>) JsonRpcCodec.readValue(responseJson);
                                var action = responseObj.get("action");
                                var content = responseObj.get("content");
                                var text = "Defaults " + (action != null ? action : "unknown");
                                if (content instanceof Map<?, ?> cm) text += ", " + cm;
                                return ToolResult.text(text);
                            } catch (Exception e) {
                                return ToolResult.error("Error: " + e.getMessage());
                            }
                        }));

        server.tools()
                .register(ToolHandler.of(
                        b -> b.name("test_elicitation_sep1330_enums")
                                .description("Elicitation with enums")
                                .inputSchema(INPUT_SCHEMA_NO_ARGS),
                        (ctx, request) -> {
                            try {
                                var props = new LinkedHashMap<String, Object>();
                                props.put(
                                        "untitledSingle",
                                        Map.of("type", "string", "enum", List.of("option1", "option2", "option3")));
                                props.put(
                                        "titledSingle",
                                        Map.of(
                                                "type",
                                                "string",
                                                "oneOf",
                                                List.of(
                                                        Map.of("const", "opt_a", "title", "Option A"),
                                                        Map.of("const", "opt_b", "title", "Option B"))));
                                props.put(
                                        "legacyEnum",
                                        Map.of(
                                                "type",
                                                "string",
                                                "enum",
                                                List.of("val1", "val2"),
                                                "enumNames",
                                                List.of("Value 1", "Value 2")));
                                props.put(
                                        "untitledMulti",
                                        Map.of(
                                                "type",
                                                "array",
                                                "items",
                                                Map.of("type", "string", "enum", List.of("x", "y", "z"))));
                                props.put(
                                        "titledMulti",
                                        Map.of(
                                                "type",
                                                "array",
                                                "items",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "anyOf",
                                                        List.of(
                                                                Map.of("const", "item1", "title", "Item One"),
                                                                Map.of("const", "item2", "title", "Item Two")))));
                                var paramsMap = Map.of(
                                        "mode", "form",
                                        "message", "Please select your preferences",
                                        "requestedSchema", Map.of("type", "object", "properties", props));
                                var responseJson = ctx.sendRequest(
                                                "elicitation/create", JsonRpcCodec.writeValueAsString(paramsMap))
                                        .get(2, TimeUnit.SECONDS);
                                var responseObj = (Map<String, Object>) JsonRpcCodec.readValue(responseJson);
                                var action = responseObj.get("action");
                                var content = responseObj.get("content");
                                var text = "Enums " + (action != null ? action : "unknown");
                                if (content instanceof Map<?, ?> cm) text += ", " + cm;
                                return ToolResult.text(text);
                            } catch (Exception e) {
                                return ToolResult.error("Error: " + e.getMessage());
                            }
                        }));
    }
}
