/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.protocol.api.annotations.InternalApi;
import dev.tachyonmcp.protocol.api.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.protocol.api.server.features.resources.ResourceHandler;

@InternalApi
record ResourceEntry(ResourceDescriptor descriptor, ResourceHandler handler) {}
