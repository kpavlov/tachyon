/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.annotations.InternalApi;

@InternalApi
record ResourceEntry(ResourceDescriptor descriptor, ResourceHandler handler) {}
