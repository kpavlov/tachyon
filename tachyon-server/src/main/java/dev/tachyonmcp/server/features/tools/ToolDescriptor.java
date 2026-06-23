/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public interface ToolDescriptor {

    String name();

    @Nullable
    String title();

    @Nullable
    String description();

    @Nullable
    JsonNode inputSchema();

    @Nullable
    JsonNode outputSchema();

    @Nullable
    TaskSupport taskSupport();

    @Nullable
    ToolAnnotations annotations();

    @Nullable
    List<Icon> icons();

    @Nullable
    String extensionId();

    static Builder builder(String name) {
        return new Builder(name);
    }

    final class Builder {
        private final String name;
        private String title;
        private String description;
        private JsonNode inputSchema;
        private JsonNode outputSchema;
        private TaskSupport taskSupport;
        private ToolAnnotations annotations;
        private List<Icon> icons;
        private String extensionId;

        Builder(String name) {
            this.name = name;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(JsonNode inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder outputSchema(JsonNode outputSchema) {
            this.outputSchema = outputSchema;
            return this;
        }

        public Builder taskSupport(TaskSupport taskSupport) {
            this.taskSupport = taskSupport;
            return this;
        }

        public Builder annotations(ToolAnnotations annotations) {
            this.annotations = annotations;
            return this;
        }

        public Builder icons(List<Icon> icons) {
            this.icons = icons;
            return this;
        }

        public Builder extensionId(String extensionId) {
            this.extensionId = extensionId;
            return this;
        }

        public ToolDescriptor build() {
            return new DefaultToolDescriptor(
                    name, title, description, inputSchema, outputSchema, taskSupport, annotations, icons, extensionId);
        }
    }
}
