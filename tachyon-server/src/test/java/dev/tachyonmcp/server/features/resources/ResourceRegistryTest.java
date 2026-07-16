/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import static dev.tachyonmcp.test.TestUtils.newEngine;
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
import dev.tachyonmcp.server.config.ResourcesConfig;
import dev.tachyonmcp.server.domain.Annotations;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.Role;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.domain.UriTemplateValue;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.server.session.DefaultDispatchContext;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcError;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceRegistryTest {

    private static final ResourceHandler EMPTY_HANDLER =
            (ctx, request) -> TextResourceContents.of(request.uri(), "text/plain", "");

    private final ServerEngine server = newEngine(b -> {});
    private final DefaultResourceRegistry registry =
            new DefaultResourceRegistry(server, ResourcesConfig.builder().build());
    private final HashMap<String, RpcMethodHandler> handlers = new HashMap<>();

    private static ResourceDescriptor resource(String name) {
        return ResourceDescriptor.of(name, "test://" + name, null, null);
    }

    private static String scalar(Map<String, UriTemplateValue> params, String name) {
        return params.get(name).scalarValue();
    }

    private static DispatchContext context(Session session, ServerEngine server) {
        var ctx = DefaultDispatchContext.create(Protocols.list().getFirst(), server);
        ctx.setSession(session);
        return ctx;
    }

    @BeforeEach
    void setUp() {
        registry.registerHandlers(handlers);
    }

    @Test
    void shouldReturnEmptyListWhenNoResourcesRegistered() throws Exception {
        var result = handlers.get("resources/list").handle(DefaultDispatchContext.noop(), null);

        assertThat(result).isInstanceOf(ListResourcesResult.class);
        assertThat(((ListResourcesResult) result).resources()).isEmpty();
    }

    @Test
    void listWithZeroLimitUsesDefaultPageSize() {
        registry.register(resource("r1"), EMPTY_HANDLER);
        registry.register(resource("r2"), EMPTY_HANDLER);
        var result = registry.list(0, null);
        assertThat(result.items()).hasSize(2);
    }

    @Test
    void listWithCursorSkipsPastCursor() {
        registry.register(resource("alpha"), EMPTY_HANDLER);
        registry.register(resource("beta"), EMPTY_HANDLER);
        registry.register(resource("gamma"), EMPTY_HANDLER);
        var result = registry.list(1, "alpha");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().name()).isEqualTo("beta");
    }

    @Test
    void listReturnsCursorWhenMoreItemsAvailable() {
        registry.register(resource("a"), EMPTY_HANDLER);
        registry.register(resource("b"), EMPTY_HANDLER);
        var result = registry.list(1, null);
        assertThat(result.nextCursor()).isEqualTo("a");
    }

    @Test
    void listReturnsNullCursorWhenAllItemsReturned() {
        registry.register(resource("a"), EMPTY_HANDLER);
        var result = registry.list(10, null);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void listWithCustomPageSize() {
        var reg = new DefaultResourceRegistry(
                server, ResourcesConfig.builder().pageSize(1).build());
        reg.register(resource("a"), EMPTY_HANDLER);
        reg.register(resource("b"), EMPTY_HANDLER);
        var result = reg.list(0, null);
        assertThat(result.items()).hasSize(1);
        assertThat(result.nextCursor()).isEqualTo("a");
    }

    @Test
    void registerIsNoOpWhenResourcesCapabilityIsOff() {
        var reg = new DefaultResourceRegistry(
                server, ResourcesConfig.builder().off().build());
        var changeCount = new AtomicInteger();
        reg.onChange(changeCount::incrementAndGet);

        reg.register(resource("a"), EMPTY_HANDLER);
        reg.registerTemplate(
                ResourceTemplateDescriptor.of("template-entry", "test://entry/{id}"),
                (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", ""));

        assertThat(reg.find("a")).isEmpty();
        assertThat(reg.descriptors()).isEmpty();
        assertThat(reg.findTemplate("template-entry")).isEmpty();
        assertThat(reg.templateDescriptors()).isEmpty();
        assertThat(changeCount).hasValue(0);
    }

    @Test
    void interfaceDefaultBuilderOverloadsRegisterResourcesAndTemplates() throws Exception {
        ResourceRegistry api = registry;

        api.register(resource -> resource.name("sync").uri("test://sync"), EMPTY_HANDLER)
                .registerAsync(
                        resource -> resource.name("async").uri("test://async"),
                        (ctx, request) -> CompletableFuture.completedFuture(
                                TextResourceContents.of(request.uri(), "text/plain", "async")))
                .registerTemplate(
                        template -> template.name("sync-template").uriTemplate("test://sync/{id}"),
                        (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", scalar(params, "id")))
                .registerTemplateAsync(
                        template -> template.name("async-template").uriTemplate("test://async/{id}"),
                        (ctx, uri, params) -> CompletableFuture.completedFuture(
                                TextResourceContents.of(uri, "text/plain", scalar(params, "id"))));

        assertThat(api.descriptors()).extracting(ResourceDescriptor::name).containsExactly("async", "sync");
        assertThat(api.find("sync")).isPresent();
        assertThat(api.find("missing")).isEmpty();
        assertThat(api.templateDescriptors())
                .extracting(ResourceTemplateDescriptor::name)
                .containsExactly("async-template", "sync-template");
        assertThat(api.findTemplate("sync-template")).isPresent();
        assertThat(api.findTemplate("missing")).isEmpty();
        assertThat(api.unregister("sync")).isTrue();
        assertThat(api.unregister("sync")).isFalse();
        assertThat(api.unregisterTemplate("sync-template")).isTrue();
        assertThat(api.unregisterTemplate("sync-template")).isFalse();
    }

    @Test
    void registerRequiresHandler() {
        assertThat(ResourceRegistry.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("register"))
                .hasSize(2)
                .allSatisfy(method -> assertThat(method.getParameterCount()).isEqualTo(2));
    }

    @Test
    void registerTemplateRequiresDescriptorAndHandler() {
        assertThat(ResourceRegistry.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("registerTemplate"))
                .hasSize(2)
                .allSatisfy(method -> assertThat(method.getParameterCount()).isEqualTo(2));
    }

    @Test
    void shouldReturnErrorWhenResourceNotFound() throws Exception {
        var result = handlers.get("resources/read")
                .handle(DefaultDispatchContext.noop(), Map.<String, Object>of("uri", "test://nonexistent"));

        assertThat(result).isInstanceOf(JsonRpcError.class);
        assertThat(((JsonRpcError) result).code()).isEqualTo(JsonRpcErrors.RESOURCE_NOT_FOUND);
    }

    @Test
    void shouldReturnErrorWhenUriMissing() throws Exception {
        var result = handlers.get("resources/read").handle(DefaultDispatchContext.noop(), Map.of());

        assertThat(result).isInstanceOf(JsonRpcError.class);
    }

    @Test
    void shouldReadResourceContentByUri() throws Exception {
        var descriptor = ResourceDescriptor.of("test-resource", "test://resource/1", "Test resource", "text/plain");
        registry.register(
                descriptor, (ctx, req) -> TextResourceContents.of("test://resource/1", "text/plain", "content"));

        var result = handlers.get("resources/read")
                .handle(DefaultDispatchContext.noop(), Map.<String, Object>of("uri", "test://resource/1"));

        assertThat(result).isInstanceOf(ReadResourceResult.class);
        var readResult = (ReadResourceResult) result;
        assertThat(readResult.contents()).hasSize(1);
        assertThat(readResult.contents().getFirst().uri()).isEqualTo("test://resource/1");
    }

    @Test
    void shouldReturnEmptyTemplateList() throws Exception {
        var result = handlers.get("resources/templates/list").handle(DefaultDispatchContext.noop(), null);

        assertThat(result).isInstanceOf(ListResourceTemplatesResult.class);
        assertThat(((ListResourceTemplatesResult) result).resourceTemplates()).isEmpty();
    }

    @Test
    void subscribeRejectsNullSession() throws Exception {
        var result = handlers.get("resources/subscribe")
                .handle(DefaultDispatchContext.noop(), Map.of("uri", "test://resource/1"));

        assertThat(result).isInstanceOf(JsonRpcError.class);
        assertThat(((JsonRpcError) result).code()).isEqualTo(JsonRpcErrors.INVALID_REQUEST);
    }

    @Test
    void unsubscribeRejectsNullSession() throws Exception {
        var result = handlers.get("resources/unsubscribe")
                .handle(DefaultDispatchContext.noop(), Map.of("uri", "test://resource/1"));

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
        registry.register(
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

        registry.register(
                ResourceDescriptor.of("r1", "test://r1", null, null),
                (ctx, req) -> TextResourceContents.of("test://r1", "text/plain", ""));

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldFireOnChangeWhenExistingResourceRemoved() {
        registry.register(
                ResourceDescriptor.of("r1", "test://r1", null, null),
                (ctx, req) -> TextResourceContents.of("test://r1", "text/plain", ""));

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.unregister("r1");

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldNotFireOnChangeWhenRemovingNonExistentResource() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.unregister("does-not-exist");

        assertThat(callCount).hasValue(0);
    }

    @Test
    void shouldFireOnChangeWhenTemplateAdded() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.registerTemplate(
                ResourceTemplateDescriptor.of("tmpl", "test://tmpl/{id}"),
                (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", ""));

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldReplaceHandlerAndFireOnChangeWhenAddedWithSameName() {
        registry.register(
                ResourceDescriptor.of("doc", "resource://doc-v1", null, "text/plain"),
                (ctx, req) -> TextResourceContents.of("resource://doc-v1", "text/plain", "v1"));

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.register(
                ResourceDescriptor.of("doc", "resource://doc-v1", null, "text/plain"),
                (ctx, req) -> TextResourceContents.of("resource://doc-v1", "text/plain", "v2"));

        assertThat(callCount).hasValue(1);
        assertThat(registry.find("doc")).isPresent();
        assertThat(registry.descriptors()).hasSize(1);
    }

    @Test
    void shouldEvictOldUriWhenResourceUpdatedWithNewUri() throws Exception {
        registry.register(
                ResourceDescriptor.of("doc", "resource://doc-v1", null, "text/plain"),
                (ctx, req) -> TextResourceContents.of("resource://doc-v1", "text/plain", "v1"));

        registry.register(
                ResourceDescriptor.of("doc", "resource://doc-v2", null, "text/plain"),
                (ctx, req) -> TextResourceContents.of("resource://doc-v2", "text/plain", "v2"));

        // old URI must no longer resolve
        var oldUriResult = handlers.get("resources/read")
                .handle(DefaultDispatchContext.noop(), Map.<String, Object>of("uri", "resource://doc-v1"));
        assertThat(oldUriResult).isInstanceOf(JsonRpcError.class);

        // new URI must resolve correctly
        var newUriResult = handlers.get("resources/read")
                .handle(DefaultDispatchContext.noop(), Map.<String, Object>of("uri", "resource://doc-v2"));
        assertThat(newUriResult).isInstanceOf(ReadResourceResult.class);
        assertThat(registry.descriptors()).hasSize(1);
    }

    @Test
    void shouldFireOnChangeWhenResourceUriUpdated() {
        registry.register(
                ResourceDescriptor.of("doc", "resource://doc-v1", null, "text/plain"),
                (ctx, req) -> TextResourceContents.of("resource://doc-v1", "text/plain", "v1"));

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.register(
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
                1024L,
                List.of(icon));
        registry.register(descriptor, (ctx, req) -> TextResourceContents.of("test://full", "text/plain", "content"));

        var result = (ListResourcesResult) handlers.get("resources/list").handle(DefaultDispatchContext.noop(), null);

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
        assertThat(resource.size()).isEqualTo(1024L);
        assertThat(resource.icons()).hasSize(1);
        assertThat(resource.icons().getFirst().src()).isEqualTo("https://example.com/icon.png");
    }

    @Test
    void shouldRejectTemplateWithBlankName() {
        assertThatThrownBy(() -> ResourceTemplateDescriptor.of("", "resource://{id}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectTemplateWithBlankUriTemplate() {
        assertThatThrownBy(() -> ResourceTemplateDescriptor.of("tmpl", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uriTemplate");
    }

    @Test
    void shouldRejectTemplateWithInvalidVariableName() {
        assertThatThrownBy(() -> ResourceTemplateDescriptor.of("bad", "resource://{foo-bar}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("foo-bar");
    }

    @Test
    void shouldRejectTemplateWithEmptyBraces() {
        assertThatThrownBy(() -> ResourceTemplateDescriptor.of("bad", "resource://{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty URI template expression");
    }

    @Test
    void shouldRejectTemplateWithUnmatchedOpenBrace() {
        assertThatThrownBy(() -> ResourceTemplateDescriptor.of("bad", "resource://{foo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed URI template");
    }

    @Test
    void shouldAllowRepeatedVariableNames() {
        var descriptor = ResourceTemplateDescriptor.of("dictionary", "resource://dictionary/{term:1}/{term}");

        assertThat(descriptor.uriTemplate()).isEqualTo("resource://dictionary/{term:1}/{term}");
    }

    @Test
    void shouldPassExplodedSequenceToTemplateHandler() throws Exception {
        var captured = new AtomicReference<Map<String, UriTemplateValue>>();
        registry.registerTemplate(
                ResourceTemplateDescriptor.of("files", "resource://files{/segments*}"), (ctx, uri, params) -> {
                    captured.set(params);
                    return TextResourceContents.of(uri, "text/plain", "ok");
                });

        var result = handlers.get("resources/read")
                .handle(
                        DefaultDispatchContext.noop(),
                        Map.<String, Object>of("uri", "resource://files/one/two%20words"));

        assertThat(result).isInstanceOf(ReadResourceResult.class);
        assertThat(captured.get())
                .containsExactly(Map.entry("segments", new UriTemplateValue.Sequence(List.of("one", "two words"))));
    }

    @Test
    void shouldPreferMoreSpecificTemplateOnOverlap() throws Exception {
        var matched = new AtomicReference<@Nullable String>();
        registry.registerTemplate(
                ResourceTemplateDescriptor.of("generic", "resource://{type}/{id}"), (ctx, uri, params) -> {
                    matched.set("generic");
                    return TextResourceContents.of(uri, "text/plain", "generic");
                });
        registry.registerTemplate(
                ResourceTemplateDescriptor.of("specific", "resource://users/{id}"), (ctx, uri, params) -> {
                    matched.set("specific");
                    return TextResourceContents.of(uri, "text/plain", "specific");
                });

        handlers.get("resources/read")
                .handle(DefaultDispatchContext.noop(), Map.<String, Object>of("uri", "resource://users/42"));

        assertThat(matched).hasValue("specific");
    }

    @Test
    void shouldMapAllResourceTemplateFieldsToProtocolModel() throws Exception {
        var annotations = Annotations.of(null, 0.5, null);
        var icon = Icon.of("https://example.com/tmpl.png", null, null, null);
        registry.registerTemplate(
                ResourceTemplateDescriptor.of(
                        "tmpl",
                        "test://tmpl/{id}",
                        "Template desc",
                        "text/plain",
                        "Template Title",
                        annotations,
                        List.of(icon)),
                (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", "content-" + scalar(params, "id")));

        var result = (ListResourceTemplatesResult)
                handlers.get("resources/templates/list").handle(DefaultDispatchContext.noop(), null);

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
                "sized-resource", "test://sized", "A resource", "application/octet-stream", null, null, 4096L, null);
        var contentLoaded = new java.util.concurrent.atomic.AtomicBoolean(false);
        registry.register(descriptor, (ctx, req) -> {
            contentLoaded.set(true);
            return TextResourceContents.of("test://sized", "application/octet-stream", "data");
        });

        // resources/list returns size WITHOUT loading content
        var listResult =
                (ListResourcesResult) handlers.get("resources/list").handle(DefaultDispatchContext.noop(), null);
        assertThat(listResult.resources().getFirst().size()).isEqualTo(4096L);
        assertThat(contentLoaded).isFalse();

        // resources/read triggers lazy content load
        handlers.get("resources/read")
                .handle(DefaultDispatchContext.noop(), Map.<String, Object>of("uri", "test://sized"));
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
