// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.config.ServerIdentityBuilder
import dev.tachyonmcp.server.domain.Icon

@TachyonDsl
public class ServerInfoScope
    @PublishedApi
    internal constructor() {
        public var name: String? = null
        public var title: String? = null
        public val icons: MutableList<Icon> = mutableListOf()
        public var version: String? = null
        public var description: String? = null
        public var instructions: String? = null
        public var websiteUrl: String? = null

        @PublishedApi internal fun applyTo(builder: ServerIdentityBuilder) {
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
