---
name: mcp-codegen
description: Generate JVM MCP Models and Jackson Streaming Codecs
---
# Generate JVM MCP Models and Jackson Streaming Codecs

## Purpose

Generate strongly typed Java records and custom Jackson Streaming API codecs from the MCP protocol specification located at:

```text
tachyon-mcp/protocol/mcp-2025-11-25.ts
```

The generated code must support the complete MCP protocol surface, including all client and server messages, requests, notifications, results, capabilities, resources, prompts, tools, roots, sampling, elicitation, logging, completion, and future protocol extensions defined in the specification.

The generator must produce deterministic output suitable for source control.

---

## Output Locations

### Models

Generate Java records under:

```text
src/generated/java/me/kpavlov/tachyon/mcp/models
```

### Codecs

Generate codecs under:

```text
src/generated/java/me/kpavlov/tachyon/mcp/codecs
```

### Registry

Generate protocol registry classes under:

```text
src/generated/java/me/kpavlov/tachyon/mcp/protocol
```

---

## Design Goals

### Required

* Java 21+
* Records only
* Immutable types
* No Lombok
* No reflection
* No Jackson databind
* No ObjectMapper serialization in production code
* Compatible with Jackson Streaming API
* Deterministic generation
* Stable class names across runs

### Forbidden

Do not generate:

```java
@JsonProperty
@JsonCreator
@JsonDeserialize
@JsonSerialize
```

Do not generate:

```java
ObjectMapper.readValue(...)
ObjectMapper.writeValue(...)
```

Do not generate mutable beans.

---

## Type Mapping

### Primitive Types

| TypeScript | Java     |
|------------|----------|
| string     | String   |
| boolean    | boolean  |
| number     | double   |
| integer    | long     |
| unknown    | JsonNode |
| null       | Void     |
| Uint8Array | byte[]   |

### Collections

| TypeScript  | Java     |
|-------------|----------|
| T[]         | List<T>  |
| Record<K,V> | Map<K,V> |

Use:

```java
java.util.List
java.util.Map
```

---

## Union Types

Generate sealed interfaces.

Example:

TypeScript:

```typescript
type RequestId = string | number;
```

Generated:

```java
public sealed interface RequestId
    permits StringRequestId, LongRequestId {
}
```

Implementations:

```java
public record StringRequestId(
    String value
) implements RequestId {
}
```

```java
public record LongRequestId(
    long value
) implements RequestId {
}
```

Never collapse unions to Object.

---

## MCP Message Hierarchy

Generate sealed hierarchy.

Root:

```java
public sealed interface McpMessage
    permits McpRequest,
            McpNotification,
            McpResponse {
}
```

Request:

```java
public sealed interface McpRequest
    extends McpMessage {
}
```

Notification:

```java
public sealed interface McpNotification
    extends McpMessage {
}
```

Response:

```java
public sealed interface McpResponse
    extends McpMessage {
}
```

All generated protocol messages must participate in the hierarchy.

---

## Request Models

Every MCP request must generate:

```java
public record ToolCallRequest(
    RequestId id,
    ToolCallParams params
) implements McpRequest {
}
```

Similar generation required for:

* InitializeRequest
* PingRequest
* CompleteRequest
* ResourcesReadRequest
* ResourcesSubscribeRequest
* PromptsGetRequest
* ToolsCallRequest
* SamplingCreateMessageRequest
* RootsListRequest
* ElicitationRequest
* LoggingSetLevelRequest

and every request defined in the specification.

---

## Notifications

Generate notification records.

Example:

```java
public record ResourceUpdatedNotification(
    ResourceUpdatedParams params
) implements McpNotification {
}
```

Generate all notifications defined by MCP.

---

## Results

Generate immutable result records.

Example:

```java
public record ToolCallResult(
    List<ContentBlock> content,
    boolean isError
) {
}
```

Generate all result types.

---

## Enums

Generate Java enums.

Example:

```java
public enum LoggingLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}
```

Preserve protocol literals.

---

## Additional Properties

When schema permits arbitrary fields:

Generate:

```java
Map<String, JsonNode> additionalProperties
```

as the final record component.

Example:

```java
public record ClientCapabilities(
    Map<String, JsonNode> additionalProperties
) {
}
```

---

## JsonNode Usage

Use JsonNode only when:

* schema explicitly allows arbitrary JSON
* schema uses unknown
* schema uses open-ended object definitions

Do not replace strongly typed objects with JsonNode.

---

## Codec Generation

Generate one codec per generated model.

Example:

```java
public final class ToolCallRequestCodec {
}
```

---

## Decoder API

Every codec must expose:

```java
public ToolCallRequest decode(
    JsonParser parser
) throws IOException
```

Requirements:

* Jackson Streaming API only
* No ObjectMapper
* No TreeModel creation
* No reflection
* No databind

Example:

```java
while (parser.nextToken() != JsonToken.END_OBJECT) {
    String field = parser.currentName();
}
```

---

## Encoder API

Every codec must expose:

```java
public void encode(
    JsonGenerator generator,
    ToolCallRequest value
) throws IOException
```

Example:

```java
generator.writeStartObject();
generator.writeStringField("method", "tools/call");
generator.writeEndObject();
```

---

## Fluent Builders

Every generated record includes a static fluent builder.

Example usage:

```java
var result = CallToolResult.builder()
    .content(List.of(contentBlock))
    .isError(false)
    .build();
```

### Builder API

```java
public static Builder builder() { ... }

public static final class Builder {
    // one field per record component
    private List<ContentBlock> content;
    private Boolean isError;
    ...

    // fluent setter per field
    public Builder content(List<ContentBlock> content) { ... return this; }
    public Builder isError(Boolean isError) { ... return this; }

    // terminal operation
    public CallToolResult build() { ... }
}
```

- Setter methods are named after the field (not `with*` prefix)
- Each setter returns `Builder` for chaining
- `build()` calls the canonical record constructor
- Builders are also generated for nested inner record types

## Codec Registry

Generate:

```java
public final class McpCodecRegistry {
}
```

Responsibilities:

* request codec lookup
* notification codec lookup
* response codec lookup
* method name resolution

Example:

```java
public ToolCallRequestCodec toolCallRequestCodec();
```

---

## Protocol Method Registry

Generate:

```java
public final class McpMethodRegistry {
}
```

Containing constants for every protocol method.

Example:

```java
public static final String TOOLS_CALL = "tools/call";
```

---

## Method Dispatch Metadata

Generate metadata describing mappings.

Example:

```java
public record MethodDescriptor(
    String method,
    Class<?> requestType,
    Class<?> resultType
) {
}
```

Registry must contain all MCP methods.

---

## Unknown Message Handling

Generate:

```java
public record UnknownRequest(
    RequestId id,
    String method,
    JsonNode params
) implements McpRequest {
}
```

And corresponding codec support.

The generated system must tolerate protocol evolution.

---

## Versioning

Embed protocol version:

```java
public final class McpProtocolVersion {
    public static final String VERSION = "2025-11-25";
}
```

Value derived from source specification filename.

---

## Generation Rules

### Naming

Convert:

```text
tools/call
```

to:

```java
ToolsCallRequest
ToolsCallResult
ToolsCallParams
```

### Stability

Generated names must never depend on declaration order.

### Determinism

Repeated generation against unchanged specification must produce byte-identical output.

---

## Validation Requirements

Fail generation when:

* unresolved type references exist
* cyclic inheritance exists
* duplicate protocol methods exist
* duplicate generated class names exist

---

## Testing Generation

Also generate:

```text
src/generated-test/java
```

Containing:

* codec roundtrip tests
* request decode tests
* response decode tests
* notification decode tests

Each generated type must have:

```java
decode -> encode -> decode
```

equivalence tests.

---

## Success Criteria

The generated codebase must:

1. Represent the complete MCP protocol specification.
2. Use immutable Java records.
3. Avoid reflection and databind.
4. Support high-performance Jackson streaming serialization.
5. Support protocol evolution through unknown message handling.
6. Provide deterministic code generation suitable for production infrastructure systems.
