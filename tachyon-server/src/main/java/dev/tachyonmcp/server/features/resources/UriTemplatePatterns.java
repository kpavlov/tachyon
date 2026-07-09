/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.resources;

import java.util.regex.Pattern;

final class UriTemplatePatterns {

    static final Pattern VAR = Pattern.compile("\\{([^}]+)\\}");
    static final Pattern VALID_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]*");

    private UriTemplatePatterns() {}
}
