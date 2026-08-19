/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2026_07_28.codecs;

import dev.tachyonmcp.api.json.PayloadDeserializer;
import dev.tachyonmcp.core.protocol.ProtocolRequestMapper.SubscriptionListenRequest;
import dev.tachyonmcp.core.protocol.ProtocolRequestMapper.ToolCallRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Request mapper for MCP 2026-07-28.
 *
 * <p>MCP 2026-07-28 ignores the legacy {@code tools/call.task} parameter. Task creation is
 * server-directed through the tasks extension (SEP-2663).
 */
public final class McpRequestMapper extends dev.tachyonmcp.core.protocol.mcp.v2025_11_25.codecs.McpRequestMapper {

    @Override
    public ToolCallRequest callTool(@Nullable Object params, PayloadDeserializer payloadDeserializer) {
        return callTool(params, payloadDeserializer, false);
    }

    @Override
    public boolean supportsLegacyTaskAugmentation() {
        return false;
    }

    @Override
    public boolean supportsSubscriptionsListen() {
        return true;
    }

    @Override
    public SubscriptionListenRequest subscriptionsListen(@Nullable Object params) {
        var map = asMap(params);
        if (!(map.get("notifications") instanceof Map<?, ?> filter)) {
            return new SubscriptionListenRequest(false, false, false, Set.of());
        }
        Set<String> resourceSubscriptions = filter.get("resourceSubscriptions") instanceof List<?> uris
                ? uris.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .collect(Collectors.toUnmodifiableSet())
                : Set.of();
        return new SubscriptionListenRequest(
                Boolean.TRUE.equals(filter.get("toolsListChanged")),
                Boolean.TRUE.equals(filter.get("promptsListChanged")),
                Boolean.TRUE.equals(filter.get("resourcesListChanged")),
                resourceSubscriptions);
    }
}
