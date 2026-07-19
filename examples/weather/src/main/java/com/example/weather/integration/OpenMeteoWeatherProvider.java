/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather.integration;

import com.example.weather.spi.CityNotFoundException;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class OpenMeteoWeatherProvider implements WeatherProvider {

    private static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    private final HttpClient httpClient;

    public OpenMeteoWeatherProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public WeatherObservation currentWeather(String city) throws Exception {
        var location = location(city, responseBody(httpClient.send(geocodingRequest(city), HttpResponse.BodyHandlers.ofString())));
        var forecast = responseBody(httpClient.send(forecastRequest(location), HttpResponse.BodyHandlers.ofString()));
        return weather(city, forecast);
    }

    @Override
    public CompletableFuture<WeatherObservation> currentWeatherAsync(String city) {
        return httpClient.sendAsync(geocodingRequest(city), HttpResponse.BodyHandlers.ofString())
            .thenApplyAsync(OpenMeteoWeatherProvider::responseBody, executor)
            .thenApply(response -> location(city, response))
            .thenCompose(location -> httpClient.sendAsync(forecastRequest(location), HttpResponse.BodyHandlers.ofString()))
            .thenApplyAsync(OpenMeteoWeatherProvider::responseBody, executor)
            .thenApply(forecast -> weather(city, forecast));
    }

    private static HttpRequest geocodingRequest(String city) {
        var encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
        return HttpRequest.newBuilder(URI.create(GEOCODING_URL + "?name=" + encodedCity + "&count=1&language=en"))
            .GET()
            .build();
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
