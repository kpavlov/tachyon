/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp20260728;

import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThat;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.server.domain.FormInputRequest;
import dev.tachyonmcp.api.server.domain.InputRequestBundle;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.api.server.features.tasks.TaskSupport;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.features.tasks.TasksExtension;
import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import dev.tachyonmcp.testkit.Mcp20260728Client;
import dev.tachyonmcp.testkit.TestTaskExecutionEngine;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class TasksExtensionTest extends AbstractStatelessMcpE2eTest {

    @Test
    void taskProducingToolPublishesSnapshotAndGetRefreshesConnector() throws Exception {
        var observedAt = Instant.parse("2026-08-27T07:00:00Z");
        var initial = TaskSnapshot.working("workflow-1", observedAt, 1);
        var completed = TaskSnapshot.builder()
                .from(initial)
                .status(TaskState.COMPLETED)
                .statusMessage("Done")
                .lastUpdatedAt(observedAt.plusSeconds(1))
                .result(TaskResult.completed(Map.of("bookingId", "booking-1")))
                .revision(2)
                .build();
        var taskEngine = new TestTaskExecutionEngine().publish(initial);
        startTasksServer(taskEngine, "book", ToolResult.task(initial));

        try (var client = tasksClient()) {
            var callResponse = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"book","arguments":{}}}
                    """);
            assertThat(callResponse).isSuccess().hasResultType("task");
            assertThatJson(callResponse.body()).isEqualTo("""
                    {
                      "jsonrpc":"2.0",
                      "id":1,
                      "result":{
                        "taskId":"workflow-1",
                        "status":"working",
                        "createdAt":"2026-08-27T07:00:00Z",
                        "lastUpdatedAt":"2026-08-27T07:00:00Z",
                        "ttlMs":null,
                        "resultType":"task"
                      }
                    }
                    """);

            taskEngine.publish(completed);
            var getResponse = client.post("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/get","params":{"taskId":"workflow-1"}}
                    """);
            assertThatJson(getResponse.body()).isEqualTo("""
                    {
                      "jsonrpc":"2.0",
                      "id":2,
                      "result":{
                        "taskId":"workflow-1",
                        "status":"completed",
                        "statusMessage":"Done",
                        "createdAt":"2026-08-27T07:00:00Z",
                        "lastUpdatedAt":"2026-08-27T07:00:01Z",
                        "ttlMs":null,
                        "resultType":"complete",
                        "result":{
                          "content":[{"type":"text","text":"{\\"bookingId\\":\\"booking-1\\"}"}],
                          "structuredContent":{"bookingId":"booking-1"},
                          "resultType":"complete"
                        }
                      }
                    }
                    """);
            assertThat(taskEngine.refreshedTaskIds()).containsExactly("workflow-1");
        }
    }

    @Test
    void cancelDelegatesToConnectorAndPublishesReturnedSnapshot() throws Exception {
        var initial = TaskSnapshot.working("workflow-cancel", Instant.parse("2026-08-27T07:00:00Z"), 1);
        var taskEngine = new TestTaskExecutionEngine().publish(initial);
        startTasksServer(taskEngine, "book", ToolResult.task(initial));

        try (var client = tasksClient()) {
            client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"book","arguments":{}}}
                    """);
            var response = client.post("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/cancel","params":{"taskId":"workflow-cancel"}}
                    """);

            assertThatJson(response.body()).isEqualTo("""
                    {"jsonrpc":"2.0","id":2,"result":{"resultType":"complete"}}
                    """);
            assertThat(taskEngine.cancelledTaskIds()).containsExactly("workflow-cancel");

            var refreshed = client.post("""
                    {"jsonrpc":"2.0","id":3,"method":"tasks/get","params":{"taskId":"workflow-cancel"}}
                    """);
            assertThatJson(refreshed.body()).inPath("$.result.status").isEqualTo("cancelled");
            assertThat(taskEngine.refreshedTaskIds()).containsExactly("workflow-cancel");
        }
    }

    @Test
    void updateForwardsInputToConnectorWithoutReinvokingTool() throws Exception {
        var observedAt = Instant.parse("2026-08-27T07:00:00Z");
        var pendingInput = new InputRequestBundle(
                Map.of(
                        "approval",
                        FormInputRequest.of("Approve booking", JsonSchema.unchecked("{\"type\":\"object\"}"))),
                "approval-round-1");
        var snapshot = TaskSnapshot.builder()
                .taskId("workflow-input")
                .status(TaskState.INPUT_REQUIRED)
                .createdAt(observedAt)
                .lastUpdatedAt(observedAt)
                .pendingInput(pendingInput)
                .revision(1)
                .build();
        var taskEngine = new TestTaskExecutionEngine().publish(snapshot);
        startTasksServer(taskEngine, "book", ToolResult.task(snapshot));

        try (var client = tasksClient()) {
            client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"book","arguments":{}}}
                    """);
            var response = client.post("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/update","params":{
                      "taskId":"workflow-input","inputResponses":{"approval":{"approved":true}}}}
                    """);

            assertThat(response).isSuccess();
            assertThat(taskEngine.submittedInputs()).singleElement().satisfies(submission -> {
                assertThat(submission.taskId()).isEqualTo("workflow-input");
                assertThat(submission.input().requestState()).isEqualTo("approval-round-1");
                assertThat(submission.input().inputResponses()).containsKey("approval");
            });
        }
    }

    @Test
    void taskMethodsRequireClientExtensionDeclaration() throws Exception {
        var snapshot = TaskSnapshot.working("workflow-gated", Instant.parse("2026-08-27T07:00:00Z"), 1);
        var taskEngine = new TestTaskExecutionEngine().publish(snapshot);
        startTasksServer(taskEngine, "book", ToolResult.task(snapshot));

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"book","arguments":{}}}
                    """);
            assertThat(response).isJsonRpcError();
        }
    }

    private void startTasksServer(TestTaskExecutionEngine taskEngine, String toolName, ToolResult result) {
        startServer(
                builder -> builder.capabilities(c -> c.tasks(taskEngine, false, true, true))
                        .withExtensions(TasksExtension.instance()),
                registrar -> registrar
                        .tools()
                        .register(
                                b -> b.name(toolName).taskSupport(TaskSupport.REQUIRED), (context, request) -> result));
    }

    private Mcp20260728Client tasksClient() {
        return createModernTestClient()
                .withExtensions(Map.of(TasksExtension.ID, JsonNodeFactory.instance.objectNode()));
    }
}
