/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather;

import com.example.weather.service.WeatherService;
import com.example.weather.spi.WeatherObservation;
import com.example.weather.spi.WeatherProvider;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.features.HandlerFutures;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.NoopInteractionContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GetWeatherToolTest {

    @Test
    void elicitsAnotherCityWhenTheFirstIsUnknown() throws Exception {
        var method = new AtomicReference<String>();
        var context = context("{\"action\":\"accept\",\"content\":{\"city\":\"Tallinn\"}}", method);

        var result = invoke(GetWeatherTool.create(new WeatherService(new TestWeatherProvider())), context);

        assertThat(method).hasValue("elicitation/create");
        assertThat(result).isInstanceOf(ToolResult.Success.class);
        var success = (ToolResult.Success) result;
        assertThat(((TextContent) success.content().getFirst()).text()).startsWith("Weather in Tallinn:");
    }

    @Test
    void returnsCityNotFoundWhenElicitationIsCancelled() throws Exception {
        var method = new AtomicReference<String>();
        var context = context("{\"action\":\"cancel\"}", method);

        var result = invoke(GetWeatherTool.create(new WeatherService(new TestWeatherProvider())), context);

        assertThat(method).hasValue("elicitation/create");
        assertThat(result).isEqualTo(ToolResult.error("City not found"));
    }

    @Test
    void returnsCityNotFoundWhenTheElicitedCityIsUnknown() throws Exception {
        var method = new AtomicReference<String>();
        var context = context("{\"action\":\"accept\",\"content\":{\"city\":\"Unknown\"}}", method);

        var result = invoke(GetWeatherTool.create(new WeatherService(new TestWeatherProvider())), context);

        assertThat(method).hasValue("elicitation/create");
        assertThat(result).isEqualTo(ToolResult.error("City not found"));
    }

    @Test
    void returnsFixedErrorWhenProviderFailsWithoutLeakingDetails() throws Exception {
        WeatherProvider failing = new WeatherProvider() {
            @Override
            public WeatherObservation currentWeather(String city) throws Exception {
                throw new IOException("connection refused to internal-host:6443");
            }

            @Override
            public CompletableFuture<WeatherObservation> currentWeatherAsync(String city) {
                return CompletableFuture.failedFuture(new IOException("unused"));
            }
        };

        var result = invoke(GetWeatherTool.create(new WeatherService(failing)), new NoopInteractionContext());

        assertThat(result).isEqualTo(ToolResult.error("Could not get weather"));
    }

    private static InteractionContext context(String response, AtomicReference<String> method) {
        return new NoopInteractionContext() {
            @Override
            public CompletableFuture<String> sendRequest(String requestMethod, Object params) {
                method.set(requestMethod);
                return CompletableFuture.completedFuture(response);
            }
        };
    }

    private static ToolResult invoke(ToolHandler handler, InteractionContext context) throws Exception {
        var result = new AtomicReference<ToolResult>();
        var failure = new AtomicReference<Exception>();
        var thread = Thread.ofVirtual().start(() -> {
            try {
                result.set(HandlerFutures.joinInterruptibly(handler.handleAsync(
                        context,
                        ToolRequest.builder()
                                .name("get-weather")
                                .arguments(Map.of("city", JsonNodeFactory.instance.stringNode("Unknown")))
                                .build())));
            } catch (Exception e) {
                failure.set(e);
            }
        });
        assertThat(thread.join(Duration.ofSeconds(1))).isTrue();
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }
}
