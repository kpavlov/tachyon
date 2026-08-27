/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e;

import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThatJsonRpcResponse;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.testkit.TestTaskExecutionEngine;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TasksCoreTest extends AbstractStatefulMcpE2eTest {

    private TestTaskExecutionEngine taskEngine;

    @Override
    protected void startDefaultServer() {
        taskEngine = new TestTaskExecutionEngine();
        startServer(it -> it.capabilities(c -> c.tasks(taskEngine, true, true, true)));
    }

    @Test
    void listsAndGetsConnectorSnapshots() throws Exception {
        taskEngine.reset();
        var first = working("workflow-1", 1);
        var second = working("workflow-2", 1);
        taskEngine.publish(first).publish(second);

        try (var client = createTestClient()) {
            client.initialize();
            var listJson = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/list","params":{}}
                    """);
            assertThatJson(listJson).inPath("$.result.tasks.length()").isEqualTo(2);
            assertThat(taskEngine.listRequests()).singleElement().satisfies(request -> {
                assertThat(request.limit()).isPositive();
                assertThat(request.cursor()).isNull();
            });

            var getJson = client.sendRpc("""
                    {"jsonrpc":"2.0","id":3,"method":"tasks/get","params":{"taskId":"workflow-1"}}
                    """);
            assertThatJson(getJson).inPath("$.result.taskId").isEqualTo("workflow-1");
            assertThatJson(getJson).inPath("$.result.status").isEqualTo("working");
            assertThat(taskEngine.refreshedTaskIds()).contains("workflow-1");
        }
    }

    @Test
    void cancelsThroughConnector() throws Exception {
        taskEngine.reset();
        taskEngine.publish(working("workflow-cancel", 1));

        try (var client = createTestClient()) {
            client.initialize();
            var cancelJson = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/cancel","params":{"taskId":"workflow-cancel"}}
                    """);
            assertThatJson(cancelJson).inPath("$.result.taskId").isEqualTo("workflow-cancel");
            assertThatJson(cancelJson).inPath("$.result.status").isEqualTo("cancelled");
            assertThat(taskEngine.cancelledTaskIds()).containsExactly("workflow-cancel");
        }
    }

    @Test
    void returnsCompletedConnectorResult() throws Exception {
        taskEngine.reset();
        var completed = TaskSnapshot.builder()
                .from(working("workflow-complete", 1))
                .status(TaskState.COMPLETED)
                .result(TaskResult.completed(Map.of("output", "success")))
                .revision(2)
                .build();
        taskEngine.publish(completed);

        try (var client = createTestClient()) {
            client.initialize();
            var resultJson = client.sendRpc("""
                    {"jsonrpc":"2.0","id":3,"method":"tasks/result","params":{"taskId":"workflow-complete"}}
                    """);
            assertThatJsonRpcResponse(resultJson).isSuccess().hasStructuredContent("""
                    {"output":"success"}
                    """);
            assertThat(taskEngine.awaitedTaskIds()).containsExactly("workflow-complete");
        }
    }

    @Test
    void unknownTaskReturnsError() throws Exception {
        taskEngine.reset();
        try (var client = createTestClient()) {
            client.initialize();
            var getJson = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/get","params":{"taskId":"missing"}}
                    """);
            assertThatJsonRpcResponse(getJson).isJsonRpcError().hasErrorCode(-32602);
        }
    }

    private static TaskSnapshot working(String taskId, long revision) {
        return TaskSnapshot.working(taskId, Instant.parse("2026-08-27T07:00:00Z"), revision);
    }
}
