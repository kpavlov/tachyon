/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.completions;

import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.features.HandlerFutures;
import dev.tachyonmcp.api.server.features.completions.AsyncCompletionFn;
import dev.tachyonmcp.api.server.features.completions.CompletionResult;
import dev.tachyonmcp.core.protocol.ProtocolRequestMapper.CompletionReference;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JSON-RPC adapter for completion operations. */
public final class CompletionMethodHandlers {

    private CompletionMethodHandlers() {}

    public static void register(Map<String, RpcMethodHandler> handlers, DefaultCompletionRegistry registry) {
        handlers.put("completion/complete", new CompletionCompleteHandler(registry));
    }

    private record CompletionCompleteHandler(DefaultCompletionRegistry registry) implements RpcMethodHandler {

        private static final Logger logger = LoggerFactory.getLogger(CompletionCompleteHandler.class);
        private static final int MAX_VALUES = 100;

        @Override
        public String method() {
            return "completion/complete";
        }

        @Override
        public Object handle(DispatchContext context, Object params) throws Exception {
            return HandlerFutures.joinInterruptibly(handleAsync(context, params));
        }

        @Override
        public CompletionStage<Object> handleAsync(DispatchContext context, Object params) {
            var request = context.requestMapper().complete(params);
            Optional<AsyncCompletionFn> fn =
                    switch (request.reference()) {
                        case CompletionReference.Prompt prompt -> registry.findForPrompt(prompt.name());
                        case CompletionReference.Resource resource -> registry.findForResource(resource.uri());
                    };
            if (fn.isEmpty()) {
                return CompletableFuture.completedFuture(
                        context.responseMapper().completeResult(CompletionResult.empty()));
            }
            return HandlerFutures.invokeAndMap(
                    "Completion handler returned a null CompletionStage",
                    () -> fn.get().apply(context, request.request()),
                    context.engine().executor(),
                    (result, cause) -> {
                        if (cause != null) {
                            var error = ServerErrors.fromUnhandledException(cause, "Completion handler failed");
                            if (error.kind() == ServerError.Kind.INVALID_PARAMS) {
                                logger.debug("Completion handler rejected invalid params");
                            } else {
                                logger.error("Completion handler error", cause);
                            }
                            return error;
                        }
                        var values = result.values();
                        var hasMore = result.hasMore();
                        CompletionResult mappedResult = result;
                        if (values.size() > MAX_VALUES) {
                            values = values.subList(0, MAX_VALUES);
                            hasMore = true;
                            mappedResult = CompletionResult.builder()
                                    .values(values)
                                    .total(result.total())
                                    .hasMore(hasMore)
                                    .meta(result.meta())
                                    .build();
                        }
                        return context.responseMapper().completeResult(mappedResult);
                    });
        }
    }
}
