/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

/**
 * SPI for pluggable JSON document/schema parsing, loadable via {@link java.util.ServiceLoader}.
 *
 * <p>Implement {@link JsonDocumentFactory} and/or
 * {@link JsonSchemaFactory} and register the implementation in
 * {@code META-INF/services/dev.tachyonmcp.api.json.spi.JsonDocumentFactory} and/or
 * {@code META-INF/services/dev.tachyonmcp.api.json.spi.JsonSchemaFactory} to make it
 * discoverable by {@link JsonDocument#from(Object, Class)} and
 * {@link JsonSchema#from(Object, Class)}.
 */
@NullMarked
package dev.tachyonmcp.api.json.spi;

import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.json.JsonSchema;
import org.jspecify.annotations.NullMarked;
