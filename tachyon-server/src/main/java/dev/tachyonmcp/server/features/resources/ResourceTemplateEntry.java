/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.server.McpResourceType;
import dev.tachyonmcp.server.domain.Annotations;
import dev.tachyonmcp.server.domain.Icon;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface ResourceTemplateEntry extends McpResourceType {

    String name();

    String uriTemplate();

    @Nullable
    String description();

    @Nullable
    String mimeType();

    @Nullable
    String title();

    @Nullable
    Annotations annotations();

    @Nullable
    List<Icon> icons();

    ResourceTemplateHandler handler();

    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (uriTemplate().isBlank()) throw new IllegalArgumentException("uriTemplate must not be blank");
    }

    @Value.Check
    default void checkVariableNames() {
        var m = UriTemplatePatterns.VAR.matcher(uriTemplate());
        while (m.find()) {
            var name = m.group(1);
            if (!UriTemplatePatterns.VALID_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid URI template variable name: " + name);
            }
        }
    }

    @Value.Derived
    default List<String> paramNames() {
        var names = new ArrayList<String>();
        var m = UriTemplatePatterns.VAR.matcher(uriTemplate());
        while (m.find()) {
            names.add(m.group(1));
        }
        return List.copyOf(names);
    }

    @Value.Derived
    default Pattern compiledPattern() {
        var names = paramNames();
        var sb = new StringBuilder("^");
        var m = UriTemplatePatterns.VAR.matcher(uriTemplate());
        int last = 0;
        int i = 0;
        while (m.find()) {
            sb.append(Pattern.quote(uriTemplate().substring(last, m.start())));
            sb.append("(?<").append(names.get(i++)).append(">[^/]+)");
            last = m.end();
        }
        sb.append(Pattern.quote(uriTemplate().substring(last)));
        sb.append("$");
        return Pattern.compile(sb.toString());
    }

    static ResourceTemplateEntry of(
            String name,
            String uriTemplate,
            @Nullable String description,
            @Nullable String mimeType,
            ResourceTemplateHandler handler) {
        return DefaultResourceTemplateEntry.of(name, uriTemplate, description, mimeType, null, null, null, handler);
    }

    static ResourceTemplateEntry of(
            String name,
            String uriTemplate,
            @Nullable String description,
            @Nullable String mimeType,
            @Nullable String title,
            @Nullable Annotations annotations,
            @Nullable List<Icon> icons,
            ResourceTemplateHandler handler) {
        return DefaultResourceTemplateEntry.of(
                name, uriTemplate, description, mimeType, title, annotations, icons, handler);
    }
}
