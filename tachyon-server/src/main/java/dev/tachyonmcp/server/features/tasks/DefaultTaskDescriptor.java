/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.server.domain.Icon;
import java.util.List;
import org.jspecify.annotations.Nullable;

record DefaultTaskDescriptor(
        String name,
        @Nullable String description,
        @Nullable String title,
        @Nullable List<Icon> icons) implements TaskDescriptor {}
