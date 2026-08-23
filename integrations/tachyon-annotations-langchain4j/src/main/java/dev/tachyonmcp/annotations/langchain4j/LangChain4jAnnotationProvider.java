/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.annotations.langchain4j;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.ContentBlock;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.features.annotations.AnnotationProvider;
import dev.tachyonmcp.api.server.features.annotations.AnnotationRegistrationContext;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolFn;
import dev.tachyonmcp.api.server.features.tools.ToolRequest;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.internal.AnnotationInvocationSupport;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

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
        for (Method method : clazz.getDeclaredMethods()) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation == null) continue;
            registerTool(instance, method, annotation, context);
        }
    }

    private void registerTool(Object instance, Method method, Tool annotation, AnnotationRegistrationContext context) {
        String name = annotation.name().isEmpty() ? method.getName() : annotation.name();
        String[] values = annotation.value();
        String description = (values.length > 0 && !values[0].isEmpty()) ? values[0] : null;

        JsonSchema inputSchema = buildInputSchema(method);

        ToolDescriptor descriptor = ToolDescriptor.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .build();

        ToolFn fn = (ctx, req) -> invokeTool(instance, method, ctx, req);
        context.tools().register(descriptor, fn);
    }

    private ToolResult invokeTool(Object instance, Method method, InteractionContext ctx, ToolRequest req)
            throws Exception {
        Object[] args = resolveArgs(method, ctx, req);
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

    private Object[] resolveArgs(Method method, InteractionContext ctx, ToolRequest req) {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            if (InteractionContext.class.isAssignableFrom(type)) {
                args[i] = ctx;
            } else {
                String paramName = resolveParamName(params[i]);
                args[i] = AnnotationInvocationSupport.coerce(
                        req.arguments().asMap().get(paramName), type);
            }
        }
        return args;
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
        return ToolResult.text(result.toString());
    }

    private static String resolveParamName(Parameter param) {
        P ann = param.getAnnotation(P.class);
        if (ann != null && !ann.name().isEmpty()) {
            return ann.name();
        }
        return param.getName();
    }

    private JsonSchema buildInputSchema(Method method) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : method.getParameters()) {
            if (InteractionContext.class.isAssignableFrom(param.getType())) continue;

            String paramName = resolveParamName(param);
            String jsonType = AnnotationInvocationSupport.jsonSchemaType(param.getType());

            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", jsonType);

            boolean isRequired = true;
            P pAnn = param.getAnnotation(P.class);
            if (pAnn != null) {
                String desc = pAnn.description().isEmpty() ? pAnn.value() : pAnn.description();
                if (!desc.isEmpty()) {
                    prop.put("description", desc);
                }
                if (!P.NO_DEFAULT.equals(pAnn.defaultValue())) {
                    prop.put("default", pAnn.defaultValue());
                    isRequired = false;
                } else if (!pAnn.required()) {
                    isRequired = false;
                }
            }
            if (isOptionalType(param.getType())) {
                isRequired = false;
            }

            properties.put(paramName, prop);
            if (isRequired) required.add(paramName);
        }

        return AnnotationInvocationSupport.inputSchema(properties, required);
    }

    private static boolean isOptionalType(Class<?> type) {
        return Optional.class.isAssignableFrom(type)
                || OptionalInt.class.isAssignableFrom(type)
                || OptionalLong.class.isAssignableFrom(type)
                || OptionalDouble.class.isAssignableFrom(type);
    }
}
