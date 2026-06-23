/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

record DefaultIcon(
        String src,
        @Nullable String mimeType,
        @Nullable List<String> sizes,
        @Nullable String theme) implements Icon {

    DefaultIcon {
        if (sizes != null) {
            sizes = java.util.Collections.unmodifiableList(sizes);
        }
    }
}
