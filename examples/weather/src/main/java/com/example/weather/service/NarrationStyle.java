/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum NarrationStyle {
    PLAIN("plain"),
    CONCISE("concise"),
    PIRATE("pirate");

    private final String value;

    NarrationStyle(String value) {
        this.value = value;
    }

    String value() {
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

    public static String inputSchema() {
        var values = Arrays.stream(values())
            .map(style -> "\"%s\"".formatted(style.value))
            .collect(Collectors.joining(", "));
        // language=json
        return """
            {
                  "type": "object",
                  "properties": {
                    "forecast": {"type": "string"},
                    "style": {"type": "string", "enum": [%s]}
                  },
                  "required": ["forecast", "style"]
                }
            """.formatted(values);
    }
}
