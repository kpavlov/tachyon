/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.domain;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Reads binary content for content/resource domain types. The stream is read fully but not
 * closed; the caller retains ownership and is responsible for closing it.
 */
final class BinaryData {

    private BinaryData() {}

    static byte[] readAllBytes(InputStream in) {
        try {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
