/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.completions;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.features.HandlerFutures;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry of completion handlers, keyed independently by prompt name and by resource
 * URI/template.
 */
@InternalApi
public class DefaultCompletionRegistry implements CompletionRegistry {

    private final ConcurrentHashMap<String, CompletionHandler> promptHandlers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletionHandler> resourceHandlers = new ConcurrentHashMap<>();

    @Override
    public CompletionRegistry registerForPrompt(String promptName, CompletionHandler handler) {
        promptHandlers.put(promptName, handler);
        return this;
    }

    @Override
    public CompletionRegistry registerForResource(String uriOrTemplate, CompletionHandler handler) {
        resourceHandlers.put(uriOrTemplate, handler);
        return this;
    }

    @Override
    public boolean unregisterForPrompt(String promptName) {
        return promptHandlers.remove(promptName) != null;
    }

    @Override
    public boolean unregisterForResource(String uriOrTemplate) {
        return resourceHandlers.remove(uriOrTemplate) != null;
    }

    @Override
    public Optional<CompletionHandler> findForPrompt(String promptName) {
        return Optional.ofNullable(promptHandlers.get(promptName));
    }

    @Override
    public Optional<CompletionHandler> findForResource(String uriOrTemplate) {
        return Optional.ofNullable(resourceHandlers.get(uriOrTemplate));
    }

    /**
     * Returns whether no completion handlers are registered.
     */
    public boolean isEmpty() {
        return promptHandlers.isEmpty() && resourceHandlers.isEmpty();
    }

    /**
     * Registers the RPC handler for {@code completion/complete}.
     *
     * @param registry the map to populate with the completion RPC handler
     */
    public void registerHandlers(Map<String, RpcMethodHandler> registry) {
        registry.put("completion/complete", new CompletionCompleteHandler(this));
    }

    private record CompletionCompleteHandler(DefaultCompletionRegistry registry) implements RpcMethodHandler {

        private static final Logger logger = LoggerFactory.getLogger(CompletionCompleteHandler.class);

        /** MCP spec: servers return at most 100 completion values per response. */
        private static final int MAX_VALUES = 100;

        @Override
        public String method() {
            return "completion/complete";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var paramsMap = params instanceof Map<?, ?> m ? m : Map.of();

            if (!(paramsMap.get("ref") instanceof Map<?, ?> ref)) {
                return JsonRpcErrors.invalidParams("Missing or invalid ref parameter");
            }
            if (!(paramsMap.get("argument") instanceof Map<?, ?> argument)) {
                return JsonRpcErrors.invalidParams("Missing or invalid argument parameter");
            }
            if (!(argument.get("name") instanceof String argumentName)
                    || !(argument.get("value") instanceof String argumentValue)) {
                return JsonRpcErrors.invalidParams("argument.name and argument.value are required");
            }

            Optional<CompletionHandler> handler;
            var refType = ref.get("type");
            if ("ref/prompt".equals(refType)) {
                if (!(ref.get("name") instanceof String promptName)) {
                    return JsonRpcErrors.invalidParams("ref.name is required for ref/prompt");
                }
                handler = registry.findForPrompt(promptName);
            } else if ("ref/resource".equals(refType)) {
                if (!(ref.get("uri") instanceof String uri)) {
                    return JsonRpcErrors.invalidParams("ref.uri is required for ref/resource");
                }
                handler = registry.findForResource(uri);
            } else {
                return JsonRpcErrors.invalidParams("Unknown ref.type: " + refType);
            }

            if (handler.isEmpty()) {
                return context.responseMapper().completeResult(List.of(), null, false);
            }

            var request =
                    new CompletionRequest(argumentName, argumentValue, resolvedArguments(paramsMap.get("context")));
            CompletionResult result;
            try {
                result = HandlerFutures.joinInterruptibly(handler.get().handleAsync(context, request));
            } catch (Exception e) {
                logger.error("Completion handler error for ref.type '{}'", refType, e);
                return JsonRpcErrors.internalError("Completion handler failed");
            }

            var values = result.values();
            var hasMore = result.hasMore();
            if (values.size() > MAX_VALUES) {
                values = values.subList(0, MAX_VALUES);
                hasMore = true;
            }
            return context.responseMapper().completeResult(values, result.total(), hasMore);
        }

        private static Map<String, String> resolvedArguments(@Nullable Object contextObj) {
            if (!(contextObj instanceof Map<?, ?> ctxMap) || !(ctxMap.get("arguments") instanceof Map<?, ?> argsMap)) {
                return Map.of();
            }
            var resolved = new LinkedHashMap<String, String>();
            for (var entry : argsMap.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
                    resolved.put(key, value);
                }
            }
            return resolved;
        }
    }
}
