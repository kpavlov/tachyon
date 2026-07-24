/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol;

import dev.tachyonmcp.annotations.InternalApi;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Registry of {@link ProtocolResponseMapper} implementations built from {@link Protocols#list()}.
 */
@InternalApi
public class ProtocolMappers {

    private ProtocolMappers() {}

    static final List<ProtocolResponseMapper> MAPPERS;

    static {
        MAPPERS = Protocols.list().stream().map(Protocol::responseMapper).toList();
        if (MAPPERS.isEmpty()) {
            throw new IllegalStateException("No ProtocolResponseMapper found.");
        }
    }

    /**
     * Returns the mapper for the given protocol family and version, or {@code null} if none registered.
     */
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
