/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.annotations.spring.ai;

import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.ContentBlock;
import dev.tachyonmcp.api.server.domain.PromptArgument;
import dev.tachyonmcp.api.server.domain.PromptMessage;
import dev.tachyonmcp.api.server.domain.ResourceContents;
import dev.tachyonmcp.api.server.domain.TextResourceContents;
import dev.tachyonmcp.api.server.domain.ToolAnnotations;
import dev.tachyonmcp.api.server.features.annotations.AnnotationProvider;
import dev.tachyonmcp.api.server.features.annotations.AnnotationRegistrationContext;
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.api.server.features.prompts.PromptFn;
import dev.tachyonmcp.api.server.features.prompts.PromptResult;
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.api.server.features.resources.ResourceFn;
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor;
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
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

/**
 * {@link AnnotationProvider} that discovers Spring AI MCP annotations
 * ({@code @McpTool}, {@code @McpResource}, {@code @McpPrompt}) on an annotated instance and
 * registers the corresponding Tachyon descriptors.
 *
 * <p>Resource methods whose {@code uri} contains URI-template variables (e.g.
 * {@code file:///{path}}) are registered as resource templates.
 *
 * <p>Usage:
 * <pre>{@code
 * serverBuilder.annotations(ctx -> {
 *     ctx.provider(new SpringAiAnnotationProvider());
 *     ctx.register(new MyToolClass());
 * });
 * }</pre>
 */
public class SpringAiAnnotationProvider implements AnnotationProvider {

    private static final List<String> INJECTED_CONTEXT_TYPES = List.of(
            "org.springframework.ai.mcp.annotation.context.McpSyncRequestContext",
            "org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext",
            "org.springframework.ai.mcp.annotation.context.MetaProvider");

    @Override
    public void register(Object instance, AnnotationRegistrationContext context) {
        Class<?> clazz = instance.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            registerTool(instance, method, context);
            registerResource(instance, method, context);
            registerPrompt(instance, method, context);
        }
    }

    private void registerTool(Object instance, Method method, AnnotationRegistrationContext context) {
        McpTool annotation = method.getAnnotation(McpTool.class);
        if (annotation == null) return;

        ToolDescriptor descriptor = ToolDescriptor.builder()
                .name(resolveName(annotation.name(), method))
                .description(blankToNull(annotation.description()))
                .title(blankToNull(annotation.title()))
                .inputSchema(buildInputSchema(method))
                .annotations(mapToolAnnotations(annotation))
                .build();

        ToolFn fn = (ctx, req) -> invokeTool(instance, method, ctx, req);
        context.tools().register(descriptor, fn);
    }

    private static ToolAnnotations mapToolAnnotations(McpTool annotation) {
        McpTool.McpAnnotations ann = annotation.annotations();
        return ToolAnnotations.builder()
                .title(blankToNull(ann.title()))
                .readOnlyHint(ann.readOnlyHint())
                .destructiveHint(ann.destructiveHint())
                .idempotentHint(ann.idempotentHint())
                .openWorldHint(ann.openWorldHint())
                .build();
    }

    private ToolResult invokeTool(Object instance, Method method, InteractionContext ctx, ToolRequest req)
            throws Exception {
        Object result = invoke(
                method, instance, resolveArgs(method, ctx, req.arguments().asMap()));
        return convertToolResult(result);
    }

    /**
     * Invokes {@code method} on {@code instance}, unwrapping {@link InvocationTargetException} to
     * its cause. Must call {@link Method#invoke} directly (not via a shared helper in another
     * package) since it's caller-sensitive: reflective access to a non-public annotated method
     * depends on the package of the code that calls it.
     */
    private static Object invoke(Method method, Object instance, Object... args) throws Exception {
        try {
            return method.invoke(instance, args);
        } catch (InvocationTargetException e) {
            throw AnnotationInvocationSupport.unwrap(e);
        }
    }

    private void registerResource(Object instance, Method method, AnnotationRegistrationContext context) {
        McpResource annotation = method.getAnnotation(McpResource.class);
        if (annotation == null) return;

        String uri = annotation.uri();
        if (uri.isBlank()) {
            throw new IllegalArgumentException("@McpResource on " + method + " requires a non-blank uri");
        }

        ResourceFn staticFn = (ctx, req) ->
                convertResourceContents(invoke(method, instance, resolveArgs(method, ctx, Map.of())), req.uri());

        if (uri.contains("{")) {
            ResourceTemplateDescriptor descriptor = ResourceTemplateDescriptor.builder()
                    .name(resolveName(annotation.name(), method))
                    .uriTemplate(uri)
                    .description(blankToNull(annotation.description()))
                    .title(blankToNull(annotation.title()))
                    .mimeType(mimeTypeOrNull(annotation.mimeType()))
                    .build();
            context.resources().registerTemplate(descriptor, (ctx, req) -> {
                Map<String, Object> values = new LinkedHashMap<>();
                req.params().forEach((name, value) -> values.put(name, value.scalarValue()));
                return convertResourceContents(invoke(method, instance, resolveArgs(method, ctx, values)), req.uri());
            });
        } else {
            ResourceDescriptor descriptor = ResourceDescriptor.builder()
                    .name(resolveName(annotation.name(), method))
                    .uri(uri)
                    .description(blankToNull(annotation.description()))
                    .title(blankToNull(annotation.title()))
                    .mimeType(mimeTypeOrNull(annotation.mimeType()))
                    .build();
            context.resources().register(descriptor, staticFn);
        }
    }

    private void registerPrompt(Object instance, Method method, AnnotationRegistrationContext context) {
        McpPrompt annotation = method.getAnnotation(McpPrompt.class);
        if (annotation == null) return;

        PromptDescriptor.Builder builder = PromptDescriptor.builder()
                .name(resolveName(annotation.name(), method))
                .description(blankToNull(annotation.description()))
                .title(blankToNull(annotation.title()));
        for (Parameter param : method.getParameters()) {
            if (isInjectedParam(param.getType())) continue;
            builder.addArguments(PromptArgument.of(param.getName(), null, null, true));
        }
        PromptDescriptor descriptor = builder.build();

        PromptFn fn = (ctx, req) -> convertPromptResult(invoke(
                method, instance, resolveArgs(method, ctx, req.arguments().asMap())));
        context.prompts().register(descriptor, fn);
    }

    private Object[] resolveArgs(Method method, InteractionContext ctx, @Nullable Map<String, Object> values) {
        Parameter[] params = method.getParameters();
        Object[] resolved = new Object[params.length];
        Map<String, Object> map = values != null ? values : Map.of();
        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            if (InteractionContext.class.isAssignableFrom(type)) {
                resolved[i] = ctx;
            } else if (isInjectedParam(type)) {
                resolved[i] = null;
            } else {
                resolved[i] = AnnotationInvocationSupport.coerce(map.get(params[i].getName()), type);
            }
        }
        return resolved;
    }

    private static boolean isInjectedParam(Class<?> type) {
        return INJECTED_CONTEXT_TYPES.contains(type.getName());
    }

    private static ToolResult convertToolResult(Object result) {
        return switch (result) {
            case null -> ToolResult.empty();
            case ToolResult tr -> tr;
            case String s -> ToolResult.text(s);
            case ContentBlock cb -> ToolResult.content(cb);
            default -> ToolResult.text(result.toString());
        };
    }

    private static ResourceContents convertResourceContents(Object result, String uri) {
        if (result instanceof ResourceContents rc) {
            return rc;
        }
        if (result instanceof String s) {
            return TextResourceContents.of(uri, s, null);
        }
        return TextResourceContents.of(uri, result.toString(), null);
    }

    private static PromptResult convertPromptResult(Object result) {
        if (result instanceof PromptResult pr) {
            return pr;
        }
        if (result instanceof PromptMessage pm) {
            return PromptResult.messages(List.of(pm));
        }
        if (result instanceof String s) {
            return PromptResult.messages(List.of(PromptMessage.user(s)));
        }
        return PromptResult.messages(List.of(PromptMessage.user(result.toString())));
    }

    private JsonSchema buildInputSchema(Method method) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : method.getParameters()) {
            if (isInjectedParam(param.getType()) || InteractionContext.class.isAssignableFrom(param.getType())) {
                continue;
            }

            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", AnnotationInvocationSupport.jsonSchemaType(param.getType()));

            McpToolParam ann = param.getAnnotation(McpToolParam.class);
            boolean isRequired = true;
            if (ann != null) {
                if (!ann.description().isEmpty()) prop.put("description", ann.description());
                isRequired = ann.required();
            }
            if (!isRequired || isOptionalType(param.getType())) {
                isRequired = false;
            }

            properties.put(param.getName(), prop);
            if (isRequired) required.add(param.getName());
        }

        return AnnotationInvocationSupport.inputSchema(properties, required);
    }

    private static boolean isOptionalType(Class<?> type) {
        return Optional.class.isAssignableFrom(type)
                || OptionalInt.class.isAssignableFrom(type)
                || OptionalLong.class.isAssignableFrom(type)
                || OptionalDouble.class.isAssignableFrom(type);
    }

    private static String resolveName(String annotationValue, Method method) {
        return blankOrEmpty(annotationValue) ? method.getName() : annotationValue;
    }

    private static @Nullable String blankToNull(String value) {
        return blankOrEmpty(value) ? null : value;
    }

    private static @Nullable String mimeTypeOrNull(String mimeType) {
        return blankOrEmpty(mimeType) ? null : mimeType;
    }

    private static boolean blankOrEmpty(String value) {
        return value.isBlank();
    }
}
