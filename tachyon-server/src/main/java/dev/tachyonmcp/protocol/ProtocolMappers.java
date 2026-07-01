/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol;

import java.util.List;
import java.util.ServiceLoader;
import org.jspecify.annotations.Nullable;

/**
 * Registry of {@link ProtocolResponseMapper} implementations discovered via {@link ServiceLoader}.
 */
public class ProtocolMappers {

    private ProtocolMappers() {}

    static final List<ProtocolResponseMapper> MAPPERS;

    static {
        MAPPERS = ServiceLoader.load(ProtocolResponseMapper.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        if (MAPPERS.isEmpty()) {
            throw new IllegalStateException("No ProtocolResponseMapper found.");
        }
    }

    /** Returns the mapper for the given protocol family and version, or {@code null} if none registered. */
    @Nullable
    public static ProtocolResponseMapper getMapper(String protocolName, String protocolVersion) {
        for (var mapper : MAPPERS) {
            if (mapper.supports(protocolName, protocolVersion)) {
                return mapper;
            }
        }
        return null;
    }
}
