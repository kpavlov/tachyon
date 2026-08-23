/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.internal;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.json.JsonSchema;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Reflection helpers shared by {@code AnnotationProvider} implementations (mcp-java, LangChain4j,
 * Spring AI): coercing a raw JSON-RPC argument to a parameter's declared type, mapping a Java type
 * to its JSON Schema {@code "type"} value, and unwrapping an {@link InvocationTargetException} to
 * its cause. {@link #jsonSchemaType} and {@link #coerce} agree on exactly the same set of numeric
 * types, so a generated schema never advertises a type the coercion can't actually produce.
 *
 * <p>{@link Method#invoke} is caller-sensitive: whether it may reach a non-public annotated
 * method depends on the package of the code that calls it. Providers must therefore call {@code
 * method.invoke(...)} themselves rather than through this class, and pass the caught {@link
 * InvocationTargetException} to {@link #unwrap} to rethrow the annotated method's real exception.
 */
@ExperimentalApi
public final class AnnotationInvocationSupport {

    private AnnotationInvocationSupport() {}

    private static final Map<Class<?>, String> JSON_SCHEMA_TYPES = Map.ofEntries(
            Map.entry(String.class, "string"),
            Map.entry(int.class, "integer"),
            Map.entry(Integer.class, "integer"),
            Map.entry(long.class, "integer"),
            Map.entry(Long.class, "integer"),
            Map.entry(short.class, "integer"),
            Map.entry(Short.class, "integer"),
            Map.entry(byte.class, "integer"),
            Map.entry(Byte.class, "integer"),
            Map.entry(double.class, "number"),
            Map.entry(Double.class, "number"),
            Map.entry(float.class, "number"),
            Map.entry(Float.class, "number"),
            Map.entry(boolean.class, "boolean"),
            Map.entry(Boolean.class, "boolean"));

    /** Maps {@code type} to a JSON Schema {@code "type"} value, defaulting to {@code "string"}. */
    public static String jsonSchemaType(Class<?> type) {
        return JSON_SCHEMA_TYPES.getOrDefault(type, "string");
    }

    /**
     * Assembles a JSON Schema {@code "object"} envelope around {@code properties} and resolves it
     * through {@link JsonSchema#from(Map)}. {@code properties} iterates in its own order, so pass a
     * {@link LinkedHashMap} to keep parameter order stable in the emitted schema. {@code required}
     * is omitted entirely when empty rather than emitted as an empty array.
     *
     * @param properties the {@code properties} object, keyed by parameter name
     * @param required   the names of the required parameters
     * @return the assembled input schema
     */
    public static JsonSchema inputSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return JsonSchema.from(schema);
    }

    /**
     * Coerces {@code raw} to {@code type} when it's a boxed/primitive numeric widening or
     * narrowing; returns {@code raw} unchanged for every other case, including when it's already
     * an instance of {@code type}.
     */
    public static @Nullable Object coerce(@Nullable Object raw, Class<?> type) {
        if (raw == null || type.isInstance(raw)) return raw;
        if (raw instanceof Number num) {
            if (type == int.class || type == Integer.class) return num.intValue();
            if (type == long.class || type == Long.class) return num.longValue();
            if (type == double.class || type == Double.class) return num.doubleValue();
            if (type == float.class || type == Float.class) return num.floatValue();
            if (type == short.class || type == Short.class) return num.shortValue();
            if (type == byte.class || type == Byte.class) return num.byteValue();
        }
        return raw;
    }

    /**
     * Returns the real exception an annotated method threw, unwrapped from the {@link
     * InvocationTargetException} that {@link Method#invoke} wraps it in. Callers do {@code throw
     * AnnotationInvocationSupport.unwrap(e);} from their {@code catch (InvocationTargetException
     * e)} block. If the cause is an {@link Error}, it is thrown directly from this method instead
     * of being returned, since {@code Error} isn't an {@code Exception}.
     */
    public static Exception unwrap(InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof Exception ex) return ex;
        if (cause instanceof Error err) throw err;
        return e;
    }
}
