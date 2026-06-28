/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.conformance;

/**
 * Test against the latest edge version of MCP conformance tests
 */
class EdgeServerConformanceIT extends AbstractServerConformanceIT {

    EdgeServerConformanceIT() {
        super(new EdgeConformanceServer(), "0.2.0-alpha.7", "conformance-baseline-0.2.yml", "edge", "2026-07-28");
    }
}
