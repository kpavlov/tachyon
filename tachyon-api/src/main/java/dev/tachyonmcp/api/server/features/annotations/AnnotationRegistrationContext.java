/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.annotations;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.json.PayloadDeserializer;
import dev.tachyonmcp.api.json.PayloadSerializer;
import dev.tachyonmcp.api.server.features.completions.Completions;
import dev.tachyonmcp.api.server.features.prompts.Prompts;
import dev.tachyonmcp.api.server.features.resources.Resources;
import dev.tachyonmcp.api.server.features.tools.Tools;

/**
 * Façade passed to an {@link AnnotationProvider} during registration. Exposes the server's
 * feature registries so that providers translate annotation metadata into standard Tachyon
 * descriptor + handler registrations, plus the server's configured payload serde so providers can
 * coerce annotated-method arguments through {@link AnnotationInvocationSupport#coerce} instead of
 * a provider-specific mapper.
 *
 * <p>Providers must not store or re-expose this context beyond the {@link
 * AnnotationProvider#register} call; a provider that needs the serde during invocation (not just
 * registration) should capture {@link #payloadSerializer()}/{@link #payloadDeserializer()} in its
 * registered handler closures.
 */
@ExperimentalApi
public interface AnnotationRegistrationContext {

    /** Returns the tool registry façade.
     *
     * @return the tools registry
     */
    Tools tools();

    /** Returns the resource registry façade.
     *
     * @return the resources registry
     */
    Resources resources();

    /** Returns the prompt registry façade.
     *
     * @return the prompts registry
     */
    Prompts prompts();

    /** Returns the completion registry façade.
     *
     * @return the completions registry
     */
    Completions completions();

    /** Returns the server's configured payload serializer.
     *
     * @return the payload serializer
     */
    PayloadSerializer payloadSerializer();

    /** Returns the server's configured payload deserializer.
     *
     * @return the payload deserializer
     */
    PayloadDeserializer payloadDeserializer();
}
