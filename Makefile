.PHONY: build test package conformance e2e clean format all inspector example

all: clean format build example

build:
	@echo "🏗️ Building..."
	@mvn -version
	@mvn verify -q

test:
	@echo "🧪 Running tests..."
	@mvn test

package:
	@echo "📦 Packaging and installing tachyon-server to local repository..."
	@rm -rf target/local-repo
	@mvn install -pl tachyon-server -am -DskipTests -q

example: package
	@echo "🌤️ Building and testing weather example..."
	@mvn verify -f examples/weather/pom.xml -Dtachyon-server.version=1-SNAPSHOT

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

format:
	@echo "🎨 Formatting code with Spotless (Palantir)..."
	@mvn spotless:apply -q

.PHONY: inspector
inspector:
	@echo "🧐 MCP Inspector"
	@npx -y @modelcontextprotocol/inspector --config mcp-inspector.json
