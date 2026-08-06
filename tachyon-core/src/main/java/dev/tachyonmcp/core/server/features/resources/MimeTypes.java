/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Default filename-extension-to-MIME-type mapping for resource and skill content.
 *
 * <p>The JDK's own {@code URLConnection.guessContentTypeFromName} and {@code
 * Files.probeContentType} are platform-dependent and miss common extensions such as {@code .yaml}
 * or {@code .toml}, so this table is maintained explicitly, loaded from the {@code
 * mime-types.csv} classpath resource bundled alongside this class ({@code extension,mimeType,
 * isText} rows). {@link #guess(String)} falls back to {@code application/octet-stream} for
 * unknown extensions; {@link #guess(String, Map)} lets a caller override or extend individual
 * extensions without replacing the whole table. {@link #isText(String)} reads the {@code isText}
 * column for a known MIME type rather than guessing.
 */
public final class MimeTypes {

    /** MIME type returned by {@link #guess} for an extension with no known mapping. */
    public static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    private static final Map<String, String> DEFAULTS;
    private static final Map<String, Boolean> TEXTUAL;

    static {
        var byExtension = new HashMap<String, String>();
        var textByMimeType = new HashMap<String, Boolean>();
        try (var in = MimeTypes.class.getResourceAsStream("mime-types.csv")) {
            if (in == null) {
                throw new IllegalStateException("mime-types.csv not found on classpath");
            }
            try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                reader.readLine(); // header
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    var columns = line.split(",", 3);
                    byExtension.put(columns[0], columns[1]);
                    textByMimeType.put(columns[1], Boolean.parseBoolean(columns[2]));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        DEFAULTS = Map.copyOf(byExtension);
        TEXTUAL = Map.copyOf(textByMimeType);
    }

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
     * UTF-8 text rather than a binary blob, per the {@code isText} column of {@code
     * mime-types.csv}. Falls back to the {@code text/} prefix convention for a MIME type the table
     * doesn't know, e.g. one supplied via a {@link #guess(String, Map)} override.
     *
     * @param mimeType the MIME type to classify
     * @return {@code true} when the content should be read/served as text
     */
    public static boolean isText(String mimeType) {
        var known = TEXTUAL.get(mimeType);
        return known != null ? known : mimeType.startsWith("text/");
    }

    private static String extensionOf(String fileName) {
        var lower = fileName.toLowerCase(Locale.ROOT);
        var dot = lower.lastIndexOf('.');
        return dot >= 0 ? lower.substring(dot + 1) : "";
    }
}
