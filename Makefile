.PHONY: all ci build test lint package conformance e2e clean format help inspector examples examples-snapshot

.DEFAULT_GOAL := help

help: ## List available targets
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-22s %s\n", $$1, $$2}'

all: clean format build examples-snapshot examples ## Full build: clean, format, build, examples

ci: clean build ## CI pipeline: clean + build

build: ## Compile, test, verify (mvn verify)
	@echo "🏗️ Building..."
	@mvn -version
	@mvn verify --no-transfer-progress

test: ## Run unit + e2e tests
	@echo "🧪 Running tests..."
	@mvn test --no-transfer-progress

package: ## Install artifacts to local Maven repo (skip tests)
	@echo "📦 Packaging and installing tachyon-server to local repository..."
	@rm -rf ~/.m2/repository/dev/tachyonmcp/
	@mvn install -pl tachyon-server-kotlin -am -DskipTests -Dspotbugs.skip -Dspotless.skip

examples: ## Build live examples against published artifacts
	@echo "🌤️📡 Building Live examples..."
	@mvn verify -f examples/weather/pom.xml --no-transfer-progress
	@mvn verify -f examples/echo-kotlin/pom.xml --no-transfer-progress
	@echo "✅ Done!"

examples-snapshot: package ## Build examples against local SNAPSHOT artifacts
	@echo "🌤️🎬 Building SNAPSHOT examples..."
	@mvn verify -f examples/weather/pom.xml -Dtachyon-server.version=1.0.0-SNAPSHOT --no-transfer-progress
	@mvn verify -f examples/echo-kotlin/pom.xml -Dtachyon-server.version=1.0.0-SNAPSHOT --no-transfer-progress
	@echo "✅ Done!"

conformance: package ## Run MCP conformance suite
	@echo "🔄 Running MCP conformance suite..."
	@rm -rf conformance/target/failsafe-reports/conformance-results/**
	@mvn verify -am -pl conformance

e2e: package ## Run end-to-end tests
	@echo "🔗 Running end-to-end tests..."
	@mvn test -pl e2e -am

clean: ## Remove all build artifacts
	@echo "🧹 Cleaning..."
	@find . -type d -name target -exec rm -rf {} +
	@echo "✅ All clean!"

format: ## Auto-format code (Spotless + Detekt)
	@echo "🎨 Formatting code..."
	@mvn spotless:apply -q
	@mvn install -pl tachyon-server -DskipTests -Dspotbugs.skip -Dspotless.skip -q
	@mvn exec:java@detekt-format -pl tachyon-server-kotlin -am -q
	@echo "✅ Done..."

lint: ## Check code style and bugs (Spotless + Detekt + SpotBugs)
	@echo "🔍 Linting code..."
	@mvn spotless:check -pl !reports
	@mvn exec:java@detekt -pl tachyon-server-kotlin
	@mvn spotbugs:check -pl !reports,!e2e
	@echo "✅ Done..."

inspector: ## Launch MCP Inspector UI
	@echo "🧐 MCP Inspector"
	@npx -y @modelcontextprotocol/inspector --config mcp-inspector.json
