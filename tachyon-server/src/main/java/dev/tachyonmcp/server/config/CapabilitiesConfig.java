/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.config;

import org.immutables.value.Value;

/** Configuration of which MCP capabilities to enable and their behaviour. */
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class CapabilitiesConfig {

    /** Controls whether tools are exposed ({@code ON}), hidden ({@code OFF}), or auto-detected ({@code AUTO}). */
    @Value.Default
    public Mode toolsMode() {
        return Mode.AUTO;
    }

    /** Whether to emit notifications when the tool list changes. */
    @Value.Default
    public boolean toolsListChanged() {
        return false;
    }

    /** Controls whether resources are exposed ({@code ON}), hidden ({@code OFF}), or auto-detected ({@code AUTO}). */
    @Value.Default
    public Mode resourcesMode() {
        return Mode.AUTO;
    }

    /** Whether clients can subscribe to resource change notifications. */
    @Value.Default
    public boolean resourcesSubscribe() {
        return false;
    }

    /** Whether to emit notifications when the resource list changes. */
    @Value.Default
    public boolean resourcesListChanged() {
        return false;
    }

    /** Controls whether prompts are exposed ({@code ON}), hidden ({@code OFF}), or auto-detected ({@code AUTO}). */
    @Value.Default
    public Mode promptsMode() {
        return Mode.AUTO;
    }

    /** Whether to emit notifications when the prompt list changes. */
    @Value.Default
    public boolean promptsListChanged() {
        return false;
    }

    /** Whether the server supports {@code tasks/list}. */
    @Value.Default
    public boolean tasksList() {
        return false;
    }

    /** Whether the server supports {@code tasks/cancel}. */
    @Value.Default
    public boolean tasksCancel() {
        return false;
    }

    /** Whether the server supports task-augmented tool call requests. */
    @Value.Default
    public boolean tasksRequests() {
        return false;
    }

    /** Whether the server supports completion. */
    @Value.Default
    public boolean completions() {
        return false;
    }

    /** Whether the server supports logging. */
    @Value.Default
    public boolean logging() {
        return false;
    }

    /** Default configuration with all capabilities auto-detected and change notifications off. */
    public static final CapabilitiesConfig DEFAULT = builder().build();

    public static Builder builder() {
        return ImmutableCapabilitiesConfig.builder();
    }

    /** Builder for {@link CapabilitiesConfig}. */
    public interface Builder {

        Builder toolsMode(Mode mode);

        Builder toolsListChanged(boolean v);

        Builder resourcesMode(Mode mode);

        Builder resourcesSubscribe(boolean v);

        Builder resourcesListChanged(boolean v);

        Builder promptsMode(Mode mode);

        Builder promptsListChanged(boolean v);

        Builder tasksList(boolean v);

        Builder tasksCancel(boolean v);

        Builder tasksRequests(boolean v);

        Builder completions(boolean v);

        Builder logging(boolean v);

        CapabilitiesConfig build();

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

        default Builder tasks(boolean list, boolean cancel, boolean toolCallRequests) {
            return tasksList(list).tasksCancel(cancel).tasksRequests(toolCallRequests);
        }
    }
}
