/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.config;

import dev.tachyonmcp.server.features.Pagination;

/**
 * Shared configuration for a single-list MCP capability (tools/prompts): enablement mode,
 * list-changed notifications, and default page size.
 *
 * @param mode        enabled/disabled/auto-detected (default {@link Mode#AUTO})
 * @param listChanged whether to advertise list-changed notifications (default {@code false})
 * @param pageSize    default page size when a list request omits its limit
 *                    (default {@link Pagination#DEFAULT_PAGE_SIZE})
 */
public record FeatureConfig(Mode mode, boolean listChanged, int pageSize) {

    static final FeatureConfig DEFAULT = new FeatureConfig(Mode.AUTO, false, Pagination.DEFAULT_PAGE_SIZE);

    public FeatureConfig {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, got: " + pageSize);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link FeatureConfig}. */
    public static final class Builder {
        private Mode mode = DEFAULT.mode;
        private boolean listChanged = DEFAULT.listChanged;
        private int pageSize = DEFAULT.pageSize;

        private Builder() {}

        public Builder mode(Mode mode) {
            this.mode = mode;
            return this;
        }

        public Builder listChanged(boolean listChanged) {
            this.listChanged = listChanged;
            return this;
        }

        public Builder pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        /** Enables the capability ({@code mode = ON}). */
        public Builder on() {
            return mode(Mode.ON);
        }

        /** Disables the capability ({@code mode = OFF}). */
        public Builder off() {
            return mode(Mode.OFF);
        }

        public FeatureConfig build() {
            return new FeatureConfig(mode, listChanged, pageSize);
        }
    }
}
