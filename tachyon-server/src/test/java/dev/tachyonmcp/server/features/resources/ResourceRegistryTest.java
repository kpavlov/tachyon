/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.EmptyResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListResourceTemplatesResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListResourcesResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ReadResourceResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Resource;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceTemplate;
import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.TachyonMcpServer;
import dev.tachyonmcp.server.domain.Annotations;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.Role;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.session.DefaultMcpContext;
import dev.tachyonmcp.server.session.SseConnection;
import dev.tachyonmcp.server.session.SseEvent;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcError;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceRegistryTest {

    private final McpServer server = TachyonMcpServer.builder().build();
    private final ResourceRegistry registry = new ResourceRegistry(server);
    private final HashMap<String, McpMethodHandler> handlers = new HashMap<>();

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
        var descriptor = new ResourceDescriptor("test-resource", "test://resource/1", "Test resource", "text/plain");
        registry.add(descriptor, (ctx, req) -> new TextResourceContents("test://resource/1", "text/plain", "content"));

        var result = handlers.get("resources/read")
                .handle(DefaultMcpContext.noop(), Map.<String, Object>of("uri", "test://resource/1"));

        assertThat(result).isInstanceOf(ReadResourceResult.class);
        var readResult = (ReadResourceResult) result;
        assertThat(readResult.contents()).hasSize(1);
        assertThat(readResult.contents().get(0).uri()).isEqualTo("test://resource/1");
    }

    @Test
    void shouldReturnEmptyTemplateList() throws Exception {
        var result = handlers.get("resources/templates/list").handle(DefaultMcpContext.noop(), null);

        assertThat(result).isInstanceOf(ListResourceTemplatesResult.class);
        assertThat(((ListResourceTemplatesResult) result).resourceTemplates()).isEmpty();
    }

    @Test
    void shouldRecordSubscription() throws Exception {
        var session = server.createSession("test-session");
        session.activate();

        var result = handlers.get("resources/subscribe")
                .handle(new DefaultMcpContext(session, server), Map.of("uri", "test://resource/1"));

        assertThat(result).isInstanceOf(EmptyResult.class);
        assertThat(registry.isSubscribed("test://resource/1", "test-session")).isTrue();
    }

    @Test
    void shouldRemoveSubscriptionOnUnsubscribe() throws Exception {
        var session = server.createSession("test-session");
        session.activate();

        handlers.get("resources/subscribe")
                .handle(new DefaultMcpContext(session, server), Map.of("uri", "test://resource/1"));
        assertThat(registry.isSubscribed("test://resource/1", "test-session")).isTrue();

        var result = handlers.get("resources/unsubscribe")
                .handle(new DefaultMcpContext(session, server), Map.of("uri", "test://resource/1"));

        assertThat(result).isInstanceOf(EmptyResult.class);
        assertThat(registry.isSubscribed("test://resource/1", "test-session")).isFalse();
    }

    @Test
    void shouldSendUpdatedNotificationToSubscribedSession() throws Exception {
        var conn = new CollectingConnection();
        var sess = server.createSession("notify-test");
        sess.connection(conn);
        sess.activate();

        handlers.get("resources/subscribe")
                .handle(new DefaultMcpContext(sess, server), Map.of("uri", "test://resource/1"));
        registry.add(
                new ResourceDescriptor("test-resource", "test://resource/1", "Test resource", "text/plain"),
                (ctx, req) -> new TextResourceContents("test://resource/1", "text/plain", ""));

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
                new ResourceDescriptor("r1", "test://r1", null, null),
                (ctx, req) -> new TextResourceContents("test://r1", "text/plain", ""));

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldFireOnChangeWhenExistingResourceRemoved() {
        registry.add(
                new ResourceDescriptor("r1", "test://r1", null, null),
                (ctx, req) -> new TextResourceContents("test://r1", "text/plain", ""));

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
    void shouldReplaceHandlerAndFireOnChangeWhenAddedWithSameName() {
        registry.add(
                new ResourceDescriptor("doc", "resource://doc-v1", null, "text/plain"),
                (ctx, req) -> new TextResourceContents("resource://doc-v1", "text/plain", "v1"));

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.add(
                new ResourceDescriptor("doc", "resource://doc-v1", null, "text/plain"),
                (ctx, req) -> new TextResourceContents("resource://doc-v1", "text/plain", "v2"));

        assertThat(callCount).hasValue(1);
        assertThat(registry.get("doc")).isNotNull();
        assertThat(registry.getAll()).hasSize(1);
    }

    @Test
    void shouldEvictOldUriWhenResourceUpdatedWithNewUri() throws Exception {
        registry.add(
                new ResourceDescriptor("doc", "resource://doc-v1", null, "text/plain"),
                (ctx, req) -> new TextResourceContents("resource://doc-v1", "text/plain", "v1"));

        registry.add(
                new ResourceDescriptor("doc", "resource://doc-v2", null, "text/plain"),
                (ctx, req) -> new TextResourceContents("resource://doc-v2", "text/plain", "v2"));

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
                new ResourceDescriptor("doc", "resource://doc-v1", null, "text/plain"),
                (ctx, req) -> new TextResourceContents("resource://doc-v1", "text/plain", "v1"));

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.add(
                new ResourceDescriptor("doc", "resource://doc-v2", null, "text/plain"),
                (ctx, req) -> new TextResourceContents("resource://doc-v2", "text/plain", "v2"));

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldMapAllResourceDescriptorFieldsToProtocolModel() throws Exception {
        var annotations = new Annotations(List.of(Role.USER), 0.9, "2026-01-01T00:00:00Z");
        var icon = new Icon("https://example.com/icon.png", "image/png", null, null);
        var descriptor = new ResourceDescriptor(
                "full-resource",
                "test://full",
                "Full description",
                "text/plain",
                "Full Title",
                annotations,
                1024.0,
                List.of(icon));
        registry.add(descriptor, (ctx, req) -> new TextResourceContents("test://full", "text/plain", "content"));

        var result = (ListResourcesResult) handlers.get("resources/list").handle(DefaultMcpContext.noop(), null);

        assertThat(result.resources()).hasSize(1);
        var resource = (Resource) result.resources().getFirst();
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
    void shouldMapAllResourceTemplateFieldsToProtocolModel() throws Exception {
        var annotations = new Annotations(null, 0.5, null);
        var icon = new Icon("https://example.com/tmpl.png", null, null, null);
        var entry = new ResourceTemplateEntry(
                "tmpl",
                "test://tmpl/{id}",
                "Template desc",
                "text/plain",
                "Template Title",
                annotations,
                List.of(icon),
                id -> new TextResourceContents("test://tmpl/" + id, "text/plain", "content-" + id));
        registry.addTemplate(entry);

        var result = (ListResourceTemplatesResult)
                handlers.get("resources/templates/list").handle(DefaultMcpContext.noop(), null);

        assertThat(result.resourceTemplates()).hasSize(1);
        var tmpl = (ResourceTemplate) result.resourceTemplates().getFirst();
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
        var descriptor = new ResourceDescriptor(
                "sized-resource", "test://sized", "A resource", "application/octet-stream", null, null, 4096.0, null);
        var contentLoaded = new java.util.concurrent.atomic.AtomicBoolean(false);
        registry.add(descriptor, (ctx, req) -> {
            contentLoaded.set(true);
            return new TextResourceContents("test://sized", "application/octet-stream", "data");
        });

        // resources/list returns size WITHOUT loading content
        var listResult = (ListResourcesResult) handlers.get("resources/list").handle(DefaultMcpContext.noop(), null);
        assertThat(((Resource) listResult.resources().getFirst()).size()).isEqualTo(4096.0);
        assertThat(contentLoaded).isFalse();

        // resources/read triggers lazy content load
        handlers.get("resources/read").handle(DefaultMcpContext.noop(), Map.<String, Object>of("uri", "test://sized"));
        assertThat(contentLoaded).isTrue();
    }

    private static class CollectingConnection implements SseConnection {
        final java.util.ArrayList<SseEvent> sent = new java.util.ArrayList<>();
        volatile boolean writable = true;

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
