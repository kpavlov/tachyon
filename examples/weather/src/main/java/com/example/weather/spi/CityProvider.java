/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather.spi;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface CityProvider {

    CompletableFuture<List<String>> searchCities(String query);
}
