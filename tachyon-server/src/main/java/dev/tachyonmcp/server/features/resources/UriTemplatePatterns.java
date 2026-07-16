/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.resources;

import java.util.regex.Pattern;

final class UriTemplatePatterns {

    static final Pattern EXPRESSION = Pattern.compile("\\{([^}]+)\\}");

    private UriTemplatePatterns() {}
}
