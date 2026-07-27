/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.json.spi.JsonSchemaFactory;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class Jackson3JsonFactoryTest {

    private final Jackson3JsonFactory factory = Jackson3JsonFactory.INSTANCE;

    @Test
    void shouldReportStringAsSourceType() {
        assertThat(factory.sourceType()).isEqualTo(String.class);
    }

    @Test
    void shouldWrapValidJsonStringAsDocument() {
        var document = factory.toJsonDocument("{\"a\":1}");
        assertThatJson(document.json()).isEqualTo("{\"a\":1}");
    }

    @Test
    void shouldRejectMalformedJsonStringAsDocument() {
        assertThatThrownBy(() -> factory.toJsonDocument("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-json");
    }

    @Test
    void shouldWrapValidJsonStringAsSchema() {
        var schema = factory.toJsonSchema("{\"type\":\"object\"}");
        assertThatJson(schema.json()).isEqualTo("{\"type\":\"object\"}");
    }

    @Test
    void shouldRejectMalformedJsonStringAsSchema() {
        assertThatThrownBy(() -> factory.toJsonSchema("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-json");
    }

    @Test
    void shouldBeDiscoverableAsServiceLoaderProviderForString() {
        JsonSchemaFactory<?> discovered = null;
        for (JsonSchemaFactory<?> candidate : ServiceLoader.load(JsonSchemaFactory.class)) {
            if (candidate.sourceType() == String.class) {
                discovered = candidate;
            }
        }
        assertThat(discovered).isInstanceOf(Jackson3JsonFactory.class);
    }
}
