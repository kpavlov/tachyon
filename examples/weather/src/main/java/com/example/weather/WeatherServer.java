/* Copyright (c) 2026 Konstantin Pavlov. */

package com.example.weather;

import dev.tachyonmcp.server.McpServerHandle;
import dev.tachyonmcp.server.TachyonMcpServer;
import dev.tachyonmcp.server.domain.ReadResourceRequest;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.session.McpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WeatherServer {

    private static final Logger log = LoggerFactory.getLogger(WeatherServer.class);

    public static void main(String... args) {
        final var handle = createServer(8080);
        final var port = handle.port();
        log.info("Connect your MCP client to http://localhost:{}/mcp", port);
    }

    static McpServerHandle createServer(int port) {
        return TachyonMcpServer.builder()
            .info(it -> it
                .name("weather-server")
                .title("Weather Server")
                .description("Weather MCP server")
                .websiteUrl("http://localhost:8080/mcp")
                .instructions("Test instructions")
                .version("1.0")
            )
            .tool(new GetWeatherTool())
            .resource(
                ResourceDescriptor.of(
                    "prediction-article",
                    "weather://prediction/article",
                    "Weather prediction article",
                    "text/markdown"),
                WeatherServer::handleArticleResource)
            .stateless(true)
            .port(port)
            .bind();
    }

    private WeatherServer() {
    }

    private static TextResourceContents handleArticleResource(McpContext ctx, ReadResourceRequest req) {
        // language=markdown
        var article = """
            # Weather Prediction

            Weather prediction uses physics-based models and statistical methods to forecast
            atmospheric conditions. Modern forecasting combines:

            - **Numerical Weather Prediction (NWP)** — solving fluid dynamics equations
              on a 3-D grid of the atmosphere
            - **Ensemble forecasting** — running multiple model perturbations to estimate
              confidence intervals
            - **Machine learning** — neural networks that learn from historical patterns
              and improve short-term nowcasting

            The global observing system includes weather stations, radiosondes, aircraft
            reports, ocean buoys, and over 30 polar-orbiting and geostationary satellites.

            ---
            Published by Tachyon Weather MCP — %s
            """.formatted(java.time.LocalDate.now());
        return TextResourceContents.of(req.uri(), "text/markdown", article);
    }

}
