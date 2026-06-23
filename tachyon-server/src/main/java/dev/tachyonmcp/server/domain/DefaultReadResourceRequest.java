/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;

record DefaultReadResourceRequest(String uri, @Nullable Map<String, Object> meta) implements ReadResourceRequest {}
