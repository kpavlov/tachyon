/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link JsonConfig} builder behavior: defaults, custom values, and convenience methods.
 *
 * @author Konstantin Pavlov
 */
class JsonConfigTest {

    @Test
    void allNullByDefault() {
        var config = JsonConfig.builder().build();

        assertThat(config.serializer()).isNull();
        assertThat(config.deserializer()).isNull();
        assertThat(config.inputValidator()).isNull();
        assertThat(config.outputValidator()).isNull();
    }

    @Test
    void buildsWithCustomValues() {
        var ser = new PayloadSerializer() {
            @Override
            public <T> String serialize(T value) {
                return "{}";
            }
        };
        var deser = new PayloadDeserializer() {
            @Override
            public <T> T deserialize(String json, Type targetType) {
                return null;
            }
        };
        var validator = (JsonSchemaValidator) (schema, arguments) -> List.of();

        var config = JsonConfig.builder()
                .serializer(ser)
                .deserializer(deser)
                .inputSchemaValidator(validator)
                .outputSchemaValidator(validator)
                .build();

        assertThat(config.serializer()).isSameAs(ser);
        assertThat(config.deserializer()).isSameAs(deser);
        assertThat(config.inputValidator()).isSameAs(validator);
        assertThat(config.outputValidator()).isSameAs(validator);
    }

    @Test
    void serdeSetsBothSerializerAndDeserializer() {
        var serde = new PayloadSerde() {
            @Override
            public <T> String serialize(T value) {
                return "{}";
            }

            @Override
            public <T> T deserialize(String json, Type targetType) {
                return null;
            }
        };

        var config = JsonConfig.builder().serde(serde).build();

        assertThat(config.serializer()).isSameAs(serde);
        assertThat(config.deserializer()).isSameAs(serde);
    }

    @Test
    void schemaValidatorSetsBothInputAndOutput() {
        var validator = (JsonSchemaValidator) (schema, arguments) -> List.of();

        var config = JsonConfig.builder().schemaValidator(validator).build();

        assertThat(config.inputValidator()).isSameAs(validator);
        assertThat(config.outputValidator()).isSameAs(validator);
    }
}
