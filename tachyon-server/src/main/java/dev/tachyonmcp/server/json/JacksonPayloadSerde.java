/*
 * Copyright (c) 2026 Konstantin Pavlov.
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

    private static final int MAX_MESSAGE_PAYLOAD = 200;

    private final ObjectMapper mapper;

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
                    "Failed to deserialize JSON as " + targetType.getTypeName() + ": " + abbreviate(json), e);
        }
    }

    private static String abbreviate(String json) {
        return json.length() <= MAX_MESSAGE_PAYLOAD ? json : json.substring(0, MAX_MESSAGE_PAYLOAD) + "…";
    }
}
