/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.resources;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Default filename-extension-to-MIME-type mapping for resource and skill content.
 *
 * <p>The JDK's own {@code URLConnection.guessContentTypeFromName} and {@code
 * Files.probeContentType} are platform-dependent and miss common extensions such as {@code .yaml}
 * or {@code .toml}, so this table is maintained explicitly, loaded from the {@code
 * mime-types.properties} classpath resource bundled alongside this class. {@link #guess(String)}
 * falls back to {@code application/octet-stream} for unknown extensions; {@link #guess(String,
 * Map)} lets a caller override or extend individual extensions without replacing the whole table.
 */
public final class MimeTypes {

    /** MIME type returned by {@link #guess} for an extension with no known mapping. */
    public static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    private static final Map<String, String> DEFAULTS = loadDefaults();

    /**
     * Non-{@code text/}-prefixed types in {@link #DEFAULTS} that are nonetheless textual, so
     * {@link #isText(String)} can classify them alongside every {@code text/*} type.
     */
    private static final Set<String> TEXTUAL_TYPES = Set.of("application/json", "application/yaml", "application/toml");

    private MimeTypes() {}

    /**
     * Guesses the MIME type for {@code fileName} from its extension, using the default table.
     *
     * @param fileName the file name (only its extension is inspected)
     * @return the guessed MIME type, or {@link #DEFAULT_MIME_TYPE} when the extension is unknown
     */
    public static String guess(String fileName) {
        return guess(fileName, Map.of());
    }

    /**
     * Guesses the MIME type for {@code fileName} from its extension, consulting {@code overrides}
     * before the default table.
     *
     * @param fileName the file name (only its extension is inspected)
     * @param overrides extension (without the leading {@code .}, case-insensitive) to MIME type,
     *     checked before the default table
     * @return the guessed MIME type, or {@link #DEFAULT_MIME_TYPE} when the extension is unknown
     */
    public static String guess(String fileName, Map<String, String> overrides) {
        var extension = extensionOf(fileName);
        var overridden = overrides.get(extension);
        if (overridden != null) {
            return overridden;
        }
        return DEFAULTS.getOrDefault(extension, DEFAULT_MIME_TYPE);
    }

    /**
     * Returns whether {@code mimeType} represents textual content, i.e. it should be delivered as
     * UTF-8 text rather than a binary blob. Covers every {@code text/*} type plus the non-{@code
     * text/}-prefixed textual types this class's default table can produce ({@code
     * application/json}, {@code application/yaml}, {@code application/toml}).
     *
     * @param mimeType the MIME type to classify
     * @return {@code true} when the content should be read/served as text
     */
    public static boolean isText(String mimeType) {
        return mimeType.startsWith("text/") || TEXTUAL_TYPES.contains(mimeType);
    }

    private static String extensionOf(String fileName) {
        var lower = fileName.toLowerCase(Locale.ROOT);
        var dot = lower.lastIndexOf('.');
        return dot >= 0 ? lower.substring(dot + 1) : "";
    }

    private static Map<String, String> loadDefaults() {
        var properties = new Properties();
        try (var in = MimeTypes.class.getResourceAsStream("mime-types.properties")) {
            if (in == null) {
                throw new IllegalStateException("mime-types.properties not found on classpath");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var defaults = new HashMap<String, String>(properties.size());
        for (var name : properties.stringPropertyNames()) {
            defaults.put(name, properties.getProperty(name));
        }
        return Map.copyOf(defaults);
    }
}
