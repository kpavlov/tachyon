/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.jsonrpc;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.Codec;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.CodecRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/** Low-level JSON-RPC 2.0 codec: parse and serialize messages to/from Netty {@link ByteBuf}. */
public final class JsonRpcCodec {

    public static final ObjectReadContext TREE_READ_CONTEXT = new ObjectReadContext.Base() {
        @Override
        @SuppressWarnings("unchecked")
        public <T extends TreeNode> T readTree(JsonParser p) {
            try {
                return (T) readTreeValue(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    };

    public static final JsonFactory FACTORY = new JsonFactory();

    private static final String JSONRPC = "jsonrpc";
    private static final String JSONRPC_VERSION = "2.0";
    private static final String ID = "id";
    private static final String METHOD = "method";
    private static final String PARAMS = "params";
    private static final String RESULT = "result";
    private static final String ERROR = "error";
    private static final String CODE = "code";
    private static final String MESSAGE = "message";
    private static final String DATA = "data";

    private JsonRpcCodec() {}

    /** Parses a JSON-RPC message from a {@link ByteBuf}. */
    public static JsonRpcMessage parseRequest(ByteBuf buf) {
        try (var in = new ByteBufInputStream(buf);
                JsonParser p = FACTORY.createParser(ObjectReadContext.empty(), (InputStream) in)) {
            return parseRootObject(p);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse JSON-RPC message", e);
        }
    }

    /** Serializes a JSON-RPC response. */
    public static ByteBuf serializeResponse(Object id, @Nullable String resultJson) {
        return serialize(gen -> {
            gen.writeStartObject();
            gen.writeStringProperty(JSONRPC, JSONRPC_VERSION);
            writeId(gen, id);
            if (resultJson != null) {
                gen.writeName(RESULT);
                gen.writeRawValue(resultJson);
            } else {
                gen.writeNullProperty(RESULT);
            }
            gen.writeEndObject();
        });
    }

    /** Serializes a JSON-RPC error response. */
    public static ByteBuf serializeError(Object id, int code, String message, @Nullable String dataJson) {
        return serialize(gen -> {
            gen.writeStartObject();
            gen.writeStringProperty(JSONRPC, JSONRPC_VERSION);
            writeId(gen, id);
            gen.writeObjectPropertyStart(ERROR);
            gen.writeNumberProperty(CODE, code);
            gen.writeStringProperty(MESSAGE, message);
            if (dataJson != null) {
                gen.writeName(DATA);
                gen.writeRawValue(dataJson);
            }
            gen.writeEndObject();
            gen.writeEndObject();
        });
    }

    private static void writeId(JsonGenerator gen, Object id) {
        gen.writeName(ID);
        switch (id) {
            case Long l -> gen.writeNumber(l);
            case Integer i -> gen.writeNumber(i);
            case Number n -> gen.writeNumber(n.doubleValue());
            default -> gen.writeString(id.toString());
        }
    }

    /** Serializes a JSON-RPC request. */
    public static ByteBuf serializeRequest(Object id, String method, String paramsJson) {
        return serialize(gen -> {
            gen.writeStartObject();
            gen.writeStringProperty(JSONRPC, JSONRPC_VERSION);
            gen.writeStringProperty(METHOD, method);
            writeId(gen, id);
            gen.writeName(PARAMS);
            gen.writeRawValue(paramsJson);
            gen.writeEndObject();
        });
    }

    /** Serializes a JSON-RPC notification (no ID). */
    public static ByteBuf serializeNotification(String method, String paramsJson) {
        return serialize(gen -> {
            gen.writeStartObject();
            gen.writeStringProperty(JSONRPC, JSONRPC_VERSION);
            gen.writeStringProperty(METHOD, method);
            gen.writeName(PARAMS);
            gen.writeRawValue(paramsJson);
            gen.writeEndObject();
        });
    }

    /** Serializes a JSON-RPC notification to a string. */
    public static String serializeNotificationAsString(String method, String paramsJson) {
        return serializeToString(gen -> {
            gen.writeStartObject();
            gen.writeStringProperty(JSONRPC, JSONRPC_VERSION);
            gen.writeStringProperty(METHOD, method);
            gen.writeName(PARAMS);
            gen.writeRawValue(paramsJson);
            gen.writeEndObject();
        });
    }

    /** Serializes a JSON-RPC request to a string. */
    public static String serializeRequestAsString(Object id, String method, String paramsJson) {
        return serializeToString(gen -> {
            gen.writeStartObject();
            gen.writeStringProperty(JSONRPC, JSONRPC_VERSION);
            gen.writeStringProperty(METHOD, method);
            writeId(gen, id);
            gen.writeName(PARAMS);
            gen.writeRawValue(paramsJson);
            gen.writeEndObject();
        });
    }

    private static String serializeToString(JsonWriter writer) {
        var sw = new StringWriter(256);
        try (JsonGenerator gen = FACTORY.createGenerator(ObjectWriteContext.empty(), sw)) {
            writer.write(gen);
            gen.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize JSON", e);
        }
        return sw.toString();
    }

    private static JsonRpcMessage parseRootObject(JsonParser p) throws IOException {
        if (p.nextToken() != JsonToken.START_OBJECT) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        return parseMessage(p);
    }

    private static JsonRpcMessage parseMessage(JsonParser p) throws IOException {
        Object id = null;
        String method = null;
        Object paramsObj = null;
        String resultJson = null;
        Integer errorCode = null;
        String errorMessage = null;
        String errorDataJson = null;
        boolean hasResult = false;
        boolean hasError = false;

        while (p.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = p.currentName();
            JsonToken token = p.nextToken();

            if (token == null) break;

            switch (fieldName) {
                case JSONRPC -> {}
                case ID -> id = parseId(p);
                case METHOD -> method = p.getString();
                case PARAMS -> paramsObj = readGenericValue(p);
                case RESULT -> {
                    hasResult = true;
                    resultJson = readRawJson(p);
                }
                case ERROR -> {
                    hasError = true;
                    if (token == JsonToken.START_OBJECT) {
                        while (p.nextToken() != JsonToken.END_OBJECT) {
                            String ef = p.currentName();
                            p.nextToken();
                            switch (ef) {
                                case CODE -> errorCode = p.getIntValue();
                                case MESSAGE -> errorMessage = p.getString();
                                case DATA -> errorDataJson = readRawJson(p);
                                default -> p.skipChildren();
                            }
                        }
                    }
                }
                default -> p.skipChildren();
            }
        }

        if (hasError && errorCode != null && errorMessage != null) {
            return new JsonRpcMessage.Error(id != null ? id : -1, errorCode, errorMessage, errorDataJson);
        }

        if (hasResult) {
            return new JsonRpcMessage.Response(id != null ? id : -1, resultJson != null ? resultJson : "null");
        }

        if (method != null) {
            if (id != null) {
                return new JsonRpcMessage.Request<>(id, method, paramsObj);
            }
            return new JsonRpcMessage.Notification<>(method, paramsObj);
        }

        throw new IllegalArgumentException("Invalid JSON-RPC message: no method, result, or error");
    }

    private static @Nullable Object parseId(JsonParser p) {
        return switch (p.currentToken()) {
            case VALUE_NUMBER_INT -> p.getLongValue();
            case VALUE_NUMBER_FLOAT -> p.getDoubleValue();
            case VALUE_STRING -> p.getString();
            case VALUE_NULL -> null;
            default -> throw new IllegalArgumentException("Unexpected id token: " + p.currentToken());
        };
    }

    /** Reads the remainder of the current JSON value as a raw JSON string. */
    public static String readRawJson(JsonParser p) {
        var writer = new StringWriter();
        try (JsonGenerator gen = FACTORY.createGenerator(ObjectWriteContext.empty(), writer)) {
            gen.copyCurrentStructure(p);
        }
        return writer.toString();
    }

    /** Reads the current JSON value as a {@link JsonNode}. */
    public static JsonNode readTreeValue(JsonParser p) throws IOException {
        return switch (p.currentToken()) {
            case START_OBJECT -> readObjectNode(p);
            case START_ARRAY -> readArrayNode(p);
            case VALUE_STRING -> JsonNodeFactory.instance.stringNode(p.getString());
            case VALUE_NUMBER_INT -> JsonNodeFactory.instance.numberNode(p.getLongValue());
            case VALUE_NUMBER_FLOAT -> JsonNodeFactory.instance.numberNode(p.getDoubleValue());
            case VALUE_TRUE -> JsonNodeFactory.instance.booleanNode(true);
            case VALUE_FALSE -> JsonNodeFactory.instance.booleanNode(false);
            case VALUE_NULL -> JsonNodeFactory.instance.nullNode();
            default -> throw new IOException("Unexpected token: " + p.currentToken());
        };
    }

    private static JsonNode readObjectNode(JsonParser p) throws IOException {
        var node = JsonNodeFactory.instance.objectNode();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            var key = p.currentName();
            p.nextToken();
            node.set(key, readTreeValue(p));
        }
        return node;
    }

    private static JsonNode readArrayNode(JsonParser p) throws IOException {
        var node = JsonNodeFactory.instance.arrayNode();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            node.add(readTreeValue(p));
        }
        return node;
    }

    /** Deserializes a JSON string to a generic Java object (Map, List, String, Number, Boolean, or null). */
    public static @Nullable Object readValue(String json) {
        try (var p = FACTORY.createParser(ObjectReadContext.empty(), json)) {
            p.nextToken();
            return readGenericValue(p);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read JSON value", e);
        }
    }

    /** Reads the current JSON token as a generic Java value. */
    public static @Nullable Object readGenericValue(JsonParser p) throws IOException {
        return switch (p.currentToken()) {
            case START_OBJECT -> readObject(p);
            case START_ARRAY -> readArray(p);
            case VALUE_STRING -> p.getString();
            case VALUE_NUMBER_INT -> p.getLongValue();
            case VALUE_NUMBER_FLOAT -> p.getDoubleValue();
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_NULL -> null;
            default -> throw new IOException("Unexpected token: " + p.currentToken());
        };
    }

    private static Map<String, Object> readObject(JsonParser p) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String key = p.currentName();
            p.nextToken();
            map.put(key, readGenericValue(p));
        }
        return map;
    }

    private static List<Object> readArray(JsonParser p) throws IOException {
        List<Object> list = new java.util.ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            list.add(readGenericValue(p));
        }
        return list;
    }

    /** Serializes a Java object to a JSON string, using registered codecs when available. */
    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String writeValueAsString(@Nullable Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof List
                || value instanceof Map) {
            return ValueSerializer.writeValueAsString(value);
        }
        var codec = CodecRegistry.codecFor(value.getClass());
        if (codec != null) {
            try (var out = new ByteArrayOutputStream(256);
                    var gen = FACTORY.createGenerator(ObjectWriteContext.empty(), out, JsonEncoding.UTF8)) {
                ((Codec) codec).encode(gen, value);
                gen.flush();
                return out.toString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to write value via codec for " + value.getClass(), e);
            }
        }
        return ValueSerializer.writeValueAsString(value);
    }

    /** Writes a Java object as a JSON value via the given generator. */
    public static void writeJsonValue(JsonGenerator gen, @Nullable Object value) {
        ValueSerializer.writeJsonValue(gen, value);
    }

    private static ByteBuf serialize(JsonWriter writer) {
        var buf = Unpooled.buffer(256);
        try (var out = new ByteBufOutputStream(buf)) {
            try (JsonGenerator gen = FACTORY.createGenerator(ObjectWriteContext.empty(), out, JsonEncoding.UTF8)) {
                writer.write(gen);
                gen.flush();
            }
        } catch (IOException e) {
            buf.release();
            throw new UncheckedIOException("Failed to serialize JSON", e);
        }
        return buf;
    }

    @FunctionalInterface
    private interface JsonWriter {
        void write(JsonGenerator gen) throws IOException;
    }
}
