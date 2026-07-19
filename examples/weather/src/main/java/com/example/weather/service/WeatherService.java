/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather.service;

import com.example.weather.spi.CityProvider;
import com.example.weather.spi.WeatherObservation;
import com.example.weather.spi.WeatherProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public record WeatherService(
    WeatherProvider weatherProvider,
    CityProvider cityProvider) {

    public WeatherObservation currentWeather(String city) throws Exception {
        return weatherProvider.currentWeather(city);
    }

    public CompletableFuture<WeatherObservation> currentWeatherAsync(String city) {
        return weatherProvider.currentWeatherAsync(city);
    }

    public CompletableFuture<List<String>> searchCities(String query) {
        return cityProvider.searchCities(query);
    }

    private static final String PREDICTION_ARTICLE = loadPredictionArticle();

    public String predictionArticle() {
        return PREDICTION_ARTICLE;
    }

    private static String loadPredictionArticle() {
        var article = WeatherService.class.getResourceAsStream("/articles/prediction-article.md");
        if (article == null) {
            throw new IllegalStateException("Missing prediction article");
        }
        try (article) {
            return new String(article.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read prediction article", e);
        }
    }

    public String rewriteForecastInstruction(String forecast, NarrationStyle style) {
        if (forecast.isBlank()) {
            throw new IllegalArgumentException("forecast is required");
        }
        return "Rewrite the following weather forecast in %s style. Preserve factual details:\n\n```%s\n```"
                .formatted(style.value(), forecast);
    }
}
