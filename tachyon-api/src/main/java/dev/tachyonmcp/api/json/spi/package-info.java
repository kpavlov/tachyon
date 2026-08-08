/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

/**
 * SPI for pluggable JSON document/schema parsing and generation, loadable via {@link
 * java.util.ServiceLoader}.
 *
 * <p>Implement {@link JsonDocumentFactory} to plug a document codec, and register the
 * implementation in {@code META-INF/services/dev.tachyonmcp.api.json.spi.JsonDocumentFactory} to
 * make it discoverable by {@link JsonDocument#from(Object, Class)}.
 *
 * <p>Implement {@link JsonSchemaFactory} to plug a schema factory for both parsed JSON sources
 * ({@link JsonSchema#from(Object, Class)}) and generated class types
 * ({@link JsonSchema#generate(Class)}), and register it in
 * {@code META-INF/services/dev.tachyonmcp.api.json.spi.JsonSchemaFactory}.
 */
@NullMarked
package dev.tachyonmcp.api.json.spi;

import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.json.JsonSchema;
import org.jspecify.annotations.NullMarked;
