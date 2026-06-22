/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.handlers;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Implementation;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ServerCapabilities;
import dev.tachyonmcp.runtime.Extension;
import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.extensions.McpExtension;
import dev.tachyonmcp.server.features.prompts.PromptRegistry;
import dev.tachyonmcp.server.features.resources.ResourceRegistry;
import dev.tachyonmcp.server.features.tools.ToolRegistry;
import dev.tachyonmcp.server.session.McpContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

public final class InitializeHandler implements McpMethodHandler {

    private final ToolRegistry toolRegistry;
    private final ResourceRegistry resourceRegistry;
    private final PromptRegistry promptRegistry;
    private final Implementation serverInfo;
    private final ServerCapabilities capabilities;
    private final List<McpExtension> extensions;

    public InitializeHandler(
            ToolRegistry toolRegistry,
            ResourceRegistry resourceRegistry,
            PromptRegistry promptRegistry,
            Implementation serverInfo,
            ServerCapabilities capabilities,
            List<McpExtension> extensions) {
        this.toolRegistry = toolRegistry;
        this.resourceRegistry = resourceRegistry;
        this.promptRegistry = promptRegistry;
        this.serverInfo = serverInfo;
        this.capabilities = capabilities;
        this.extensions = extensions;
    }

    @Override
    public String method() {
        return "initialize";
    }

    @Override
    public Object handle(McpContext context, Object params) {
        var negotiatedVersion = "2025-11-25";
        context.session().protocolVersion(negotiatedVersion);

        // Build capabilities from registries, augmented by pre-configured overrides
        var toolsCap = capabilities.tools();
        if (toolsCap == null && !toolRegistry.isEmpty()) {
            toolsCap = new ServerCapabilities.Tools(true);
        }
        var resourcesCap = capabilities.resources();
        if (resourcesCap == null && !resourceRegistry.getAll().isEmpty()) {
            resourcesCap = new ServerCapabilities.Resources(true, true);
        }
        var promptsCap = capabilities.prompts();
        if (promptsCap == null && !promptRegistry.getAll().isEmpty()) {
            promptsCap = new ServerCapabilities.Prompts(true);
        }
        var tasksCap = capabilities.tasks();
        if (tasksCap == null) {
            tasksCap = new ServerCapabilities.Tasks(
                    JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.objectNode(), null);
        }
        var emptyCap = JsonNodeFactory.instance.objectNode();
        var resultCapabilities =
                new ServerCapabilities(null, emptyCap, emptyCap, promptsCap, resourcesCap, toolsCap, tasksCap, null);

        negotiateExtensions(context, params);
        var negotiatedExtensions = buildNegotiatedExtensions(context);
        if (!negotiatedExtensions.isEmpty()) {
            resultCapabilities = new ServerCapabilities(
                    null, emptyCap, emptyCap, promptsCap, resourcesCap, toolsCap, tasksCap, negotiatedExtensions);
        }

        return new InitializeResult(negotiatedVersion, resultCapabilities, serverInfo, null, null, null);
    }

    private void negotiateExtensions(McpContext context, Object params) {
        var clientExtensions = extractClientExtensions(params);
        for (var ext : extensions) {
            if (clientExtensions.containsKey(ext.extensionId())) {
                context.enableExtension(ext.extensionId());
                var clientSettings = clientExtensions.get(ext.extensionId());
                ext.onConnectionInit(context, asMap(clientSettings));
            }
        }
    }

    private Map<String, JsonNode> buildNegotiatedExtensions(McpContext context) {
        return extensions.stream()
                .filter(e -> context.isExtensionEnabled(e.extensionId()))
                .collect(Collectors.toMap(Extension::extensionId, McpExtension::serverSettings));
    }

    private static Map<String, JsonNode> extractClientExtensions(Object params) {
        if (!(params instanceof InitializeRequestParams initParams)) {
            return Map.of();
        }
        if (initParams.capabilities() == null) {
            return Map.of();
        }
        var clientExtensions = initParams.capabilities().extensions();
        return clientExtensions != null ? clientExtensions : Map.of();
    }

    private static Map<String, JsonNode> asMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        var map = new LinkedHashMap<String, JsonNode>();
        for (var entry : node.properties()) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
