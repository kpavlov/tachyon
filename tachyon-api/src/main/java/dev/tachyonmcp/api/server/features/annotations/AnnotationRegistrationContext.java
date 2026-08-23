/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.annotations;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.server.features.completions.Completions;
import dev.tachyonmcp.api.server.features.prompts.Prompts;
import dev.tachyonmcp.api.server.features.resources.Resources;
import dev.tachyonmcp.api.server.features.tools.Tools;

/**
 * Façade passed to an {@link AnnotationProvider} during registration. Exposes only the
 * server's feature registries so that providers translate annotation metadata into standard
 * Tachyon descriptor + handler registrations.
 *
 * <p>Providers must not store or re-expose this context beyond the {@link
 * AnnotationProvider#register} call.
 */
@ExperimentalApi
public interface AnnotationRegistrationContext {

    /** Returns the tool registry façade. */
    Tools tools();

    /** Returns the resource registry façade. */
    Resources resources();

    /** Returns the prompt registry façade. */
    Prompts prompts();

    /** Returns the completion registry façade. */
    Completions completions();
}
