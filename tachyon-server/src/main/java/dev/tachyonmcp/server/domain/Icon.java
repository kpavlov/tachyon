/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An icon entry for a tool or resource, pointing to an image resource.
 *
 * <p>All fields except {@code src} are optional. Sizes use the conventional format
 * (e.g. {@code "16x16"}, {@code "32x32"}), and {@code theme} distinguishes light
 * vs. dark variants.
 */
public interface Icon {

    String src();

    @Nullable
    String mimeType();

    @Nullable
    List<String> sizes();

    @Nullable
    String theme();

    static Icon of(String src, @Nullable String mimeType, @Nullable List<String> sizes, @Nullable String theme) {
        return new DefaultIcon(src, mimeType, sizes, theme);
    }
}
