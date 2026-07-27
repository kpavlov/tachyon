# JSON & JSON Schema — Tachyon MCP Server

Tachyon models JSON with a small, provider-neutral set of types in `dev.tachyonmcp.server.json`
(`tachyon-api`), so tool schemas, structured content, and payload conversion never force a specific
JSON library on your code. `JsonSchema extends JsonDocument` — a schema *is* a document, plus the
schema-specific validation semantics the server applies to it.

| Type | Purpose |
|---|---|
| `JsonDocument` | An encoded JSON value: `json()` returns the string, `unwrap(Class)` recovers a retained provider-specific representation |
| `JsonSchema` | A `JsonDocument` the server validates as a JSON Schema (2020-12, or the schema's own declared dialect) |
| `JsonObject` / `JsonArray` | Provider-neutral, typed navigation over a JSON object/array (`stringOpt`, `intValue`, `objectOr`, ...) |

## Creating a `JsonDocument` or `JsonSchema`

Three ways to get one, in increasing order of safety:

| Method | Validates? | Use when |
|---|---|---|
| `JsonSchema.of(String)` / `JsonDocument.of(String)` | No — wraps the string as-is | You already trust the source (e.g. a literal in your own code) |
| `JsonSchema.parse(String)` / `JsonDocument.parse(String)` | Yes — rejects malformed JSON | The string comes from outside your code and might be invalid |
| `JsonSchema.from(T, Class<T>)` / `JsonDocument.from(T, Class<T>)` | Depends on the source type | You already have a parsed tree (Jackson `JsonNode`, kotlinx `JsonElement`, ...) and don't want to re-serialize it |

```java
var raw = JsonSchema.of("""{"type":"object"}""");     // no validation
var parsed = JsonSchema.parse(userSuppliedSchema);      // throws IllegalArgumentException if malformed
```

```kotlin
val raw = JsonSchema.of("""{"type":"object"}""")
val parsed = JsonSchema.parse(userSuppliedSchema)
```

`parse` and `from` are backed by a `JsonSchemaFactory`/`JsonDocumentFactory` discovered via
`ServiceLoader` (see [SPI](#spi-pluggable-jsondocumentschema-factories) below) — they only work when
a provider for the requested type is on the classpath. `tachyon-core` registers one for `String`
automatically, so `parse` works out of the box in any real server; `of` never needs one.

## Building from an already-parsed tree

If you already have a Jackson `JsonNode`, tachyon's own `JsonObject`, or (in Kotlin) a kotlinx
`JsonElement`/`JsonObject`, `from(T, Class<T>)` wraps it **without re-serializing** — the original
tree is retained and recoverable via `unwrap(Class<T>)`, instead of round-tripping through
`toString()` and re-parsing.

```java
JsonNode node = objectMapper.readTree(someSource);
JsonSchema schema = JsonSchema.from(node, JsonNode.class);

// later, get the original node back without re-parsing:
JsonNode same = schema.unwrap(JsonNode.class).orElseThrow();
```

```kotlin
val element: JsonElement = Json.parseToJsonElement(someSource)
val schema = JsonSchema.from(element, JsonElement::class.java)

// later:
val same = schema.unwrap(JsonElement::class.java).get()
```

Note the second argument is the **type you're asserting**, not `source.javaClass` / `source::class`
— pass the declared contract type (`JsonNode.class`, not `ObjectNode.class`) so the lookup matches
what the provider registered.

## Built-in providers

| Source type | Module | Registered by |
|---|---|---|
| `String` | `tachyon-core` | `Jackson3JsonFactory` |
| Jackson `JsonNode` | `tachyon-core` | `JacksonNodeJsonFactory` |
| Jackson `ObjectNode` | `tachyon-core` | `JacksonObjectJsonFactory` |
| kotlinx `JsonElement` | `tachyon-kotlin` | `KotlinxJsonElementFactory` |
| kotlinx `JsonObject` | `tachyon-kotlin` | `KotlinxJsonObjectFactory` |

`JacksonObjectJsonFactory`'s result also implements `JsonObject` itself, reading property values
directly from the wrapped `ObjectNode` — so `JsonDocument.from(node, ObjectNode.class)` gives you
typed navigation (`has`, `stringValue`, `objectOpt`, ...) without a `JsonObject.of(Map)` conversion.

All of them retain the source tree for `unwrap(Class)` rather than re-serializing it.

## `JsonObject` / `JsonArray`

Provider-neutral, typed navigation — no need to know whether the underlying value came from
Jackson, kotlinx.serialization, or a plain `Map`:

```java
var user = JsonObject.of(Map.of("name", "Ada", "age", 32));
user.stringValue("name");          // "Ada" — throws if missing/null
user.intOr("age", 0);              // 32, or 0 if missing/null
user.objectOpt("address");         // Optional<JsonObject>, empty if missing/null
```

Every accessor comes in three shapes: `xOpt(name)` (`Optional`/`OptionalInt`/...), `xValue(name)`
(throws `IllegalArgumentException` if missing or null), and `xOr(name, fallback)`. Accessing a
property as the wrong type also throws `IllegalArgumentException` — values are never coerced.

## SPI: pluggable `JsonDocument`/`JsonSchema` factories

`tachyon-api` has no JSON-parsing dependency of its own (by design — see
[docs/architecture/guidance.md](architecture/guidance.md)), so `parse`/`from` resolve to whatever
`dev.tachyonmcp.server.json.spi.JsonDocumentFactory<T>` / `JsonSchemaFactory<T>` is registered via
`java.util.ServiceLoader` for the requested `T`. To add support for another source type:

```java
package your.pkg;

public final class YourTreeJsonFactory
        implements JsonDocumentFactory<YourTreeType>, JsonSchemaFactory<YourTreeType> {

    @Override
    public Class<YourTreeType> sourceType() {
        return YourTreeType.class;
    }

    @Override
    public JsonDocument toJsonDocument(YourTreeType source) {
        return new YourTreeJsonDocument(source); // retain `source` for unwrap()
    }

    @Override
    public JsonSchema toJsonSchema(YourTreeType source) {
        return new YourTreeJsonSchema(source);
    }
}
```

Register it in `META-INF/services/dev.tachyonmcp.server.json.spi.JsonDocumentFactory` and
`META-INF/services/dev.tachyonmcp.server.json.spi.JsonSchemaFactory` (one fully-qualified class
name per line — the same class can implement both). The provider class needs a **public no-arg
constructor** — `ServiceLoader` instantiates it directly, so a private constructor (e.g. a
classic singleton) fails with `ServiceConfigurationError: Unable to get public no-arg constructor`.
If you also want a stable singleton for direct (non-SPI) use, keep a `public static provider()`
factory method returning it (see `Jackson3JsonFactory.INSTANCE`/`.provider()`) — `ServiceLoader`
will still construct its own instance via the public constructor, so the two aren't the same
object, but that's harmless for a stateless factory.

A single class can implement `JsonSchemaFactory<String>` **or** `JsonSchemaFactory<JsonNode>`, never
both — Java forbids implementing the same generic interface twice with different type arguments.
That's why `Jackson3JsonFactory` (String) and `JacksonNodeJsonFactory` (JsonNode) are separate
classes even though they're both Jackson-backed.

## Overriding the server's default `String` factory

`json { }` / `JsonConfig.Builder` lets you replace the discovered `String`-typed factory:

```java
var server = TachyonServer.builder()
    .json(cfg -> cfg.schemaFactory(mySchemaFactory))
    .port(8080)
    .build();
```

```kotlin
TachyonServer(port = 8080) {
    json { schemaFactory = mySchemaFactory } // see JsonConfig.Builder
}
```

## Payload serialization

`PayloadSerializer`/`PayloadDeserializer` (combined as `PayloadSerde`) convert structured tool
arguments/results to and from JSON strings — the default is Jackson-backed (`JacksonPayloadSerde`).
Configure via the same `json { }` scope:

```java
.json(cfg -> cfg.serde(myPayloadSerde))
```

## Schema validation

`JsonSchemaValidator` validates a `JsonDocument` against a `JsonSchema` and returns a list of
`SchemaValidationError`s. The default (`NetworkntJsonSchemaValidator`) validates JSON Schema
2020-12; `JsonSchemaValidator.NOOP` disables validation entirely (including the parsing work needed
to prepare data for it):

```java
.json(cfg -> cfg.inputSchemaValidator(myValidator).outputSchemaValidator(JsonSchemaValidator.NOOP))
```

See [tools.md](tools.md) for how input/output schemas attach to a `ToolDescriptor`, and
[configuration.md](configuration.md) for the full `json { }` / `JsonConfig.Builder` option reference.
