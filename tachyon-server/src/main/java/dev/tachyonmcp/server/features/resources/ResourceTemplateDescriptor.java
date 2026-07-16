/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.server.ServerFeature;
import dev.tachyonmcp.server.domain.Annotations;
import dev.tachyonmcp.server.domain.Icon;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface ResourceTemplateDescriptor extends ServerFeature.Descriptor {

    @Override
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

    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (uriTemplate().isBlank()) throw new IllegalArgumentException("uriTemplate must not be blank");
    }

    @Value.Check
    default void checkVariableNames() {
        var stripped = UriTemplatePatterns.VAR.matcher(uriTemplate()).replaceAll("");
        if (stripped.contains("{") || stripped.contains("}")) {
            throw new IllegalArgumentException("Malformed URI template (unmatched or empty braces): " + uriTemplate());
        }
        var m = UriTemplatePatterns.VAR.matcher(uriTemplate());
        while (m.find()) {
            var name = m.group(1);
            if (!UriTemplatePatterns.VALID_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid URI template variable name: " + name);
            }
        }
    }

    default List<String> paramNames() {
        var names = new ArrayList<String>();
        var seen = new HashSet<String>();
        var m = UriTemplatePatterns.VAR.matcher(uriTemplate());
        while (m.find()) {
            var name = m.group(1);
            if (!seen.add(name)) {
                throw new IllegalArgumentException("Duplicate URI template variable name: " + name);
            }
            names.add(name);
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

    static Builder builder() {
        return DefaultResourceTemplateDescriptor.builder();
    }

    static ResourceTemplateDescriptor of(String name, String uriTemplate) {
        return ResourceTemplateDescriptor.builder()
                .name(name)
                .uriTemplate(uriTemplate)
                .build();
    }

    static ResourceTemplateDescriptor of(
            String name,
            String uriTemplate,
            @Nullable String description,
            @Nullable String mimeType,
            @Nullable String title,
            @Nullable Annotations annotations,
            @Nullable List<Icon> icons) {
        return ResourceTemplateDescriptor.builder()
                .name(name)
                .uriTemplate(uriTemplate)
                .description(description)
                .mimeType(mimeType)
                .title(title)
                .annotations(annotations)
                .icons(icons)
                .build();
    }

    interface Builder {
        Builder name(String name);

        Builder uriTemplate(String uriTemplate);

        Builder description(@Nullable String description);

        Builder mimeType(@Nullable String mimeType);

        Builder title(@Nullable String title);

        Builder annotations(@Nullable Annotations annotations);

        Builder icons(@Nullable Iterable<? extends Icon> elements);

        ResourceTemplateDescriptor build();
    }
}
