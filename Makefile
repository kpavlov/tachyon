.PHONY: all ci build test lint package conformance e2e clean format help inspector examples examples-snapshot

.DEFAULT_GOAL := help

ifeq ($(CI),true)
SUREFIRE_FORK_COUNT ?= 1
NETTY_ARGS := -Dio.netty.eventLoopThreads=2
else
SUREFIRE_FORK_COUNT ?= 1C
NETTY_ARGS :=
endif

MAVEN_TEST_ARGS := -Dsurefire.forkCount=$(SUREFIRE_FORK_COUNT) $(NETTY_ARGS)

help: ## List available targets
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-22s %s\n", $$1, $$2}'

all: clean format build examples-snapshot examples ## Full build: clean, format, build, examples

ci: clean build ## CI pipeline: clean + build

build: ## Compile, test, verify (mvn verify)
	@echo " 🏗️ Building..."
	@./mvnw -version
	@./mvnw verify $(MAVEN_TEST_ARGS) --no-transfer-progress

test: ## Run unit + e2e tests
	@echo " 🧪 Running tests..."
	@./mvnw test $(MAVEN_TEST_ARGS) --no-transfer-progress

package: ## Install artifacts to local Maven repo (skip tests)
	@echo "📦 Packaging and installing tachyon-server to local repository..."
	@rm -rf ~/.m2/repository/dev/tachyonmcp/
	@./mvnw install -pl tachyon-server-kotlin -am -DskipTests -Dspotbugs.skip -Dspotless.skip

examples: ## Build live examples against published artifacts
	@echo "🌤️ 📡  Building LIVE examples..."
	@./mvnw verify -f examples/weather/pom.xml --no-transfer-progress
	@./mvnw verify -f examples/echo-kotlin/pom.xml --no-transfer-progress
	@echo " ✅ Done!"

examples-snapshot: package ## Build examples against local SNAPSHOT artifacts
	@echo "🌤️ 🎬 Building SNAPSHOT examples..."
	@./mvnw verify -f examples/weather/pom.xml -Dtachyon-server.version=1.0.0-SNAPSHOT --no-transfer-progress
	@./mvnw verify -f examples/echo-kotlin/pom.xml -Dtachyon-server.version=1.0.0-SNAPSHOT --no-transfer-progress
	@echo " ✅  Done!"

conformance: ## Run MCP conformance suite
	@echo " 🔄  Running MCP conformance suite..."
	@rm -rf conformance/target/surefire-reports
	@./mvnw test -am -pl conformance $(MAVEN_TEST_ARGS)

e2e: package ## Run end-to-end tests
	@echo " 🔗  Running end-to-end tests..."
	@./mvnw test -pl e2e -am $(MAVEN_TEST_ARGS)

clean: ## Remove all build artifacts
	@echo " 🧹  Cleaning..."
	@find . -type d -name target -exec rm -rf {} +
	@echo " ✅  All clean!"

format: ## Auto-format code (Spotless + Detekt)
	@echo " 🎨  Formatting code..."
	@./mvnw spotless:apply -q
	@./mvnw install -pl tachyon-server -DskipTests -Dspotbugs.skip -Dspotless.skip -q
	@./mvnw exec:java@detekt-format -pl tachyon-server-kotlin -am -q
	@echo " ✅  Done..."

lint: ## Check code style and bugs (Spotless + Detekt + SpotBugs)
	@echo " 🔍  Linting code..."
	@./mvnw spotless:check -pl !reports
	@./mvnw exec:java@detekt -pl tachyon-server-kotlin
	@./mvnw spotbugs:check -pl !reports,!e2e
	@echo " ✅  Done..."

inspector: ## Launch MCP Inspector UI
	@echo "🧐 MCP Inspector"
	@npx -y @modelcontextprotocol/inspector --config mcp-inspector.json
