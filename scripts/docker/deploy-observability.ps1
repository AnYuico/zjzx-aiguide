[CmdletBinding()]
param(
    [string]$NetworkName = "zjzx-net",
    [string]$GrafanaAdminPassword = $env:GRAFANA_ADMIN_PASSWORD,
    [switch]$Pull
)

$ErrorActionPreference = "Stop"
$composeFile = Join-Path $PSScriptRoot "observability\docker-compose.yml"

function Assert-DockerCommand {
    param([string]$Description)

    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed: $Description"
    }
}

docker version *> $null
Assert-DockerCommand "connect to Docker Desktop"

$existingNetwork = docker network ls `
    --filter "name=^${NetworkName}$" `
    --format "{{.Name}}"
Assert-DockerCommand "list Docker networks"
if ($existingNetwork -notcontains $NetworkName) {
    docker network create $NetworkName | Out-Null
    Assert-DockerCommand "create network '$NetworkName'"
}

if ([string]::IsNullOrWhiteSpace($GrafanaAdminPassword)) {
    $securePassword = Read-Host "Grafana admin password" -AsSecureString
    $credential = [System.Management.Automation.PSCredential]::new(
        "admin",
        $securePassword
    )
    $GrafanaAdminPassword = $credential.GetNetworkCredential().Password
}
if ([string]::IsNullOrWhiteSpace($GrafanaAdminPassword)) {
    throw "Grafana admin password must not be empty."
}

$env:GRAFANA_ADMIN_PASSWORD = $GrafanaAdminPassword
if ($Pull) {
    $pulled = $false
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        docker compose -f $composeFile pull
        if ($LASTEXITCODE -eq 0) {
            $pulled = $true
            break
        }
        if ($attempt -lt 3) {
            Start-Sleep -Seconds (5 * $attempt)
        }
    }
    if (-not $pulled) {
        throw "Docker command failed after 3 attempts: pull observability images"
    }
}

docker compose -f $composeFile up -d
Assert-DockerCommand "start observability stack"

$healthEndpoints = [ordered]@{
    "OTel Collector" = "http://127.0.0.1:13133/"
    "Tempo" = "http://127.0.0.1:3200/ready"
    "Prometheus" = "http://127.0.0.1:9090/-/ready"
    "Grafana" = "http://127.0.0.1:3000/api/health"
}

foreach ($entry in $healthEndpoints.GetEnumerator()) {
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try {
            Invoke-WebRequest `
                -Uri $entry.Value `
                -UseBasicParsing `
                -TimeoutSec 2 | Out-Null
            $ready = $true
            break
        }
        catch {
            Start-Sleep -Seconds 2
        }
    }
    Write-Host ("{0,-16} {1}" -f $entry.Key, $(if ($ready) {
        "ready"
    } else {
        "not ready"
    }))
}

Write-Host "Grafana:           http://127.0.0.1:3000"
Write-Host "Prometheus:        http://127.0.0.1:9090"
Write-Host "Tempo:             http://127.0.0.1:3200"
Write-Host "OTLP HTTP traces:  http://127.0.0.1:4318/v1/traces"
