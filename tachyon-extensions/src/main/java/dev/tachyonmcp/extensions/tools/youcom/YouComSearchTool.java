/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.extensions.tools.youcom;

import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;

import dev.tachyonmcp.runtime.InteractionContext;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

public final class YouComSearchTool extends AbstractSyncToolHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final URI DEFAULT_ENDPOINT = URI.create("https://ydc-index.io/v1/search");

    private final @NonNull HttpClient client;
    private final @NonNull String apiKey;
    private final @NonNull URI endpoint;

    public YouComSearchTool(@NonNull String apiKey) {
        this(apiKey, DEFAULT_ENDPOINT, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    YouComSearchTool(@NonNull String apiKey, @NonNull URI endpoint, @NonNull HttpClient client) {
        super(ToolDescriptor.builder()
                .name("youcom_search")
                .title("You.com Search")
                .description("Search the web with You.com when live external context helps.")
                .inputSchema(inputSchema())
                .build());
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.client = Objects.requireNonNull(client, "client");
    }

    public static @NonNull Optional<YouComSearchTool> fromEnv(@NonNull Map<String, String> env) {
        var key = env.get("YDC_API_KEY");
        if (key == null || key.isBlank()) return Optional.empty();
        return Optional.of(new YouComSearchTool(key));
    }

    @Override
    public @NonNull ToolResult handle(@NonNull InteractionContext context, @NonNull ToolArgs args) {
        var query = args.stringOpt("query").orElse("").trim();
        var count = Math.max(1, Math.min(args.intOr("count", 5), 10));
        if (query.isBlank()) return ToolResult.error("query is required");

        var request = HttpRequest.newBuilder(URI.create(endpoint + "?query=" + encode(query) + "&count=" + count))
                .timeout(Duration.ofSeconds(20))
                .header("X-API-Key", apiKey)
                .GET()
                .build();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return ToolResult.error("You.com search failed (HTTP " + response.statusCode() + "): "
                        + truncate(response.body()));
            }
            return parseResults(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("You.com search was interrupted");
        } catch (JacksonException e) {
            return ToolResult.error("You.com search returned invalid JSON: " + e.getMessage());
        } catch (IOException e) {
            return ToolResult.error("You.com search request failed: " + e.getMessage());
        }
    }

    private ToolResult parseResults(String body) throws IOException {
        var root = MAPPER.readTree(body);
        var results = root.path("results").path("web");
        if (!results.isArray()) return ToolResult.error("You.com search returned an unexpected response shape");

        var items = MAPPER.createArrayNode();
        for (JsonNode node : results) {
            var item = MAPPER.createObjectNode();
            item.put("title", node.path("title").asString(""));
            item.put("url", node.path("url").asString(""));
            item.put("snippet", firstText(node, "description", "snippet", "content"));
            items.add(item);
        }

        var payload = MAPPER.createObjectNode();
        payload.set("results", items);
        return ToolResult.of(payload, formatText(items));
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            var value = node.path(field).asString("");
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String truncate(String body) {
        return body.length() <= 240 ? body : body.substring(0, 240) + "...";
    }

    private static String formatText(ArrayNode items) {
        var out = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            out.append(i + 1).append(". ")
                    .append(item.path("title").asString("(untitled)"))
                    .append("\n")
                    .append(item.path("url").asString(""))
                    .append("\n")
                    .append(item.path("snippet").asString(""))
                    .append("\n\n");
        }
        return out.toString().trim();
    }

    private static JsonNode inputSchema() {
        var root = MAPPER.createObjectNode();
        root.put("type", "object");
        var props = root.putObject("properties");
        props.putObject("query").put("type", "string");
        props.putObject("count").put("type", "integer").put("minimum", 1).put("maximum", 10);
        root.putArray("required").add("query");
        return root;
    }
}
