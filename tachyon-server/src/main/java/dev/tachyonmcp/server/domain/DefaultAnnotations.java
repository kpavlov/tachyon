/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

record DefaultAnnotations(
        @Nullable List<Role> audience,
        @Nullable Double priority,
        @Nullable String lastModified) implements Annotations {}
