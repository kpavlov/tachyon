/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.config.Mode;
import dev.tachyonmcp.api.server.features.tasks.TaskExecutionEngine;
import dev.tachyonmcp.api.server.features.tasks.TaskFeature;
import dev.tachyonmcp.api.server.features.tasks.TaskInput;
import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import dev.tachyonmcp.core.server.features.Pagination;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CapabilitiesConfigTest {

    @Test
    void defaultPageSizeIs50() {
        var config = CapabilitiesConfig.DEFAULT;

        assertThat(config.tools().pageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
        assertThat(config.resources().pageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
        assertThat(config.prompts().pageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
        assertThat(config.tasks().pageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void rejectsNonPositiveToolsPageSize(int size) {
        assertThatThrownBy(
                        () -> CapabilitiesConfig.builder().toolsPageSize(size).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void rejectsNonPositiveResourcesPageSize(int size) {
        assertThatThrownBy(() ->
                        CapabilitiesConfig.builder().resourcesPageSize(size).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void rejectsNonPositiveTasksPageSize(int size) {
        assertThatThrownBy(
                        () -> CapabilitiesConfig.builder().tasksPageSize(size).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void rejectsNonPositivePromptsPageSize(int size) {
        assertThatThrownBy(
                        () -> CapabilitiesConfig.builder().promptsPageSize(size).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesPositiveToolsPageSize() {
        var config = CapabilitiesConfig.builder().toolsPageSize(10).build();

        assertThat(config.tools().pageSize()).isEqualTo(10);
    }

    @Test
    void chainedFlatSettersAccumulateOnSameSubConfig() {
        var config = CapabilitiesConfig.builder().tools().toolsPageSize(2).build();

        assertThat(config.tools().mode()).isEqualTo(Mode.ON);
        assertThat(config.tools().pageSize()).isEqualTo(2);
    }

    @Test
    void storesConfiguredTaskExecutionEngine() {
        var engine = new StubTaskExecutionEngine(Set.of(TaskFeature.CANCEL));

        var config =
                CapabilitiesConfig.builder().tasks(engine, false, true, false).build();

        assertThat(config.tasks().taskExecutionEngine()).isSameAs(engine);
    }

    @Test
    void rejectsFeaturesUnsupportedByTaskExecutionEngine() {
        var engine = new StubTaskExecutionEngine(Set.of(TaskFeature.LIST));

        assertThatThrownBy(() -> CapabilitiesConfig.builder()
                        .tasks(engine, true, true, true)
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StubTaskExecutionEngine")
                .hasMessageContaining("CANCEL")
                .hasMessageContaining("REQUESTS");
    }

    @Test
    void validatesTaskFeaturesAfterFlatSetterChanges() {
        var engine = new StubTaskExecutionEngine(Set.of());

        assertThatThrownBy(() -> CapabilitiesConfig.builder()
                        .tasks(engine)
                        .tasksCancel(true)
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCEL");
    }

    @Test
    void enabledTasksConfigRequiresExplicitEngine() {
        var tasks = TasksConfig.builder().enabled(true).cancel(true).build();

        assertThatThrownBy(() -> CapabilitiesConfig.builder().tasks(tasks).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tasks capability requires a TaskExecutionEngine");
    }

    private record StubTaskExecutionEngine(Set<TaskFeature> supportedFeatures) implements TaskExecutionEngine {

        @Override
        public TaskSnapshot refresh(InteractionContext context, String taskId) {
            return null;
        }

        @Override
        public TaskSnapshot cancel(InteractionContext context, String taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void submitInput(InteractionContext context, String taskId, TaskInput input) {
            throw new UnsupportedOperationException();
        }
    }
}
