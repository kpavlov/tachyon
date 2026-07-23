// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather.spi

class CityNotFoundException(
    city: String,
) : RuntimeException("City not found: $city")
