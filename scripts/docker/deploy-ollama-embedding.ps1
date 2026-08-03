[CmdletBinding()]
param(
    [string]$Image = "ollama/ollama:0.32.3",
    [string]$ContainerName = "zjzx-ollama",
    [string]$NetworkName = "zjzx-net",
    [string]$DataVolume = "zjzx-ollama-data",
    [int]$Port = 11434,
    [string]$EmbeddingModel = "bge-m3"
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
Assert-DockerCommand "list local Ollama images"
if ($localImage -notcontains $Image) {
    throw "Ollama image is not available locally: $Image"
}

$existingContainer = docker container ls -a `
    --filter "name=^/$ContainerName$" `
    --format "{{.ID}}"
Assert-DockerCommand "inspect existing Ollama container"
if ($existingContainer) {
    throw "Container '$ContainerName' already exists. This script will not replace it."
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
    "--memory", "4g",
    "--health-cmd", "ollama list",
    "--health-interval", "10s",
    "--health-timeout", "5s",
    "--health-retries", "12",
    "--health-start-period", "20s",
    "-p", "127.0.0.1:${Port}:11434",
    "-v", "${DataVolume}:/root/.ollama",
    $Image
)

& docker @runArguments | Out-Null
Assert-DockerCommand "start Ollama container"

$health = "starting"
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $health = docker inspect `
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}' `
        $ContainerName
    Assert-DockerCommand "read Ollama health"
    if ($health -eq "healthy") {
        break
    }
    Start-Sleep -Seconds 2
}

if ($health -ne "healthy") {
    throw "Ollama did not become healthy. Check: docker logs $ContainerName"
}

Write-Host "Pulling embedding model '$EmbeddingModel'. This may take several minutes."
docker exec $ContainerName ollama pull $EmbeddingModel
Assert-DockerCommand "pull Ollama embedding model '$EmbeddingModel'"

Write-Host "Ollama container:   $ContainerName"
Write-Host "Docker network:     $NetworkName"
Write-Host "Embedding endpoint: http://127.0.0.1:$Port"
Write-Host "Embedding model:    $EmbeddingModel"
Write-Host "Health:             $health"
