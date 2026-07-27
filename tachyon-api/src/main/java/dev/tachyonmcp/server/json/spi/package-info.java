/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

/**
 * SPI for pluggable JSON document/schema parsing, loadable via {@link java.util.ServiceLoader}.
 *
 * <p>Implement {@link dev.tachyonmcp.server.json.spi.JsonDocumentFactory} and/or
 * {@link dev.tachyonmcp.server.json.spi.JsonSchemaFactory} and register the implementation in
 * {@code META-INF/services/dev.tachyonmcp.server.json.spi.JsonDocumentFactory} and/or
 * {@code META-INF/services/dev.tachyonmcp.server.json.spi.JsonSchemaFactory} to make it
 * discoverable by {@link dev.tachyonmcp.server.json.JsonDocument#from(Object, Class)} and
 * {@link dev.tachyonmcp.server.json.JsonSchema#from(Object, Class)}.
 */
@NullMarked
package dev.tachyonmcp.server.json.spi;

import org.jspecify.annotations.NullMarked;
