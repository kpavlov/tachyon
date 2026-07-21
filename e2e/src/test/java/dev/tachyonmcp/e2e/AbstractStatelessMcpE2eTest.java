/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

public abstract class AbstractStatelessMcpE2eTest extends AbstractMcpE2eTest {

    @Override
    protected final SessionMode sessionMode() {
        return SessionMode.STATELESS;
    }

    @Override
    protected void startDefaultServer() {
        var h = SharedStatelessE2eServer.ensureStarted();
        this.server = h;
        this.port = h.port();
        this.usingCustomServer = false;
    }
}
