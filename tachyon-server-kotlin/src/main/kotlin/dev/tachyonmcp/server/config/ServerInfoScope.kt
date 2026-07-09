// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.Icon

@TachyonDsl
public class ServerInfoScope
    @PublishedApi
    internal constructor() {
        /** Server name advertised to clients. */
        public var name: String? = null

        /** Human-readable server title. */
        public var title: String? = null

        /** Server icon set. */
        public val icons: MutableList<Icon> = mutableListOf()

        /** Server version advertised to clients. */
        public var version: String? = null

        /** Human-readable server description. */
        public var description: String? = null

        /** Instructions for how to use the server. */
        public var instructions: String? = null

        /** Server website URL. */
        public var websiteUrl: String? = null

        @PublishedApi internal fun applyTo(builder: ServerIdentity.Builder) {
            name?.let(builder::name)
            version?.let(builder::version)
            description?.let(builder::description)
            instructions?.let(builder::instructions)
            title?.let(builder::title)
            websiteUrl?.let(builder::websiteUrl)
            if (!icons.isEmpty()) {
                icons.let(builder::icons)
            }
        }
    }
