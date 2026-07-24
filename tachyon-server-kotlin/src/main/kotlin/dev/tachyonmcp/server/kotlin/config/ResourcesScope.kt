// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.kotlin.config

import dev.tachyonmcp.server.config.Mode
import dev.tachyonmcp.server.config.ResourcesConfig
import dev.tachyonmcp.server.features.Pagination
import dev.tachyonmcp.server.kotlin.TachyonDsl

@TachyonDsl
public class ResourcesScope
    @PublishedApi
    internal constructor() {
        /** Enablement mode: `ON`, `OFF`, or `AUTO`. */
        public var mode: Mode = Mode.AUTO

        /** Whether to advertise list-changed notifications. */
        public var listChanged: Boolean = false

        /** Default page size when a list request omits its limit. */
        public var pageSize: Int = Pagination.DEFAULT_PAGE_SIZE

        /** Whether resource subscriptions (`resources/subscribe`) are supported. */
        public var subscribe: Boolean = false

        @PublishedApi
        internal fun toConfig(): ResourcesConfig =
            ResourcesConfig
                .builder()
                .mode(mode)
                .listChanged(listChanged)
                .pageSize(pageSize)
                .subscribe(subscribe)
                .build()
    }
