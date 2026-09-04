/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.otel;

import io.opentelemetry.api.common.AttributeKey;

/**
 * The MCP and GenAI semantic-convention keys that {@code opentelemetry-semconv-incubating} has
 * deprecated — <em>"Moved to the OpenTelemetry GenAI semantic conventions repository"</em> — and
 * the values that go with them.
 *
 * <p>They are declared here rather than imported because the move has no destination yet: <a
 * href="https://github.com/open-telemetry/semantic-conventions-genai">semantic-conventions-genai</a>
 * publishes no Java artifact, so {@code io.opentelemetry.semconv} still ships only {@code
 * opentelemetry-semconv} and {@code opentelemetry-semconv-incubating}. Importing the deprecated
 * constants would mean warnings today and a broken build the release OTel removes them; copying
 * them is what OpenTelemetry's own instrumentation libraries do for unstable conventions.
 *
 * <p>🔥 Delete this class and import the generated constants once a GenAI semconv Java artifact
 * ships. The literals below are the contract — keep them verbatim.
 *
 * <p>Everything still maintained in the semconv artifacts is imported normally: {@code
 * jsonrpc.request.id}, {@code rpc.response.status_code}, {@code error.type} and the {@code
 * network.*} keys.
 *
 * @see <a href="https://github.com/open-telemetry/semantic-conventions-genai/tree/main/model/mcp">
 *     semantic-conventions-genai / model / mcp</a>
 */
final class McpAttributes {

    static final AttributeKey<String> MCP_METHOD_NAME = AttributeKey.stringKey("mcp.method.name");
    static final AttributeKey<String> MCP_SESSION_ID = AttributeKey.stringKey("mcp.session.id");
    static final AttributeKey<String> MCP_PROTOCOL_VERSION = AttributeKey.stringKey("mcp.protocol.version");
    static final AttributeKey<String> MCP_RESOURCE_URI = AttributeKey.stringKey("mcp.resource.uri");

    static final AttributeKey<String> GEN_AI_TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    static final AttributeKey<String> GEN_AI_PROMPT_NAME = AttributeKey.stringKey("gen_ai.prompt.name");
    static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");

    /**
     * Never generated into the semconv artifact at all — it exists only in the GenAI conventions
     * repository, and is opt-in there because tool arguments carry credentials and personal data.
     */
    static final AttributeKey<String> GEN_AI_TOOL_CALL_ARGUMENTS = AttributeKey.stringKey("gen_ai.tool.call.arguments");

    /** {@code mcp.method.name} values this instrumentation branches on. */
    static final String TOOLS_CALL = "tools/call";

    static final String PROMPTS_GET = "prompts/get";

    /** {@code gen_ai.operation.name} value for a tool call. */
    static final String EXECUTE_TOOL = "execute_tool";

    /** {@code error.type} value for a {@code CallToolResult} carrying {@code isError: true}. */
    static final String TOOL_ERROR = "tool_error";

    private McpAttributes() {}
}
