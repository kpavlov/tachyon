/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.conformance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

class ConformanceReportWriter {

    private static final Pattern SCENARIO_LINE = Pattern.compile("^[✗✓] (.+): (\\d+) passed, (\\d+) failed$");

    record ScenarioResult(String name, int passed, int failed) {}

    static List<String> parseBaseline(Path baselinePath) throws IOException {
        try (var lines = Files.lines(baselinePath)) {
            return lines.filter(l -> l.startsWith("  - "))
                    .map(l -> l.substring(4).strip())
                    .toList();
        }
    }

    static List<ScenarioResult> parseResults(List<String> outputLines) {
        var results = new ArrayList<ScenarioResult>();
        boolean inSummary = false;
        for (var line : outputLines) {
            if (line.startsWith("=== SUMMARY ===")) {
                inSummary = true;
                continue;
            }
            if (!inSummary) continue;
            if (line.startsWith("Total:")) continue;
            var matcher = SCENARIO_LINE.matcher(line.trim());
            if (matcher.matches()) {
                results.add(new ScenarioResult(
                        matcher.group(1), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))));
            }
        }
        return results;
    }

    static void writeReport(
            Path outputPath, List<ScenarioResult> scenarios, List<String> expectedFailures, long totalMillis)
            throws IOException {
        var totalTests = scenarios.size();
        var totalSkipped = (int) scenarios.stream()
                .filter(s -> s.failed() > 0 && expectedFailures.contains(s.name()))
                .count();
        var totalFailures = (int) scenarios.stream()
                .filter(s -> s.failed() > 0 && !expectedFailures.contains(s.name()))
                .count();
        var now = Instant.now();

        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<testsuite name=\"MCPConformance\" tests=\"")
                .append(totalTests)
                .append("\" failures=\"")
                .append(totalFailures)
                .append("\" errors=\"0\" skipped=\"")
                .append(totalSkipped)
                .append("\" time=\"")
                .append(String.format("%.3f", totalMillis / 1000.0))
                .append("\" timestamp=\"")
                .append(now.toString())
                .append("\">\n");

        for (var scenario : scenarios) {
            sb.append("  <testcase name=\"")
                    .append(escapeXml(scenario.name()))
                    .append("\" classname=\"me.kpavlov.tachyon.mcp.conformance\" time=\"0\"");

            if (scenario.failed() > 0) {
                if (expectedFailures.contains(scenario.name())) {
                    sb.append(">\n    <skipped message=\"Expected failure (baseline)\"/>\n  </testcase>\n");
                } else {
                    sb.append(">\n    <failure message=\"")
                            .append(scenario.passed())
                            .append(" passed, ")
                            .append(scenario.failed())
                            .append(" failed")
                            .append("\" type=\"conformance\">\n");
                    sb.append("    </failure>\n  </testcase>\n");
                }
            } else {
                sb.append("/>\n");
            }
        }

        sb.append("</testsuite>\n");

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
