/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp.v2025_11_25;

import dev.tachyonmcp.e2e.mcp.AbstractMcpE2eTest;

public abstract class AbstractStatefulMcpE2eTest extends AbstractMcpE2eTest {

    @Override
    protected final SessionMode sessionMode() {
        return SessionMode.STATEFUL;
    }
}
