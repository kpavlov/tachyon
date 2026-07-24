/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.json;

import java.lang.reflect.Type;
import tools.jackson.databind.ObjectMapper;

/**
 * Default {@link PayloadSerde} backed by Jackson.
 *
 * @author Konstantin Pavlov
 */
public final class JacksonPayloadSerde implements PayloadSerde {

    private final ObjectMapper mapper;

    public static final JacksonPayloadSerde INSTANCE = new JacksonPayloadSerde();

    public JacksonPayloadSerde() {
        this(JsonUtils.mapper());
    }

    public JacksonPayloadSerde(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String serialize(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            var type = value != null ? value.getClass().getName() : "null";
            throw new IllegalArgumentException("Failed to serialize value of type " + type, e);
        }
    }

    @Override
    public <T> T deserialize(String json, Type targetType) {
        try {
            return mapper.readValue(json, mapper.constructType(targetType));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to deserialize JSON as " + targetType.getTypeName() + " (payload length=" + json.length()
                            + ")",
                    e);
        }
    }
}
