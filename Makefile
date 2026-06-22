.PHONY: build test package conformance e2e clean format all

all: clean format build

build:
	@echo "🏗️  Building..."
	@mvn -version
	@mvn verify -q

test:
	@echo "🧪 Running tests..."
	@mvn test

package:
	@echo "📦 Packaging tachyon-mcp SNAPSHOT..."
	@mvn package -pl tachyon-mcp -DskipTests -q

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
	@echo "✅ Done"

.PHONY: inspector
inspector:
	@echo "🧐 MCP Inspector"
	@npx -y @modelcontextprotocol/inspector
