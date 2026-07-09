/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Prompt;
import dev.tachyonmcp.server.domain.PromptArgument;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.Role;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import java.util.List;

final class McpPromptMapper {

    private McpPromptMapper() {}

    static Prompt toPrompt(PromptDescriptor d) {
        return new Prompt(
                d.description(),
                toProtocolPromptArguments(d.arguments()),
                null,
                d.name(),
                d.title(),
                ContentBlockMappers.toProtocolIcons(d.icons()));
    }

    static List<dev.tachyonmcp.protocol.mcp.v2025_11_25.models.PromptArgument> toProtocolPromptArguments(
            List<PromptArgument> domain) {
        if (domain == null) return null;
        return domain.stream()
                .map(a -> new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.PromptArgument(
                        a.description(), a.required(), a.name(), a.title()))
                .toList();
    }

    static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.PromptMessage toProtocolMessage(PromptMessage domain) {
        if (domain == null) return null;
        return new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.PromptMessage(
                toProtocolRole(domain.role()), ContentBlockMappers.toProtocolContentBlock(domain.content()));
    }

    static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role toProtocolRole(Role domain) {
        if (domain == null) return null;
        return switch (domain) {
            case USER -> dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role.USER;
            case ASSISTANT -> dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role.ASSISTANT;
        };
    }
}
