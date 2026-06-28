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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

class ConformanceReportWriter {

    private static final Pattern SCENARIO_LINE = Pattern.compile("^[✗✓] (.+): (\\d+) passed, (\\d+) failed$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record ScenarioResult(String name, int passed, int failed) {}

    record SpecReference(String id, String url) {}

    record CheckDetail(
            String id,
            String name,
            String description,
            String status,
            String errorMessage,
            List<SpecReference> specReferences,
            JsonNode details) {}

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

    static List<CheckDetail> readChecks(Path outputDir) throws IOException {
        if (!Files.isDirectory(outputDir)) {
            return List.of();
        }
        var checks = new ArrayList<CheckDetail>();
        try (var files = Files.walk(outputDir)) {
            var jsonFiles = files.filter(p -> p.endsWith("checks.json")).toList();
            for (var jsonFile : jsonFiles) {
                var root = MAPPER.readTree(jsonFile.toFile());
                if (root instanceof ArrayNode array) {
                    for (var node : array) {
                        checks.add(parseCheck(node));
                    }
                }
            }
        }
        return checks;
    }

    private static CheckDetail parseCheck(JsonNode node) {
        var specRefs = new ArrayList<SpecReference>();
        var refsNode = node.get("specReferences");
        if (refsNode instanceof ArrayNode refs) {
            for (var ref : refs) {
                specRefs.add(new SpecReference(
                        ref.get("id").asString(), ref.get("url").asString()));
            }
        }
        return new CheckDetail(
                node.get("id").asString(),
                node.get("name").asString(),
                node.get("description").asString(),
                node.get("status").asString(),
                node.has("errorMessage") ? node.get("errorMessage").asString() : "<none>",
                specRefs,
                node.get("details"));
    }

    static void logFailedScenarios(
            List<ScenarioResult> scenarios, List<String> expectedFailures, List<CheckDetail> checks) {
        var failed = scenarios.stream().filter(s -> s.failed() > 0).toList();
        if (failed.isEmpty()) return;
        System.out.println("[conformance] Results (⚠️-baseline):");
        for (var scenario : failed) {
            var isBaseline = expectedFailures.contains(scenario.name());
            var marker = isBaseline ? "⚠️" : "❌";
            System.out.println("  " + marker + " " + scenario.name() + " (passed: " + scenario.passed() + ", failed: "
                    + scenario.failed() + ")");
            for (var check : checks) {
                if (!check.name().equals(scenario.name()) || !"FAILURE".equals(check.status())) continue;
                System.out.println("      " + check.description());
                System.out.println("      error: " + check.errorMessage());
                System.out.println("      details: " + check.details());
            }
        }
    }

    static void writeReport(
            Path outputPath,
            List<ScenarioResult> scenarios,
            List<String> expectedFailures,
            long totalMillis,
            List<CheckDetail> checks)
            throws IOException {
        logFailedScenarios(scenarios, expectedFailures, checks);
        var xml = buildXmlReport(scenarios, expectedFailures, totalMillis, checks);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, xml, StandardCharsets.UTF_8);
    }

    private static String buildXmlReport(
            List<ScenarioResult> scenarios, List<String> expectedFailures, long totalMillis, List<CheckDetail> checks) {
        var totalTests = scenarios.size();
        var totalSkipped = (int) scenarios.stream()
                .filter(s -> s.failed() > 0 && expectedFailures.contains(s.name()))
                .count();
        var totalFailures = (int) scenarios.stream()
                .filter(s -> s.failed() > 0 && !expectedFailures.contains(s.name()))
                .count();

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
                .append(Instant.now())
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
                    appendFailedChecks(sb, scenario.name(), checks);
                    sb.append("    </failure>\n  </testcase>\n");
                }
            } else {
                sb.append("/>\n");
            }
        }

        sb.append("</testsuite>\n");
        return sb.toString();
    }

    private static void appendFailedChecks(StringBuilder sb, String scenarioName, List<CheckDetail> checks) {
        var matching = checks.stream()
                .filter(c -> c.name().equals(scenarioName) && "FAILURE".equals(c.status()))
                .toList();
        for (var check : matching) {
            sb.append("      <check id=\"").append(escapeXml(check.id())).append("\">\n");
            sb.append("        <description>")
                    .append(escapeXml(check.description()))
                    .append("</description>\n");
            sb.append("        <errorMessage>")
                    .append(escapeXml(check.errorMessage()))
                    .append("</errorMessage>\n");
            sb.append("        <details>")
                    .append(escapeXml(check.details().toString()))
                    .append("</details>\n");
            if (!check.specReferences().isEmpty()) {
                sb.append("        <specReferences>\n");
                for (var ref : check.specReferences()) {
                    sb.append("          <specReference id=\"")
                            .append(escapeXml(ref.id()))
                            .append("\" url=\"")
                            .append(escapeXml(ref.url()))
                            .append("\"/>\n");
                }
                sb.append("        </specReferences>\n");
            }
            sb.append("      </check>\n");
        }
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
