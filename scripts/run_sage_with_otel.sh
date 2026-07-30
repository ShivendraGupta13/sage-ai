#!/usr/bin/env bash
# Launch Sage AI Spring Boot Application with OpenTelemetry Java Agent (macOS / Linux)

set -e

AGENT_DIR="agents"
AGENT_JAR="${AGENT_DIR}/opentelemetry-javaagent.jar"
AGENT_URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.1.0/opentelemetry-javaagent.jar"

# ── 1. Robust JAVA_HOME Enforcement ─────────────────────────────────────────
resolve_java_home() {
  if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "$JAVA_HOME"
    return
  fi

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    /usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home 2>/dev/null
    return
  fi

  for candidate in \
    /usr/lib/jvm/java-21-openjdk* \
    /usr/lib/jvm/jdk-21* \
    /Library/Java/JavaVirtualMachines/jdk-21*.jdk/Contents/Home; do
    if [ -x "$candidate/bin/java" ]; then
      echo "$candidate"
      return
    fi
  done

  if command -v java >/dev/null 2>&1; then
    dirname "$(dirname "$(readlink -f "$(command -v java)")")"
    return
  fi
}

DETECTED_JAVA_HOME=$(resolve_java_home)

if [ -n "$DETECTED_JAVA_HOME" ] && [ -x "$DETECTED_JAVA_HOME/bin/java" ]; then
  export JAVA_HOME="$DETECTED_JAVA_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
  echo "Using JAVA_HOME: $JAVA_HOME"
else
  echo "ERROR: A valid JDK 21 installation could not be found."
  echo "Please set JAVA_HOME to a valid JDK installation directory."
  exit 1
fi

# ── 2. Ensure Agent JAR Exists ─────────────────────────────────────────────
mkdir -p "${AGENT_DIR}"

if [ ! -f "${AGENT_JAR}" ]; then
  echo "Downloading OpenTelemetry Java Agent (v2.1.0)..."
  curl -L -o "${AGENT_JAR}" "${AGENT_URL}"
  echo "OpenTelemetry Agent downloaded to ${AGENT_JAR}"
fi

# ── 3. Configure OpenTelemetry Environment ──────────────────────────────────
export OTEL_SERVICE_NAME="sage-ai"
export OTEL_EXPORTER_OTLP_PROTOCOL="grpc"
export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317"
export OTEL_TRACES_EXPORTER="otlp"
export OTEL_METRICS_EXPORTER="none"
export OTEL_LOGS_EXPORTER="none"
export JAVA_TOOL_OPTIONS="-javaagent:${AGENT_JAR}"

echo "Starting Sage AI with OpenTelemetry Agent -> http://localhost:4317 ..."
./mvnw spring-boot:run
