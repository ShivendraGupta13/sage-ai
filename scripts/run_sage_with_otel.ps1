# Launch Sage AI Spring Boot Application with OpenTelemetry Java Agent
#
# Usage:
#   .\scripts\run_sage_with_otel.ps1

$ErrorActionPreference = "Stop"

$AGENT_DIR = "agents"
$AGENT_JAR = "$AGENT_DIR/opentelemetry-javaagent.jar"
$AGENT_URL = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.1.0/opentelemetry-javaagent.jar"

# ── 1. Robust JAVA_HOME Enforcement ─────────────────────────────────────────
function Resolve-ValidJavaHome {
    # Check current JAVA_HOME env var
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        return $env:JAVA_HOME
    }

    # Known candidate paths on Windows
    $candidatePaths = @(
        "C:\Program Files\Java\jdk-21.0.12",
        "C:\Program Files\Java\jdk-21*",
        "C:\Program Files\Java\jdk*",
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Eclipse Adoptium\jdk*",
        "C:\Program Files\Amazon Corretto\jdk-21*",
        "C:\Program Files\Microsoft\jdk-21*"
    )

    foreach ($pathPattern in $candidatePaths) {
        $resolved = Get-Item -Path $pathPattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($resolved -and (Test-Path "$($resolved.FullName)\bin\java.exe")) {
            return $resolved.FullName
        }
    }

    # Derive from java.exe location if available in Path
    $javaCmd = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if ($javaCmd) {
        $parentDir = Split-Path (Split-Path $javaCmd.Source -Parent) -Parent
        if (Test-Path "$parentDir\bin\java.exe") {
            return $parentDir
        }
    }

    return $null
}

$validJavaHome = Resolve-ValidJavaHome

if (-not $validJavaHome) {
    Write-Host "ERROR: A valid JDK 21 installation could not be found." -ForegroundColor Red
    Write-Host "Please install JDK 21+ and set JAVA_HOME to the JDK installation root directory (e.g. C:\Program Files\Java\jdk-21.0.12)." -ForegroundColor Yellow
    exit 1
}

$env:JAVA_HOME = $validJavaHome
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
Write-Host "Using JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Cyan

# ── 2. Ensure Agent JAR Exists ─────────────────────────────────────────────
if (-not (Test-Path $AGENT_DIR)) {
    New-Item -ItemType Directory -Path $AGENT_DIR | Out-Null
}

if (-not (Test-Path $AGENT_JAR)) {
    Write-Host "Downloading OpenTelemetry Java Agent (v2.1.0)..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $AGENT_URL -OutFile $AGENT_JAR
    Write-Host "OpenTelemetry Agent downloaded to $AGENT_JAR" -ForegroundColor Green
}

# ── 3. Configure OpenTelemetry Environment ──────────────────────────────────
$gitCommit = (git rev-parse --short HEAD 2>$null)
if (-not $gitCommit) { $gitCommit = "unknown" }

$env:OTEL_SERVICE_NAME = "sage-ai"
$env:OTEL_RESOURCE_ATTRIBUTES = "service.name=sage-ai,service.version=0.0.1-SNAPSHOT,git.commit=$gitCommit,mlflow.source.git.commit=$gitCommit"
$env:OTEL_EXPORTER_OTLP_PROTOCOL = "grpc"
$env:OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4317"
$env:OTEL_TRACES_EXPORTER = "otlp"
$env:OTEL_METRICS_EXPORTER = "none"
$env:OTEL_LOGS_EXPORTER = "none"
$env:JAVA_TOOL_OPTIONS = "-javaagent:$AGENT_JAR"

Write-Host "Starting Sage AI with OpenTelemetry Agent -> http://localhost:4317 ..." -ForegroundColor Green

# ── 4. Launch Spring Boot ───────────────────────────────────────────────────
.\mvnw spring-boot:run
