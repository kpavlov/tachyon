/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.json;

import dev.tachyonmcp.api.json.spi.JsonSchemaFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

final class JsonSchemas {

    private JsonSchemas() {}

    static <T> JsonSchema from(T source, Class<T> type) {
        for (JsonSchemaFactory factory : Holder.FACTORIES) {
            var schema = factory.toJsonSchema(source, type);
            if (schema.isPresent()) {
                return schema.get();
            }
        }
        throw new IllegalStateException("No JsonSchemaFactory registered for " + type.getName()
                + " sources via META-INF/services/"
                + JsonSchemaFactory.class.getName() + ".");
    }

    static JsonSchema generated(Class<?> type) {
        for (JsonSchemaFactory factory : Holder.FACTORIES) {
            var schema = factory.tryGenerate(type);
            if (schema.isPresent()) {
                return schema.get();
            }
        }
        throw new IllegalStateException("No schema generated for " + type.getName()
                + ": no JsonSchemaFactory registered in "
                + "META-INF/services/dev.tachyonmcp.api.json.spi.JsonSchemaFactory produces one."
                + " Check for a build-time codegen resource at "
                + "META-INF/kt-schema/schemas/" + type.getName().replace('.', '/') + ".json"
                + " or add a generator factory to the classpath.");
    }

    private static final class Holder {
        static final List<JsonSchemaFactory> FACTORIES = discover();

        private static List<JsonSchemaFactory> discover() {
            var factories = new ArrayList<JsonSchemaFactory>();
            for (JsonSchemaFactory factory : ServiceLoader.load(JsonSchemaFactory.class)) {
                factories.add(factory);
            }
            factories.sort(Comparator.comparingInt(JsonSchemaFactory::priority));
            return List.copyOf(factories);
        }
    }
}
