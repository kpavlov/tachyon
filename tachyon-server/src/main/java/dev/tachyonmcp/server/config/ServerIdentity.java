/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.config;

import dev.tachyonmcp.server.domain.Icon;
import java.util.List;
import java.util.Objects;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

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
    }

    public static ServerIdentityBuilder builder() {
        return new ServerIdentityBuilder();
    }
}
