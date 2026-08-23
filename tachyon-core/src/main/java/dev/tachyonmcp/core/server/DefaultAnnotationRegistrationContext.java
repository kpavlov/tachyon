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
final class DefaultAnnotationRegistrationContext implements AnnotationRegistrationContext {

    private final Tools tools;
    private final Resources resources;
    private final Prompts prompts;
    private final Completions completions;
    private final PayloadSerializer payloadSerializer;
    private final PayloadDeserializer payloadDeserializer;

    DefaultAnnotationRegistrationContext(
            Tools tools,
            Resources resources,
            Prompts prompts,
            Completions completions,
            PayloadSerializer payloadSerializer,
            PayloadDeserializer payloadDeserializer) {
        this.tools = tools;
        this.resources = resources;
        this.prompts = prompts;
        this.completions = completions;
        this.payloadSerializer = payloadSerializer;
        this.payloadDeserializer = payloadDeserializer;
    }

    @Override
    public Tools tools() {
        return tools;
    }

    @Override
    public Resources resources() {
        return resources;
    }

    @Override
    public Prompts prompts() {
        return prompts;
    }

    @Override
    public Completions completions() {
        return completions;
    }

    @Override
    public PayloadSerializer payloadSerializer() {
        return payloadSerializer;
    }

    @Override
    public PayloadDeserializer payloadDeserializer() {
        return payloadDeserializer;
    }
}
