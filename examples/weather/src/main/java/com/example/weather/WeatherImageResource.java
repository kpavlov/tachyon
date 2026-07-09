/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather;

import dev.tachyonmcp.server.domain.BlobResourceContents;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

final class WeatherImageResource {

    private static final String URI = "weather://current/image";
    private static final String MIME_TYPE = "image/png";
    private static final byte[] IMAGE_DATA;

    static {
        try (var in = WeatherImageResource.class.getResourceAsStream("/images/sun-and-cloud.png")) {
            if (in == null) {
                throw new RuntimeException("Image resource not found");
            }
            IMAGE_DATA = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load weather image", e);
        }
    }

    static BlobResourceContents read() {
        return BlobResourceContents.of(URI, MIME_TYPE, Base64.getEncoder().encodeToString(IMAGE_DATA));
    }

    private WeatherImageResource() {
    }
}
