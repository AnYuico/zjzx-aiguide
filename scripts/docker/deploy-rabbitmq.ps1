[CmdletBinding()]
param(
    [string]$Image = "rabbitmq:4.3.2-management",
    [string]$ContainerName = "zjzx-rabbitmq",
    [string]$NetworkName = "zjzx-net",
    [string]$NacosContainerName = "zjzx-nacos",
    [string]$DataVolume = "zjzx-rabbitmq-data",
    [int]$AmqpPort = 5672,
    [int]$ManagementPort = 15672,
    [string]$VirtualHost = "/",
    [string]$RabbitUsername = $env:RABBITMQ_USERNAME,
    [string]$RabbitPassword = $env:RABBITMQ_PASSWORD
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
Assert-DockerCommand "list local RabbitMQ images"
if ($localImage -notcontains $Image) {
    throw "RabbitMQ image is not available locally: $Image"
}

$existingRabbit = docker container ls -a `
    --filter "name=^/$ContainerName$" `
    --format "{{.ID}}"
Assert-DockerCommand "inspect existing RabbitMQ container"
if ($existingRabbit) {
    throw "Container '$ContainerName' already exists. This script will not replace it."
}

if ([string]::IsNullOrWhiteSpace($RabbitUsername)) {
    $RabbitUsername = Read-Host "RabbitMQ username"
}
if ([string]::IsNullOrWhiteSpace($RabbitUsername)) {
    throw "RabbitMQ username must not be empty."
}

if ([string]::IsNullOrWhiteSpace($RabbitPassword)) {
    $securePassword = Read-Host "RabbitMQ password" -AsSecureString
    $credential = [System.Management.Automation.PSCredential]::new(
        $RabbitUsername,
        $securePassword
    )
    $RabbitPassword = $credential.GetNetworkCredential().Password
}
if ([string]::IsNullOrWhiteSpace($RabbitPassword)) {
    throw "RabbitMQ password must not be empty."
}

$existingNetwork = docker network ls `
    --filter "name=^${NetworkName}$" `
    --format "{{.Name}}"
Assert-DockerCommand "list Docker networks"
if ($existingNetwork -notcontains $NetworkName) {
    docker network create $NetworkName | Out-Null
    Assert-DockerCommand "create network '$NetworkName'"
}

$nacosId = docker container ls -a `
    --filter "name=^/$NacosContainerName$" `
    --format "{{.ID}}"
Assert-DockerCommand "inspect Nacos container"
if ($nacosId) {
    $nacosNetworks = docker inspect `
        --format '{{range $name, $value := .NetworkSettings.Networks}}{{$name}} {{end}}' `
        $NacosContainerName
    Assert-DockerCommand "inspect Nacos networks"
    if (($nacosNetworks -split '\s+') -notcontains $NetworkName) {
        docker network connect $NetworkName $NacosContainerName
        Assert-DockerCommand "connect Nacos to '$NetworkName'"
    }
} else {
    Write-Warning "Nacos container '$NacosContainerName' was not found. RabbitMQ will still be deployed."
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
    "--hostname", $ContainerName,
    "--restart", "unless-stopped",
    "--network", $NetworkName,
    "--memory", "768m",
    "--health-cmd", "rabbitmq-diagnostics -q ping",
    "--health-interval", "10s",
    "--health-timeout", "5s",
    "--health-retries", "10",
    "--health-start-period", "30s",
    "-p", "127.0.0.1:${AmqpPort}:5672",
    "-p", "127.0.0.1:${ManagementPort}:15672",
    "-v", "${DataVolume}:/var/lib/rabbitmq",
    "-e", "RABBITMQ_DEFAULT_USER=$RabbitUsername",
    "-e", "RABBITMQ_DEFAULT_PASS=$RabbitPassword",
    "-e", "RABBITMQ_DEFAULT_VHOST=$VirtualHost",
    $Image
)

& docker @runArguments | Out-Null
Assert-DockerCommand "start RabbitMQ container"

$health = "starting"
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $health = docker inspect `
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}' `
        $ContainerName
    Assert-DockerCommand "read RabbitMQ health"
    if ($health -eq "healthy") {
        break
    }
    Start-Sleep -Seconds 2
}

Write-Host "RabbitMQ container: $ContainerName"
Write-Host "Docker network:     $NetworkName"
Write-Host "AMQP endpoint:      127.0.0.1:$AmqpPort"
Write-Host "Management UI:      http://127.0.0.1:$ManagementPort"
Write-Host "Virtual host:       $VirtualHost"
Write-Host "Health:             $health"

if ($health -ne "healthy") {
    Write-Warning "RabbitMQ has not become healthy yet. Check: docker logs $ContainerName"
}
