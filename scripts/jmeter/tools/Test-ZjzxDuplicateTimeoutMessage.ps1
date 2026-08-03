[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern("^[A-Za-z0-9_-]{1,32}$")]
    [string]$RunId,
    [Parameter(Mandatory)]
    [switch]$ConfirmPublish,
    [string]$MySqlContainer = "mysql8030",
    [string]$RabbitContainer = "zjzx-rabbitmq",
    [string]$MySqlPassword = $env:MYSQL_PASSWORD,
    [string]$RabbitUsername = $env:RABBITMQ_USERNAME,
    [string]$RabbitPassword = $env:RABBITMQ_PASSWORD,
    [string]$RabbitManagementUrl = "http://127.0.0.1:15672"
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
$MySqlPassword = Read-PlainSecret $MySqlPassword "Local MySQL root password"
$RabbitPassword = Read-PlainSecret `
    $RabbitPassword `
    "RabbitMQ management password"

function Invoke-MySql {
    param([string]$Sql)

    $output = @(& docker exec `
        -e "MYSQL_PWD=$MySqlPassword" `
        $MySqlContainer `
        mysql `
        -uroot `
        -D db_zjzx `
        --batch `
        --skip-column-names `
        --raw `
        -e $Sql)
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL query failed."
    }
    return @($output | ForEach-Object { ([string]$_).Trim() })
}

function Invoke-Scalar {
    param([string]$Sql)

    $rows = @(Invoke-MySql $Sql)
    if ($rows.Count -ne 1) {
        throw "Expected one MySQL scalar row, received $($rows.Count)."
    }
    return [long]$rows[0]
}

function Get-DeadQueueCount {
    $rows = @(& docker exec $RabbitContainer rabbitmqctl `
        -q list_queues name messages_ready)
    if ($LASTEXITCODE -ne 0) {
        throw "RabbitMQ queue query failed."
    }
    foreach ($row in $rows) {
        $parts = ([string]$row) -split "`t"
        if ($parts.Count -eq 2 -and
                $parts[0] -eq "zjzx.order.timeout-check.dlq") {
            return [long]$parts[1]
        }
    }
    throw "Timeout dead-letter queue was not found."
}

$row = @(Invoke-MySql @"
SELECT m.event_id, m.exchange_name, m.routing_key, m.event_type, m.payload,
       o.order_no
FROM mq_outbox m
JOIN order_info o ON m.event_id = CONCAT('order.timeout:', o.order_no)
WHERE o.remark = 'jmeter-load-test:$RunId'
  AND o.order_status = -1
  AND o.is_deleted = 0
  AND m.status = 1
ORDER BY o.id
LIMIT 1;
"@)
if ($row.Count -ne 1) {
    throw "No completed timeout event was found for run $RunId."
}
$parts = $row[0] -split "`t", 6
if ($parts.Count -ne 6) {
    throw "Unexpected timeout outbox row."
}
$eventId = $parts[0]
$exchange = $parts[1]
$routingKey = $parts[2]
$eventType = $parts[3]
$payload = $parts[4]
$orderNo = $parts[5]

$consumeBefore = Invoke-Scalar @"
SELECT COUNT(*)
FROM mq_consume_log
WHERE consumer_name = 'service-order:timeout-check'
  AND event_id = '$eventId';
"@
$releaseTasksBefore = Invoke-Scalar @"
SELECT COUNT(*)
FROM inventory_operation_task
WHERE order_no = '$orderNo' AND operation_type = 2;
"@
$releaseOutboxBefore = Invoke-Scalar @"
SELECT COUNT(*)
FROM mq_outbox
WHERE event_id = CONCAT('inventory.release:', '$orderNo');
"@
$deadBefore = Get-DeadQueueCount

if ($consumeBefore -ne 1 -or
        $releaseTasksBefore -ne 1 -or
        $releaseOutboxBefore -ne 1) {
    throw "The original timeout event has not reached a valid final state."
}

$credentialText = "${RabbitUsername}:${RabbitPassword}"
$authorization = [Convert]::ToBase64String(
    [Text.Encoding]::ASCII.GetBytes($credentialText)
)
$headers = @{
    Authorization = "Basic $authorization"
}
$publishBody = @{
    properties = @{
        content_type = "application/json"
        content_encoding = "UTF-8"
        delivery_mode = 2
        message_id = $eventId
        type = $eventType
        headers = @{
            "x-event-id" = $eventId
        }
    }
    routing_key = $routingKey
    payload = $payload
    payload_encoding = "string"
} | ConvertTo-Json -Depth 8
$encodedExchange = [Uri]::EscapeDataString($exchange)
$response = Invoke-RestMethod `
    -Method Post `
    -Uri "$RabbitManagementUrl/api/exchanges/%2F/$encodedExchange/publish" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $publishBody `
    -TimeoutSec 10
if (-not $response.routed) {
    throw "RabbitMQ did not route the duplicate timeout event."
}

Start-Sleep -Seconds 3

$consumeAfter = Invoke-Scalar @"
SELECT COUNT(*)
FROM mq_consume_log
WHERE consumer_name = 'service-order:timeout-check'
  AND event_id = '$eventId';
"@
$releaseTasksAfter = Invoke-Scalar @"
SELECT COUNT(*)
FROM inventory_operation_task
WHERE order_no = '$orderNo' AND operation_type = 2;
"@
$releaseOutboxAfter = Invoke-Scalar @"
SELECT COUNT(*)
FROM mq_outbox
WHERE event_id = CONCAT('inventory.release:', '$orderNo');
"@
$deadAfter = Get-DeadQueueCount
$orderStatus = Invoke-Scalar @"
SELECT order_status FROM order_info WHERE order_no = '$orderNo';
"@

if ($consumeAfter -ne 1 -or
        $releaseTasksAfter -ne 1 -or
        $releaseOutboxAfter -ne 1 -or
        $deadAfter -ne $deadBefore -or
        $orderStatus -ne -1) {
    throw "Duplicate timeout event changed business state or entered the DLQ."
}

Write-Host "[PASS] Duplicate timeout event was routed and consumed."
Write-Host "[PASS] Consume log remained one row."
Write-Host "[PASS] Release task and release Outbox remained one row."
Write-Host "[PASS] Order remained cancelled and DLQ did not grow."
