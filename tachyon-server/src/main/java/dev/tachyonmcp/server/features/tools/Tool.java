/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tools;

/**
 * Represents a tool with a descriptor and handler.
 * A tool provides a means of performing operations or tasks,
 * with its behavior defined by its descriptor and handler.
 *
 * @author Konstantin Pavlov
 */
public interface Tool {

    ToolDescriptor descriptor();

    ToolHandler handler();
}
