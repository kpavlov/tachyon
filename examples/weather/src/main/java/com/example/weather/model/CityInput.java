/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package com.example.weather.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonClassDescription("City to look up weather for.")
@JsonTypeName("CityInput")
public record CityInput(@JsonPropertyDescription("City name") String city) {
}
