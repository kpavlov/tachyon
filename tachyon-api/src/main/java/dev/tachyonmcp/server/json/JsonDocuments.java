/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import dev.tachyonmcp.server.json.spi.JsonDocumentFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

final class JsonDocuments {

    private JsonDocuments() {}

    static String requireContent(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("json must not be null or blank");
        }
        return json;
    }

    static <T> JsonDocument from(T source, Class<T> type) {
        if (source instanceof JsonDocument doc) {
            return doc;
        }
        return factoryFor(type).toJsonDocument(source);
    }

    @SuppressWarnings("unchecked")
    private static <T> JsonDocumentFactory<T> factoryFor(Class<T> type) {
        var factory = Holder.FACTORIES.get(type);
        if (factory == null) {
            throw new IllegalStateException("No JsonDocumentFactory<" + type.getName()
                    + "> implementation found on the classpath. Register one via META-INF/services/"
                    + JsonDocumentFactory.class.getName() + ".");
        }
        return (JsonDocumentFactory<T>) factory;
    }

    private static final class Holder {
        static final Map<Class<?>, JsonDocumentFactory<?>> FACTORIES = discover();

        private static Map<Class<?>, JsonDocumentFactory<?>> discover() {
            var map = new HashMap<Class<?>, JsonDocumentFactory<?>>();
            for (JsonDocumentFactory<?> factory : ServiceLoader.load(JsonDocumentFactory.class)) {
                map.put(factory.sourceType(), factory);
            }
            return Map.copyOf(map);
        }
    }
}
