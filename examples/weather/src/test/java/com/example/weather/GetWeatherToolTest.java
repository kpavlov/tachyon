/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather;

import com.example.weather.service.WeatherService;
import com.example.weather.spi.CityProvider;
import com.example.weather.spi.WeatherObservation;
import com.example.weather.spi.WeatherProvider;
import dev.tachyonmcp.runtime.ContextNotifications;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.Args;
import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.features.HandlerFutures;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.NoopInteractionContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class GetWeatherToolTest {

    private final WeatherProvider weatherProvider = new TestWeatherProvider();
    private final CityProvider cityProvider = new TestCityProvider();

    @Test
    void returnsCityNotFoundWhenElicitationIsCancelled() throws Exception {
        var method = new AtomicReference<@Nullable String>();
        var context = context("{\"action\":\"cancel\"}", method);

        var result = invoke(GetWeatherTool.create(new WeatherService(weatherProvider, cityProvider)), context);

        assertThat(method).hasValue("elicitation/create");
        assertThat(result).isEqualTo(ToolResult.error("City not found"));
    }

    @Test
    void returnsCityNotFoundWhenTheElicitedCityIsUnknown() throws Exception {
        var method = new AtomicReference<@Nullable String>();
        var context = context("{\"action\":\"accept\",\"content\":{\"city\":\"Unknown\"}}", method);

        var result = invoke(GetWeatherTool.create(new WeatherService(weatherProvider, cityProvider)), context);

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

        var result = invoke(GetWeatherTool.create(
            new WeatherService(failing, cityProvider)), context("unused", new AtomicReference<>()
        ));

        assertThat(result).isEqualTo(ToolResult.error("Could not get weather"));
    }

    private static final ContextNotifications NOOP_NOTIFICATIONS = new ContextNotifications() {
        @Override
        public void log(LoggingLevel level, @Nullable String logger, @Nullable Object data) {}

        @Override
        public void progress(@Nullable Object progressToken, double progress, double total, String message) {}

        @Override
        public void comment(@Nullable String message) {}
    };

    private static InteractionContext context(String response, AtomicReference<@Nullable String> method) {
        return new NoopInteractionContext() {
            @Override
            public CompletableFuture<String> sendRequest(String requestMethod, Object params) {
                method.set(requestMethod);
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public ContextNotifications notifications() {
                return NOOP_NOTIFICATIONS;
            }
        };
    }

    private static ToolResult invoke(ToolHandler handler, InteractionContext context) throws Exception {
        var result = new AtomicReference<@Nullable ToolResult>();
        var failure = new AtomicReference<@Nullable Exception>();
        Thread.ofVirtual().start(() -> {
            try {
                result.set(HandlerFutures.joinInterruptibly(handler.handleAsync(
                        context,
                        ToolRequest.builder()
                                .name("get-weather")
                                .arguments(Args.of(Map.of("city", JsonNodeFactory.instance.stringNode("Unknown"))))
                                .build())));
            } catch (Exception e) {
                failure.set(e);
            }
        });
        await().atMost(Duration.ofSeconds(1)).until(() -> result.get() != null || failure.get() != null);
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }
}
