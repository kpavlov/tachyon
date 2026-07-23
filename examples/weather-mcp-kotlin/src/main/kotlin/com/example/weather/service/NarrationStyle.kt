// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather.service

enum class NarrationStyle(
    val value: String,
) {
    PLAIN("plain"),
    CONCISE("concise"),
    PIRATE("pirate"),
    ;

    companion object {
        fun styleNames(): List<String> = entries.map { it.value }

        fun from(value: String): NarrationStyle =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unsupported style: $value")

        fun inputSchema(): String {
            val values = entries.joinToString(", ") { "\"${it.value}\"" }
            // language=json
            return """
                {
                  "type": "object",
                  "properties": {
                    "forecast": {"type": "string"},
                    "style": {"type": "string", "enum": [$values]}
                  },
                  "required": ["forecast", "style"]
                }
                """.trimIndent()
        }
    }
}
