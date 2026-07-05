.PHONY: build test lint package conformance e2e clean format all ci inspector examples examples-snapshot

all: clean format build examples-snapshot examples
ci: clean build

build:
	@echo "🏗️ Building..."
	@mvn -version
	@mvn verify --no-transfer-progress

test:
	@echo "🧪 Running tests..."
	@mvn test --no-transfer-progress

package:
	@echo "📦 Packaging and installing tachyon-server to local repository..."
	@rm -rf ~/.m2/repository/dev/tachyonmcp/
	@mvn install -pl tachyon-server-kotlin -am -DskipTests -Dspotbugs.skip -Dspotless.skip

examples:
	@echo "🌤️📡 Building Live examples..."
	@mvn verify -f examples/weather/pom.xml --no-transfer-progress
	@mvn verify -f examples/echo-kotlin/pom.xml --no-transfer-progress
	@echo "✅ Done!"

examples-snapshot: package
	@echo "🌤️🎬 Building SNAPSHOT examples..."
	@mvn verify -f examples/weather/pom.xml -Dtachyon-server.version=1.0.0-SNAPSHOT --no-transfer-progress
	@mvn verify -f examples/echo-kotlin/pom.xml -Dtachyon-server.version=1.0.0-SNAPSHOT --no-transfer-progress
	@echo "✅ Done!"

conformance: package
	@echo "🔄 Running MCP conformance suite..."
	@rm -rf conformance/target/failsafe-reports/conformance-results/**
	@mvn verify -am -pl conformance

e2e: package
	@echo "🔗 Running end-to-end tests..."
	@mvn test -pl e2e -am

clean:
	@echo "🧹 Cleaning..."
	@find . -type d -name target -exec rm -rf {} +
	@echo "✅ All clean!"

format:
	@echo "🎨 Formatting code..."
	@mvn spotless:apply -q
	@mvn exec:java@detekt-format -pl tachyon-server-kotlin -q
	@echo "✅ Done..."

lint:
	@echo "🔍 Linting code..."
	@mvn spotless:check -pl !reports
	@mvn exec:java@detekt -pl tachyon-server-kotlin
	@mvn spotbugs:check -pl !reports,!e2e
	@echo "✅ Done..."

.PHONY: inspector
inspector:
	@echo "🧐 MCP Inspector"
	@npx -y @modelcontextprotocol/inspector --config mcp-inspector.json
