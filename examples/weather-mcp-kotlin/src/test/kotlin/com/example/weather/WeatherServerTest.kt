// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather

import com.example.weather.service.WeatherService
import dev.tachyonmcp.server.TachyonServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.spec.McpError
import io.modelcontextprotocol.spec.McpSchema
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

class WeatherServerTest {
    companion object {
        private val weatherProvider = TestWeatherProvider()
        private val cityProvider = TestCityProvider()
        private val weatherService = WeatherService(weatherProvider, cityProvider)
        private lateinit var handle: TachyonServer
        private lateinit var clientTransport: HttpClientStreamableHttpTransport
        private lateinit var client: McpSyncClient
        private lateinit var initResult: McpSchema.InitializeResult
        private val progressNotifications = CopyOnWriteArrayList<McpSchema.ProgressNotification>()

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            handle = createServer(0, weatherService)
            val port = handle.port()

            clientTransport = HttpClientStreamableHttpTransport.builder("http://localhost:$port").build()
            client =
                McpClient
                    .sync(clientTransport)
                    .elicitation { _ ->
                        McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, mapOf("city" to "Tallinn"))
                    }.progressConsumer { progressNotifications.add(it) }
                    .build()

            initResult = client.initialize()
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            client.close()
            clientTransport.close()
            handle.close()
        }
    }

    @Test
    fun `should get server info`() {
        initResult.serverInfo().name() shouldBe "weather-server-kotlin"
        initResult.serverInfo().title() shouldBe "Weather Server (Kotlin)"
        initResult.serverInfo().description() shouldBe "Weather MCP server built with Tachyon Kotlin DSL"
        initResult.serverInfo().websiteUrl() shouldBe
            "https://github.com/kpavlov/tachyon/tree/main/examples/weather-mcp-kotlin"
        initResult.serverInfo().icons() shouldHaveSize 1
        initResult.serverInfo().icons().first().src() shouldStartWith "data:image/png;base64,"
        initResult.protocolVersion() shouldBe "2025-11-25"
        initResult.instructions() shouldBe "Test instructions"
    }

    @Test
    fun `should list tools`() {
        val result = client.listTools()

        result.tools() shouldHaveSize 1
        val tool = result.tools().first()
        tool.name() shouldBe "get-weather"
        tool.title() shouldBe "Current Weather"
        tool.description() shouldBe "Get current weather for a city"
        tool.outputSchema() shouldBe null
    }

    @Test
    fun `should call weather tool`() {
        val result =
            client.callTool(
                McpSchema.CallToolRequest
                    .builder("get-weather")
                    .arguments(mapOf("city" to "London", "units" to "celsius"))
                    .build(),
            )

        val content = result.content().first()
        content.shouldBeInstanceOf<McpSchema.TextContent>()
        val text = content.text()
        text shouldStartWith "Weather in London:"
        text shouldContain "Temperature:"
        text shouldContain "°C"
        text shouldContain "Humidity:"
        text shouldContain "Wind:"
    }

    @Test
    fun `should emit progress while fetching weather`() {
        val result =
            client.callTool(
                McpSchema.CallToolRequest
                    .builder("get-weather")
                    .arguments(mapOf("city" to "London"))
                    .progressToken("weather-progress")
                    .build(),
            )

        result.isError shouldNotBe true
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            progressNotifications shouldHaveSize 2
        }
        progressNotifications.map { it.progressToken() to it.message() } shouldContainExactly
            listOf(
                "weather-progress" to "Fetching weather for London",
                "weather-progress" to "Weather retrieved for London",
            )
    }

    @Test
    fun `should call weather tool after eliciting another city`() {
        val result =
            client.callTool(
                McpSchema.CallToolRequest
                    .builder("get-weather")
                    .arguments(mapOf("city" to "Unknown"))
                    .build(),
            )

        val content = result.content().first()
        content.shouldBeInstanceOf<McpSchema.TextContent>()
        content.text() shouldStartWith "Weather in Tallinn:"
    }

    @Test
    fun `should list resources`() {
        val result = client.listResources()

        result.resources() shouldHaveSize 2
        val article = result.resources().first { it.uri() == "weather://prediction/article" }
        article.name() shouldBe "prediction-article"
        article.title() shouldBe "Weather Prediction"
        article.description() shouldBe "Weather prediction article"
        article.mimeType() shouldBe "text/markdown"
        article.size() shouldBe weatherService.predictionArticle.toByteArray().size.toLong()
        article.annotations().audience() shouldContainExactly
            listOf(McpSchema.Role.USER, McpSchema.Role.ASSISTANT)
        article.annotations().priority() shouldBe 0.8
        article.annotations().lastModified() shouldBe "2026-07-23T00:00:00Z"
        article.icons().single().src() shouldStartWith "data:image/png;base64,"
        article.icons().single().mimeType() shouldBe "image/png"
        article.icons().single().sizes() shouldContainExactly listOf("256x256")
        article.icons().single().theme() shouldBe "light"

        val weather = result.resources().first { it.uri() == "weather://featured/current" }
        weather.name() shouldBe "featured-current-weather"
        weather.title() shouldBe "Featured Current Weather"
        weather.description() shouldBe "Current weather in Tallinn"
        weather.mimeType() shouldBe "application/json"
        weather.annotations() shouldBe article.annotations()
        weather.icons() shouldBe article.icons()
    }

    @Test
    fun `should read text resource`() {
        val article = client.listResources().resources().first { it.uri() == "weather://prediction/article" }

        val result = client.readResource(article)

        val contents = result.contents().first()
        contents.shouldBeInstanceOf<McpSchema.TextResourceContents>()
        val text = contents
        text.uri() shouldBe "weather://prediction/article"
        text.mimeType() shouldBe "text/markdown"
        text.text().trim() shouldStartWith "# Weather Prediction"
    }

    @Test
    fun `should read current weather resource`() {
        val weather = client.listResources().resources().first { it.uri() == "weather://featured/current" }

        val result = client.readResource(weather)

        val contents = result.contents().first()
        contents.shouldBeInstanceOf<McpSchema.TextResourceContents>()
        val text = contents
        text.uri() shouldBe "weather://featured/current"
        text.mimeType() shouldBe "application/json"
        text.text() shouldContain "Tallinn"
        text.text() shouldContain "Clear sky"
    }

    @Test
    fun `should list resource templates`() {
        val result = client.listResourceTemplates()

        result.resourceTemplates() shouldHaveSize 1
        val template = result.resourceTemplates().first()
        template.uriTemplate() shouldBe "weather://current/{city}"
        template.name() shouldBe "current-weather"
        template.mimeType() shouldBe "application/json"
    }

    @Test
    fun `should read current weather from template`() {
        val result =
            client.readResource(McpSchema.ReadResourceRequest.builder("weather://current/London").build())

        val contents = result.contents().first()
        contents.shouldBeInstanceOf<McpSchema.TextResourceContents>()
        val text = contents
        text.uri() shouldBe "weather://current/London"
        text.mimeType() shouldBe "application/json"
        text.text() shouldContain "London"
    }

    @Test
    fun `should return invalid params when template city is unknown`() {
        val error =
            shouldThrow<McpError> {
                client.readResource(McpSchema.ReadResourceRequest.builder("weather://current/Unknown").build())
            }
        error.jsonRpcError.code() shouldBe -32602
    }

    @Test
    fun `should complete city name for current weather template`() {
        val result =
            client.completeCompletion(
                McpSchema.CompleteRequest
                    .builder(
                        McpSchema.ResourceReference("weather://current/{city}"),
                        McpSchema.CompleteRequest.CompleteArgument("city", "Lo"),
                    ).build(),
            )

        result.completion().values() shouldContainExactlyInAnyOrder listOf("London", "Los Angeles")
        result.completion().hasMore() shouldNotBe true
    }

    @Test
    fun `should return empty completion for blank query`() {
        val result =
            client.completeCompletion(
                McpSchema.CompleteRequest
                    .builder(
                        McpSchema.ResourceReference("weather://current/{city}"),
                        McpSchema.CompleteRequest.CompleteArgument("city", ""),
                    ).build(),
            )

        result.completion().values() shouldHaveSize 0
    }

    @Test
    fun `should complete style name for rewrite-forecast prompt`() {
        val result =
            client.completeCompletion(
                McpSchema.CompleteRequest
                    .builder(
                        McpSchema.PromptReference("rewrite-forecast"),
                        McpSchema.CompleteRequest.CompleteArgument("style", "pi"),
                    ).build(),
            )

        result.completion().values() shouldContainExactly listOf("pirate")
    }

    @Test
    fun `should return empty completion for non-style argument of rewrite-forecast prompt`() {
        val result =
            client.completeCompletion(
                McpSchema.CompleteRequest
                    .builder(
                        McpSchema.PromptReference("rewrite-forecast"),
                        McpSchema.CompleteRequest.CompleteArgument("forecast", "Rain"),
                    ).build(),
            )

        result.completion().values() shouldHaveSize 0
    }

    @Test
    fun `should list prompts`() {
        val result = client.listPrompts()

        result.prompts() shouldHaveSize 1
        val prompt = result.prompts().first()
        prompt.name() shouldBe "rewrite-forecast"
        prompt.description() shouldBe "Rewrites a weather forecast in a chosen style"
        prompt.arguments() shouldHaveSize 2
    }

    @Test
    fun `should get prompt`() {
        val result =
            client.getPrompt(
                McpSchema.GetPromptRequest
                    .builder("rewrite-forecast")
                    .arguments(mapOf("forecast" to "Rain in London", "style" to "pirate"))
                    .build(),
            )

        result.messages() shouldHaveSize 1
        val message = result.messages().first()
        message.role() shouldBe McpSchema.Role.USER
        message.content().shouldBeInstanceOf<McpSchema.TextContent>()
        (message.content() as McpSchema.TextContent).text() shouldBe
            "Rewrite the following weather forecast in pirate style. Preserve factual details:\n\n```Rain in London\n```"
    }
}
