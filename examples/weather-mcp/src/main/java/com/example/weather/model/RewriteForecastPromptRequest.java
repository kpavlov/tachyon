/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.weather.model;

import com.example.weather.service.NarrationStyle;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonClassDescription("Input for rewriting a weather forecast in a chosen style.")
@JsonTypeName("RewriteForecastPromptRequest")
public record RewriteForecastPromptRequest(
        @JsonPropertyDescription("Weather forecast to rewrite") String forecast,
        @JsonPropertyDescription("Narration style") NarrationStyle style) {
}
