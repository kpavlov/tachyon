// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather

import com.example.weather.spi.CityProvider

class TestCityProvider : CityProvider {
    override fun searchCities(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return KNOWN_CITIES.filter { it.lowercase().startsWith(query.lowercase()) }
    }

    companion object {
        private val KNOWN_CITIES = listOf("London", "Los Angeles", "Tokyo", "Tallinn")
    }
}
