// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather.service

enum class NarrationStyle(
    val value: String,
) {
    plain("plain"),
    concise("concise"),
    pirate("pirate"),
    ;

    companion object {
        fun styleNames(): List<String> = entries.map { it.value }

        fun from(value: String): NarrationStyle =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unsupported style: $value")
    }
}
