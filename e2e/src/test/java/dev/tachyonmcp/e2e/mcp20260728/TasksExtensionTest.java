/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.server.domain.FormInputRequest;
import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.features.tasks.TaskSupport;
import dev.tachyonmcp.api.server.features.tools.ToolFn;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.features.tasks.TasksExtension;
import dev.tachyonmcp.core.server.json.JsonUtils;
import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * MCP 2026-07-28 tasks (<a href="https://modelcontextprotocol.io/seps/2663-tasks-extension">SEP-2663</a>):
 * task creation is server-directed and gated on the client declaring {@code
 * io.modelcontextprotocol/tasks} per request (there's no session to negotiate it once, and no
 * client-side {@code task} field to opt in with — that legacy 2025-11-25 field is ignored
 * entirely). {@code tasks/list} and {@code tasks/result} are removed outright; {@code tasks/get}
 * and {@code tasks/cancel} remain, gated the same way as task-augmented {@code tools/call}.
 */
class TasksExtensionTest extends AbstractStatelessMcpE2eTest {

    private void startTasksServer() {
        startServer(builder -> builder.withExtensions(TasksExtension.instance()), registrar -> {});
    }

    @Test
    void ignoresLegacyTaskAugmentation() throws Exception {
        startServer(
                builder -> {},
                registrar -> registrar
                        .tools()
                        .register(
                                b -> b.name("sleep").taskSupport(TaskSupport.OPTIONAL),
                                (context, request) -> ToolResult.text("done")));

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{
                    "name":"sleep","arguments":{},"task":"ignored",
                    "_meta":{"io.modelcontextprotocol/clientCapabilities":{"extensions":{
                    "io.modelcontextprotocol/tasks":{}}}}}}
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body()).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "result": {
                        "content": [{"type": "text", "text": "done"}],
                        "resultType": "complete"
                      }
                    }
                    """);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"tasks/list", "tasks/result"})
    void doesNotExposeMethodsRemovedBySep2663(String method) throws Exception {
        startServer(builder -> {}, registrar -> {});

        try (var client = createModernTestClient()) {
            var params = "tasks/list".equals(method) ? "{}" : "{\"taskId\":\"task-1\"}";
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"%s","params":%s}
                    """.formatted(method, params));

            assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
            assertThatJson(response.body()).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "error": {"code": -32601, "message": "Method not found"}
                    }
                    """);
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
                              "result":{"taskId":"%s","status":"submitted","ttlMs":null,"resultType":"complete"}
                            }
                            """.formatted(task.id()));
        }
    }

    @Test
    void completedTaskInlinesItsResult() throws Exception {
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
                              "result":{
                                "taskId":"%s",
                                "status":"completed",
                                "ttlMs":null,
                                "resultType":"complete",
                                "result":{
                                  "content":[{"type":"text","text":"{\\"output\\":\\"done\\"}"}],
                                  "structuredContent":{"output":"done"},
                                  "resultType":"complete"
                                }
                              }
                            }
                            """.formatted(task.id()));
        }
    }

    @Test
    void toolLevelErrorInlinesResultAndReportsCompletedNotFailed() throws Exception {
        startTasksServer();
        var task = server.tasks().create();
        task.fail(new TaskResult.Failed(List.of(TextContent.of("bad input")), null, null));

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
                              "result":{
                                "taskId":"%s",
                                "status":"completed",
                                "ttlMs":null,
                                "resultType":"complete",
                                "result":{
                                  "content":[{"type":"text","text":"bad input"}],
                                  "isError":true,
                                  "resultType":"complete"
                                }
                              }
                            }
                            """.formatted(task.id()));
        }
    }

    @Test
    void protocolFailureInlinesErrorAndReportsFailedStatus() throws Exception {
        startTasksServer();
        var task = server.tasks().create();
        task.fail(TaskResult.failed(new ServerError(ServerError.Kind.INVALID_PARAMS, "bad request")));

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
                              "result":{
                                "taskId":"%s",
                                "status":"failed",
                                "ttlMs":null,
                                "resultType":"complete",
                                "error":{"code":-32602,"message":"bad request"}
                              }
                            }
                            """.formatted(task.id()));
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
    void acceptsTasksCancelWhenDeclared() throws Exception {
        startTasksServer();
        var task = server.tasks().create();

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tasks/cancel","params":{"taskId":"%s",\
                    "_meta":{"io.modelcontextprotocol/clientCapabilities":{"extensions":{"%s":{}}}}}}
                    """.formatted(task.id(), TasksExtension.ID));

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body()).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "result": {"resultType": "complete"}
                    }
                    """);

            var getResponse = client.post("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/get","params":{"taskId":"%s",\
                    "_meta":{"io.modelcontextprotocol/clientCapabilities":{"extensions":{"%s":{}}}}}}
                    """.formatted(task.id(), TasksExtension.ID));

            assertThat(getResponse.statusCode()).as(getResponse.body()).isEqualTo(200);
            assertThatJson(getResponse.body()).inPath("$.result.status").isEqualTo("cancelled");
        }
    }

    @Test
    void rejectsTaskAugmentedToolCallWithoutDeclaredExtension() throws Exception {
        startServer(
                builder -> {},
                registrar -> registrar
                        .tools()
                        .register(
                                b -> b.name("sleep").taskSupport(TaskSupport.REQUIRED),
                                (context, request) -> ToolResult.text("done")));

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"sleep","arguments":{}}}
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
    void acceptsTaskAugmentedRequiredToolWhenDeclaredPerRequest() throws Exception {
        startServer(
                builder -> builder.withExtensions(TasksExtension.instance()),
                registrar -> registrar
                        .tools()
                        .register(
                                b -> b.name("sleep").taskSupport(TaskSupport.REQUIRED),
                                (context, request) -> ToolResult.text("done")));

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{\
                    "name":"sleep","arguments":{},\
                    "_meta":{"io.modelcontextprotocol/clientCapabilities":{"extensions":{"%s":{}}}}}}
                    """.formatted(TasksExtension.ID));

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body())
                    .whenIgnoringPaths(
                            "$.result.taskId", "$.result.createdAt", "$.result.lastUpdatedAt", "$.result._meta")
                    .isEqualTo("""
                            {
                              "jsonrpc": "2.0",
                              "id": 1,
                              "result": {"status": "working", "ttlMs": null, "resultType": "task"}
                            }
                            """);
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

    /**
     * A task-augmented tool that asks for {@code user_name} the first time it's called, then
     * completes once a {@code tasks/update} carries an answer back through a resumed dispatch.
     */
    private static ToolFn greetHandler() {
        return (context, request) -> {
            var inputResponses = request.inputResponses();
            if (inputResponses == null || !inputResponses.containsKey("user_name")) {
                return ToolResult.inputRequired(
                        Map.of("user_name", FormInputRequest.of("What is your name?", JsonSchema.objectSchema())),
                        "greet-state");
            }
            var name = stringField(inputResponses.get("user_name"), "name", "World");
            return ToolResult.text("Hello, " + name + "!");
        };
    }

    private static String stringField(@Nullable Object value, String name, String defaultValue) {
        if (value instanceof Map<?, ?> map && map.get(name) instanceof String text) {
            return text;
        }
        return defaultValue;
    }

    private static String extractTaskId(String responseJson) {
        return JsonUtils.parse(responseJson).get("result").get("taskId").asString();
    }

    @Test
    void tasksGetInlinesInputRequestsWhenTaskAwaitsInput() throws Exception {
        startServer(
                builder -> builder.withExtensions(TasksExtension.instance()),
                registrar -> registrar
                        .tools()
                        .register(b -> b.name("greet").taskSupport(TaskSupport.REQUIRED), greetHandler()));

        try (var client = createModernTestClient()) {
            client.withExtensions(Map.of(TasksExtension.ID, JsonNodeFactory.instance.objectNode()));

            var callResponse = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"greet","arguments":{}}}
                    """);
            assertThat(callResponse.statusCode()).as(callResponse.body()).isEqualTo(200);
            var taskId = extractTaskId(callResponse.body());

            var getResponse = client.awaitTaskStatus(taskId, "input_required");

            assertThatJson(getResponse)
                    .whenIgnoringPaths("$.result._meta", "$.result.createdAt", "$.result.lastUpdatedAt")
                    .isEqualTo("""
                            {
                              "jsonrpc":"2.0",
                              "id":"tasks-get",
                              "result":{
                                "taskId":"%s",
                                "status":"input_required",
                                "statusMessage":"Input required",
                                "ttlMs":null,
                                "resultType":"complete",
                                "inputRequests":{
                                  "user_name":{
                                    "method":"elicitation/create",
                                    "params":{"message":"What is your name?","requestedSchema":{"type":"object"}}
                                  }
                                }
                              }
                            }
                            """.formatted(taskId));
        }
    }

    @Test
    void tasksUpdateResumesTaskAndItCompletes() throws Exception {
        startServer(
                builder -> builder.withExtensions(TasksExtension.instance()),
                registrar -> registrar
                        .tools()
                        .register(b -> b.name("greet").taskSupport(TaskSupport.REQUIRED), (context, request) -> {
                            var inputResponses = request.inputResponses();
                            if (inputResponses == null || !inputResponses.containsKey("user_name")) {
                                return ToolResult.inputRequired(
                                        Map.of(
                                                "user_name",
                                                FormInputRequest.of("What is your name?", JsonSchema.objectSchema())),
                                        "greet-state");
                            }
                            var name = stringField(inputResponses.get("user_name"), "name", "World");
                            return ToolResult.text("Hello, " + name + "!");
                        }));

        try (var client = createModernTestClient()) {
            client.withExtensions(Map.of(TasksExtension.ID, JsonNodeFactory.instance.objectNode()));

            var callResponse = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"greet","arguments":{}}}
                    """);
            var taskId = extractTaskId(callResponse.body());
            client.awaitTaskStatus(taskId, "input_required");

            var updateResponse = client.post("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/update","params":{\
                    "taskId":"%s","inputResponses":{"user_name":{"name":"Alice"}}}}
                    """.formatted(taskId));

            assertThat(updateResponse.statusCode()).as(updateResponse.body()).isEqualTo(200);
            assertThatJson(updateResponse.body()).isEqualTo("""
                    {"jsonrpc":"2.0","id":2,"result":{"resultType":"complete"}}
                    """);

            var completedTask = client.awaitTaskStatus(taskId, "completed");
            assertThatJson(completedTask)
                    .inPath("$.result.result.content[0].text")
                    .isEqualTo("Hello, Alice!");
        }
    }

    @Test
    void tasksUpdateSupportsMultipleRoundsOfInputRequired() throws Exception {
        startServer(
                builder -> builder.withExtensions(TasksExtension.instance()),
                registrar -> registrar
                        .tools()
                        .register(b -> b.name("multi-round").taskSupport(TaskSupport.REQUIRED), (context, request) -> {
                            var inputResponses = request.inputResponses();
                            var requestState = request.requestState();
                            if (inputResponses == null || requestState == null) {
                                return ToolResult.inputRequired(
                                        Map.of("step1", FormInputRequest.of("Step 1?", JsonSchema.objectSchema())),
                                        "round-1");
                            }
                            if ("round-1".equals(requestState)) {
                                return ToolResult.inputRequired(
                                        Map.of("step2", FormInputRequest.of("Step 2?", JsonSchema.objectSchema())),
                                        "round-2");
                            }
                            if ("round-2".equals(requestState)) {
                                return ToolResult.inputRequired(
                                        Map.of("step3", FormInputRequest.of("Step 3?", JsonSchema.objectSchema())),
                                        "round-3");
                            }
                            return ToolResult.text("all steps done");
                        }));

        try (var client = createModernTestClient()) {
            client.withExtensions(Map.of(TasksExtension.ID, JsonNodeFactory.instance.objectNode()));

            var callResponse = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"multi-round","arguments":{}}}
                    """);
            var taskId = extractTaskId(callResponse.body());
            client.awaitTaskStatus(taskId, "input_required");

            for (var step : List.of("step1", "step2", "step3")) {
                var updateResponse = client.post("""
                        {"jsonrpc":"2.0","id":2,"method":"tasks/update","params":{\
                        "taskId":"%s","inputResponses":{"%s":"answer"}}}
                        """.formatted(taskId, step));
                assertThat(updateResponse.statusCode())
                        .as(updateResponse.body())
                        .isEqualTo(200);
                if (!"step3".equals(step)) {
                    client.awaitTaskStatus(taskId, "input_required");
                }
            }

            var completedTask = client.awaitTaskStatus(taskId, "completed");
            assertThatJson(completedTask)
                    .inPath("$.result.result.content[0].text")
                    .isEqualTo("all steps done");
        }
    }

    @Test
    void tasksUpdateIgnoresUnknownAndAlreadySatisfiedKeys() throws Exception {
        startServer(
                builder -> builder.withExtensions(TasksExtension.instance()),
                registrar -> registrar
                        .tools()
                        .register(b -> b.name("greet").taskSupport(TaskSupport.REQUIRED), (context, request) -> {
                            var inputResponses = request.inputResponses();
                            if (inputResponses == null
                                    || !inputResponses.containsKey("user_name")
                                    || !inputResponses.containsKey("email")) {
                                return ToolResult.inputRequired(
                                        Map.of(
                                                "user_name",
                                                FormInputRequest.of("What is your name?", JsonSchema.objectSchema()),
                                                "email",
                                                FormInputRequest.of("What is your email?", JsonSchema.objectSchema())),
                                        "greet-state");
                            }
                            return ToolResult.text(
                                    "Hello, " + stringField(inputResponses.get("user_name"), "name", "World") + "!");
                        }));

        try (var client = createModernTestClient()) {
            client.withExtensions(Map.of(TasksExtension.ID, JsonNodeFactory.instance.objectNode()));

            var callResponse = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"greet","arguments":{}}}
                    """);
            var taskId = extractTaskId(callResponse.body());
            client.awaitTaskStatus(taskId, "input_required");

            var updateResponse = client.post("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/update","params":{\
                    "taskId":"%s","inputResponses":{"user_name":{"name":"Alice"},"unknown_key":"ignored"}}}
                    """.formatted(taskId));

            assertThat(updateResponse.statusCode()).as(updateResponse.body()).isEqualTo(200);

            var stillWaiting = client.getTask(null, taskId);
            assertThatJson(stillWaiting).inPath("$.result.status").isEqualTo("input_required");

            var secondUpdate = client.post("""
                    {"jsonrpc":"2.0","id":3,"method":"tasks/update","params":{\
                    "taskId":"%s","inputResponses":{"user_name":{"name":"Bob"},"email":"alice@example.com"}}}
                    """.formatted(taskId));

            assertThat(secondUpdate.statusCode()).as(secondUpdate.body()).isEqualTo(200);
            var completedTask = client.awaitTaskStatus(taskId, "completed");
            assertThatJson(completedTask)
                    .inPath("$.result.result.content[0].text")
                    .isEqualTo("Hello, Alice!");
        }
    }

    @Test
    void tasksUpdateAcceptsPartialResponsesAcrossMultipleCalls() throws Exception {
        startServer(
                builder -> builder.withExtensions(TasksExtension.instance()),
                registrar -> registrar
                        .tools()
                        .register(b -> b.name("two-fields").taskSupport(TaskSupport.REQUIRED), (context, request) -> {
                            var inputResponses = request.inputResponses();
                            if (inputResponses == null
                                    || !inputResponses.containsKey("name")
                                    || !inputResponses.containsKey("email")) {
                                return ToolResult.inputRequired(
                                        Map.of(
                                                "name", FormInputRequest.of("Name?", JsonSchema.objectSchema()),
                                                "email", FormInputRequest.of("Email?", JsonSchema.objectSchema())),
                                        null);
                            }
                            return ToolResult.text(
                                    "name=" + inputResponses.get("name") + " email=" + inputResponses.get("email"));
                        }));

        try (var client = createModernTestClient()) {
            client.withExtensions(Map.of(TasksExtension.ID, JsonNodeFactory.instance.objectNode()));

            var callResponse = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"two-fields","arguments":{}}}
                    """);
            var taskId = extractTaskId(callResponse.body());
            client.awaitTaskStatus(taskId, "input_required");

            var firstUpdate = client.post("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/update","params":{\
                    "taskId":"%s","inputResponses":{"name":"Alice"}}}
                    """.formatted(taskId));
            assertThat(firstUpdate.statusCode()).as(firstUpdate.body()).isEqualTo(200);

            // Still waiting on "email" -- task must remain input_required, not resume early.
            var stillWaiting = client.getTask(null, taskId);
            assertThatJson(stillWaiting).inPath("$.result.status").isEqualTo("input_required");

            var secondUpdate = client.post("""
                    {"jsonrpc":"2.0","id":3,"method":"tasks/update","params":{\
                    "taskId":"%s","inputResponses":{"email":"alice@example.com"}}}
                    """.formatted(taskId));
            assertThat(secondUpdate.statusCode()).as(secondUpdate.body()).isEqualTo(200);

            var completedTask = client.awaitTaskStatus(taskId, "completed");
            assertThatJson(completedTask)
                    .inPath("$.result.result.content[0].text")
                    .isEqualTo("name=Alice email=alice@example.com");
        }
    }

    @Test
    void tasksUpdateOnUnknownTaskReturnsInvalidParams() throws Exception {
        startTasksServer();

        try (var client = createModernTestClient()) {
            client.withExtensions(Map.of(TasksExtension.ID, JsonNodeFactory.instance.objectNode()));

            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tasks/update","params":{\
                    "taskId":"does-not-exist","inputResponses":{}}}
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32602);
        }
    }

    @Test
    void tasksUpdateOnTaskNotAwaitingInputIsNoOpAck() throws Exception {
        startTasksServer();
        var task = server.tasks().create();
        task.complete(TaskResult.completed(JsonUtils.parse("{\"output\":\"done\"}")));

        try (var client = createModernTestClient()) {
            client.withExtensions(Map.of(TasksExtension.ID, JsonNodeFactory.instance.objectNode()));

            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tasks/update","params":{\
                    "taskId":"%s","inputResponses":{"anything":"ignored"}}}
                    """.formatted(task.id()));

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body()).isEqualTo("""
                    {"jsonrpc":"2.0","id":1,"result":{"resultType":"complete"}}
                    """);

            var getResponse = client.getTask(null, task.id());
            assertThatJson(getResponse).inPath("$.result.status").isEqualTo("completed");
        }
    }

    @Test
    void tasksUpdateRejectsWithoutDeclaredExtension() throws Exception {
        startTasksServer();
        var task = server.tasks().create();

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tasks/update","params":{"taskId":"%s","inputResponses":{}}}
                    """.formatted(task.id()));

            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32021);
        }
    }
}
