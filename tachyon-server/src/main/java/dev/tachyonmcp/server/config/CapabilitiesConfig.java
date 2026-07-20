/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.config;

import java.time.Duration;

/**
 * Configuration of which MCP capabilities to enable and their behaviour.
 *
 * @param tools       tools capability configuration
 * @param resources   resources capability configuration
 * @param prompts     prompts capability configuration
 * @param tasks       tasks capability configuration
 * @param completions completions enablement mode (default {@link Mode#AUTO})
 * @param logging     whether the logging capability is enabled (default {@code false})
 */
public record CapabilitiesConfig(
        FeatureConfig tools,
        ResourcesConfig resources,
        FeatureConfig prompts,
        TasksConfig tasks,
        Mode completions,
        boolean logging) {

    /**
     * Default configuration with all capabilities auto-detected and change notifications off.
     */
    public static final CapabilitiesConfig DEFAULT = new CapabilitiesConfig(
            FeatureConfig.DEFAULT,
            ResourcesConfig.DEFAULT,
            FeatureConfig.DEFAULT,
            TasksConfig.DEFAULT,
            Mode.AUTO,
            false);

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link CapabilitiesConfig}.
     */
    public static final class Builder {

        private FeatureConfig.Builder toolsBuilder = FeatureConfig.builder();
        private ResourcesConfig.Builder resourcesBuilder = ResourcesConfig.builder();
        private FeatureConfig.Builder promptsBuilder = FeatureConfig.builder();
        private TasksConfig.Builder tasksBuilder = TasksConfig.builder();
        private Mode completions = Mode.AUTO;
        private boolean logging;

        private Builder() {}

        // === Nested config setters ===

        public Builder tools(FeatureConfig config) {
            toolsBuilder = FeatureConfig.builder()
                    .mode(config.mode())
                    .listChanged(config.listChanged())
                    .pageSize(config.pageSize());
            return this;
        }

        public Builder resources(ResourcesConfig config) {
            resourcesBuilder = ResourcesConfig.builder()
                    .mode(config.mode())
                    .listChanged(config.listChanged())
                    .pageSize(config.pageSize())
                    .subscribe(config.subscribe());
            return this;
        }

        public Builder prompts(FeatureConfig config) {
            promptsBuilder = FeatureConfig.builder()
                    .mode(config.mode())
                    .listChanged(config.listChanged())
                    .pageSize(config.pageSize());
            return this;
        }

        public Builder tasks(TasksConfig config) {
            tasksBuilder = TasksConfig.builder()
                    .enabled(config.enabled())
                    .list(config.list())
                    .cancel(config.cancel())
                    .requests(config.requests())
                    .pageSize(config.pageSize())
                    .keepAlive(config.keepAlive());
            return this;
        }

        // === Flat field setters ===

        public Builder toolsMode(Mode toolsMode) {
            toolsBuilder.mode(toolsMode);
            return this;
        }

        public Builder toolsListChanged(boolean toolsListChanged) {
            toolsBuilder.listChanged(toolsListChanged);
            return this;
        }

        public Builder toolsPageSize(int toolsPageSize) {
            toolsBuilder.pageSize(toolsPageSize);
            return this;
        }

        public Builder resourcesMode(Mode resourcesMode) {
            resourcesBuilder.mode(resourcesMode);
            return this;
        }

        public Builder resourcesSubscribe(boolean resourcesSubscribe) {
            resourcesBuilder.subscribe(resourcesSubscribe);
            return this;
        }

        public Builder resourcesListChanged(boolean resourcesListChanged) {
            resourcesBuilder.listChanged(resourcesListChanged);
            return this;
        }

        public Builder resourcesPageSize(int resourcesPageSize) {
            resourcesBuilder.pageSize(resourcesPageSize);
            return this;
        }

        public Builder promptsMode(Mode promptsMode) {
            promptsBuilder.mode(promptsMode);
            return this;
        }

        public Builder promptsListChanged(boolean promptsListChanged) {
            promptsBuilder.listChanged(promptsListChanged);
            return this;
        }

        public Builder promptsPageSize(int promptsPageSize) {
            promptsBuilder.pageSize(promptsPageSize);
            return this;
        }

        public Builder completions(Mode completions) {
            this.completions = completions;
            return this;
        }

        /** @deprecated Use {@link #completions(Mode)} with {@link Mode#ON} or {@link Mode#OFF}. */
        @Deprecated
        public Builder completions(boolean enabled) {
            this.completions = enabled ? Mode.ON : Mode.OFF;
            return this;
        }

        public Builder tasksEnabled(boolean tasksEnabled) {
            tasksBuilder.enabled(tasksEnabled);
            return this;
        }

        public Builder tasksList(boolean tasksList) {
            tasksBuilder.list(tasksList);
            return this;
        }

        public Builder tasksCancel(boolean tasksCancel) {
            tasksBuilder.cancel(tasksCancel);
            return this;
        }

        public Builder tasksRequests(boolean tasksRequests) {
            tasksBuilder.requests(tasksRequests);
            return this;
        }

        public Builder tasksPageSize(int tasksPageSize) {
            tasksBuilder.pageSize(tasksPageSize);
            return this;
        }

        public Builder tasksKeepAlive(Duration tasksKeepAlive) {
            tasksBuilder.keepAlive(tasksKeepAlive);
            return this;
        }

        public Builder logging(boolean logging) {
            this.logging = logging;
            return this;
        }

        public CapabilitiesConfig build() {
            return new CapabilitiesConfig(
                    toolsBuilder.build(),
                    resourcesBuilder.build(),
                    promptsBuilder.build(),
                    tasksBuilder.build(),
                    completions,
                    logging);
        }

        // === Convenience defaults ===

        public Builder completions() {
            return completions(Mode.ON);
        }

        public Builder noCompletions() {
            return completions(Mode.OFF);
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

        public Builder noTasks() {
            return tasksEnabled(false);
        }

        public Builder tasks() {
            tasksBuilder
                    .enabled(true)
                    .list(TasksConfig.DEFAULT_TASK_LIST)
                    .cancel(TasksConfig.DEFAULT_TASK_CANCEL)
                    .requests(TasksConfig.DEFAULT_TASK_REQUESTS);
            return this;
        }

        public Builder tasks(boolean list, boolean cancel, boolean requests) {
            tasksBuilder.enabled(true).list(list).cancel(cancel).requests(requests);
            return this;
        }
    }
}
