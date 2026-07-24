// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.kotlin.json

import dev.tachyonmcp.server.json.PayloadSerde
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

/**
 * [dev.tachyonmcp.server.json.PayloadSerde] backed by kotlinx.serialization.
 *
 * Serializers are resolved from the [json] serializers module by runtime type, so payload
 * classes must be `@Serializable` (or built-in). Generic containers lose their type arguments
 * at runtime — prefer dedicated `@Serializable` payload classes over raw maps.
 * Requires kotlinx-serialization-json on the classpath.
 *
 * @author Konstantin Pavlov
 */
public class KxSerializationSerde(
    private val json: Json = KxSerializationSerde.json,
) : PayloadSerde {
    private val serializerCache: ConcurrentHashMap<Type, KSerializer<*>> = ConcurrentHashMap()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> serialize(value: T): String {
        val serializer =
            serializerCache.computeIfAbsent(value.javaClass) {
                json.serializersModule.serializer(it)
            } as KSerializer<Any>
        return json.encodeToString(serializer, value)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> deserialize(
        payload: String,
        targetType: Type,
    ): T {
        val deserializer =
            serializerCache.getOrPut(targetType) {
                json.serializersModule.serializer(targetType)
            } as KSerializer<T>
        return json.decodeFromString(deserializer, payload)
    }

    public companion object {
        @JvmField
        public val json: Json = Json { ignoreUnknownKeys = true }

        public val Default: KxSerializationSerde = KxSerializationSerde()
    }
}
