/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.extensions.AdvertiseMode;
import dev.tachyonmcp.api.server.extensions.ExtensionSettings;
import dev.tachyonmcp.api.server.extensions.ServerExtension;
import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import java.util.Map;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

/** {@code server/discover} (2026-07-28) must advertise registered extensions the same way {@code initialize} does. */
class DiscoverExtensionsTest extends AbstractStatelessMcpE2eTest {

    private static final String ALWAYS_EXT_ID = "com.example/always";
    private static final String NEVER_EXT_ID = "com.example/never";
    private static final String NEGOTIATED_EXT_ID = "com.example/negotiated";

    @Test
    void extensionAdvertisedInDiscoverCapabilities() throws Exception {
        startServer(it -> it.withExtensions(new TestExtension(ALWAYS_EXT_ID, AdvertiseMode.ALWAYS)));

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc": "2.0", "id": 1, "method": "server/discover"}
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body())
                    .inPath("$.result.capabilities.extensions")
                    // language=JSON
                    .isEqualTo("""
                            {"com.example/always": {"version": "1.0"}}
                            """);
        }
    }

    @Test
    void neverAdvertisedExtensionExcludedFromDiscoverCapabilities() throws Exception {
        startServer(it -> it.withExtensions(
                new TestExtension(ALWAYS_EXT_ID, AdvertiseMode.ALWAYS),
                new TestExtension(NEVER_EXT_ID, AdvertiseMode.NEVER)));

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc": "2.0", "id": 1, "method": "server/discover"}
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body())
                    .when(Option.IGNORING_EXTRA_FIELDS)
                    // language=JSON
                    .isEqualTo("""
                            {
                              "result": {
                                "capabilities": {
                                  "extensions": {"com.example/always": {"version": "1.0"}}
                                }
                              }
                            }
                            """);
        }
    }

    @Test
    void negotiatedExtensionAdvertisedWhenDiscoverRequestDeclaresIt() throws Exception {
        startServer(it -> it.withExtensions(new TestExtension(NEGOTIATED_EXT_ID, AdvertiseMode.NEGOTIATED)));

        try (var client = createModernTestClient()) {
            client.withExtensions(Map.of(NEGOTIATED_EXT_ID, JsonNodeFactory.instance.objectNode()));
            var response = client.post("""
                    {"jsonrpc": "2.0", "id": 1, "method": "server/discover"}
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body())
                    .inPath("$.result.capabilities.extensions")
                    // language=JSON
                    .isEqualTo("""
                            {"com.example/negotiated": {"version": "1.0"}}
                            """);
        }
    }

    @Test
    void negotiatedExtensionNotAdvertisedWhenDiscoverRequestDoesNotDeclareIt() throws Exception {
        startServer(it -> it.withExtensions(new TestExtension(NEGOTIATED_EXT_ID, AdvertiseMode.NEGOTIATED)));

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc": "2.0", "id": 1, "method": "server/discover"}
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body())
                    .node("result.capabilities.extensions")
                    .isAbsent();
        }
    }

    private static final class TestExtension implements ServerExtension {

        private final String id;
        private final AdvertiseMode advertiseMode;

        TestExtension(String id, AdvertiseMode advertiseMode) {
            this.id = id;
            this.advertiseMode = advertiseMode;
        }

        @Override
        public String extensionId() {
            return id;
        }

        @Override
        public AdvertiseMode advertiseMode() {
            return advertiseMode;
        }

        @Override
        public ExtensionSettings serverSettings() {
            return ExtensionSettings.of(Map.of("version", "1.0"));
        }
    }
}
