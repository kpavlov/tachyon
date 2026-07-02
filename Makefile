.PHONY: build test lint package conformance e2e clean format all inspector examples

all: clean format lint build examples

build:
	@echo "🏗️ Building..."
	@mvn -version
	@mvn verify -q

test:
	@echo "🧪 Running tests..."
	@mvn test

package:
	@echo "📦 Packaging and installing tachyon-server to local repository..."
	@rm -rf ~/.m2/repository/dev/tachyonmcp
	@mvn install -pl tachyon-server,tachyon-server-kotlin,e2e -am -DskipTests

examples: package
	@echo "🌤️ Building and testing weather example..."
	@mvn verify -f examples/weather/pom.xml -Dtachyon-server.version=1.0.0-SNAPSHOT --no-transfer-progress
	@mvn verify -f examples/echo-kotlin/pom.xml -Dtachyon-server.version=1.0.0-SNAPSHOT --no-transfer-progress

conformance: package
	@echo "🔄 Running MCP conformance suite..."
	@rm -rf conformance/target/failsafe-reports/conformance-results/**
	@mvn verify -am -pl conformance

e2e: package
	@echo "🔗 Running end-to-end tests..."
	@mvn test -pl e2e -am

clean:
	@echo "🧹 Cleaning..."
	@mvn clean -q
	@mvn clean -q -f examples/weather/pom.xml

format:
	@echo "🎨 Formatting code..."
	@mvn spotless:apply -q
	@mvn exec:java@detekt-format -pl tachyon-server-kotlin -q
	@echo "✅ Done..."

lint:
	@echo "🔍 Linting code..."
	@mvn spotless:check -pl !reports
	@mvn exec:java@detekt -pl tachyon-server-kotlin
	@mvn spotbugs:check -pl !reports

.PHONY: inspector
inspector:
	@echo "🧐 MCP Inspector"
	@npx -y @modelcontextprotocol/inspector --config mcp-inspector.json
