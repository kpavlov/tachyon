/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package com.example.weather.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonClassDescription("Unit used to represent temperature.")
@JsonTypeName("TemperatureUnit")
public enum TemperatureUnit {
    @JsonProperty("celsius") CELSIUS,
    @JsonProperty("fahrenheit") FAHRENHEIT
}
