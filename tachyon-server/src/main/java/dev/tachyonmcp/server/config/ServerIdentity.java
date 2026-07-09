/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.config;

import dev.tachyonmcp.server.domain.Icon;
import java.util.List;
import java.util.Objects;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * Server identity metadata sent to the client during initialization.
 *
 * @param name         server name (required)
 * @param version      server version (required)
 * @param description  optional human-readable description
 * @param title        optional display title
 * @param websiteUrl   optional URL to the server's website
 * @param instructions optional instructions for how to use the server
 * @param icons        optional list of icon entries
 */
@Value.Builder
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE, typeImmutable = "Default*")
public record ServerIdentity(
        String name,
        String version,
        @Nullable String description,
        @Nullable String title,
        @Nullable String websiteUrl,
        @Nullable String instructions,
        @Nullable List<Icon> icons) {

    public static final ServerIdentity DEFAULT = new ServerIdentity("tachyon-mcp", "0.1", null, null, null, null, null);

    public ServerIdentity {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        if (icons != null) {
            icons = List.copyOf(icons);
        }
    }

    public static ServerIdentityBuilder builder() {
        return new ServerIdentityBuilder();
    }
}
