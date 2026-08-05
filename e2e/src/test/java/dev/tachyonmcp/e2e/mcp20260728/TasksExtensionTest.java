/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.features.tasks.TaskSupport;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.features.tasks.TasksExtension;
import dev.tachyonmcp.core.server.json.JsonUtils;
import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 tasks (<a href="https://modelcontextprotocol.io/seps/1686-tasks">SEP-1686</a>):
 * this revision exposes the wider seven-state workflow, so a freshly created task reports
 * {@code "submitted"} on the wire — where 2025-11-25 folds the same internal {@code SUBMITTED}
 * state down to {@code "working"} (see {@code TasksExtensionTest} in the default package).
 */
class TasksExtensionTest extends AbstractStatelessMcpE2eTest {

    private void startTasksServer() {
        startServer(builder -> builder.extension(TasksExtension.instance()), registrar -> {});
    }

    @Test
    void freshTaskReportsSubmittedStatus() throws Exception {
        startTasksServer();
        var task = server.tasks().create();

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tasks/get","params":{"taskId":"%s",\
                    "_meta":{"io.modelcontextprotocol/clientCapabilities":{"extensions":{"%s":{}}}}}}
                    """.formatted(task.id(), TasksExtension.ID));

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body())
                    .whenIgnoringPaths("$.result.createdAt", "$.result.lastUpdatedAt")
                    .isEqualTo("""
                            {
                              "jsonrpc":"2.0",
                              "id":1,
                              "result":{"taskId":"%s","status":"submitted","ttl":null}
                            }
                            """.formatted(task.id()));
        }
    }

    @Test
    void completedTaskReportsCompletedStatus() throws Exception {
        startTasksServer();
        var task = server.tasks().create();
        task.complete(TaskResult.completed(JsonUtils.parse("{\"output\":\"done\"}")));

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/get","params":{"taskId":"%s",\
                    "_meta":{"io.modelcontextprotocol/clientCapabilities":{"extensions":{"%s":{}}}}}}
                    """.formatted(task.id(), TasksExtension.ID));

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body())
                    .whenIgnoringPaths("$.result.createdAt", "$.result.lastUpdatedAt")
                    .isEqualTo("""
                            {
                              "jsonrpc":"2.0",
                              "id":2,
                              "result":{"taskId":"%s","status":"completed","ttl":null}
                            }
                            """.formatted(task.id()));
        }
    }

    @Test
    void rejectsTasksGetWithoutDeclaredExtension() throws Exception {
        startTasksServer();
        var task = server.tasks().create();

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tasks/get","params":{"taskId":"%s"}}
                    """.formatted(task.id()));

            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32021);
            assertThatJson(response.body())
                    .inPath("$.error.data.requiredCapabilities.extensions")
                    .isObject()
                    .containsKey(TasksExtension.ID);
        }
    }

    @Test
    void doesNotRetainTasksExtensionAcrossRequestsOnSameConnection() throws Exception {
        startTasksServer();
        var task = server.tasks().create();

        try (var client = createModernTestClient()) {
            var declared = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tasks/get","params":{"taskId":"%s",\
                    "_meta":{"io.modelcontextprotocol/clientCapabilities":{"extensions":{"%s":{}}}}}}
                    """.formatted(task.id(), TasksExtension.ID));
            assertThat(declared.statusCode()).as(declared.body()).isEqualTo(200);

            // 2026-07-28 has no session: declaring the extension on request 1 must not leave it
            // enabled for request 2 on the same (likely pooled/reused) HTTP connection.
            var undeclared = client.post("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/get","params":{"taskId":"%s"}}
                    """.formatted(task.id()));
            assertThat(undeclared.statusCode()).as(undeclared.body()).isEqualTo(400);
            assertThatJson(undeclared.body()).inPath("$.error.code").isEqualTo(-32021);
        }
    }

    @Test
    void rejectsTasksCancelWithoutDeclaredExtension() throws Exception {
        startTasksServer();
        var task = server.tasks().create();

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tasks/cancel","params":{"taskId":"%s"}}
                    """.formatted(task.id()));

            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32021);
        }
    }

    @Test
    void rejectsTaskAugmentedToolCallWithoutDeclaredExtension() throws Exception {
        startServer(
                builder -> {},
                registrar -> registrar
                        .tools()
                        .register(
                                b -> b.name("sleep").taskSupport(TaskSupport.OPTIONAL),
                                (context, request) -> ToolResult.text("done")));

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"sleep","arguments":{},"task":{}}}
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32021);
            assertThatJson(response.body())
                    .inPath("$.error.data.requiredCapabilities.extensions")
                    .isObject()
                    .containsKey(TasksExtension.ID);
        }
    }

    @Test
    void rejectsExtensionGatedToolWithoutPerRequestDeclaration() throws Exception {
        startTasksServer();

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"create_task","arguments":{"name":"my-task"}}}
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32602);
            assertThatJson(response.body()).inPath("$.error.message").isEqualTo("Unknown tool: create_task");
        }
    }

    @Test
    void acceptsExtensionGatedToolWhenDeclaredPerRequest() throws Exception {
        startTasksServer();

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"create_task","arguments":{"name":"my-task"},\
                    "_meta":{"io.modelcontextprotocol/clientCapabilities":{"extensions":{"%s":{}}}}}}
                    """.formatted(TasksExtension.ID));

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body()).inPath("$.result.content[0].text").isString();
        }
    }
}
