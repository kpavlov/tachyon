/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.config;

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class CapabilitiesConfig {

    @Value.Default
    public Mode toolsMode() {
        return Mode.AUTO;
    }

    @Value.Default
    public boolean toolsListChanged() {
        return false;
    }

    @Value.Default
    public Mode resourcesMode() {
        return Mode.AUTO;
    }

    @Value.Default
    public boolean resourcesSubscribe() {
        return false;
    }

    @Value.Default
    public boolean resourcesListChanged() {
        return false;
    }

    @Value.Default
    public Mode promptsMode() {
        return Mode.AUTO;
    }

    @Value.Default
    public boolean promptsListChanged() {
        return false;
    }

    @Value.Default
    public boolean tasksList() {
        return false;
    }

    @Value.Default
    public boolean tasksCancel() {
        return false;
    }

    @Value.Default
    public boolean tasksRequests() {
        return false;
    }

    @Value.Default
    public boolean completions() {
        return false;
    }

    @Value.Default
    public boolean logging() {
        return false;
    }

    public static final CapabilitiesConfig DEFAULT = builder().build();

    public static Builder builder() {
        return ImmutableCapabilitiesConfig.builder();
    }

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
