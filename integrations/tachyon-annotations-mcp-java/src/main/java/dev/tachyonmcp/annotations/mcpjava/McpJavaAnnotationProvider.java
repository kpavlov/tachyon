/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.annotations.mcpjava;

import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.json.PayloadDeserializer;
import dev.tachyonmcp.api.json.PayloadSerializer;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.BlobResourceContents;
import dev.tachyonmcp.api.server.domain.PromptArgument;
import dev.tachyonmcp.api.server.domain.PromptMessage;
import dev.tachyonmcp.api.server.domain.TextResourceContents;
import dev.tachyonmcp.api.server.domain.ToolAnnotations;
import dev.tachyonmcp.api.server.features.annotations.AnnotationInvocationSupport;
import dev.tachyonmcp.api.server.features.annotations.AnnotationProvider;
import dev.tachyonmcp.api.server.features.annotations.AnnotationRegistrationContext;
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.api.server.features.prompts.PromptFn;
import dev.tachyonmcp.api.server.features.prompts.PromptRequest;
import dev.tachyonmcp.api.server.features.prompts.PromptResult;
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.api.server.features.resources.ResourceFn;
import dev.tachyonmcp.api.server.features.resources.ResourceRequest;
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolFn;
import dev.tachyonmcp.api.server.features.tools.ToolRequest;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * {@link AnnotationProvider} that discovers mcp-java annotations
 * ({@code @Tool}, {@code @Resource}, {@code @ResourceTemplate}, {@code @Prompt})
 * on an annotated instance and registers the corresponding Tachyon descriptors.
 *
 * <p>Usage:
 * <pre>{@code
 * serverBuilder.annotations(ctx -> {
 *     ctx.provider(new McpJavaAnnotationProvider());
 *     ctx.register(new MyToolClass());
 * });
 * }</pre>
 */
public class McpJavaAnnotationProvider implements AnnotationProvider {

    private static final String ELEMENT_NAME = "<<element name>>";

    @Override
    public void register(Object instance, AnnotationRegistrationContext context) {
        Class<?> clazz = instance.getClass();
        PayloadSerializer serializer = context.payloadSerializer();
        PayloadDeserializer deserializer = context.payloadDeserializer();
        for (Method method : clazz.getDeclaredMethods()) {
            registerTools(instance, method, context, serializer, deserializer);
            registerResources(instance, method, context, serializer, deserializer);
            registerResourceTemplates(instance, method, context, serializer, deserializer);
            registerPrompts(instance, method, context, serializer, deserializer);
        }
    }

    private void registerTools(
            Object instance,
            Method method,
            AnnotationRegistrationContext context,
            PayloadSerializer serializer,
            PayloadDeserializer deserializer) {
        org.mcpjava.server.tools.Tool annotation = method.getAnnotation(org.mcpjava.server.tools.Tool.class);
        if (annotation == null) return;

        String name = resolveName(annotation.name(), method);
        String description = annotation.description().isEmpty() ? null : annotation.description();
        String title = annotation.title().isEmpty() ? null : annotation.title();

        ToolAnnotations toolAnnotations = mapToolAnnotations(annotation.annotations());
        JsonSchema inputSchema = buildInputSchema(method, org.mcpjava.server.tools.ToolArg.class);

        ToolDescriptor descriptor = ToolDescriptor.builder()
                .name(name)
                .description(description)
                .title(title)
                .inputSchema(inputSchema)
                .annotations(toolAnnotations)
                .build();

        ToolFn fn = (ctx, req) -> invokeTool(instance, method, ctx, req, serializer, deserializer);
        context.tools().register(descriptor, fn);
    }

    private ToolAnnotations mapToolAnnotations(org.mcpjava.server.tools.Tool.Annotations ann) {
        return ToolAnnotations.builder()
                .readOnlyHint(ann.readOnlyHint())
                .destructiveHint(ann.destructiveHint())
                .idempotentHint(ann.idempotentHint())
                .openWorldHint(ann.openWorldHint())
                .build();
    }

    private ToolResult invokeTool(
            Object instance,
            Method method,
            InteractionContext ctx,
            ToolRequest req,
            PayloadSerializer serializer,
            PayloadDeserializer deserializer)
            throws Exception {
        Object[] args = resolveArgs(
                method, ctx, req.arguments().asMap(), org.mcpjava.server.tools.ToolArg.class, serializer, deserializer);
        Object result = invoke(method, instance, args);
        return convertToolResult(result);
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
            Map<String, Object> values,
            Class<? extends Annotation> argAnnotation,
            PayloadSerializer serializer,
            PayloadDeserializer deserializer) {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            if (InteractionContext.class.isAssignableFrom(type)) {
                args[i] = ctx;
                continue;
            }
            String paramName = resolveParamName(params[i], argAnnotation);
            if (values.containsKey(paramName)) {
                args[i] = AnnotationInvocationSupport.coerce(
                        values.get(paramName), params[i].getParameterizedType(), serializer, deserializer);
            } else {
                Annotation ann = params[i].getAnnotation(argAnnotation);
                String defaultVal = ann != null ? getStringAttribute(ann, "defaultValue") : null;
                args[i] = (defaultVal != null && !defaultVal.isEmpty()) ? parseDefaultLiteral(defaultVal, type) : null;
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

    private ToolResult convertToolResult(Object result) {
        if (result == null) return ToolResult.empty();
        if (result instanceof ToolResult tr) return tr;
        if (result instanceof org.mcpjava.server.tools.ToolResponse tr) return convertMcpJavaToolResponse(tr);
        if (result instanceof String s) return ToolResult.text(s);
        if (result instanceof dev.tachyonmcp.api.server.domain.ContentBlock cb) {
            return ToolResult.content(cb);
        }
        if (result instanceof List<?> list) {
            List<dev.tachyonmcp.api.server.domain.ContentBlock> blocks = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof dev.tachyonmcp.api.server.domain.ContentBlock cb) {
                    blocks.add(cb);
                } else {
                    blocks.add(dev.tachyonmcp.api.server.domain.TextContent.of(item.toString()));
                }
            }
            return ToolResult.content(blocks.toArray(new dev.tachyonmcp.api.server.domain.ContentBlock[0]));
        }
        return ToolResult.text(result.toString());
    }

    /**
     * Translates mcp-java's native {@link org.mcpjava.server.tools.ToolResponse}, preserving its
     * content blocks, structured content, and error status rather than falling back to {@code
     * toString()}.
     */
    private ToolResult convertMcpJavaToolResponse(org.mcpjava.server.tools.ToolResponse response) {
        List<dev.tachyonmcp.api.server.domain.ContentBlock> blocks = response.content().stream()
                .map(this::convertMcpJavaContentBlock)
                .toList();
        if (response.isError()) {
            return ToolResult.Error.builder().content(blocks).build();
        }
        return ToolResult.Success.builder()
                .structuredValue(response.structuredContent().orElse(null))
                .content(blocks)
                .build();
    }

    private dev.tachyonmcp.api.server.domain.ContentBlock convertMcpJavaContentBlock(
            org.mcpjava.server.content.ContentBlock block) {
        if (block instanceof org.mcpjava.server.content.TextContent tc) {
            return dev.tachyonmcp.api.server.domain.TextContent.of(tc.text());
        }
        return dev.tachyonmcp.api.server.domain.TextContent.of(block.toString());
    }

    private void registerResources(
            Object instance,
            Method method,
            AnnotationRegistrationContext context,
            PayloadSerializer serializer,
            PayloadDeserializer deserializer) {
        org.mcpjava.server.resources.Resource annotation =
                method.getAnnotation(org.mcpjava.server.resources.Resource.class);
        if (annotation == null) return;

        for (Parameter param : method.getParameters()) {
            if (InteractionContext.class.isAssignableFrom(param.getType())) continue;
            AnnotationInvocationSupport.requireBindable(param, method);
        }

        String name = resolveName(annotation.name(), method);
        String description = annotation.description().isEmpty() ? null : annotation.description();
        String title = annotation.title().isEmpty() ? null : annotation.title();
        String mimeType = annotation.mimeType().isEmpty() ? null : annotation.mimeType();
        Long size = annotation.size() >= 0 ? (long) annotation.size() : null;

        ResourceDescriptor descriptor = ResourceDescriptor.builder()
                .name(name)
                .uri(annotation.uri())
                .description(description)
                .title(title)
                .mimeType(mimeType)
                .size(size)
                .build();

        ResourceFn fn = (ctx, req) -> invokeResource(instance, method, ctx, req, serializer, deserializer);
        context.resources().register(descriptor, fn);
    }

    private dev.tachyonmcp.api.server.domain.ResourceContents invokeResource(
            Object instance,
            Method method,
            InteractionContext ctx,
            ResourceRequest req,
            PayloadSerializer serializer,
            PayloadDeserializer deserializer)
            throws Exception {
        Object[] args = resolveArgs(
                method,
                ctx,
                Map.of(),
                org.mcpjava.server.resources.ResourceTemplateArg.class,
                serializer,
                deserializer);
        Object result = invoke(method, instance, args);
        return convertResourceContents(result, req.uri());
    }

    private dev.tachyonmcp.api.server.domain.ResourceContents convertResourceContents(Object result, String uri) {
        if (result instanceof dev.tachyonmcp.api.server.domain.ResourceContents rc) return rc;
        if (result instanceof String s) {
            return TextResourceContents.of(uri, s, null);
        }
        if (result instanceof byte[] bytes) {
            return BlobResourceContents.of(uri, bytes, null);
        }
        return TextResourceContents.of(uri, result.toString(), null);
    }

    private void registerResourceTemplates(
            Object instance,
            Method method,
            AnnotationRegistrationContext context,
            PayloadSerializer serializer,
            PayloadDeserializer deserializer) {
        org.mcpjava.server.resources.ResourceTemplate annotation =
                method.getAnnotation(org.mcpjava.server.resources.ResourceTemplate.class);
        if (annotation == null) return;

        for (Parameter param : method.getParameters()) {
            if (InteractionContext.class.isAssignableFrom(param.getType())) continue;
            AnnotationInvocationSupport.requireBindable(param, method);
        }

        String name = resolveName(annotation.name(), method);
        String description = annotation.description().isEmpty() ? null : annotation.description();
        String title = annotation.title().isEmpty() ? null : annotation.title();
        String mimeType = annotation.mimeType().isEmpty() ? null : annotation.mimeType();

        ResourceTemplateDescriptor descriptor = ResourceTemplateDescriptor.builder()
                .name(name)
                .uriTemplate(annotation.uriTemplate())
                .description(description)
                .title(title)
                .mimeType(mimeType)
                .build();

        ResourceFn fn = (ctx, req) -> invokeResourceTemplate(instance, method, ctx, req, serializer, deserializer);
        context.resources().registerTemplate(descriptor, fn);
    }

    private dev.tachyonmcp.api.server.domain.ResourceContents invokeResourceTemplate(
            Object instance,
            Method method,
            InteractionContext ctx,
            ResourceRequest req,
            PayloadSerializer serializer,
            PayloadDeserializer deserializer)
            throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        req.params().forEach((name, value) -> values.put(name, value.scalarValue()));
        Object[] args = resolveArgs(
                method, ctx, values, org.mcpjava.server.resources.ResourceTemplateArg.class, serializer, deserializer);
        Object result = invoke(method, instance, args);
        return convertResourceContents(result, req.uri());
    }

    private void registerPrompts(
            Object instance,
            Method method,
            AnnotationRegistrationContext context,
            PayloadSerializer serializer,
            PayloadDeserializer deserializer) {
        org.mcpjava.server.prompts.Prompt annotation = method.getAnnotation(org.mcpjava.server.prompts.Prompt.class);
        if (annotation == null) return;

        List<PromptArgument> arguments = buildPromptArguments(method);

        String name = resolveName(annotation.name(), method);
        String description = annotation.description().isEmpty() ? null : annotation.description();
        String title = annotation.title().isEmpty() ? null : annotation.title();

        PromptDescriptor descriptor = PromptDescriptor.builder()
                .name(name)
                .description(description)
                .title(title)
                .arguments(arguments)
                .build();

        PromptFn fn = (ctx, req) -> invokePrompt(instance, method, ctx, req, serializer, deserializer);
        context.prompts().register(descriptor, fn);
    }

    private List<PromptArgument> buildPromptArguments(Method method) {
        List<PromptArgument> args = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            if (InteractionContext.class.isAssignableFrom(param.getType())) continue;
            AnnotationInvocationSupport.requireBindable(param, method);

            org.mcpjava.server.prompts.PromptArg ann = param.getAnnotation(org.mcpjava.server.prompts.PromptArg.class);
            String paramName = resolveParamName(param, org.mcpjava.server.prompts.PromptArg.class);
            String desc = (ann != null && !ann.description().isEmpty()) ? ann.description() : null;
            String paramTitle = (ann != null && !ann.title().isEmpty()) ? ann.title() : null;
            Boolean required = (ann != null) ? ann.required() : true;
            args.add(PromptArgument.builder()
                    .name(paramName)
                    .description(desc)
                    .title(paramTitle)
                    .required(required)
                    .build());
        }
        return args;
    }

    private PromptResult invokePrompt(
            Object instance,
            Method method,
            InteractionContext ctx,
            PromptRequest req,
            PayloadSerializer serializer,
            PayloadDeserializer deserializer)
            throws Exception {
        Object[] args = resolveArgs(
                method,
                ctx,
                req.arguments().asMap(),
                org.mcpjava.server.prompts.PromptArg.class,
                serializer,
                deserializer);
        Object result = invoke(method, instance, args);
        return convertPromptResult(result);
    }

    private PromptResult convertPromptResult(Object result) {
        if (result instanceof PromptResult pr) return pr;
        if (result instanceof String s) {
            return PromptResult.messages(List.of(PromptMessage.user(s)));
        }
        if (result instanceof PromptMessage pm) {
            return PromptResult.messages(List.of(pm));
        }
        if (result instanceof List<?> list) {
            List<PromptMessage> messages = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof PromptMessage pm) {
                    messages.add(pm);
                } else {
                    messages.add(PromptMessage.user(item.toString()));
                }
            }
            return PromptResult.messages(messages);
        }
        return PromptResult.messages(List.of(PromptMessage.user(result.toString())));
    }

    private static String resolveName(String annotationValue, Method method) {
        if (ELEMENT_NAME.equals(annotationValue) || annotationValue.isEmpty()) {
            return method.getName();
        }
        return annotationValue;
    }

    private static <A extends Annotation> String resolveParamName(Parameter param, Class<A> annotationType) {
        A ann = param.getAnnotation(annotationType);
        if (ann != null) {
            String name = getAnnotationName(ann);
            if (!ELEMENT_NAME.equals(name) && !name.isEmpty()) {
                return name;
            }
        }
        return param.getName();
    }

    private static <A extends Annotation> String getAnnotationName(A ann) {
        String name = getStringAttribute(ann, "name");
        return name != null ? name : "";
    }

    private JsonSchema buildInputSchema(Method method, Class<? extends Annotation> argAnnotation) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : method.getParameters()) {
            if (InteractionContext.class.isAssignableFrom(param.getType())) continue;
            AnnotationInvocationSupport.requireBindable(param, method);

            String paramName = resolveParamName(param, argAnnotation);
            String jsonType = AnnotationInvocationSupport.jsonSchemaType(param.getParameterizedType());

            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", jsonType);

            Annotation ann = param.getAnnotation(argAnnotation);
            String description = getStringAttribute(ann, "description");
            if (description != null && !description.isEmpty()) {
                prop.put("description", description);
            }

            properties.put(paramName, prop);

            boolean isRequired = true;
            if (ann != null) {
                Boolean requiredVal = getBooleanAttribute(ann, "required");
                if (requiredVal != null) isRequired = requiredVal;
                String defaultVal = getStringAttribute(ann, "defaultValue");
                if (defaultVal != null && !defaultVal.isEmpty()) isRequired = false;
            }
            if (AnnotationInvocationSupport.isOptionalType(param.getParameterizedType())) {
                isRequired = false;
            }
            if (isRequired) required.add(paramName);
        }

        return AnnotationInvocationSupport.inputSchema(properties, required);
    }

    /** Reflectively reads a {@link String}-typed annotation attribute, or {@code null} if absent/not a String. */
    private static @Nullable String getStringAttribute(@Nullable Annotation annotation, String attributeName) {
        Object val = getAnnotationAttribute(annotation, attributeName);
        return val instanceof String s ? s : null;
    }

    /** Reflectively reads a {@code boolean}-typed annotation attribute, or {@code null} if absent/not a boolean. */
    private static @Nullable Boolean getBooleanAttribute(@Nullable Annotation annotation, String attributeName) {
        Object val = getAnnotationAttribute(annotation, attributeName);
        return val instanceof Boolean b ? b : null;
    }

    private static @Nullable Object getAnnotationAttribute(@Nullable Annotation annotation, String attributeName) {
        if (annotation == null) return null;
        try {
            Method m = annotation.annotationType().getMethod(attributeName);
            return m.invoke(annotation);
        } catch (Exception e) {
            return null;
        }
    }
}
