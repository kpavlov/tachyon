/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.weather.spi;

public final class CityNotFoundException extends RuntimeException {

    public CityNotFoundException(String city) {
        super("City not found: " + city);
    }
}
