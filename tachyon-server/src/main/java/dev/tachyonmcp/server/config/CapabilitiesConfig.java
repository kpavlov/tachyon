/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.config;

import dev.tachyonmcp.server.features.Pagination;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of which MCP capabilities to enable and their behaviour.
 *
 * @param toolsMode           controls whether tools are exposed, hidden, or auto-detected
 * @param toolsListChanged    whether to emit notifications when the tool list changes
 * @param resourcesMode       controls whether resources are exposed, hidden, or auto-detected
 * @param resourcesSubscribe  whether clients can subscribe to resource change notifications
 * @param resourcesListChanged whether to emit notifications when the resource list changes
 * @param promptsMode         controls whether prompts are exposed, hidden, or auto-detected
 * @param promptsListChanged  whether to emit notifications when the prompt list changes
 * @param tasksList           whether the server supports tasks/list
 * @param tasksCancel         whether the server supports tasks/cancel
 * @param tasksRequests       whether the server supports task-augmented tool call requests
 * @param completions          whether the server supports completion
 * @param logging              whether the server supports logging
 * @param toolsPageSize        default page size for tools/list when limit is omitted (≤0 floors to {@value Pagination#DEFAULT_PAGE_SIZE})
 * @param resourcesPageSize    default page size for resources/list when limit is omitted (≤0 floors to {@value Pagination#DEFAULT_PAGE_SIZE})
 * @param promptsPageSize      default page size for prompts/list when limit is omitted (≤0 floors to {@value Pagination#DEFAULT_PAGE_SIZE})
 * @param tasksPageSize        default page size for tasks/list when limit is omitted (≤0 floors to {@value Pagination#DEFAULT_PAGE_SIZE})
 */
public record CapabilitiesConfig(
        Mode toolsMode,
        boolean toolsListChanged,
        Mode resourcesMode,
        boolean resourcesSubscribe,
        boolean resourcesListChanged,
        Mode promptsMode,
        boolean promptsListChanged,
        boolean tasksList,
        boolean tasksCancel,
        boolean tasksRequests,
        boolean completions,
        boolean logging,
        int toolsPageSize,
        int resourcesPageSize,
        int promptsPageSize,
        int tasksPageSize) {

    public CapabilitiesConfig {
        if (toolsMode == null) toolsMode = Mode.AUTO;
        if (resourcesMode == null) resourcesMode = Mode.AUTO;
        if (promptsMode == null) promptsMode = Mode.AUTO;
        if (toolsPageSize <= 0) toolsPageSize = Pagination.DEFAULT_PAGE_SIZE;
        if (resourcesPageSize <= 0) resourcesPageSize = Pagination.DEFAULT_PAGE_SIZE;
        if (promptsPageSize <= 0) promptsPageSize = Pagination.DEFAULT_PAGE_SIZE;
        if (tasksPageSize <= 0) tasksPageSize = Pagination.DEFAULT_PAGE_SIZE;
    }

    /** Default configuration with all capabilities auto-detected and change notifications off. */
    public static final CapabilitiesConfig DEFAULT = builder().build();

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link CapabilitiesConfig}. */
    public static final class Builder {

        private @Nullable Mode toolsMode;
        private boolean toolsListChanged;
        private @Nullable Mode resourcesMode;
        private boolean resourcesSubscribe;
        private boolean resourcesListChanged;
        private @Nullable Mode promptsMode;
        private boolean promptsListChanged;
        private boolean tasksList;
        private boolean tasksCancel;
        private boolean tasksRequests;
        private boolean completions;
        private boolean logging;
        private int toolsPageSize = Pagination.DEFAULT_PAGE_SIZE;
        private int resourcesPageSize = Pagination.DEFAULT_PAGE_SIZE;
        private int promptsPageSize = Pagination.DEFAULT_PAGE_SIZE;
        private int tasksPageSize = Pagination.DEFAULT_PAGE_SIZE;

        private Builder() {}

        public Builder toolsMode(Mode mode) {
            this.toolsMode = mode;
            return this;
        }

        public Builder toolsListChanged(boolean listChanged) {
            this.toolsListChanged = listChanged;
            return this;
        }

        public Builder resourcesMode(Mode mode) {
            this.resourcesMode = mode;
            return this;
        }

        public Builder resourcesSubscribe(boolean subscribe) {
            this.resourcesSubscribe = subscribe;
            return this;
        }

        public Builder resourcesListChanged(boolean listChanged) {
            this.resourcesListChanged = listChanged;
            return this;
        }

        public Builder promptsMode(Mode mode) {
            this.promptsMode = mode;
            return this;
        }

        public Builder promptsListChanged(boolean listChanged) {
            this.promptsListChanged = listChanged;
            return this;
        }

        public Builder tasksList(boolean list) {
            this.tasksList = list;
            return this;
        }

        public Builder tasksCancel(boolean cancel) {
            this.tasksCancel = cancel;
            return this;
        }

        public Builder tasksRequests(boolean requests) {
            this.tasksRequests = requests;
            return this;
        }

        public Builder completions(boolean enabled) {
            this.completions = enabled;
            return this;
        }

        public Builder logging(boolean enabled) {
            this.logging = enabled;
            return this;
        }

        /**
         * @param toolsPageSize default page size for tools/list when limit is omitted
         */
        public Builder toolsPageSize(int toolsPageSize) {
            this.toolsPageSize = toolsPageSize;
            return this;
        }

        /**
         * @param resourcesPageSize default page size for resources/list when limit is omitted
         */
        public Builder resourcesPageSize(int resourcesPageSize) {
            this.resourcesPageSize = resourcesPageSize;
            return this;
        }

        /**
         * @param promptsPageSize default page size for prompts/list when limit is omitted
         */
        public Builder promptsPageSize(int promptsPageSize) {
            this.promptsPageSize = promptsPageSize;
            return this;
        }

        /**
         * @param tasksPageSize default page size for tasks/list when limit is omitted
         */
        public Builder tasksPageSize(int tasksPageSize) {
            this.tasksPageSize = tasksPageSize;
            return this;
        }

        public CapabilitiesConfig build() {
            return new CapabilitiesConfig(
                    toolsMode != null ? toolsMode : Mode.AUTO,
                    toolsListChanged,
                    resourcesMode != null ? resourcesMode : Mode.AUTO,
                    resourcesSubscribe,
                    resourcesListChanged,
                    promptsMode != null ? promptsMode : Mode.AUTO,
                    promptsListChanged,
                    tasksList,
                    tasksCancel,
                    tasksRequests,
                    completions,
                    logging,
                    toolsPageSize,
                    resourcesPageSize,
                    promptsPageSize,
                    tasksPageSize);
        }

        // === Convenience defaults ===

        public Builder completions() {
            return completions(true);
        }

        public Builder logging() {
            return logging(true);
        }

        public Builder tools() {
            return toolsMode(Mode.ON).toolsListChanged(false);
        }

        public Builder tools(boolean listChanged) {
            return toolsMode(Mode.ON).toolsListChanged(listChanged);
        }

        public Builder noTools() {
            return toolsMode(Mode.OFF);
        }

        public Builder resources() {
            return resourcesMode(Mode.ON).resourcesSubscribe(false).resourcesListChanged(false);
        }

        public Builder resources(boolean subscribe, boolean listChanged) {
            return resourcesMode(Mode.ON).resourcesSubscribe(subscribe).resourcesListChanged(listChanged);
        }

        public Builder noResources() {
            return resourcesMode(Mode.OFF);
        }

        public Builder prompts() {
            return promptsMode(Mode.ON).promptsListChanged(false);
        }

        public Builder prompts(boolean listChanged) {
            return promptsMode(Mode.ON).promptsListChanged(listChanged);
        }

        public Builder noPrompts() {
            return promptsMode(Mode.OFF);
        }

        public Builder tasks() {
            return tasksList(true).tasksCancel(false).tasksRequests(false);
        }

        public Builder tasks(boolean list, boolean cancel, boolean requests) {
            return tasksList(list).tasksCancel(cancel).tasksRequests(requests);
        }
    }
}
