/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import dev.tachyonmcp.transport.netty.NettyServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Tag("conformance")
abstract class AbstractServerConformanceTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractServerConformanceTest.class);
    private final AbstractConformanceServer serverFactory;
    private final String conformanceVersion;
    private final String baselineFileName;
    private final String suiteName;
    private final String protocolVersion;
    private final boolean stateful;

    AbstractServerConformanceTest(
            AbstractConformanceServer serverFactory,
            String conformanceVersion,
            String baselineFileName,
            String suiteName,
            String protocolVersion,
            boolean stateful) {
        this.serverFactory = serverFactory;
        this.conformanceVersion = conformanceVersion;
        this.baselineFileName = baselineFileName;
        this.suiteName = suiteName;
        this.protocolVersion = protocolVersion;
        this.stateful = stateful;
    }

    @TestFactory
    Stream<DynamicTest> serverConformance() throws Exception {
        assumeTrue(ConformanceRunner.isNpxAvailable(), "npx is not available on this system");

        var outputDir = "target/surefire-reports/" + suiteName + "-conformance-results";

        var baselinePath = Path.of(baselineFileName);
        var expectedFailures =
                Files.exists(baselinePath) ? ConformanceReportWriter.parseBaseline(baselinePath) : List.<String>of();

        var tempRunner =
                new ConformanceRunner("http://localhost:0/mcp", conformanceVersion, outputDir, baselineFileName);
        var scenarios = tempRunner.listScenarios(protocolVersion);

        if (scenarios.isEmpty()) {
            System.out.println("[conformance] No scenarios match protocol version " + protocolVersion + " for suite "
                    + suiteName + " — all tests disabled");
        }

        return scenarios.stream()
                .map(scenario ->
                        dynamicTest(scenario, () -> runConformanceScenario(scenario, outputDir, expectedFailures)));
    }

    @Test
    @Disabled("Run it manually")
    void runAll() throws IOException, InterruptedException {

        var outputDir = "target/surefire-reports/" + suiteName + "-conformance-results";

        var server = serverFactory.startServer(stateful);
        try (server;
                var nettyServer = new NettyServer(0, server)) {
            var port = nettyServer.port();

            var runner = new ConformanceRunner(
                    "http://localhost:" + port + "/mcp", conformanceVersion, outputDir, baselineFileName);

            log.info("[conformance] Running suite {} successfully", suiteName);

            var result = runner.runSuite("all", protocolVersion);

            System.out.println("[conformance] Result " + String.join("\n", result.outputLines()) + " successfully");

            if (!result.passed()) {
                fail("[conformance] Suite " + suiteName + " failed");
            }
        }
    }

    private void runConformanceScenario(String scenario, String outputDir, List<String> expectedFailures)
            throws Exception {
        var server = serverFactory.startServer(stateful);
        try (server;
                var nettyServer = new NettyServer(0, server)) {
            var port = nettyServer.port();
            var scenarioRunner = new ConformanceRunner(
                    "http://localhost:" + port + "/mcp", conformanceVersion, outputDir, baselineFileName);

            System.out.println("[conformance] Running: " + scenario);
            var result = scenarioRunner.runScenario(scenario, protocolVersion);
            var outputLines = result.outputLines();
            outputLines.forEach(line -> System.out.println("[conformance] " + line));

            var rawFile = Path.of(outputDir, suiteName + "-" + scenario + "-raw.log");
            Files.createDirectories(rawFile.getParent());
            Files.write(rawFile, outputLines);

            var parsed = ConformanceReportWriter.parseResults(outputLines);
            int failed = parsed.isEmpty() ? 0 : parsed.getFirst().failed();
            boolean processFailed = !result.passed();

            if (processFailed && parsed.isEmpty()) {
                fail("Conformance process failed for scenario " + scenario
                        + " (exit code " + result.exitCode() + ")"
                        + " and no summary was found in output");
            }

            if (failed > 0 && expectedFailures.contains(scenario)) {
                abort("Expected failure (baseline) for scenario " + scenario);
            }

            if (processFailed) {
                fail("Conformance process failed for scenario " + scenario + " (exit code " + result.exitCode() + ")");
            }

            assertThat(failed).as("Scenario %s failed unexpectedly", scenario).isEqualTo(0);
        }
    }
}
