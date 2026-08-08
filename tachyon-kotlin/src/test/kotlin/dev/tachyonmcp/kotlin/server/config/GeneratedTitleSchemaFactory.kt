// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.json.spi.JsonSchemaFactory
import java.util.Optional

/**
 * Test-only [JsonSchemaFactory] registered via `META-INF/services` so this module's `typedTool`
 * tests resolve a schema through the real [dev.tachyonmcp.api.json.JsonSchema.generated] chain,
 * without depending on `tachyon-kotlin-kt-schema`'s reflection generator.
 */
public class GeneratedTitleSchemaFactory : JsonSchemaFactory<Class<*>> {
    override fun sourceType(): Class<Class<*>> = Class::class.java

    override fun toJsonSchema(source: Class<*>): Optional<JsonSchema> =
        Optional.of(JsonSchema.of("""{"type":"object","title":"${source.simpleName}"}"""))
}
