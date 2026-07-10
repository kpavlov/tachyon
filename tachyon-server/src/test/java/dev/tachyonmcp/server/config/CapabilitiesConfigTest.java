/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.features.Pagination;
import org.junit.jupiter.api.Test;

class CapabilitiesConfigTest {

    @Test
    void defaultPageSizeIs50() {
        var config = CapabilitiesConfig.DEFAULT;

        assertThat(config.toolsPageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
        assertThat(config.resourcesPageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
        assertThat(config.promptsPageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
        assertThat(config.tasksPageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
    }

    @Test
    void floorsNonPositiveToolsPageSizeToDefault() {
        var config = CapabilitiesConfig.builder().toolsPageSize(0).build();

        assertThat(config.toolsPageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
    }

    @Test
    void floorsNegativeToolsPageSizeToDefault() {
        var config = CapabilitiesConfig.builder().toolsPageSize(-5).build();

        assertThat(config.toolsPageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
    }

    @Test
    void preservesPositiveToolsPageSize() {
        var config = CapabilitiesConfig.builder().toolsPageSize(10).build();

        assertThat(config.toolsPageSize()).isEqualTo(10);
    }
}
