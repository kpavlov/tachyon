/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.config;

import dev.tachyonmcp.server.features.Pagination;
import org.immutables.value.Value;

/**
 * Configuration of which MCP capabilities to enable and their behaviour.
 */
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE, typeImmutable = "Default*")
public interface CapabilitiesConfig {

    @Value.Default
    default Mode toolsMode() {
        return Mode.AUTO;
    }

    @Value.Default
    default boolean toolsListChanged() {
        return false;
    }

    @Value.Default
    default Mode resourcesMode() {
        return Mode.AUTO;
    }

    @Value.Default
    default boolean resourcesSubscribe() {
        return false;
    }

    @Value.Default
    default boolean resourcesListChanged() {
        return false;
    }

    @Value.Default
    default Mode promptsMode() {
        return Mode.AUTO;
    }

    @Value.Default
    default boolean promptsListChanged() {
        return false;
    }

    @Value.Default
    default boolean tasksList() {
        return false;
    }

    @Value.Default
    default boolean tasksCancel() {
        return false;
    }

    @Value.Default
    default boolean tasksRequests() {
        return false;
    }

    @Value.Default
    default boolean completions() {
        return false;
    }

    @Value.Default
    default boolean logging() {
        return false;
    }

    @Value.Default
    default int toolsPageSize() {
        return Pagination.DEFAULT_PAGE_SIZE;
    }

    @Value.Default
    default int resourcesPageSize() {
        return Pagination.DEFAULT_PAGE_SIZE;
    }

    @Value.Default
    default int promptsPageSize() {
        return Pagination.DEFAULT_PAGE_SIZE;
    }

    @Value.Default
    default int tasksPageSize() {
        return Pagination.DEFAULT_PAGE_SIZE;
    }

    @Value.Check
    default void check() {
        if (toolsPageSize() <= 0) {
            throw new IllegalArgumentException("toolsPageSize must be positive, got: " + toolsPageSize());
        }
        if (resourcesPageSize() <= 0) {
            throw new IllegalArgumentException("resourcesPageSize must be positive, got: " + resourcesPageSize());
        }
        if (promptsPageSize() <= 0) {
            throw new IllegalArgumentException("promptsPageSize must be positive, got: " + promptsPageSize());
        }
        if (tasksPageSize() <= 0) {
            throw new IllegalArgumentException("tasksPageSize must be positive, got: " + tasksPageSize());
        }
    }

    /** Default configuration with all capabilities auto-detected and change notifications off. */
    CapabilitiesConfig DEFAULT = builder().build();

    static Builder builder() {
        return DefaultCapabilitiesConfig.builder();
    }

    /** Builder for {@link CapabilitiesConfig}. */
    interface Builder {

        Builder toolsMode(Mode toolsMode);

        Builder toolsListChanged(boolean toolsListChanged);

        Builder resourcesMode(Mode resourcesMode);

        Builder resourcesSubscribe(boolean resourcesSubscribe);

        Builder resourcesListChanged(boolean resourcesListChanged);

        Builder promptsMode(Mode promptsMode);

        Builder promptsListChanged(boolean promptsListChanged);

        Builder tasksList(boolean tasksList);

        Builder tasksCancel(boolean tasksCancel);

        Builder tasksRequests(boolean tasksRequests);

        Builder completions(boolean completions);

        Builder logging(boolean logging);

        Builder toolsPageSize(int toolsPageSize);

        Builder resourcesPageSize(int resourcesPageSize);

        Builder promptsPageSize(int promptsPageSize);

        Builder tasksPageSize(int tasksPageSize);

        CapabilitiesConfig build();

        // === Convenience defaults ===

        default Builder completions() {
            return completions(true);
        }

        default Builder logging() {
            return logging(true);
        }

        default Builder tools() {
            return toolsMode(Mode.ON).toolsListChanged(false);
        }

        default Builder tools(boolean listChanged) {
            return toolsMode(Mode.ON).toolsListChanged(listChanged);
        }

        default Builder noTools() {
            return toolsMode(Mode.OFF);
        }

        default Builder resources() {
            return resourcesMode(Mode.ON).resourcesSubscribe(false).resourcesListChanged(false);
        }

        default Builder resources(boolean subscribe, boolean listChanged) {
            return resourcesMode(Mode.ON).resourcesSubscribe(subscribe).resourcesListChanged(listChanged);
        }

        default Builder noResources() {
            return resourcesMode(Mode.OFF);
        }

        default Builder prompts() {
            return promptsMode(Mode.ON).promptsListChanged(false);
        }

        default Builder prompts(boolean listChanged) {
            return promptsMode(Mode.ON).promptsListChanged(listChanged);
        }

        default Builder noPrompts() {
            return promptsMode(Mode.OFF);
        }

        default Builder tasks() {
            return tasksList(true).tasksCancel(false).tasksRequests(false);
        }

        default Builder tasks(boolean list, boolean cancel, boolean requests) {
            return tasksList(list).tasksCancel(cancel).tasksRequests(requests);
        }
    }
}
