/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.annotations.langchain4j;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.json.PayloadDeserializer;
import dev.tachyonmcp.api.json.PayloadSerializer;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.ContentBlock;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.features.annotations.AnnotationInvocationSupport;
import dev.tachyonmcp.api.server.features.annotations.AnnotationProvider;
import dev.tachyonmcp.api.server.features.annotations.AnnotationRegistrationContext;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolFn;
import dev.tachyonmcp.api.server.features.tools.ToolRequest;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * {@link AnnotationProvider} that discovers LangChain4j {@link Tool} annotations
 * on an annotated instance and registers the corresponding Tachyon tool descriptors.
 *
 * <p>LangChain4j does not define resource or prompt annotations; only tools are mapped.
 *
 * <p>Usage:
 * <pre>{@code
 * serverBuilder.annotations(ctx -> {
 *     ctx.provider(new LangChain4jAnnotationProvider());
 *     ctx.register(new MyToolClass());
 * });
 * }</pre>
 */
public class LangChain4jAnnotationProvider implements AnnotationProvider {

    @Override
    public void register(Object instance, AnnotationRegistrationContext context) {
        Class<?> clazz = instance.getClass();
        PayloadSerializer serializer = context.payloadSerializer();
        PayloadDeserializer deserializer = context.payloadDeserializer();
        for (Method method : clazz.getDeclaredMethods()) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation == null) continue;
            registerTool(instance, method, annotation, context, serializer, deserializer);
        }
    }

    private void registerTool(
            Object instance,
            Method method,
            Tool annotation,
            AnnotationRegistrationContext context,
            PayloadSerializer serializer,
            PayloadDeserializer deserializer) {
        String name = annotation.name().isEmpty() ? method.getName() : annotation.name();
        String[] values = annotation.value();
        String description = (values.length > 0 && !values[0].isEmpty()) ? values[0] : null;

        JsonSchema inputSchema = buildInputSchema(method, deserializer);

        ToolDescriptor descriptor = ToolDescriptor.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .build();

        ToolFn fn = (ctx, req) -> invokeTool(instance, method, ctx, req, serializer, deserializer);
        context.tools().register(descriptor, fn);
    }

    private ToolResult invokeTool(
            Object instance,
            Method method,
            InteractionContext ctx,
            ToolRequest req,
            PayloadSerializer serializer,
            PayloadDeserializer deserializer)
            throws Exception {
        Object[] args = resolveArgs(method, ctx, req, serializer, deserializer);
        Object result = invoke(method, instance, args);
        return convertResult(result);
    }

    /**
     * Invokes {@code method} on {@code instance}, unwrapping {@link InvocationTargetException} to
     * its cause. Must call {@link Method#invoke} directly (not via a shared helper in another
     * package) since it's caller-sensitive: reflective access to a non-public annotated method
     * depends on the package of the code that calls it.
     */
    private Object invoke(Method method, Object instance, Object... args) throws Exception {
        try {
            return method.invoke(instance, args);
        } catch (InvocationTargetException e) {
            throw AnnotationInvocationSupport.unwrap(e);
        }
    }

    private Object[] resolveArgs(
            Method method,
            InteractionContext ctx,
            ToolRequest req,
            PayloadSerializer serializer,
            PayloadDeserializer deserializer) {
        final Parameter[] params = method.getParameters();
        final Object[] args = new Object[params.length];
        final Map<String, Object> values = req.arguments().asMap();
        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            if (InteractionContext.class.isAssignableFrom(type)) {
                args[i] = ctx;
                continue;
            }
            String paramName = resolveParamName(params[i]);
            if (values.containsKey(paramName)) {
                args[i] = AnnotationInvocationSupport.coerce(
                        values.get(paramName), params[i].getParameterizedType(), serializer, deserializer);
            } else {
                P pAnn = params[i].getAnnotation(P.class);
                String defaultVal =
                        (pAnn != null && !P.NO_DEFAULT.equals(pAnn.defaultValue())) ? pAnn.defaultValue() : null;
                args[i] = defaultVal != null ? parseDefaultLiteral(defaultVal, type) : null;
            }
        }
        return args;
    }

    /** Parses an annotation's {@code defaultValue} string literal as the parameter's declared type. */
    private static @Nullable Object parseDefaultLiteral(String literal, Class<?> type) {
        if (type == int.class || type == Integer.class) return Integer.valueOf(literal);
        if (type == long.class || type == Long.class) return Long.valueOf(literal);
        if (type == short.class || type == Short.class) return Short.valueOf(literal);
        if (type == byte.class || type == Byte.class) return Byte.valueOf(literal);
        if (type == double.class || type == Double.class) return Double.valueOf(literal);
        if (type == float.class || type == Float.class) return Float.valueOf(literal);
        if (type == boolean.class || type == Boolean.class) return Boolean.valueOf(literal);
        return literal;
    }

    private ToolResult convertResult(Object result) {
        if (result == null) {
            return ToolResult.text("Success");
        }
        if (result instanceof ToolResult tr) {
            return tr;
        }
        if (result instanceof String s) {
            return ToolResult.text(s);
        }
        if (result instanceof ContentBlock cb) {
            return ToolResult.content(cb);
        }
        if (result instanceof List<?> list) {
            List<ContentBlock> blocks = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof ContentBlock cb) {
                    blocks.add(cb);
                } else {
                    blocks.add(TextContent.of(item.toString()));
                }
            }
            return ToolResult.content(blocks.toArray(new ContentBlock[0]));
        }
        if (result instanceof Number || result instanceof Boolean || result instanceof Character) {
            return ToolResult.text(result.toString());
        }
        return ToolResult.structured(result);
    }

    private static String resolveParamName(Parameter param) {
        P ann = param.getAnnotation(P.class);
        if (ann != null && !ann.name().isEmpty()) {
            return ann.name();
        }
        return param.getName();
    }

    /**
     * Builds the tool's input schema via LangChain4j's own {@link ToolSpecifications}, which
     * (unlike {@link AnnotationInvocationSupport#jsonSchemaType}) correctly describes records,
     * enums, {@code List<T>}, and nested POJOs, and already unwraps {@code Optional<T>} — types
     * the shared scalar-only mapping rejects. LangChain4j knows nothing of Tachyon's {@link
     * InteractionContext} parameter, so it's stripped from the generated schema afterward.
     */
    @SuppressWarnings("unchecked")
    private JsonSchema buildInputSchema(Method method, PayloadDeserializer deserializer) {
        String specJson = ToolSpecifications.toolSpecificationFrom(method).toJson();
        Map<String, Object> spec = deserializer.deserialize(specJson, Map.class);
        Map<String, Object> parametersSchema = (Map<String, Object>) spec.get("parameters");
        Map<String, Object> properties = new LinkedHashMap<>((Map<String, Object>) parametersSchema.get("properties"));
        List<String> required = new ArrayList<>((List<String>) parametersSchema.getOrDefault("required", List.of()));

        for (Parameter param : method.getParameters()) {
            if (InteractionContext.class.isAssignableFrom(param.getType())) {
                String paramName = resolveParamName(param);
                properties.remove(paramName);
                required.remove(paramName);
            }
        }

        return AnnotationInvocationSupport.inputSchema(properties, required);
    }
}
