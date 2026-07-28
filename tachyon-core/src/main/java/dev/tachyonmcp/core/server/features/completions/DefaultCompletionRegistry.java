/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.completions;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.config.Mode;
import dev.tachyonmcp.api.server.domain.InvalidArgumentException;
import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.features.HandlerFutures;
import dev.tachyonmcp.api.server.features.completions.AsyncCompletionFn;
import dev.tachyonmcp.api.server.features.completions.CompletionFn;
import dev.tachyonmcp.api.server.features.completions.CompletionRequest;
import dev.tachyonmcp.api.server.features.completions.Completions;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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

    private static final Logger logger = LoggerFactory.getLogger(DefaultCompletionRegistry.class);

    private final ConcurrentHashMap<String, AsyncCompletionFn> promptFns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AsyncCompletionFn> resourceFns = new ConcurrentHashMap<>();
    private final Mode mode;

    public DefaultCompletionRegistry() {
        this(Mode.AUTO);
    }

    public DefaultCompletionRegistry(Mode mode) {
        this.mode = mode;
    }

    @Override
    public Completions registerForPrompt(String promptName, CompletionFn fn) {
        return registerForPromptAsync(promptName, (context, request) -> {
            HandlerFutures.assumeVirtualThread();
            return HandlerFutures.completedOrFailed(() -> fn.apply(context, request));
        });
    }

    @Override
    public Completions registerForPromptAsync(String promptName, AsyncCompletionFn fn) {
        if (mode == Mode.OFF) {
            logger.debug("Completion '{}' not registered: completions capability is OFF", promptName);
            return this;
        }
        promptFns.put(promptName, fn);

        return this;
    }

    @Override
    public Completions registerForResource(String uriOrTemplate, CompletionFn fn) {
        return registerForResourceAsync(uriOrTemplate, (context, request) -> {
            HandlerFutures.assumeVirtualThread();
            return HandlerFutures.completedOrFailed(() -> fn.apply(context, request));
        });
    }

    @Override
    public Completions registerForResourceAsync(String uriOrTemplate, AsyncCompletionFn fn) {
        if (mode == Mode.OFF) {
            logger.debug("Completion for '{}' not registered: completions capability is OFF", uriOrTemplate);
            return this;
        }
        resourceFns.put(uriOrTemplate, fn);
        return this;
    }

    @Override
    public boolean unregisterForPrompt(String promptName) {
        return promptFns.remove(promptName) != null;
    }

    @Override
    public boolean unregisterForResource(String uriOrTemplate) {
        return resourceFns.remove(uriOrTemplate) != null;
    }

    private Optional<AsyncCompletionFn> findForPrompt(String promptName) {
        return Optional.ofNullable(promptFns.get(promptName));
    }

    private Optional<AsyncCompletionFn> findForResource(String uriOrTemplate) {
        return Optional.ofNullable(resourceFns.get(uriOrTemplate));
    }

    /**
     * Returns whether no completion handlers are registered.
     */
    @Override
    public boolean isEmpty() {
        return promptFns.isEmpty() && resourceFns.isEmpty();
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

        /** Compatibility fallback for callers invoking the blocking SPI method directly. */
        @Override
        public Object handle(DispatchContext context, Object params) throws Exception {
            return HandlerFutures.joinInterruptibly(handleAsync(context, params));
        }

        /** Runs on the dispatcher's virtual thread; composes the handler's stage without blocking it. */
        @Override
        public CompletionStage<Object> handleAsync(DispatchContext context, Object params) {
            var paramsMap = params instanceof Map<?, ?> m ? m : Map.of();

            if (!(paramsMap.get("ref") instanceof Map<?, ?> ref)) {
                return CompletableFuture.completedFuture(
                        ServerErrors.invalidParams("Missing or invalid ref parameter"));
            }
            if (!(paramsMap.get("argument") instanceof Map<?, ?> argument)) {
                return CompletableFuture.completedFuture(
                        ServerErrors.invalidParams("Missing or invalid argument parameter"));
            }
            if (!(argument.get("name") instanceof String argumentName)
                    || !(argument.get("value") instanceof String argumentValue)) {
                return CompletableFuture.completedFuture(
                        ServerErrors.invalidParams("argument.name and argument.value are required"));
            }

            var refType = ref.get("type");
            Optional<AsyncCompletionFn> fn;
            String promptName;
            String uri;
            if ("ref/prompt".equals(refType)) {
                if (!(ref.get("name") instanceof String pn)) {
                    return CompletableFuture.completedFuture(
                            ServerErrors.invalidParams("ref.name is required for ref/prompt"));
                }
                promptName = pn;
                fn = registry.findForPrompt(promptName);
            } else if ("ref/resource".equals(refType)) {
                if (!(ref.get("uri") instanceof String u)) {
                    return CompletableFuture.completedFuture(
                            ServerErrors.invalidParams("ref.uri is required for ref/resource"));
                }
                uri = u;
                fn = registry.findForResource(uri);
            } else {
                return CompletableFuture.completedFuture(ServerErrors.invalidParams("Unknown ref.type: " + refType));
            }

            if (fn.isEmpty()) {
                return CompletableFuture.completedFuture(
                        context.responseMapper().completeResult(List.of(), null, false));
            }

            var request =
                    CompletionRequest.of(argumentName, argumentValue, resolvedArguments(paramsMap.get("context")));
            // invokeAndMap: guards the synchronous-throw/null-stage cases, then re-anchors onto a
            // tachyon- virtual thread only when the handler's stage is still pending, so a
            // foreign completer thread never leaks into response mapping, without adding an
            // executor hop to the common already-resolved case.
            return HandlerFutures.invokeAndMap(
                    "Completion handler for ref.type '" + refType + "' returned a null CompletionStage",
                    () -> fn.get().apply(context, request),
                    context.engine().executor(),
                    (result, cause) -> {
                        if (cause != null) {
                            if (cause instanceof InvalidArgumentException e) {
                                return ServerErrors.invalidParams(
                                        "invalid argument '" + e.argName() + "': " + e.getMessage());
                            }
                            var error = ServerErrors.fromUnhandledException(cause, "Completion handler failed");
                            if (error.kind() == ServerError.Kind.INVALID_PARAMS) {
                                logger.debug("Completion handler rejected invalid params for ref.type '{}'", refType);
                            } else {
                                logger.error("Completion handler error for ref.type '{}'", refType, cause);
                            }
                            return error;
                        }
                        var values = result.values();
                        var hasMore = result.hasMore();
                        if (values.size() > MAX_VALUES) {
                            values = values.subList(0, MAX_VALUES);
                            hasMore = true;
                        }
                        return context.responseMapper().completeResult(values, result.total(), hasMore);
                    });
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
