/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.tachyonmcp.api.server.domain.TextResourceContents;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 {@code subscriptions/listen} (SEP-2575): the request-scoped SSE stream that
 * replaces {@code resources/subscribe} and the plain HTTP GET stream for the stateless transport.
 */
class SubscriptionsListenTest extends AbstractStatelessMcpE2eTest {

    @BeforeEach
    void freshServer() {
        startServer(
                b -> b.capabilities(c -> c.tools(true).resources(false, true).prompts(true)));
    }

    @Test
    void acknowledgesFirstWithMatchingSubscriptionId() throws Exception {
        var lines = new CopyOnWriteArrayList<String>();
        try (var client = createModernTestClient()) {
            var response = client.sendStreamingRequest(null, """
                {"jsonrpc":"2.0","id":1,"method":"subscriptions/listen",
                 "params":{"notifications":{"toolsListChanged":true}}}
                """);
            assertThat(response.statusCode()).isEqualTo(200);
            var consume = CompletableFuture.runAsync(() -> response.body().forEach(lines::add));
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(payloads(lines)).isNotEmpty());

            var first = payloads(lines).getFirst();
            assertThat(first).contains("\"notifications/subscriptions/acknowledged\"");
            assertThat(first).contains("\"io.modelcontextprotocol/subscriptionId\":\"1\"");

            response.body().close();
            awaitQuietly(consume);
        }
    }

    @Test
    void enforcesRequestedFilterNoUnrequestedNotificationsLeak() throws Exception {
        var lines = new CopyOnWriteArrayList<String>();
        try (var client = createModernTestClient()) {
            var response = client.sendStreamingRequest(null, """
                {"jsonrpc":"2.0","id":1,"method":"subscriptions/listen",
                 "params":{"notifications":{"toolsListChanged":true}}}
                """);
            var consume = CompletableFuture.runAsync(() -> response.body().forEach(lines::add));
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(payloads(lines))
                            .anyMatch(l -> l.contains("notifications/subscriptions/acknowledged")));

            // Trigger a resources change (not requested) then a tools change (requested). Because
            // both go through the same per-stream event loop in submission order, observing the
            // tools notification proves the server already had its chance to (wrongly) deliver the
            // resources one first.
            server.resources()
                    .register(
                            r -> r.name("trigger-resource").uri("test://trigger"),
                            (ctx, req) -> TextResourceContents.of(req.uri(), "anything", "text/plain"));
            server.tools()
                    .register(
                            t -> t.name("trigger-tool").description("d").inputSchema("{\"type\":\"object\"}"),
                            (ctx, req) -> ToolResult.text("x"));

            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() ->
                            assertThat(payloads(lines)).anyMatch(l -> l.contains("notifications/tools/list_changed")));

            assertThat(payloads(lines)).noneMatch(l -> l.contains("notifications/resources/list_changed"));

            response.body().close();
            awaitQuietly(consume);
        }
    }

    @Test
    void pushesResourceUpdatedOnlyForSubscribedUri() throws Exception {
        var lines = new CopyOnWriteArrayList<String>();
        try (var client = createModernTestClient()) {
            var response = client.sendStreamingRequest(null, """
                {"jsonrpc":"2.0","id":1,"method":"subscriptions/listen",
                 "params":{"notifications":{"resourceSubscriptions":["test://a"]}}}
                """);
            var consume = CompletableFuture.runAsync(() -> response.body().forEach(lines::add));
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(payloads(lines))
                            .anyMatch(l -> l.contains("notifications/subscriptions/acknowledged")));

            server.resources().notifyResourceUpdated("test://b");
            server.resources().notifyResourceUpdated("test://a");

            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(payloads(lines))
                            .anyMatch(l -> l.contains("notifications/resources/updated") && l.contains("test://a")));

            assertThat(payloads(lines))
                    .filteredOn(l -> l.contains("notifications/resources/updated"))
                    .noneMatch(l -> l.contains("test://b"));

            response.body().close();
            awaitQuietly(consume);
        }
    }

    @Test
    void demultiplexesConcurrentSubscriptionsById() throws Exception {
        var linesA = new CopyOnWriteArrayList<String>();
        var linesB = new CopyOnWriteArrayList<String>();
        try (var client = createModernTestClient()) {
            var responseA = client.sendStreamingRequest(null, """
                {"jsonrpc":"2.0","id":10,"method":"subscriptions/listen",
                 "params":{"notifications":{"toolsListChanged":true}}}
                """);
            var responseB = client.sendStreamingRequest(null, """
                {"jsonrpc":"2.0","id":20,"method":"subscriptions/listen",
                 "params":{"notifications":{"toolsListChanged":true}}}
                """);
            var consumeA = CompletableFuture.runAsync(() -> responseA.body().forEach(linesA::add));
            var consumeB = CompletableFuture.runAsync(() -> responseB.body().forEach(linesB::add));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(payloads(linesA)).anyMatch(l -> l.contains("acknowledged"));
                assertThat(payloads(linesB)).anyMatch(l -> l.contains("acknowledged"));
            });

            server.tools()
                    .register(
                            t -> t.name("trigger-tool-2").description("d").inputSchema("{\"type\":\"object\"}"),
                            (ctx, req) -> ToolResult.text("x"));

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(payloads(linesA))
                        .anyMatch(l -> l.contains("notifications/tools/list_changed")
                                && l.contains("\"io.modelcontextprotocol/subscriptionId\":\"10\""));
                assertThat(payloads(linesB))
                        .anyMatch(l -> l.contains("notifications/tools/list_changed")
                                && l.contains("\"io.modelcontextprotocol/subscriptionId\":\"20\""));
            });

            responseA.body().close();
            responseB.body().close();
            awaitQuietly(consumeA);
            awaitQuietly(consumeB);
        }
    }

    @Test
    void serverStaysResponsiveAfterClientDisconnect() throws Exception {
        var lines = new CopyOnWriteArrayList<String>();
        try (var client = createModernTestClient()) {
            var response = client.sendStreamingRequest(null, """
                {"jsonrpc":"2.0","id":1,"method":"subscriptions/listen",
                 "params":{"notifications":{"toolsListChanged":true}}}
                """);
            var consume = CompletableFuture.runAsync(() -> response.body().forEach(lines::add));
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(payloads(lines)).anyMatch(l -> l.contains("acknowledged")));

            response.body().close();
            awaitQuietly(consume);

            // A dropped subscriber must not wedge the server: a list-change fired after disconnect,
            // and a fresh unrelated request, must both still succeed.
            server.tools()
                    .register(
                            t -> t.name("trigger-tool-3").description("d").inputSchema("{\"type\":\"object\"}"),
                            (ctx, req) -> ToolResult.text("x"));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                var pingResponse = client.sendRpc(null, """
                    {"jsonrpc":"2.0","id":99,"method":"tools/list","params":{}}
                    """);
                assertThat(pingResponse).contains("\"trigger-tool-3\"");
            });
        }
    }

    @Test
    void gracefullyClosesOnServerShutdown() throws Exception {
        var lines = new CopyOnWriteArrayList<String>();
        try (var client = createModernTestClient()) {
            var response = client.sendStreamingRequest(null, """
                {"jsonrpc":"2.0","id":1,"method":"subscriptions/listen",
                 "params":{"notifications":{"toolsListChanged":true}}}
                """);
            var consume = CompletableFuture.runAsync(() -> response.body().forEach(lines::add));
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(payloads(lines)).anyMatch(l -> l.contains("acknowledged")));

            stopServer();
            consume.get(15, TimeUnit.SECONDS);

            var last = payloads(lines).getLast();
            // language=json
            assertThatJson(last).isEqualTo("""
                    {"jsonrpc":"2.0",
                    "id":1,
                    "result":{
                      "_meta": {"io.modelcontextprotocol/subscriptionId":"1"},
                      "resultType":"complete"
                      }
                    }
                    """);
        }
    }

    private static void awaitQuietly(CompletableFuture<Void> future) {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Expected: closing the client-side stream makes the background line reader fail.
        }
    }

    private static List<String> payloads(List<String> lines) {
        return lines.stream()
                .map(l -> l.startsWith("data: ") ? l.substring(6) : l.startsWith("data:") ? l.substring(5) : "")
                .filter(p -> !p.isBlank())
                .toList();
    }
}
