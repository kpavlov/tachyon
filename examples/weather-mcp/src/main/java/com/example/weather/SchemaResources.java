/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.weather;

import dev.tachyonmcp.api.json.JsonSchema;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class SchemaResources {

    private SchemaResources() {
    }

    static JsonSchema load(Class<?> type) {
        var path = "META-INF/kt-schema/schemas/%s.json".formatted(type.getName().replace('.', '/'));
        try (InputStream in = type.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing generated schema resource: " + path);
            }
            return JsonSchema.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read schema resource: " + path, e);
        }
    }
}
