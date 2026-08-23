/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.annotations;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.json.PayloadDeserializer;
import dev.tachyonmcp.api.json.PayloadSerializer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * Reflection helpers shared by {@link AnnotationProvider} implementations (mcp-java, LangChain4j,
 * Spring AI): coercing a raw JSON-RPC argument to a parameter's declared type through the
 * server's configured {@link PayloadSerializer}/{@link PayloadDeserializer}, mapping a Java type
 * to its JSON Schema {@code "type"} value, rejecting parameter types neither can describe, and
 * unwrapping an {@link InvocationTargetException} to its cause. {@link #jsonSchemaType} and {@link
 * #coerce} agree on exactly the same set of supported types — String, numeric/boolean
 * primitives and wrappers, and {@code Optional}/{@code OptionalInt}/{@code OptionalLong}/{@code
 * OptionalDouble} wrapping one of those — so a generated schema never advertises a type the
 * coercion can't actually produce, and {@link #requireBindable} rejects everything else at
 * registration time instead of silently misdescribing it.
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

    /**
     * Maps {@code type} to a JSON Schema {@code "type"} value, unwrapping {@code Optional}-family
     * types to their inner type first.
     *
     * @throws IllegalStateException if {@code type} isn't one {@link #coerce} can produce
     */
    public static String jsonSchemaType(Type type) {
        Type effective = unwrapOptional(type);
        if (effective instanceof Class<?> cls) {
            String json = JSON_SCHEMA_TYPES.get(cls);
            if (json != null) return json;
        }
        throw new IllegalStateException(unsupportedTypeMessage(type));
    }

    /**
     * Rejects {@code param} at registration time if its type is neither a plain bindable scalar
     * nor an {@code Optional}-family wrapper of one — anything a generated schema and {@link
     * #coerce} can't agree on. Callers should invoke this only for parameters that aren't handled
     * some other way (e.g. an injected {@code InteractionContext} or a framework-specific request
     * context), after excluding those.
     *
     * @throws IllegalStateException if {@code param}'s type isn't bindable
     */
    public static void requireBindable(Parameter param, Method method) {
        try {
            jsonSchemaType(param.getParameterizedType());
        } catch (IllegalStateException e) {
            throw new IllegalStateException(
                    "Unsupported parameter type " + param.getParameterizedType().getTypeName() + " for parameter '"
                            + param.getName() + "' on " + method,
                    e);
        }
    }

    /** Returns whether {@code type} is {@code Optional}, {@code OptionalInt}, {@code OptionalLong}, or {@code OptionalDouble}. */
    public static boolean isOptionalType(Type type) {
        return type == OptionalInt.class
                || type == OptionalLong.class
                || type == OptionalDouble.class
                || type == Optional.class
                || (type instanceof ParameterizedType pt && pt.getRawType() == Optional.class);
    }

    /**
     * Returns the inner type of an {@code Optional}-family {@code type} (e.g. {@code String} for
     * {@code Optional<String>}, {@code int.class} for {@code OptionalInt}), or {@code type}
     * unchanged if it isn't one.
     */
    public static Type unwrapOptional(Type type) {
        if (type == OptionalInt.class) return int.class;
        if (type == OptionalLong.class) return long.class;
        if (type == OptionalDouble.class) return double.class;
        if (type instanceof ParameterizedType pt && pt.getRawType() == Optional.class) {
            return pt.getActualTypeArguments()[0];
        }
        return type;
    }

    private static String unsupportedTypeMessage(Type type) {
        return "Unsupported type " + type.getTypeName() + " — supported: String, numeric/boolean primitives and "
                + "wrappers, and Optional<T>/OptionalInt/OptionalLong/OptionalDouble of those.";
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
     * Coerces {@code raw} to {@code type} via a round trip through {@code serializer} and {@code
     * deserializer} — the same pattern {@code Args.decode(Type)} uses for whole argument objects,
     * applied here per parameter. Returns {@code raw} unchanged (no round trip) when it's already
     * {@code null} or already an instance of {@code type}. Correctly constructs {@code
     * Optional}-family wrappers around the coerced inner value, since {@code type} carries full
     * generic information (e.g. {@code Optional<Integer>}), not an erased {@link Class}.
     */
    public static @Nullable Object coerce(
            @Nullable Object raw, Type type, PayloadSerializer serializer, PayloadDeserializer deserializer) {
        if (raw == null) return null;
        if (type instanceof Class<?> cls && cls.isInstance(raw)) return raw;
        return deserializer.deserialize(serializer.serialize(raw), type);
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
