/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather.spi;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface CityProvider {

    /**
     * Returns city names matching the given (possibly partial) query, for completion suggestions.
     */
    CompletableFuture<List<String>> searchCities(String query);
}
