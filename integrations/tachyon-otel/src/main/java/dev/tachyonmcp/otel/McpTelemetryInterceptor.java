/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.otel;

import static dev.tachyonmcp.otel.McpAttributes.EXECUTE_TOOL;
import static dev.tachyonmcp.otel.McpAttributes.GEN_AI_OPERATION_NAME;
import static dev.tachyonmcp.otel.McpAttributes.GEN_AI_PROMPT_NAME;
import static dev.tachyonmcp.otel.McpAttributes.GEN_AI_TOOL_CALL_ARGUMENTS;
import static dev.tachyonmcp.otel.McpAttributes.GEN_AI_TOOL_NAME;
import static dev.tachyonmcp.otel.McpAttributes.MCP_METHOD_NAME;
import static dev.tachyonmcp.otel.McpAttributes.MCP_PROTOCOL_VERSION;
import static dev.tachyonmcp.otel.McpAttributes.MCP_RESOURCE_URI;
import static dev.tachyonmcp.otel.McpAttributes.MCP_SESSION_ID;
import static dev.tachyonmcp.otel.McpAttributes.PROMPTS_GET;
import static dev.tachyonmcp.otel.McpAttributes.TOOLS_CALL;
import static dev.tachyonmcp.otel.McpAttributes.TOOL_ERROR;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.incubating.JsonrpcIncubatingAttributes.JSONRPC_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE;

import dev.tachyonmcp.api.server.interceptor.McpInterceptor;
import dev.tachyonmcp.api.server.interceptor.McpInvocation;
import dev.tachyonmcp.api.server.interceptor.McpOutcome;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.NetworkAttributes;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;

/**
 * Records an OpenTelemetry {@code SERVER} span and an {@code mcp.server.operation.duration}
 * measurement for every inbound MCP request and notification, following the OpenTelemetry MCP
 * semantic conventions.
 *
 * <pre>{@code
 * var server = TachyonServer.builder()
 *         .withInterceptors(McpTelemetryInterceptor.create(GlobalOpenTelemetry.get()))
 *         .build();
 * }</pre>
 *
 * <p>Attribute keys come from {@code opentelemetry-semconv-incubating} where it still maintains
 * them. The {@code mcp.*} and {@code gen_ai.*} keys it has deprecated in favour of the GenAI
 * conventions repository live in {@link McpAttributes} instead — see that class for why.
 *
 * <h2>Trace context</h2>
 *
 * <p>Spans are parented by {@link io.opentelemetry.context.Context#current()}. Under the
 * OpenTelemetry Java agent that is the Netty HTTP server span, so an incoming {@code traceparent}
 * header is already extracted and propagated across Tachyon's executor hops — MCP spans slot
 * underneath the HTTP span with no extra configuration. Without the agent they are root spans: this
 * interceptor does not read HTTP headers itself.
 *
 * <h2>Payloads</h2>
 *
 * <p>Tool arguments are <b>not</b> recorded by default — they routinely carry credentials and
 * personal data, which is why the conventions mark them opt-in. Enable deliberately with {@link
 * Builder#recordPayloads(boolean)}. Tool <em>results</em> are never recorded: rendering a {@code
 * ToolResult} as a span attribute would mean serializing a domain object outside the server's
 * configured payload serializer.
 *
 * @author Konstantin Pavlov
 * @see <a href="https://github.com/open-telemetry/semantic-conventions-genai/tree/main/model/mcp">
 *     semantic-conventions-genai / model / mcp</a>
 */
public final class McpTelemetryInterceptor implements McpInterceptor {

    private static final String INSTRUMENTATION_NAME = "dev.tachyonmcp.otel";
    private static final String OPERATION_DURATION = "mcp.server.operation.duration";
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    /**
     * JSON-RPC codes a server returns because the <em>caller</em> sent something it could not
     * serve. The conventions say these must not count as server errors, and state the rule in terms
     * of codes — which is why the code is resolved by the dispatcher and handed to us rather than
     * re-derived here from {@code ServerError.Kind}, whose mapping differs between MCP versions.
     */
    private static final Set<Integer> CALLER_FAULT_CODES = Set.of(-32700, -32600, -32601, -32602, -32002);

    private final Tracer tracer;
    private final DoubleHistogram operationDuration;
    private final boolean recordPayloads;

    private McpTelemetryInterceptor(OpenTelemetry openTelemetry, boolean recordPayloads) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
        this.operationDuration = openTelemetry
                .getMeter(INSTRUMENTATION_NAME)
                .histogramBuilder(OPERATION_DURATION)
                .setUnit("s")
                .setDescription("MCP request or notification duration as observed on the receiver")
                .build();
        this.recordPayloads = recordPayloads;
    }

    /**
     * Creates an interceptor with payload recording disabled.
     *
     * @param openTelemetry the OpenTelemetry instance supplying the tracer and meter
     * @return a new interceptor
     */
    public static McpTelemetryInterceptor create(OpenTelemetry openTelemetry) {
        return builder(openTelemetry).build();
    }

    /**
     * Returns a builder for an interceptor.
     *
     * @param openTelemetry the OpenTelemetry instance supplying the tracer and meter
     * @return a new builder
     */
    public static Builder builder(OpenTelemetry openTelemetry) {
        return new Builder(openTelemetry);
    }

    @Override
    public McpOutcome intercept(McpInvocation invocation, Chain chain) {
        // Built once: the metric keeps only the low-cardinality half, the span gets all of it.
        // Session and request ids must never reach the histogram -- one time series per request.
        final var shared = sharedAttributes(invocation);
        final var span = tracer.spanBuilder(spanName(invocation))
                .setSpanKind(SpanKind.SERVER)
                .setAllAttributes(shared)
                .setAllAttributes(spanOnlyAttributes(invocation))
                .startSpan();
        final var startNanos = System.nanoTime();
        // The scope encloses the handler, so a span the handler starts is a child of this one.
        try (var ignored = span.makeCurrent()) {
            final var outcome = chain.proceed();
            end(span, shared, invocation, outcome, null, startNanos);
            return outcome;
        } catch (RuntimeException | Error e) {
            end(span, shared, invocation, null, e, startNanos);
            throw e;
        }
    }

    /**
     * Builds the span name as {@code {method} {target}}, falling back to the bare method when the
     * operation addresses no low-cardinality target. The resource URI is deliberately excluded from
     * the name — it is high-cardinality.
     */
    private static String spanName(McpInvocation invocation) {
        final var target = target(invocation);
        return target == null ? invocation.method() : invocation.method() + " " + target;
    }

    /**
     * The registered tool or prompt this operation addresses, or {@code null} for every other
     * method.
     *
     * <p>Gated by method on purpose. {@link McpInvocation#targetName()} reports the top-level {@code
     * name} parameter of <em>any</em> method, including custom extension methods whose {@code name}
     * may be unbounded — a user id, a document key. Only tool and prompt names are the bounded
     * values the conventions mean by "target", and only they are safe in a span name or a metric
     * dimension.
     */
    private static @Nullable String target(McpInvocation invocation) {
        return switch (invocation.method()) {
            case TOOLS_CALL, PROMPTS_GET -> invocation.targetName().orElse(null);
            default -> null;
        };
    }

    private static boolean isToolCall(McpInvocation invocation) {
        return TOOLS_CALL.equals(invocation.method());
    }

    /** Attributes shared by the span and the duration histogram. All low-cardinality. */
    private static Attributes sharedAttributes(McpInvocation invocation) {
        final var builder = Attributes.builder()
                .put(MCP_METHOD_NAME, invocation.method())
                .put(MCP_PROTOCOL_VERSION, invocation.protocolVersion())
                .put(NETWORK_TRANSPORT, NetworkAttributes.NetworkTransportValues.TCP)
                .put(NETWORK_PROTOCOL_NAME, "http");
        final var target = target(invocation);
        if (target != null) {
            if (isToolCall(invocation)) {
                builder.put(GEN_AI_TOOL_NAME, target).put(GEN_AI_OPERATION_NAME, EXECUTE_TOOL);
            } else {
                builder.put(GEN_AI_PROMPT_NAME, target);
            }
        }
        return builder.build();
    }

    /** Identifying attributes that belong on a span but would explode a metric's cardinality. */
    private static Attributes spanOnlyAttributes(McpInvocation invocation) {
        final var builder = Attributes.builder();
        final var requestId = invocation.requestId();
        if (requestId != null) {
            builder.put(JSONRPC_REQUEST_ID, requestId.toString());
        }
        final var sessionId = invocation.sessionId();
        if (sessionId != null) {
            builder.put(MCP_SESSION_ID, sessionId);
        }
        invocation.resourceUri().ifPresent(uri -> builder.put(MCP_RESOURCE_URI, uri));
        return builder.build();
    }

    private void end(
            Span span,
            Attributes shared,
            McpInvocation invocation,
            @Nullable McpOutcome outcome,
            @Nullable Throwable error,
            long startNanos) {
        final var metricAttributes = Attributes.builder().putAll(shared);
        if (error != null) {
            final var unwrapped = unwrap(error);
            span.recordException(unwrapped);
            span.setStatus(StatusCode.ERROR, Objects.toString(unwrapped.getMessage(), ""));
            classify(span, metricAttributes, unwrapped.getClass().getName());
        } else if (outcome != null) {
            recordOutcome(span, metricAttributes, outcome);
            if (recordPayloads && isToolCall(invocation)) {
                invocation.params().ifPresent(params -> span.setAttribute(GEN_AI_TOOL_CALL_ARGUMENTS, params.json()));
            }
        }
        span.end();
        operationDuration.record((System.nanoTime() - startNanos) / NANOS_PER_SECOND, metricAttributes.build());
    }

    /**
     * Applies the conventions' error classification to an outcome the dispatcher already resolved
     * against the negotiated protocol version. A code the caller caused — a bad request, an unknown
     * method or resource — is reported through {@code rpc.response.status_code} but leaves the span
     * status {@code UNSET}: the server behaved correctly.
     */
    private static void recordOutcome(Span span, AttributesBuilder metricAttributes, McpOutcome outcome) {
        switch (outcome) {
            case McpOutcome.Success ignored -> {}
            case McpOutcome.PayloadFailure ignored -> classify(span, metricAttributes, TOOL_ERROR);
            case McpOutcome.Failure(var error, var jsonRpcCode, var cause) -> {
                final var code = String.valueOf(jsonRpcCode);
                span.setAttribute(RPC_RESPONSE_STATUS_CODE, code);
                metricAttributes.put(RPC_RESPONSE_STATUS_CODE, code);
                classify(span, metricAttributes, error.kind().name());
                if (cause != null) {
                    // A handler or inner interceptor threw; the dispatcher folded it into the
                    // outcome, so this is the only place the stack trace is still available.
                    span.recordException(unwrap(cause));
                }
                if (!CALLER_FAULT_CODES.contains(jsonRpcCode)) {
                    span.setStatus(StatusCode.ERROR, error.message());
                }
            }
        }
    }

    /** Strips the {@code CompletionException} an async handler's failure may still be wrapped in. */
    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : error;
    }

    private static void classify(Span span, AttributesBuilder metricAttributes, String errorType) {
        span.setAttribute(ERROR_TYPE, errorType);
        metricAttributes.put(ERROR_TYPE, errorType);
    }

    /** Builder for {@link McpTelemetryInterceptor}. */
    public static final class Builder {

        private final OpenTelemetry openTelemetry;
        private boolean recordPayloads;

        private Builder(OpenTelemetry openTelemetry) {
            this.openTelemetry = Objects.requireNonNull(openTelemetry, "openTelemetry cannot be null");
        }

        /**
         * Records {@code tools/call} arguments as the {@code gen_ai.tool.call.arguments} span
         * attribute.
         *
         * <p>Off by default. Tool arguments frequently carry credentials and personal data, and
         * spans are commonly exported to third-party backends — enable only where that is
         * acceptable.
         *
         * @param recordPayloads whether to record tool call arguments
         * @return this builder
         */
        public Builder recordPayloads(boolean recordPayloads) {
            this.recordPayloads = recordPayloads;
            return this;
        }

        /**
         * Builds the interceptor.
         *
         * @return a new interceptor
         */
        public McpTelemetryInterceptor build() {
            return new McpTelemetryInterceptor(openTelemetry, recordPayloads);
        }
    }
}
