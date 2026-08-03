# Generate JSON Schema from Kotlin classes

[kt-schema](https://github.com/kpavlov/kt-schema) generates
[JSON Schema 2020-12](https://json-schema.org/draft/2020-12) from Kotlin classes at runtime.
Use it when Kotlin models should define your tool contract. This removes hand-written schema
strings that can drift from the handler.

The runnable
[Weather MCP Kotlin example](https://github.com/kpavlov/tachyon/tree/main/examples/weather-mcp-kotlin)
uses the reflection generator for tool input, structured output, and elicitation schemas.

## Understand the request and response path

The [MCP 2026-07-28 tool specification](https://modelcontextprotocol.io/specification/2026-07-28/server/tools)
defines two related contracts: `inputSchema` describes `tools/call` arguments, and
`outputSchema` describes the result's `structuredContent`. Schemas without `$schema` use
JSON Schema 2020-12, as defined by the
[MCP JSON Schema rules](https://modelcontextprotocol.io/specification/2026-07-28/basic#json-schema-usage).

Tachyon implements the contract in this order:

| Phase | MCP data | Tachyon code |
|---|---|---|
| Discovery | `tools/list` returns `inputSchema` and `outputSchema` | `ToolDescriptor` |
| Request | `tools/call.params.arguments` | Validated before the handler, then exposed by `request.arguments()` |
| Handler | Application logic returns a domain value | `ToolResult.structured(value)` |
| Response | `result.structuredContent` | Serialized by the configured payload serde, validated against `outputSchema`, then encoded |
| Compatibility | `result.content` contains serialized JSON text | Tachyon adds the text block when the handler doesn't provide one |

Schema generation doesn't deserialize arguments. The weather handler reads its already-validated
`Args` through `request.arguments()`. Its `WeatherObservation` result is serialized after the
handler returns.

## Add the dependencies

The weather example uses kt-schema `0.7.0` with kotlinx.serialization JSON:

```xml
<properties>
    <kotlinx-serialization-json.version>1.11.0</kotlinx-serialization-json.version>
    <kt-schema.version>0.7.0</kt-schema.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.jetbrains.kotlinx</groupId>
        <artifactId>kotlinx-serialization-json</artifactId>
        <version>${kotlinx-serialization-json.version}</version>
    </dependency>
    <dependency>
        <groupId>me.kpavlov.kt.schema</groupId>
        <artifactId>kt-schema-generator-json-jvm</artifactId>
        <version>${kt-schema.version}</version>
    </dependency>
</dependencies>
```

Add these dependencies to an existing [`tachyon-kotlin`](kotlin.md#dependency) application.

## Complete weather tool integration

The following code comes from `examples/weather-mcp-kotlin`. The production example keeps each
model in its own file.

### Define the input and output models

`GetWeatherRequest` uses `@Description` for schema descriptions and a default value for the optional
temperature unit:

```kotlin
package com.example.weather.model

import me.kpavlov.kt.schema.Description

data class GetWeatherInput(
    @Description("City name (e.g., London, Tokyo, New York)")
    val city: String,
    @Description("Temperature unit (default: Celsius)")
    val units: TemperatureUnit = TemperatureUnit.Celsius,
)
```

The enum supplies the schema values:

```kotlin
package com.example.weather.model

import me.kpavlov.kt.schema.Description

@Description("Unit used to represent temperature")
enum class TemperatureUnit {
    Celsius,
    Fahrenheit
}
```

The handler returns `WeatherObservation` as structured content. The example marks it
`@Serializable` because its configured Tachyon serde is kotlinx.serialization:

```kotlin
package com.example.weather.spi

import com.example.weather.model.TemperatureUnit
import kotlinx.serialization.Serializable

@Serializable
data class WeatherObservation(
    val city: String,
    val condition: String,
    val temperature: Double,
    val temperatureUnit: TemperatureUnit,
    val humidity: Int,
    val windSpeed: Double,
)
```

### Generate both tool schemas

Create one generator and use the same source models as the handler:

```kotlin
import com.example.weather.model.GetWeatherRequest
import com.example.weather.spi.WeatherObservation
import dev.tachyonmcp.kotlin.server.features.tools.ToolDescriptor
import me.kpavlov.kt.schema.generator.json.JsonSchemaConfig
import me.kpavlov.kt.schema.generator.json.ReflectionClassJsonSchemaGenerator

private val schemaGenerator =
    ReflectionClassJsonSchemaGenerator(
        json = kotlinx.serialization.json.Json { encodeDefaults = false },
        config = JsonSchemaConfig.Default,
    )

val getWeatherToolDescriptor =
    ToolDescriptor {
        name = "get-weather"
        title = "Current Weather"
        description = "Get current weather for a city"
        inputSchema(schemaGenerator.generateSchemaString(GetWeatherInput::class))
        outputSchema(schemaGenerator.generateSchemaString(WeatherObservation::class))
    }
```

`generateSchemaString` returns encoded JSON Schema. The `ToolDescriptor` builder accepts that
string directly and Tachyon validates the schema when the tool is registered.

The running weather server publishes both generated schemas through `tools/list`:

<details>
<summary>Show the complete <code>tools/list</code> JSON response</summary>

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "description": "Get current weather for a city",
        "inputSchema": {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "com.example.weather.model.GetWeatherRequestst",
          "type": "object",
          "properties": {
            "city": {
              "type": "string",
              "description": "City name (e.g., London, Tokyo, New York)"
            },
            "units": {
              "$ref": "#/$defs/com.example.weather.model.TemperatureUnit",
              "description": "Temperature unit (default: Celsius)"
            }
          },
          "additionalProperties": false,
          "required": [
            "city"
          ],
          "$defs": {
            "com.example.weather.model.TemperatureUnit": {
              "type": "string",
              "description": "Unit used to represent temperature",
              "enum": [
                "Celsius",
                "Fahrenheit"
              ]
            }
          }
        },
        "outputSchema": {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "com.example.weather.spi.WeatherObservation",
          "type": "object",
          "properties": {
            "city": {
              "type": "string"
            },
            "condition": {
              "type": "string"
            },
            "temperature": {
              "type": "number"
            },
            "temperatureUnit": {
              "$ref": "#/$defs/com.example.weather.model.TemperatureUnit"
            },
            "humidity": {
              "type": "integer"
            },
            "windSpeed": {
              "type": "number"
            }
          },
          "additionalProperties": false,
          "required": [
            "city",
            "condition",
            "temperature",
            "temperatureUnit",
            "humidity",
            "windSpeed"
          ],
          "$defs": {
            "com.example.weather.model.TemperatureUnit": {
              "type": "string",
              "description": "Unit used to represent temperature",
              "enum": [
                "Celsius",
                "Fahrenheit"
              ]
            }
          }
        },
        "name": "get-weather",
        "title": "Current Weather"
      }
    ]
  }
}
```

</details>

### Return the output model and register the tool

The weather handler passes a `WeatherObservation` to `ToolResult.structured`:

```kotlin
fun attempt(city: String): ToolResult =
    try {
        ToolResult.structured(
            fetchWithProgress(
                ctx, progressToken, weatherService, city, temperatureUnit
            )
        )
    } catch (e: Exception) {
        if (e is CityNotFoundException) throw e
        internalError(e)
    }
```

The configured payload serde serializes this value. Tachyon validates the serialized value against
the generated `outputSchema` before writing the MCP response.

The server selects kotlinx.serialization and registers the generated descriptor with the handler:

```kotlin
return buildServer {
    network { this.port = port }
    json { serde = KxSerializationSerde.Default }

    tool(getWeatherToolDescriptor) { getWeather(weatherService) }
}
```

This gives one end-to-end contract:

| Stage | Source of truth |
|---|---|
| Tool input schema | `GetWeatherRequest` |
| Handler arguments | `request.arguments()` |
| Tool output schema | `WeatherObservation` |
| Structured result | `WeatherObservation` returned through `ToolResult.structured` |
| Payload encoding | `KxSerializationSerde.Default` |

The MCP 2026-07-28 specification permits any JSON value in `structuredContent`. Tachyon currently
accepts object-root tool output schemas and object-shaped structured results, so use a data class
as the top-level result rather than a list or primitive.

See the exact
[`GetWeatherTool.kt`](https://github.com/kpavlov/tachyon/blob/main/examples/weather-mcp-kotlin/src/main/kotlin/com/example/weather/GetWeatherTool.kt)
and
[`WeatherServer.kt`](https://github.com/kpavlov/tachyon/blob/main/examples/weather-mcp-kotlin/src/main/kotlin/com/example/weather/WeatherServer.kt)
sources for progress notifications, elicitation, resources, prompts, and error handling.

## Generate other schema types

The same generator creates the weather example's elicitation and prompt input schemas. APIs that
take `JsonSchema` instead of an encoded string use `JsonSchema.parse`:

```kotlin
private val CITY_SCHEMA =
    JsonSchema.parse(
        schemaGenerator.generateSchemaString(CityElicitationInput::class),
    )

private data class CityElicitationInput(val city: String)
```

The generator configuration determines how Kotlin types map to JSON Schema:

| Kotlin declaration | Generated schema |
|---|---|
| `@Description("...")` | `description` |
| `val city: String` | Required string property |
| `val units: TemperatureUnit = Celsius` | Optional enum property |
| `val value: T?` | Optional nullable property |
| `enum class` | `enum` values |
| Nested type | `$defs` and `$ref` |

The weather example uses `JsonSchemaConfig.Default`, the general-purpose JSON Schema preset.

## Run the source example

From the repository root:

```shell
cd examples/weather-mcp-kotlin
./mvnw package
java -jar target/weather-mcp-kotlin-example.jar
```

Connect an MCP client to `http://localhost:8080/mcp`, then inspect `get-weather` through
`tools/list` or call it through `tools/call`.

Next, see [JSON and JSON Schema](json.md) for validation and provider behavior,
[Tools](tools.md) for tool contracts, the [Kotlin DSL](kotlin.md) for handler APIs, or the
[MCP 2026-07-28 schema reference](https://modelcontextprotocol.io/specification/2026-07-28/schema)
for wire types.
