/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import org.jspecify.annotations.Nullable;

public interface Notifications {

    void send(String method, Object params);

    void progress(@Nullable Object progressToken, double progress, double total, String message);

    void info(String logger, Object data);

    void warning(String logger, Object data);

    void error(String logger, Object data);
}
