/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.features.annotations.AnnotationProvider;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link ServerBuilder#annotations} wiring end to end through {@link
 * DefaultServerBuilder}: multiple {@code annotations(...)} calls compose, a provider switch
 * mid-chain dispatches each registration to the provider active at its own call, and annotation
 * registrations run after {@code withTools}/{@code withResources}/... bootstrap registrations.
 *
 * @author Konstantin Pavlov
 */
class AnnotationsBuilderWiringTest {

    private static AnnotationProvider toolProvider(String toolName, String describedBy) {
        return (instance, context) -> context.tools()
                .register(tool -> tool.name(toolName).description(describedBy), (ctx, req) -> ToolResult.empty());
    }

    @Test
    void annotationsComposesAcrossMultipleCalls() {
        try (var server = TachyonServer.builder()
                .annotations(
                        a -> a.withProvider(toolProvider("first", "from-first")).register(new Object()))
                .annotations(a ->
                        a.withProvider(toolProvider("second", "from-second")).register(new Object()))
                .build()) {
            assertThat(server.tools().find("first")).isPresent();
            assertThat(server.tools().find("second")).isPresent();
        }
    }

    @Test
    void providerSwitchingWithinOneCallDispatchesEachRegistrationToItsOwnProvider() {
        try (var server = TachyonServer.builder()
                .annotations(a -> a.withProvider(toolProvider("from-first", "first-provider"))
                        .register(new Object())
                        .withProvider(toolProvider("from-second", "second-provider"))
                        .register(new Object()))
                .build()) {
            assertThat(server.tools().find("from-first").orElseThrow().description())
                    .isEqualTo("first-provider");
            assertThat(server.tools().find("from-second").orElseThrow().description())
                    .isEqualTo("second-provider");
        }
    }

    @Test
    void annotationRegistrationsRunAfterBootstrapRegistrations() {
        try (var server = TachyonServer.builder()
                .withTools(tools -> tools.register(
                        tool -> tool.name("shared").description("bootstrap"), (ctx, req) -> ToolResult.empty()))
                .annotations(a ->
                        a.withProvider(toolProvider("shared", "annotation")).register(new Object()))
                .build()) {
            assertThat(server.tools().find("shared").orElseThrow().description())
                    .isEqualTo("annotation");
        }
    }
}
