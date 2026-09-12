/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server;

import dev.tachyonmcp.api.json.PayloadDeserializer;
import dev.tachyonmcp.api.json.PayloadSerializer;
import dev.tachyonmcp.api.server.features.annotations.AnnotationRegistrationContext;
import dev.tachyonmcp.api.server.features.completions.Completions;
import dev.tachyonmcp.api.server.features.prompts.Prompts;
import dev.tachyonmcp.api.server.features.resources.Resources;
import dev.tachyonmcp.api.server.features.tools.Tools;

/**
 * Bridges the generic {@link AnnotationRegistrationContext} to the concrete feature registries
 * and configured payload serde of a constructed {@link TachyonServer}.
 */
record DefaultAnnotationRegistrationContext(
        Tools tools,
        Resources resources,
        Prompts prompts,
        Completions completions,
        PayloadSerializer payloadSerializer,
        PayloadDeserializer payloadDeserializer)
        implements AnnotationRegistrationContext {}
