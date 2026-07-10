/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.config;

import dev.tachyonmcp.server.features.Pagination;

/**
 * Configuration for the resources capability: the same knobs as {@link FeatureConfig} plus
 * subscription support.
 *
 * @param mode        enabled/disabled/auto-detected (default {@link Mode#AUTO})
 * @param listChanged whether to advertise list-changed notifications (default {@code false})
 * @param pageSize    default page size when a list request omits its limit
 * @param subscribe   whether resource subscriptions ({@code resources/subscribe}) are supported
 *                    (default {@code false})
 */
public record ResourcesConfig(Mode mode, boolean listChanged, int pageSize, boolean subscribe) {

    static final ResourcesConfig DEFAULT = new ResourcesConfig(Mode.AUTO, false, Pagination.DEFAULT_PAGE_SIZE, false);

    public ResourcesConfig {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, got: " + pageSize);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link ResourcesConfig}. */
    public static final class Builder {
        private Mode mode = DEFAULT.mode;
        private boolean listChanged = DEFAULT.listChanged;
        private int pageSize = DEFAULT.pageSize;
        private boolean subscribe = DEFAULT.subscribe;

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

        public Builder subscribe(boolean subscribe) {
            this.subscribe = subscribe;
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

        public ResourcesConfig build() {
            return new ResourcesConfig(mode, listChanged, pageSize, subscribe);
        }
    }
}
