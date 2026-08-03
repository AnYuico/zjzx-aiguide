[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet("Before", "WhileConsumerStopped", "AfterConsumer", "AfterTimeout")]
    [string]$Phase,
    [Parameter(Mandatory)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$ActivityId,
    [Parameter(Mandatory)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$SkuId,
    [Parameter(Mandatory)]
    [ValidateRange(1, 1000)]
    [int]$ExpectedAccepted,
    [string]$MySqlContainer = "mysql8030",
    [string]$RedisContainer = "redis7010",
    [string]$RabbitContainer = "zjzx-rabbitmq",
    [string]$MySqlPassword = $env:MYSQL_PASSWORD,
    [string]$RedisPassword = $env:REDIS_PASSWORD,
    [string]$SnapshotFile = "",
    [ValidateRange(0, 600)]
    [int]$WaitSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($SnapshotFile)) {
    $SnapshotFile = Join-Path $PSScriptRoot `
        "..\data\seckill-run-$ActivityId-$SkuId.json"
}
$SnapshotFile =
    $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath(
        $SnapshotFile
    )

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

$MySqlPassword = Read-PlainSecret $MySqlPassword "Local MySQL root password"
$RedisPassword = Read-PlainSecret $RedisPassword "Local Redis password"

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

function Invoke-RedisScalar {
    param([string[]]$Command)

    $arguments = @(
        "exec",
        $RedisContainer,
        "redis-cli",
        "--no-auth-warning",
        "-a",
        $RedisPassword,
        "--raw"
    ) + $Command
    $result = & docker @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Redis query failed."
    }
    if ([string]::IsNullOrWhiteSpace([string]$result)) {
        return 0L
    }
    return [long]$result
}

function Get-RabbitQueueReady {
    param([string]$QueueName)

    $rows = @(& docker exec $RabbitContainer rabbitmqctl `
        -q list_queues name messages_ready)
    if ($LASTEXITCODE -ne 0) {
        throw "RabbitMQ queue query failed."
    }
    foreach ($row in $rows) {
        $parts = ([string]$row) -split "`t"
        if ($parts.Count -eq 2 -and $parts[0] -eq $QueueName) {
            return [long]$parts[1]
        }
    }
    throw "RabbitMQ queue not found: $QueueName"
}

function Assert-Equal {
    param([string]$Name, [long]$Actual, [long]$Expected)

    if ($Actual -ne $Expected) {
        throw "$Name expected $Expected but was $Actual."
    }
    Write-Host ("[PASS] {0}: {1}" -f $Name, $Actual)
}

function Wait-Until {
    param([string]$Description, [scriptblock]$Condition)

    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    do {
        if (& $Condition) {
            Write-Host "[READY] $Description"
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Description."
}

$baseKey = "seckill:{$($ActivityId):$($SkuId)}:"
$stockKey = "${baseKey}stock"
$resultsKey = "${baseKey}results"
$pendingKey = "${baseKey}pending"
$queueName = "zjzx.order.seckill-create"
$deadQueueName = "zjzx.order.seckill-create.dlq"

if ($Phase -eq "Before") {
    $skuRows = @(Invoke-MySql @"
SELECT total_stock, available_stock, status
FROM seckill_sku
WHERE activity_id = $ActivityId
  AND sku_id = $SkuId
  AND is_deleted = 0;
"@)
    if ($skuRows.Count -ne 1) {
        throw "The activity must contain exactly one matching SKU row."
    }
    $skuParts = $skuRows[0] -split "`t"
    if ($skuParts.Count -ne 3 -or [long]$skuParts[2] -ne 1) {
        throw "The seckill SKU must be active before the test."
    }
    $existing = Invoke-Scalar @"
SELECT COUNT(*)
FROM seckill_order_request
WHERE activity_id = $ActivityId AND sku_id = $SkuId;
"@
    Assert-Equal "existing activity requests" $existing 0
    $redisStock = Invoke-RedisScalar @("GET", $stockKey)
    $availableStock = [long]$skuParts[1]
    Assert-Equal "Redis and MySQL activity stock" $redisStock $availableStock
    if ($availableStock -lt $ExpectedAccepted) {
        throw "Activity stock $availableStock is below ExpectedAccepted $ExpectedAccepted."
    }

    $snapshot = [ordered]@{
        activityId = $ActivityId
        skuId = $SkuId
        expectedAccepted = $ExpectedAccepted
        totalStock = [long]$skuParts[0]
        availableStock = $availableStock
        productStock = Invoke-Scalar `
            "SELECT stock_num FROM product_sku WHERE id = $SkuId;"
        queueReady = Get-RabbitQueueReady $queueName
        deadQueueReady = Get-RabbitQueueReady $deadQueueName
    }
    New-Item -ItemType Directory -Path (Split-Path $SnapshotFile) -Force |
        Out-Null
    $snapshot | ConvertTo-Json |
        Set-Content -LiteralPath $SnapshotFile -Encoding UTF8
    Write-Host "Snapshot: $SnapshotFile"
    return
}

if (-not (Test-Path -LiteralPath $SnapshotFile -PathType Leaf)) {
    throw "Seckill snapshot not found: $SnapshotFile"
}
$snapshot = Get-Content -LiteralPath $SnapshotFile -Raw | ConvertFrom-Json
if ([long]$snapshot.activityId -ne $ActivityId -or
        [long]$snapshot.skuId -ne $SkuId) {
    throw "Snapshot does not match this activity and SKU."
}

$expectedRedisStock = [long]$snapshot.availableStock - $ExpectedAccepted

if ($Phase -eq "WhileConsumerStopped") {
    Assert-Equal "Redis activity stock" (
        Invoke-RedisScalar @("GET", $stockKey)
    ) $expectedRedisStock
    Assert-Equal "Redis queued result count" (
        Invoke-RedisScalar @("HLEN", $resultsKey)
    ) $ExpectedAccepted
    Assert-Equal "Redis unpublished pending count" (
        Invoke-RedisScalar @("ZCARD", $pendingKey)
    ) 0
    Assert-Equal "database requests while consumer stopped" (
        Invoke-Scalar @"
SELECT COUNT(*) FROM seckill_order_request
WHERE activity_id = $ActivityId AND sku_id = $SkuId;
"@
    ) 0
    $readyDelta = (Get-RabbitQueueReady $queueName) - [long]$snapshot.queueReady
    Assert-Equal "RabbitMQ queued seckill messages" $readyDelta $ExpectedAccepted
    return
}

if ($Phase -eq "AfterConsumer") {
    Wait-Until "seckill consumer completion" {
        (Invoke-Scalar @"
SELECT COUNT(*) FROM seckill_order_request
WHERE activity_id = $ActivityId
  AND sku_id = $SkuId
  AND status = 2;
"@) -eq $ExpectedAccepted
    }
    Assert-Equal "successful seckill requests" (
        Invoke-Scalar @"
SELECT COUNT(*) FROM seckill_order_request
WHERE activity_id = $ActivityId AND sku_id = $SkuId AND status = 2;
"@
    ) $ExpectedAccepted
    Assert-Equal "distinct request IDs" (
        Invoke-Scalar @"
SELECT COUNT(DISTINCT request_id)
FROM seckill_order_request
WHERE activity_id = $ActivityId AND sku_id = $SkuId;
"@
    ) $ExpectedAccepted
    Assert-Equal "distinct seckill order numbers" (
        Invoke-Scalar @"
SELECT COUNT(DISTINCT order_no)
FROM seckill_order_request
WHERE activity_id = $ActivityId AND sku_id = $SkuId;
"@
    ) $ExpectedAccepted
    Assert-Equal "distinct successful users" (
        Invoke-Scalar @"
SELECT COUNT(DISTINCT user_id) FROM seckill_order_request
WHERE activity_id = $ActivityId AND sku_id = $SkuId AND status = 2;
"@
    ) $ExpectedAccepted
    Assert-Equal "failed seckill requests" (
        Invoke-Scalar @"
SELECT COUNT(*) FROM seckill_order_request
WHERE activity_id = $ActivityId AND sku_id = $SkuId AND status = 3;
"@
    ) 0
    Assert-Equal "created seckill orders" (
        Invoke-Scalar @"
SELECT COUNT(*)
FROM order_info o
JOIN seckill_order_request r ON r.order_id = o.id
WHERE r.activity_id = $ActivityId
  AND r.sku_id = $SkuId
  AND r.status = 2
  AND o.order_source = 3
  AND o.is_deleted = 0;
"@
    ) $ExpectedAccepted
    Assert-Equal "reserved physical inventory" (
        Invoke-Scalar @"
SELECT COUNT(*)
FROM inventory_reservation i
JOIN seckill_order_request r ON r.order_no = i.order_no
WHERE r.activity_id = $ActivityId
  AND r.sku_id = $SkuId
  AND i.status = 0;
"@
    ) $ExpectedAccepted
    Assert-Equal "MySQL activity stock" (
        Invoke-Scalar @"
SELECT available_stock FROM seckill_sku
WHERE activity_id = $ActivityId AND sku_id = $SkuId AND is_deleted = 0;
"@
    ) $expectedRedisStock
    Assert-Equal "physical SKU stock" (
        Invoke-Scalar "SELECT stock_num FROM product_sku WHERE id = $SkuId;"
    ) ([long]$snapshot.productStock - $ExpectedAccepted)
    Assert-Equal "RabbitMQ ready messages" (
        Get-RabbitQueueReady $queueName
    ) ([long]$snapshot.queueReady)
    Assert-Equal "RabbitMQ dead messages" (
        Get-RabbitQueueReady $deadQueueName
    ) ([long]$snapshot.deadQueueReady)
    Assert-Equal "negative stock rows" (
        Invoke-Scalar "SELECT COUNT(*) FROM product_sku WHERE stock_num < 0;"
    ) 0
    return
}

Wait-Until "seckill timeout release" {
    (Invoke-Scalar @"
SELECT COUNT(*)
FROM seckill_order_request r
JOIN order_info o ON o.id = r.order_id
WHERE r.activity_id = $ActivityId
  AND r.sku_id = $SkuId
  AND r.stock_returned = 1
  AND o.order_status = -1;
"@) -eq $ExpectedAccepted
}
Assert-Equal "cancelled seckill orders with returned stock" (
    Invoke-Scalar @"
SELECT COUNT(*)
FROM seckill_order_request r
JOIN order_info o ON o.id = r.order_id
WHERE r.activity_id = $ActivityId
  AND r.sku_id = $SkuId
  AND r.stock_returned = 1
  AND o.order_status = -1;
"@
) $ExpectedAccepted
Assert-Equal "restored MySQL activity stock" (
    Invoke-Scalar @"
SELECT available_stock FROM seckill_sku
WHERE activity_id = $ActivityId AND sku_id = $SkuId AND is_deleted = 0;
"@
) ([long]$snapshot.availableStock)
Assert-Equal "restored Redis activity stock" (
    Invoke-RedisScalar @("GET", $stockKey)
) ([long]$snapshot.availableStock)
Assert-Equal "restored physical SKU stock" (
    Invoke-Scalar "SELECT stock_num FROM product_sku WHERE id = $SkuId;"
) ([long]$snapshot.productStock)
Assert-Equal "released physical inventory" (
    Invoke-Scalar @"
SELECT COUNT(*)
FROM inventory_reservation i
JOIN seckill_order_request r ON r.order_no = i.order_no
WHERE r.activity_id = $ActivityId
  AND r.sku_id = $SkuId
  AND i.status = 2;
"@
) $ExpectedAccepted
Assert-Equal "negative stock rows" (
    Invoke-Scalar "SELECT COUNT(*) FROM product_sku WHERE stock_num < 0;"
) 0
