/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package com.example.mcpjava;

import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.resources.ResourceTemplateArg;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

/**
 * Plain mcp-java annotated service. It has no Tachyon dependency in its source code.
 */
public class AnnotatedService {

    /**
     * Adds two numbers.
     */
    @Tool(
        name = "add",
        description = "Adds two integers",
        annotations = @Tool.Annotations(readOnlyHint = true)
    )
    public int add(@ToolArg(name = "left", description = "First number") int left,
                   @ToolArg(name = "right", description = "Second number") int right) {
        return left + right;
    }

    /**
     * Returns static application configuration.
     */
    @Resource(uri = "app://config",
        name = "config",
        description = "Application configuration",
        mimeType = "application/json")
    public String config() {
        return "{\"name\":\"mcp-java-example\",\"version\":\"1.0\"}";
    }

    /**
     * Returns a greeting for a URI-template parameter.
     */
    @ResourceTemplate(uriTemplate = "app://greeting/{name}",
        name = "greeting",
        description = "A greeting for a name")
    public String greeting(@ResourceTemplateArg(name = "name") String name) {
        return "Hello, " + name + "!";
    }

    /**
     * Creates a welcome prompt.
     */
    @Prompt(
        name = "welcome",
        description = "Creates a welcome message")
    public String welcome(@PromptArg(name = "name", description = "Name to welcome") String name) {
        return "Welcome to MCP, " + name + "!";
    }
}
