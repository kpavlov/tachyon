// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.domain

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

internal class ResourceFactoriesTest {
    @Test
    fun `TextResourceContents without meta resolves unambiguously`() {
        val contents = TextResourceContents("mem://text", "hello")

        assertSoftly {
            contents.uri() shouldBe "mem://text"
            contents.text() shouldBe "hello"
            contents.mimeType() shouldBe null
        }
    }

    @Test
    fun `TextResourceContents accepts kotlinx meta`() {
        val meta = mapOf("trace" to Json.parseToJsonElement("""{"id": 7}""") as JsonObject)

        val contents = TextResourceContents("mem://text", "hello", "text/plain", meta)

        assertSoftly {
            contents.mimeType() shouldBe "text/plain"
            val converted = contents.meta().shouldNotBeNull()
            converted shouldContainKey "trace"
            converted["trace"]?.get("id")?.asInt() shouldBe 7
        }
    }

    @Test
    fun `BlobResourceContents without meta resolves unambiguously`() {
        val contents = BlobResourceContents("mem://blob", "AQID")

        assertSoftly {
            contents.uri() shouldBe "mem://blob"
            contents.blob() shouldBe "AQID"
            contents.mimeType() shouldBe null
        }
    }

    @Test
    fun `BlobResourceContents accepts kotlinx meta`() {
        val meta = mapOf("trace" to Json.parseToJsonElement("""{"id": 7}""") as JsonObject)

        val contents = BlobResourceContents("mem://blob", "AQID", "application/octet-stream", meta)

        assertSoftly {
            contents.mimeType() shouldBe "application/octet-stream"
            val converted = contents.meta().shouldNotBeNull()
            converted shouldContainKey "trace"
            converted["trace"]?.get("id")?.asInt() shouldBe 7
        }
    }
}
