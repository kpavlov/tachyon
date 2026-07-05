// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.json

import kotlinx.serialization.serializer
import java.lang.reflect.Type
import kotlinx.serialization.json.Json as KotlinxJson

/**
 * [PayloadSerde] backed by kotlinx.serialization.
 *
 * Serializers are resolved from the [json] serializers module by runtime type, so payload
 * classes must be `@Serializable` (or built-in). Generic containers lose their type arguments
 * at runtime — prefer dedicated `@Serializable` payload classes over raw maps.
 * Requires kotlinx-serialization-json on the classpath.
 *
 * @author Konstantin Pavlov
 */
public class KxPayloadSerde(
    private val json: KotlinxJson = KotlinxJson.Default,
) : PayloadSerde {
    override fun serialize(value: Any): String {
        val serializer = json.serializersModule.serializer(value.javaClass)
        return json.encodeToString(serializer, value)
    }

    override fun <T : Any> deserialize(
        payload: String,
        targetType: Type,
    ): T {
        val deserializer = json.serializersModule.serializer(targetType)
        @Suppress("UNCHECKED_CAST")
        return json.decodeFromString(deserializer, payload) as T
    }
}
