/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.server.features.Pagination;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CapabilitiesConfigTest {

    @Test
    void defaultPageSizeIs50() {
        var config = CapabilitiesConfig.DEFAULT;

        assertThat(config.toolsPageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
        assertThat(config.resourcesPageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
        assertThat(config.promptsPageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
        assertThat(config.tasksPageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
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

        assertThat(config.toolsPageSize()).isEqualTo(10);
    }
}
