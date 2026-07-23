// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather.spi

fun interface CityProvider {
    fun searchCities(query: String): List<String>
}
