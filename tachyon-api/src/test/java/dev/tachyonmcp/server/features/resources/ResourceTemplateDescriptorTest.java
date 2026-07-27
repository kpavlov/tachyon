/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.resources;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.domain.Annotations;
import dev.tachyonmcp.server.domain.Icon;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceTemplateDescriptorTest {

    @Test
    void shouldBuildWithAllParameters() {
        var annotations = Annotations.of(List.of(), 0.5, "2026-01-01T00:00:00Z");
        var icons = List.of(Icon.of("https://example.com/icon.png", "image/png", null, null));
        var descriptor = ResourceTemplateDescriptor.builder()
                .name("tmpl")
                .uriTemplate("resource://{id}")
                .description("A template description")
                .mimeType("text/plain")
                .title("Template Title")
                .annotations(annotations)
                .icons(icons)
                .extensionId("ext-1")
                .build();

        assertThat(descriptor.name()).isEqualTo("tmpl");
        assertThat(descriptor.uriTemplate()).isEqualTo("resource://{id}");
        assertThat(descriptor.description()).isEqualTo("A template description");
        assertThat(descriptor.mimeType()).isEqualTo("text/plain");
        assertThat(descriptor.title()).isEqualTo("Template Title");
        assertThat(descriptor.annotations()).isSameAs(annotations);
        assertThat(descriptor.icons()).isEqualTo(icons);
        assertThat(descriptor.extensionId()).isEqualTo("ext-1");
    }

    @Test
    void shouldDefaultExtensionIdToNull() {
        var descriptor = ResourceTemplateDescriptor.builder()
                .name("tmpl")
                .uriTemplate("resource://{id}")
                .build();

        assertThat(descriptor.extensionId()).isNull();
    }
}
