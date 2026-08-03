[CmdletBinding()]
param(
    [string]$Image = "pgvector/pgvector:0.8.2-pg16-bookworm",
    [string]$ContainerName = "zjzx-pgvector",
    [string]$NetworkName = "zjzx-net",
    [string]$DataVolume = "zjzx-pgvector-data",
    [int]$Port = 5432,
    [string]$Database = "zjzx_agent",
    [string]$PostgresUsername = $env:AGENT_PGVECTOR_USERNAME,
    [string]$PostgresPassword = $env:AGENT_PGVECTOR_PASSWORD
)

$ErrorActionPreference = "Stop"

function Assert-DockerCommand {
    param([string]$Description)

    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed: $Description"
    }
}

docker version *> $null
Assert-DockerCommand "connect to Docker Desktop"

$localImage = docker image ls `
    --filter "reference=$Image" `
    --format "{{.Repository}}:{{.Tag}}"
Assert-DockerCommand "list local PGvector images"
if ($localImage -notcontains $Image) {
    throw "PGvector image is not available locally: $Image"
}

$existingContainer = docker container ls -a `
    --filter "name=^/$ContainerName$" `
    --format "{{.ID}}"
Assert-DockerCommand "inspect existing PGvector container"
if ($existingContainer) {
    throw "Container '$ContainerName' already exists. This script will not replace it."
}

if ([string]::IsNullOrWhiteSpace($PostgresUsername)) {
    $PostgresUsername = Read-Host "PostgreSQL username"
}
if ([string]::IsNullOrWhiteSpace($PostgresUsername)) {
    throw "PostgreSQL username must not be empty."
}

if ([string]::IsNullOrWhiteSpace($PostgresPassword)) {
    $securePassword = Read-Host "PostgreSQL password" -AsSecureString
    $credential = [System.Management.Automation.PSCredential]::new(
        $PostgresUsername,
        $securePassword
    )
    $PostgresPassword = $credential.GetNetworkCredential().Password
}
if ([string]::IsNullOrWhiteSpace($PostgresPassword)) {
    throw "PostgreSQL password must not be empty."
}

$existingNetwork = docker network ls `
    --filter "name=^${NetworkName}$" `
    --format "{{.Name}}"
Assert-DockerCommand "list Docker networks"
if ($existingNetwork -notcontains $NetworkName) {
    docker network create $NetworkName | Out-Null
    Assert-DockerCommand "create network '$NetworkName'"
}

$existingVolume = docker volume ls `
    --filter "name=^${DataVolume}$" `
    --format "{{.Name}}"
Assert-DockerCommand "list Docker volumes"
if ($existingVolume -notcontains $DataVolume) {
    docker volume create $DataVolume | Out-Null
    Assert-DockerCommand "create volume '$DataVolume'"
}

$runArguments = @(
    "run", "-d",
    "--name", $ContainerName,
    "--restart", "unless-stopped",
    "--network", $NetworkName,
    "--memory", "768m",
    "--health-cmd", "pg_isready -U $PostgresUsername -d $Database",
    "--health-interval", "10s",
    "--health-timeout", "5s",
    "--health-retries", "10",
    "--health-start-period", "20s",
    "-p", "127.0.0.1:${Port}:5432",
    "-v", "${DataVolume}:/var/lib/postgresql/data",
    "-e", "POSTGRES_DB=$Database",
    "-e", "POSTGRES_USER=$PostgresUsername",
    "-e", "POSTGRES_PASSWORD=$PostgresPassword",
    $Image
)

& docker @runArguments | Out-Null
Assert-DockerCommand "start PGvector container"

$health = "starting"
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $health = docker inspect `
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}' `
        $ContainerName
    Assert-DockerCommand "read PGvector health"
    if ($health -eq "healthy") {
        break
    }
    Start-Sleep -Seconds 2
}

Write-Host "PGvector container: $ContainerName"
Write-Host "Docker network:     $NetworkName"
Write-Host "JDBC endpoint:      jdbc:postgresql://127.0.0.1:${Port}/${Database}"
Write-Host "Database user:      $PostgresUsername"
Write-Host "Health:             $health"

if ($health -ne "healthy") {
    Write-Warning "PGvector has not become healthy yet. Check: docker logs $ContainerName"
} else {
    $extensions = @("vector", "hstore")
    foreach ($ext in $extensions) {
        $sql = "CREATE EXTENSION IF NOT EXISTS `"$ext`";"
        docker exec $ContainerName psql -U $PostgresUsername -d $Database -v "ON_ERROR_STOP=1" -c $sql | Out-Null
        Assert-DockerCommand "create PostgreSQL extension $ext"
    }
    Write-Host "Extensions:         vector, hstore"
}
