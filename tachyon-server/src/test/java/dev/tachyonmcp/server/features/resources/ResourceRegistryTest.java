/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.EmptyResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListResourceTemplatesResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListResourcesResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ReadResourceResult;
import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SseConnection;
import dev.tachyonmcp.runtime.SseEvent;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.domain.Annotations;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.Role;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.session.DefaultMcpContext;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcError;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceRegistryTest {

    private final Server server = TachyonServer.builder().build();
    private final ResourceRegistry registry = new ResourceRegistry(server);
    private final HashMap<String, RpcMethodHandler> handlers = new HashMap<>();

    private static DispatchContext context(Session session, Server server) {
        var ctx = DefaultMcpContext.create(Protocols.versions().getFirst(), server);
        ctx.setSession(session);
        return ctx;
    }

    @BeforeEach
    void setUp() {
        registry.registerHandlers(handlers);
    }

    @Test
    void shouldReturnEmptyListWhenNoResourcesRegistered() throws Exception {
        var result = handlers.get("resources/list").handle(DefaultMcpContext.noop(), null);

        assertThat(result).isInstanceOf(ListResourcesResult.class);
        assertThat(((ListResourcesResult) result).resources()).isEmpty();
    }

    @Test
    void shouldReturnErrorWhenResourceNotFound() throws Exception {
        var result = handlers.get("resources/read")
                .handle(DefaultMcpContext.noop(), Map.<String, Object>of("uri", "test://nonexistent"));

        assertThat(result).isInstanceOf(JsonRpcError.class);
        assertThat(((JsonRpcError) result).code()).isEqualTo(JsonRpcErrors.RESOURCE_NOT_FOUND);
    }

    @Test
    void shouldReturnErrorWhenUriMissing() throws Exception {
        var result = handlers.get("resources/read").handle(DefaultMcpContext.noop(), Map.of());

        assertThat(result).isInstanceOf(JsonRpcError.class);
    }

    @Test
    void shouldReadResourceContentByUri() throws Exception {
        var descriptor = ResourceDescriptor.of("test-resource", "test://resource/1", "Test resource", "text/plain");
        registry.add(descriptor, (ctx, req) -> TextResourceContents.of("test://resource/1", "text/plain", "content"));

        var result = handlers.get("resources/read")
                .handle(DefaultMcpContext.noop(), Map.<String, Object>of("uri", "test://resource/1"));

        assertThat(result).isInstanceOf(ReadResourceResult.class);
        var readResult = (ReadResourceResult) result;
        assertThat(readResult.contents()).hasSize(1);
        assertThat(readResult.contents().getFirst().uri()).isEqualTo("test://resource/1");
    }

    @Test
    void shouldReturnEmptyTemplateList() throws Exception {
        var result = handlers.get("resources/templates/list").handle(DefaultMcpContext.noop(), null);

        assertThat(result).isInstanceOf(ListResourceTemplatesResult.class);
        assertThat(((ListResourceTemplatesResult) result).resourceTemplates()).isEmpty();
    }

    @Test
    void subscribeRejectsNullSession() throws Exception {
        var result = handlers.get("resources/subscribe")
                .handle(DefaultMcpContext.noop(), Map.of("uri", "test://resource/1"));

        assertThat(result).isInstanceOf(JsonRpcError.class);
        assertThat(((JsonRpcError) result).code()).isEqualTo(JsonRpcErrors.INVALID_REQUEST);
    }

    @Test
    void unsubscribeRejectsNullSession() throws Exception {
        var result = handlers.get("resources/unsubscribe")
                .handle(DefaultMcpContext.noop(), Map.of("uri", "test://resource/1"));

        assertThat(result).isInstanceOf(JsonRpcError.class);
        assertThat(((JsonRpcError) result).code()).isEqualTo(JsonRpcErrors.INVALID_REQUEST);
    }

    @Test
    void shouldRecordSubscription() throws Exception {
        var session = server.createSession("test-session");
        session.activate();

        var result = handlers.get("resources/subscribe")
                .handle(context(session, server), Map.of("uri", "test://resource/1"));

        assertThat(result).isInstanceOf(EmptyResult.class);
        assertThat(registry.isSubscribed("test://resource/1", "test-session")).isTrue();
    }

    @Test
    void shouldRemoveSubscriptionOnUnsubscribe() throws Exception {
        var session = server.createSession("test-session");
        session.activate();

        handlers.get("resources/subscribe").handle(context(session, server), Map.of("uri", "test://resource/1"));
        assertThat(registry.isSubscribed("test://resource/1", "test-session")).isTrue();

        var result = handlers.get("resources/unsubscribe")
                .handle(context(session, server), Map.of("uri", "test://resource/1"));

        assertThat(result).isInstanceOf(EmptyResult.class);
        assertThat(registry.isSubscribed("test://resource/1", "test-session")).isFalse();
    }

    @Test
    void shouldPruneMapEntryWhenLastSubscriberLeaves() {
        registry.subscribe("test://resource/1", "s1");
        registry.subscribe("test://resource/1", "s2");

        registry.unsubscribe("test://resource/1", "s1");
        assertThat(registry.subscriptions).containsKey("test://resource/1");

        registry.unsubscribe("test://resource/1", "s2");
        assertThat(registry.subscriptions).doesNotContainKey("test://resource/1");
    }

    @Test
    void concurrentSubscribeSurvivesUnsubscribePruning() throws Exception {
        // Race under test: unsubscribe empties the set and prunes the map entry while a
        // concurrent subscribe is adding to it. With add/remove outside the map operation the
        // subscribe could land in the pruned (stranded) set and be silently lost; compute-based
        // mutation serializes both on the map's per-key lock, so the subscription must survive.
        var uri = "test://resource/race";
        int iterations = 1_000;
        try (var exec = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < iterations; i++) {
                registry.subscribe(uri, "leaver");
                var start = new java.util.concurrent.CountDownLatch(1);
                var unsub = exec.submit(() -> {
                    start.await();
                    registry.unsubscribe(uri, "leaver");
                    return null;
                });
                var sub = exec.submit(() -> {
                    start.await();
                    registry.subscribe(uri, "joiner");
                    return null;
                });
                start.countDown();
                unsub.get();
                sub.get();

                assertThat(registry.isSubscribed(uri, "joiner"))
                        .as("iteration %d: concurrent subscribe lost to pruning", i)
                        .isTrue();
                registry.unsubscribe(uri, "joiner");
            }
        }
    }

    @Test
    void notifyResourceUpdatedDropsDeadSessionSubscriptions() {
        var live = server.createSession("live-session");
        live.connection(new CollectingConnection());
        live.activate();

        registry.subscribe("test://resource/1", "live-session");
        registry.subscribe("test://resource/1", "dead-session"); // no such session on the server

        registry.notifyResourceUpdated("test://resource/1");

        assertThat(registry.isSubscribed("test://resource/1", "live-session")).isTrue();
        assertThat(registry.isSubscribed("test://resource/1", "dead-session")).isFalse();
    }

    @Test
    void shouldSendUpdatedNotificationToSubscribedSession() throws Exception {
        var conn = new CollectingConnection();
        var sess = server.createSession("notify-test");
        sess.connection(conn);
        sess.activate();

        handlers.get("resources/subscribe").handle(context(sess, server), Map.of("uri", "test://resource/1"));
        registry.add(
                ResourceDescriptor.of("test-resource", "test://resource/1", "Test resource", "text/plain"),
                (ctx, req) -> TextResourceContents.of("test://resource/1", "text/plain", ""));

        registry.notifyResourceUpdated("test://resource/1");

        var notificationEvent = conn.sent.stream()
                .filter(e -> e.data().contains("notifications/resources/updated"))
                .findFirst();
        assertThat(notificationEvent).isPresent();
    }

    @Test
    void shouldFireOnChangeWhenResourceAdded() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.add(
                ResourceDescriptor.of("r1", "test://r1", null, null),
                (ctx, req) -> TextResourceContents.of("test://r1", "text/plain", ""));

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldFireOnChangeWhenExistingResourceRemoved() {
        registry.add(
                ResourceDescriptor.of("r1", "test://r1", null, null),
                (ctx, req) -> TextResourceContents.of("test://r1", "text/plain", ""));

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.remove("r1");

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldNotFireOnChangeWhenRemovingNonExistentResource() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.remove("does-not-exist");

        assertThat(callCount).hasValue(0);
    }

    @Test
    void shouldFireOnChangeWhenTemplateAdded() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.addTemplate(ResourceTemplateEntry.of(
                "tmpl",
                "test://tmpl/{id}",
                null,
                null,
                (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", "")));

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldFireOnChangeWhenExistingTemplateRemoved() {
        registry.addTemplate(ResourceTemplateEntry.of(
                "tmpl",
                "test://tmpl/{id}",
                null,
                null,
                (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", "")));

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.removeTemplate("tmpl");

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldNotFireOnChangeWhenRemovingNonExistentTemplate() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.removeTemplate("does-not-exist");

        assertThat(callCount).hasValue(0);
    }

    @Test
    void shouldReplaceHandlerAndFireOnChangeWhenAddedWithSameName() {
        registry.add(
                ResourceDescriptor.of("doc", "resource://doc-v1", null, "text/plain"),
                (ctx, req) -> TextResourceContents.of("resource://doc-v1", "text/plain", "v1"));

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.add(
                ResourceDescriptor.of("doc", "resource://doc-v1", null, "text/plain"),
                (ctx, req) -> TextResourceContents.of("resource://doc-v1", "text/plain", "v2"));

        assertThat(callCount).hasValue(1);
        assertThat(registry.get("doc")).isNotNull();
        assertThat(registry.getAll()).hasSize(1);
    }

    @Test
    void shouldEvictOldUriWhenResourceUpdatedWithNewUri() throws Exception {
        registry.add(
                ResourceDescriptor.of("doc", "resource://doc-v1", null, "text/plain"),
                (ctx, req) -> TextResourceContents.of("resource://doc-v1", "text/plain", "v1"));

        registry.add(
                ResourceDescriptor.of("doc", "resource://doc-v2", null, "text/plain"),
                (ctx, req) -> TextResourceContents.of("resource://doc-v2", "text/plain", "v2"));

        // old URI must no longer resolve
        var oldUriResult = handlers.get("resources/read")
                .handle(DefaultMcpContext.noop(), Map.<String, Object>of("uri", "resource://doc-v1"));
        assertThat(oldUriResult).isInstanceOf(JsonRpcError.class);

        // new URI must resolve correctly
        var newUriResult = handlers.get("resources/read")
                .handle(DefaultMcpContext.noop(), Map.<String, Object>of("uri", "resource://doc-v2"));
        assertThat(newUriResult).isInstanceOf(ReadResourceResult.class);
        assertThat(registry.getAll()).hasSize(1);
    }

    @Test
    void shouldFireOnChangeWhenResourceUriUpdated() {
        registry.add(
                ResourceDescriptor.of("doc", "resource://doc-v1", null, "text/plain"),
                (ctx, req) -> TextResourceContents.of("resource://doc-v1", "text/plain", "v1"));

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.add(
                ResourceDescriptor.of("doc", "resource://doc-v2", null, "text/plain"),
                (ctx, req) -> TextResourceContents.of("resource://doc-v2", "text/plain", "v2"));

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldMapAllResourceDescriptorFieldsToProtocolModel() throws Exception {
        var annotations = Annotations.of(List.of(Role.USER), 0.9, "2026-01-01T00:00:00Z");
        var icon = Icon.of("https://example.com/icon.png", "image/png", null, null);
        var descriptor = ResourceDescriptor.of(
                "full-resource",
                "test://full",
                "Full description",
                "text/plain",
                "Full Title",
                annotations,
                1024.0,
                List.of(icon));
        registry.add(descriptor, (ctx, req) -> TextResourceContents.of("test://full", "text/plain", "content"));

        var result = (ListResourcesResult) handlers.get("resources/list").handle(DefaultMcpContext.noop(), null);

        assertThat(result.resources()).hasSize(1);
        var resource = result.resources().getFirst();
        assertThat(resource.name()).isEqualTo("full-resource");
        assertThat(resource.uri()).isEqualTo("test://full");
        assertThat(resource.description()).isEqualTo("Full description");
        assertThat(resource.mimeType()).isEqualTo("text/plain");
        assertThat(resource.title()).isEqualTo("Full Title");
        assertThat(resource.annotations()).isNotNull();
        assertThat(resource.annotations().audience())
                .containsExactly(dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role.USER);
        assertThat(resource.annotations().priority()).isEqualTo(annotations.priority());
        assertThat(resource.annotations().lastModified()).isEqualTo(annotations.lastModified());
        assertThat(resource.size()).isEqualTo(1024.0);
        assertThat(resource.icons()).hasSize(1);
        assertThat(resource.icons().getFirst().src()).isEqualTo("https://example.com/icon.png");
    }

    @Test
    void shouldRejectTemplateWithBlankName() {
        assertThatThrownBy(() -> ResourceTemplateEntry.of(
                        "",
                        "resource://{id}",
                        null,
                        null,
                        (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectTemplateWithBlankUriTemplate() {
        assertThatThrownBy(() -> ResourceTemplateEntry.of(
                        "tmpl",
                        "  ",
                        null,
                        null,
                        (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uriTemplate");
    }

    @Test
    void shouldRejectTemplateWithInvalidVariableName() {
        assertThatThrownBy(() -> ResourceTemplateEntry.of(
                        "bad",
                        "resource://{foo-bar}",
                        null,
                        null,
                        (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("foo-bar");
    }

    @Test
    void shouldRejectTemplateWithEmptyBraces() {
        assertThatThrownBy(() -> ResourceTemplateEntry.of(
                        "bad",
                        "resource://{}",
                        null,
                        null,
                        (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("braces");
    }

    @Test
    void shouldRejectTemplateWithUnmatchedOpenBrace() {
        assertThatThrownBy(() -> ResourceTemplateEntry.of(
                        "bad",
                        "resource://{foo",
                        null,
                        null,
                        (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("braces");
    }

    @Test
    void shouldRejectTemplateWithDuplicateVariableNames() {
        assertThatThrownBy(() -> ResourceTemplateEntry.of(
                        "bad",
                        "resource://{id}/{id}",
                        null,
                        null,
                        (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void shouldPreferMoreSpecificTemplateOnOverlap() throws Exception {
        var matched = new AtomicReference<String>();
        registry.addTemplate(
                ResourceTemplateEntry.of("generic", "resource://{type}/{id}", null, null, (ctx, uri, params) -> {
                    matched.set("generic");
                    return TextResourceContents.of(uri, "text/plain", "generic");
                }));
        registry.addTemplate(
                ResourceTemplateEntry.of("specific", "resource://users/{id}", null, null, (ctx, uri, params) -> {
                    matched.set("specific");
                    return TextResourceContents.of(uri, "text/plain", "specific");
                }));

        handlers.get("resources/read")
                .handle(DefaultMcpContext.noop(), Map.<String, Object>of("uri", "resource://users/42"));

        assertThat(matched).hasValue("specific");
    }

    @Test
    void shouldMapAllResourceTemplateFieldsToProtocolModel() throws Exception {
        var annotations = Annotations.of(null, 0.5, null);
        var icon = Icon.of("https://example.com/tmpl.png", null, null, null);
        var entry = ResourceTemplateEntry.of(
                "tmpl",
                "test://tmpl/{id}",
                "Template desc",
                "text/plain",
                "Template Title",
                annotations,
                List.of(icon),
                (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", "content-" + params.get("id")));
        registry.addTemplate(entry);

        var result = (ListResourceTemplatesResult)
                handlers.get("resources/templates/list").handle(DefaultMcpContext.noop(), null);

        assertThat(result.resourceTemplates()).hasSize(1);
        var tmpl = result.resourceTemplates().getFirst();
        assertThat(tmpl.name()).isEqualTo("tmpl");
        assertThat(tmpl.uriTemplate()).isEqualTo("test://tmpl/{id}");
        assertThat(tmpl.description()).isEqualTo("Template desc");
        assertThat(tmpl.mimeType()).isEqualTo("text/plain");
        assertThat(tmpl.title()).isEqualTo("Template Title");
        assertThat(tmpl.annotations()).isNotNull();
        assertThat(tmpl.annotations().priority()).isEqualTo(annotations.priority());
        assertThat(tmpl.icons()).hasSize(1);
        assertThat(tmpl.icons().getFirst().src()).isEqualTo("https://example.com/tmpl.png");
    }

    @Test
    void shouldReportResourceSizeWithoutLoadingContent() throws Exception {
        // size is declared upfront; content is loaded lazily via handler only on resources/read
        var descriptor = ResourceDescriptor.of(
                "sized-resource", "test://sized", "A resource", "application/octet-stream", null, null, 4096.0, null);
        var contentLoaded = new java.util.concurrent.atomic.AtomicBoolean(false);
        registry.add(descriptor, (ctx, req) -> {
            contentLoaded.set(true);
            return TextResourceContents.of("test://sized", "application/octet-stream", "data");
        });

        // resources/list returns size WITHOUT loading content
        var listResult = (ListResourcesResult) handlers.get("resources/list").handle(DefaultMcpContext.noop(), null);
        assertThat(listResult.resources().getFirst().size()).isEqualTo(4096.0);
        assertThat(contentLoaded).isFalse();

        // resources/read triggers lazy content load
        handlers.get("resources/read").handle(DefaultMcpContext.noop(), Map.<String, Object>of("uri", "test://sized"));
        assertThat(contentLoaded).isTrue();
    }

    private static class CollectingConnection implements SseConnection {
        final java.util.ArrayList<SseEvent> sent = new java.util.ArrayList<>();
        final boolean writable = true;

        @Override
        public boolean isWritable() {
            return writable;
        }

        @Override
        public void send(@NonNull SseEvent event) {
            sent.add(event);
        }
    }
}
