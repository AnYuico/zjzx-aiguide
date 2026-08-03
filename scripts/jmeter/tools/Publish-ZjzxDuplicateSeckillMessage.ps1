[CmdletBinding()]
param(
    [string]$RabbitManagementUrl = "http://127.0.0.1:15672",
    [string]$QueueName = "zjzx.order.seckill-create",
    [ValidateRange(1, 1000)]
    [int]$ExpectedReadyBefore = 2,
    [string]$RabbitUsername = $env:RABBITMQ_USERNAME,
    [string]$RabbitPassword = $env:RABBITMQ_PASSWORD,
    [ValidateRange(1, 60)]
    [int]$WaitSeconds = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-PlainSecret {
    param([string]$Value, [string]$Prompt)

    if (-not [string]::IsNullOrWhiteSpace($Value)) {
        return $Value
    }
    $secureValue = Read-Host $Prompt -AsSecureString
    $credential = [System.Management.Automation.PSCredential]::new(
        "local",
        $secureValue
    )
    return $credential.GetNetworkCredential().Password
}

if ([string]::IsNullOrWhiteSpace($RabbitUsername)) {
    $RabbitUsername = Read-Host "RabbitMQ management username"
}
$RabbitPassword = Read-PlainSecret `
    $RabbitPassword `
    "RabbitMQ management password"

$credentialText = "${RabbitUsername}:${RabbitPassword}"
$authorization = [Convert]::ToBase64String(
    [Text.Encoding]::ASCII.GetBytes($credentialText)
)
$headers = @{ Authorization = "Basic $authorization" }
$encodedQueue = [Uri]::EscapeDataString($QueueName)
$queueUri = "$RabbitManagementUrl/api/queues/%2F/$encodedQueue"

function Get-ReadyCount {
    $queue = Invoke-RestMethod `
        -Method Get `
        -Uri $queueUri `
        -Headers $headers `
        -TimeoutSec 10
    return [int]$queue.messages_ready
}

$readyBefore = Get-ReadyCount
if ($readyBefore -ne $ExpectedReadyBefore) {
    throw "Queue ready count expected $ExpectedReadyBefore but was $readyBefore."
}

$getBody = @{
    count = 1
    ackmode = "ack_requeue_true"
    encoding = "auto"
    truncate = 50000
} | ConvertTo-Json
$messages = @(Invoke-RestMethod `
    -Method Post `
    -Uri "$queueUri/get" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $getBody `
    -TimeoutSec 10)
if ($messages.Count -ne 1) {
    throw "Expected one queued seckill message, received $($messages.Count)."
}

$message = $messages[0]
$exchange = [string]$message.exchange
if ([string]::IsNullOrWhiteSpace($exchange)) {
    throw "Queued message does not contain an exchange."
}
$publishBody = @{
    properties = $message.properties
    routing_key = [string]$message.routing_key
    payload = [string]$message.payload
    payload_encoding = [string]$message.payload_encoding
} | ConvertTo-Json -Depth 12
$encodedExchange = [Uri]::EscapeDataString($exchange)
$publishResult = Invoke-RestMethod `
    -Method Post `
    -Uri "$RabbitManagementUrl/api/exchanges/%2F/$encodedExchange/publish" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $publishBody `
    -TimeoutSec 10
if (-not $publishResult.routed) {
    throw "RabbitMQ did not route the duplicate seckill message."
}

$deadline = (Get-Date).AddSeconds($WaitSeconds)
do {
    $readyAfter = Get-ReadyCount
    if ($readyAfter -eq $ExpectedReadyBefore + 1) {
        Write-Host (
            "[PASS] Duplicated one queued message; ready count is {0}." -f
            $readyAfter
        )
        Write-Host "Restart OrderService and run the AfterConsumer verification."
        return
    }
    Start-Sleep -Milliseconds 250
} while ((Get-Date) -lt $deadline)

throw (
    "Duplicate publish did not produce the expected ready count " +
    "$($ExpectedReadyBefore + 1); last value was $readyAfter."
)
