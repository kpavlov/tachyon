---
title: "Quickstart"
weight: 5
sidebar_order: 5
toc: true
description: |-
  Build a complete Java or Kotlin MCP server and call its greeting tool with curl.
---

Run a server with one `greet` tool at `http://127.0.0.1:8080/mcp`. Send a name and get a personal
greeting back. Choose your language and build tool first:

| Language | Maven | Gradle |
|---|---|---|
| Java | [Java + Maven](#java--maven) | [Java + Gradle](#java--gradle) |
| Kotlin | [Kotlin + Maven](#kotlin--maven) | [Kotlin + Gradle](#kotlin--gradle) |

## Prerequisites

- JDK 21; set `JAVA_HOME` to its installation directory.
- Maven 3.9+ or Gradle 8.14.3.
- `curl` to call the server.

> [!NOTE]
> The project files pin **Tachyon ${tachyon.version}**, available from Maven Central. Kotlin projects
> also pin Kotlin 2.2.21. No repository checkout or locally installed Tachyon artifacts are needed.

## 1. Add the dependency

Create an empty `greeting-server` directory. Expand **one** combination below and save its build files
into it, then [create the server](#2-create-a-server). Each configuration is complete.

The `tachyon-bom` pins module versions; Java uses `tachyon-core`, and Kotlin uses
`tachyon-kotlin`, which includes core transitively.

### Java + Maven

<details>
<summary>Show pom.xml</summary>

Create `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>greeting-server</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>dev.tachyonmcp</groupId>
                <artifactId>tachyon-bom</artifactId>
                <version>${tachyon.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.tachyonmcp</groupId>
            <artifactId>tachyon-core</artifactId>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.1</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.6.3</version>
                <configuration>
                    <executable>java</executable>
                    <arguments>
                        <argument>-classpath</argument>
                        <classpath/>
                        <argument>MyMcpServer</argument>
                    </arguments>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

</details>

### Java + Gradle

<details>
<summary>Show settings.gradle.kts and build.gradle.kts</summary>

Create `settings.gradle.kts`:

```kotlin
rootProject.name = "greeting-server"
```

Create `build.gradle.kts`:

```kotlin
plugins {
    java
    application
}

repositories { mavenCentral() }

dependencies {
    implementation(platform("dev.tachyonmcp:tachyon-bom:$tachyonVersion"))
    implementation("dev.tachyonmcp:tachyon-core")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

application { mainClass = "MyMcpServer" }
```

</details>

### Kotlin + Maven

<details>
<summary>Show pom.xml</summary>

Create `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>greeting-server</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>dev.tachyonmcp</groupId>
                <artifactId>tachyon-bom</artifactId>
                <version>${tachyon.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.tachyonmcp</groupId>
            <artifactId>tachyon-kotlin</artifactId>
        </dependency>
    </dependencies>
    <build>
        <sourceDirectory>src/main/kotlin</sourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <version>2.2.21</version>
                <executions>
                    <execution>
                        <id>compile</id>
                        <phase>compile</phase>
                        <goals><goal>compile</goal></goals>
                    </execution>
                </executions>
                <configuration><jvmTarget>21</jvmTarget></configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.1</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.6.3</version>
                <configuration>
                    <executable>java</executable>
                    <arguments>
                        <argument>-classpath</argument>
                        <classpath/>
                        <argument>MyMcpServerKt</argument>
                    </arguments>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

</details>

### Kotlin + Gradle

<details>
<summary>Show settings.gradle.kts and build.gradle.kts</summary>

Create `settings.gradle.kts`:

```kotlin
rootProject.name = "greeting-server"
```

Create `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.2.21"
    application
}

repositories { mavenCentral() }

dependencies {
    implementation(platform("dev.tachyonmcp:tachyon-bom:$tachyonVersion"))
    implementation("dev.tachyonmcp:tachyon-kotlin")
}

kotlin { jvmToolchain(21) }

application { mainClass = "MyMcpServerKt" }
```

</details>

## 2. Create a server

Choose **one** source file. Both implementations register the same tool, require a string `name`,
and close the server on JVM shutdown.

### Java

Create `src/main/java/MyMcpServer.java`:

```java
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.TachyonServer;

public final class MyMcpServer {
    public static void main(String[] args) {
        final var server = TachyonServer.builder()
                .name("my-server")
                .version("1.0")
                .withTools(tools -> tools.register(
                        tool -> tool.name("greet")
                                .description("Say hello to someone")
                                .inputSchema("""
                                        {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
                                        """),
                        (ctx, request) -> ToolResult.text(
                                "Hello, " + request.arguments().stringValue("name") + "!")))
                .host("127.0.0.1")
                .port(8080)
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
    }
}
```

### Kotlin

Create `src/main/kotlin/MyMcpServer.kt`:

```kotlin
import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.server.features.tools.ToolResult
import dev.tachyonmcp.kotlin.server.TachyonServer

fun main() {
    val server = TachyonServer(port = 8080) {
        info {
            name = "my-server"
            version = "1.0"
        }
        network { host = "127.0.0.1" }
        tool(
            name = "greet",
            description = "Say hello to someone",
            inputSchema = JsonSchema.parse(
                """{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}""",
            ),
        ) {
            ToolResult.text("Hello, ${arguments.stringValue("name")}!")
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread { server.close() })
}
```

### Run

From `greeting-server`, run the command for your build tool:

| Build tool | Build and run |
|---|---|
| Maven | `mvn -q compile exec:exec` |
| Gradle | `gradle --console=plain run` |

Leave this terminal running. The server listens at `http://127.0.0.1:8080/mcp`. Stop it with
**Ctrl+C** when you finish.

## 3. Test with curl

Open a second terminal. This request uses MCP **2026-07-28**: each request supplies its protocol
and client metadata, so there is no initialization handshake or session ID to copy. The method
and tool-name headers match the JSON body.

```bash
curl --fail-with-body --silent --show-error http://127.0.0.1:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: tools/call' \
  -H 'Mcp-Name: greet' \
  --data-binary '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "greet",
      "arguments": {"name": "Ada"},
      "_meta": {
        "io.modelcontextprotocol/protocolVersion": "2026-07-28",
        "io.modelcontextprotocol/clientInfo": {"name": "curl", "version": "1.0"},
        "io.modelcontextprotocol/clientCapabilities": {}
      }
    }
  }'
```

Expected response (formatted):

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [{"type": "text", "text": "Hello, Ada!"}],
    "resultType": "complete"
  }
}
```

Change `"Ada"` to your name and call again. The greeting changes with the argument. Send
`"arguments": {}` or `"arguments": {"name": 42}` to see a JSON-RPC `-32602` input-validation error.

> [!IMPORTANT]
> HTTP success alone does not mean a tool call succeeded. Inspect the JSON-RPC `error` field and,
> for tool results, `isError`.

## Next steps

- [Tools](features/tools.md) — read input, return structured output, and handle errors.
- [Testkit](testkit.md) — automate calls against a running server.
- [Resources](features/resources.md) and [prompts](features/prompts.md) — add data and reusable messages.
- [Deployment](running/deployment.md) — make the server reachable beyond your machine.
