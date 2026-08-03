/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Arrays;
import java.util.List;

@JsonClassDescription("Style used to narrate a weather forecast.")
@JsonTypeName("NarrationStyle")
public enum NarrationStyle {
    @JsonProperty("plain") PLAIN("plain"),
    @JsonProperty("concise") CONCISE("concise"),
    @JsonProperty("pirate") PIRATE("pirate");

    private final String value;

    NarrationStyle(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static List<String> styleNames() {
        return Arrays.stream(values()).map(NarrationStyle::value).toList();
    }

    public static NarrationStyle from(String value) {
        return Arrays.stream(values())
            .filter(style -> style.value.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported style: " + value));
    }
}
