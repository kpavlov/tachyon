/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e;

abstract class AbstractStatefulMcpE2eTest extends AbstractMcpE2eTest {

    @Override
    protected final SessionMode sessionMode() {
        return SessionMode.STATEFUL;
    }
}
