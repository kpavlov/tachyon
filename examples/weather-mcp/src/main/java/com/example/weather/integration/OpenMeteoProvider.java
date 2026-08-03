/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather.integration;

import com.example.weather.spi.CityNotFoundException;
import com.example.weather.spi.CityProvider;
import com.example.weather.spi.WeatherCondition;
import com.example.weather.spi.WeatherObservation;
import com.example.weather.spi.WeatherProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class OpenMeteoProvider implements WeatherProvider, CityProvider {

    private static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    private final HttpClient httpClient;

    public OpenMeteoProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public WeatherObservation currentWeather(String city) throws Exception {
        var location = location(city, responseBody(httpClient.send(geocodingRequest(city, 1), HttpResponse.BodyHandlers.ofString())));
        var forecast = responseBody(httpClient.send(forecastRequest(location), HttpResponse.BodyHandlers.ofString()));
        return weather(city, forecast);
    }

    @Override
    public CompletableFuture<WeatherObservation> currentWeatherAsync(String city) {
        return httpClient.sendAsync(geocodingRequest(city, 1), HttpResponse.BodyHandlers.ofString())
            .thenApplyAsync(OpenMeteoProvider::responseBody, executor)
            .thenApply(response -> location(city, response))
            .thenCompose(location -> httpClient.sendAsync(forecastRequest(location), HttpResponse.BodyHandlers.ofString()))
            .thenApplyAsync(OpenMeteoProvider::responseBody, executor)
            .thenApply(forecast -> weather(city, forecast));
    }

    @Override
    public CompletableFuture<List<String>> searchCities(String query) {
        if (query == null || query.length() < 2) {
            return CompletableFuture.completedFuture(List.of());
        }
        return httpClient.sendAsync(geocodingRequest(query, 10), HttpResponse.BodyHandlers.ofString())
            .thenApplyAsync(OpenMeteoProvider::responseBody, executor)
            .thenApply(OpenMeteoProvider::cityNames)
            .exceptionally(e -> List.of());
    }

    private static HttpRequest geocodingRequest(String query, int count) {
        var encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return HttpRequest.newBuilder(URI.create(GEOCODING_URL + "?name=" + encoded + "&count=" + count + "&language=en"))
            .GET()
            .build();
    }

    private static List<String> cityNames(String response) {
        var names = new LinkedHashSet<String>();
        for (var result : parse(response).path("results")) {
            var name = result.path("name").asString();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private static HttpRequest forecastRequest(Location location) {
        var uri = FORECAST_URL
            + "?latitude=" + location.latitude()
            + "&longitude=" + location.longitude()
            + "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
            + "&wind_speed_unit=kmh";
        return HttpRequest.newBuilder(URI.create(uri)).GET().build();
    }

    private static String responseBody(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Open-Meteo request failed with HTTP " + response.statusCode());
        }
        return response.body();
    }

    private Location location(String city, String response) {
        var result = parse(response).path("results").path(0);
        if (result.isMissingNode()) {
            throw new CityNotFoundException(city);
        }
        return new Location(result.path("latitude").asDouble(), result.path("longitude").asDouble());
    }

    private WeatherObservation weather(String city, String response) {
        var current = parse(response).path("current");
        return new WeatherObservation(
            city,
            condition(current.path("weather_code").asInt()).displayName(),
            current.path("temperature_2m").asDouble(),
            current.path("relative_humidity_2m").asInt(),
            current.path("wind_speed_10m").asDouble());
    }

    private static JsonNode parse(String response) {
        try {
            return MAPPER.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid Open-Meteo response", e);
        }
    }

    private static WeatherCondition condition(int weatherCode) {
        return switch (weatherCode) {
            case 0 -> WeatherCondition.CLEAR_SKY;
            case 1, 2 -> WeatherCondition.PARTLY_CLOUDY;
            case 3 -> WeatherCondition.OVERCAST;
            case 45, 48 -> WeatherCondition.FOGGY;
            case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAINY;
            case 71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOWY;
            case 95, 96, 99 -> WeatherCondition.THUNDERSTORMS;
            default -> WeatherCondition.UNKNOWN;
        };
    }

    private record Location(double latitude, double longitude) {
    }
}
