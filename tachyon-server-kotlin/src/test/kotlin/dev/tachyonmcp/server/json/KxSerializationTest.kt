// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.json

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Test

internal class KxSerializationTest {
    @Serializable
    data class Payload(
        val message: String,
        val count: Int,
    )

    private val serde = KxSerializationSerde()

    @Test
    fun `round-trips a serializable class through String`() {
        val json = serde.serialize(Payload("hi", 7))

        json shouldBe """{"message":"hi","count":7}"""
        serde.deserialize<Payload>(json, Payload::class.java) shouldBe Payload("hi", 7)
    }

    @Test
    fun `non-ascii payload survives round-trip`() {
        val payload = Payload("привет 😀", 1)

        val json = serde.serialize(payload)

        serde.deserialize<Payload>(json, Payload::class.java) shouldBe payload
    }

    @Test
    fun `malformed json fails with serialization error`() {
        shouldThrow<SerializationException> {
            serde.deserialize<Payload>("not json", Payload::class.java)
        }
    }
}
