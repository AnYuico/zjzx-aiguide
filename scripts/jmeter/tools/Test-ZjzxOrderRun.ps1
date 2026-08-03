[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet("Before", "AfterSubmit", "AfterTimeout")]
    [string]$Phase,
    [Parameter(Mandatory)]
    [ValidatePattern("^[A-Za-z0-9_-]{1,32}$")]
    [string]$RunId,
    [ValidateRange(1, [long]::MaxValue)]
    [long]$SkuId = 14,
    [ValidateRange(1, 1000)]
    [int]$ExpectedOrders = 20,
    [ValidateRange(1, 1000)]
    [int]$SkuNum = 1,
    [string]$MySqlContainer = "mysql8030",
    [string]$RedisContainer = "redis7010",
    [string]$MySqlPassword = $env:MYSQL_PASSWORD,
    [string]$RedisPassword = $env:REDIS_PASSWORD,
    [string]$SnapshotFile = "",
    [ValidateRange(0, 600)]
    [int]$WaitSeconds = 120,
    [switch]$AllowExternalStockAdjustment
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$dataDirectory = Join-Path $PSScriptRoot "..\data"
if ([string]::IsNullOrWhiteSpace($SnapshotFile)) {
    $SnapshotFile = Join-Path $dataDirectory "order-run-$RunId.json"
}
$SnapshotFile =
    $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath(
        $SnapshotFile
    )

function Read-PlainSecret {
    param(
        [string]$Value,
        [string]$Prompt
    )

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

$MySqlPassword = Read-PlainSecret `
    -Value $MySqlPassword `
    -Prompt "Local MySQL root password"
$RedisPassword = Read-PlainSecret `
    -Value $RedisPassword `
    -Prompt "Local Redis password"

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

    $rows = @(Invoke-MySql -Sql $Sql)
    if ($rows.Count -ne 1) {
        throw "Expected one MySQL scalar row, received $($rows.Count)."
    }
    return [long]$rows[0]
}

function Assert-Equal {
    param(
        [string]$Name,
        [long]$Actual,
        [long]$Expected
    )

    if ($Actual -ne $Expected) {
        throw "$Name expected $Expected but was $Actual."
    }
    Write-Host ("[PASS] {0}: {1}" -f $Name, $Actual)
}

function Wait-Until {
    param(
        [string]$Description,
        [scriptblock]$Condition
    )

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

function Get-RunOrdersWhere {
    return "o.remark = 'jmeter-load-test:$RunId' AND o.is_deleted = 0"
}

function Get-CartCleanupIssues {
    $orders = @(Invoke-MySql -Sql @"
SELECT user_id, order_no
FROM order_info o
WHERE $(Get-RunOrdersWhere)
ORDER BY id;
"@)
    if ($orders.Count -ne $ExpectedOrders) {
        throw "Cannot verify carts: expected $ExpectedOrders run orders."
    }

    $arguments = [System.Collections.Generic.List[string]]::new()
    foreach ($row in $orders) {
        $parts = $row -split "`t"
        if ($parts.Count -ne 2) {
            throw "Unexpected order row returned by MySQL."
        }
        $arguments.Add($parts[0])
        $arguments.Add($parts[1])
    }

    $lua = @"
local issues = 0
for index = 1, #ARGV, 2 do
  local cart = 'user:cart:' .. ARGV[index]
  local marker = 'user:cart:cleanup:' .. ARGV[index] .. ':' .. ARGV[index + 1]
  if redis.call('HEXISTS', cart, '$SkuId') ~= 0 then
    issues = issues + 1
  end
  if redis.call('EXISTS', marker) ~= 1 then
    issues = issues + 1
  end
end
return issues
"@
    $redisArguments = @(
        "exec",
        $RedisContainer,
        "redis-cli",
        "--no-auth-warning",
        "-a",
        $RedisPassword,
        "--raw",
        "EVAL_RO",
        $lua,
        "0"
    ) + $arguments.ToArray()
    $result = & docker @redisArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Redis cart verification failed."
    }
    return [long]$result
}

function Test-CartCleanup {
    Assert-Equal `
        -Name "cart cleanup issues" `
        -Actual (Get-CartCleanupIssues) `
        -Expected 0
}

$runWhere = Get-RunOrdersWhere

if ($Phase -eq "Before") {
    New-Item -ItemType Directory -Path (Split-Path $SnapshotFile) -Force |
        Out-Null
    $existingOrders = Invoke-Scalar -Sql @"
SELECT COUNT(*)
FROM order_info o
WHERE o.remark = 'jmeter-load-test:$RunId' AND o.is_deleted = 0;
"@
    Assert-Equal -Name "existing run orders" -Actual $existingOrders -Expected 0
    $stock = Invoke-Scalar -Sql @"
SELECT stock_num
FROM product_sku
WHERE id = $SkuId AND is_deleted = 0;
"@
    $requiredUnits = $ExpectedOrders * $SkuNum
    if ($stock -lt $requiredUnits) {
        throw "SKU $SkuId stock $stock is below required units $requiredUnits."
    }
    $snapshot = [ordered]@{
        runId = $RunId
        skuId = $SkuId
        skuNum = $SkuNum
        expectedOrders = $ExpectedOrders
        baselineStock = $stock
        databaseTime = @(
            Invoke-MySql -Sql "SELECT DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s');"
        )[0]
    }
    $snapshot | ConvertTo-Json |
        Set-Content -LiteralPath $SnapshotFile -Encoding UTF8
    Write-Host "[PASS] baseline SKU stock: $stock"
    Write-Host "Snapshot: $SnapshotFile"
    return
}

if (-not (Test-Path -LiteralPath $SnapshotFile -PathType Leaf)) {
    throw "Run snapshot not found: $SnapshotFile"
}
$snapshot = Get-Content -LiteralPath $SnapshotFile -Raw | ConvertFrom-Json
if ($snapshot.runId -ne $RunId -or [long]$snapshot.skuId -ne $SkuId) {
    throw "Snapshot does not match this run."
}

$expectedUnits = $ExpectedOrders * $SkuNum

if ($Phase -eq "AfterSubmit") {
    Wait-Until -Description "orders and cart cleanup consumers" -Condition {
        $orderCount = Invoke-Scalar -Sql @"
SELECT COUNT(*) FROM order_info o WHERE $runWhere;
"@
        return $orderCount -eq $ExpectedOrders -and
            (Get-CartCleanupIssues) -eq 0
    }

    Assert-Equal -Name "waiting-payment orders" -Actual (
        Invoke-Scalar -Sql "SELECT COUNT(*) FROM order_info o WHERE $runWhere AND o.order_status = 0;"
    ) -Expected $ExpectedOrders
    Assert-Equal -Name "successful submit claims" -Actual (
        Invoke-Scalar -Sql @"
SELECT COUNT(*)
FROM order_submit_request r
JOIN order_info o ON o.request_id = r.request_id
WHERE $runWhere AND r.status = 1;
"@
    ) -Expected $ExpectedOrders
    Assert-Equal -Name "reserved inventory requests" -Actual (
        Invoke-Scalar -Sql @"
SELECT COUNT(*)
FROM inventory_request r
JOIN order_info o ON o.order_no = r.order_no
WHERE $runWhere AND r.status = 1;
"@
    ) -Expected $ExpectedOrders
    Assert-Equal -Name "reserved inventory units" -Actual (
        Invoke-Scalar -Sql @"
SELECT COALESCE(SUM(r.sku_num), 0)
FROM inventory_reservation r
JOIN order_info o ON o.order_no = r.order_no
WHERE $runWhere AND r.status = 0 AND r.sku_id = $SkuId;
"@
    ) -Expected $expectedUnits
    Assert-Equal -Name "sent timeout outbox events" -Actual (
        Invoke-Scalar -Sql @"
SELECT COUNT(*)
FROM mq_outbox m
JOIN order_info o ON m.event_id = CONCAT('order.timeout:', o.order_no)
WHERE $runWhere AND m.status = 1;
"@
    ) -Expected $ExpectedOrders
    Assert-Equal -Name "sent cart cleanup outbox events" -Actual (
        Invoke-Scalar -Sql @"
SELECT COUNT(*)
FROM mq_outbox m
JOIN order_info o ON m.event_id = CONCAT('cart.cleanup:', o.order_no)
WHERE $runWhere AND m.status = 1;
"@
    ) -Expected $ExpectedOrders
    Assert-Equal -Name "negative stock rows" -Actual (
        Invoke-Scalar -Sql "SELECT COUNT(*) FROM product_sku WHERE stock_num < 0;"
    ) -Expected 0
    Assert-Equal -Name "SKU stock after reservation" -Actual (
        Invoke-Scalar -Sql "SELECT stock_num FROM product_sku WHERE id = $SkuId;"
    ) -Expected ([long]$snapshot.baselineStock - $expectedUnits)
    Test-CartCleanup
    return
}

Wait-Until -Description "timeout close, inventory release and completion acknowledgements" -Condition {
    $cancelled = Invoke-Scalar -Sql @"
SELECT COUNT(*) FROM order_info o WHERE $runWhere AND o.order_status = -1;
"@
    $released = Invoke-Scalar -Sql @"
SELECT COUNT(*)
FROM inventory_request r
JOIN order_info o ON o.order_no = r.order_no
WHERE $runWhere AND r.status = 3;
"@
    $completedTasks = Invoke-Scalar -Sql @"
SELECT COUNT(*)
FROM inventory_operation_task t
JOIN order_info o ON o.order_no = t.order_no
WHERE $runWhere AND t.operation_type = 2 AND t.status = 1;
"@
    return $cancelled -eq $ExpectedOrders -and
        $released -eq $ExpectedOrders -and
        $completedTasks -eq $ExpectedOrders
}

Assert-Equal -Name "cancelled orders" -Actual (
    Invoke-Scalar -Sql "SELECT COUNT(*) FROM order_info o WHERE $runWhere AND o.order_status = -1;"
) -Expected $ExpectedOrders
Assert-Equal -Name "released inventory requests" -Actual (
    Invoke-Scalar -Sql @"
SELECT COUNT(*)
FROM inventory_request r
JOIN order_info o ON o.order_no = r.order_no
WHERE $runWhere AND r.status = 3;
"@
) -Expected $ExpectedOrders
Assert-Equal -Name "released inventory units" -Actual (
    Invoke-Scalar -Sql @"
SELECT COALESCE(SUM(r.sku_num), 0)
FROM inventory_reservation r
JOIN order_info o ON o.order_no = r.order_no
WHERE $runWhere AND r.status = 2 AND r.sku_id = $SkuId;
"@
) -Expected $expectedUnits
Assert-Equal -Name "successful release tasks" -Actual (
    Invoke-Scalar -Sql @"
SELECT COUNT(*)
FROM inventory_operation_task t
JOIN order_info o ON o.order_no = t.order_no
WHERE $runWhere AND t.operation_type = 2 AND t.status = 1;
"@
) -Expected $ExpectedOrders
Assert-Equal -Name "timeout messages consumed" -Actual (
    Invoke-Scalar -Sql @"
SELECT COUNT(*)
FROM mq_consume_log c
JOIN order_info o ON c.event_id = CONCAT('order.timeout:', o.order_no)
WHERE $runWhere AND c.consumer_name = 'service-order:timeout-check';
"@
) -Expected $ExpectedOrders
Assert-Equal -Name "inventory release messages consumed" -Actual (
    Invoke-Scalar -Sql @"
SELECT COUNT(*)
FROM mq_consume_log c
JOIN order_info o ON c.event_id = CONCAT('inventory.release:', o.order_no)
WHERE $runWhere AND c.consumer_name = 'service-product:inventory-release';
"@
) -Expected $ExpectedOrders
Assert-Equal -Name "dead outbox events" -Actual (
    Invoke-Scalar -Sql @"
SELECT COUNT(*)
FROM mq_outbox m
JOIN order_info o
  ON m.event_id IN (
    CONCAT('order.timeout:', o.order_no),
    CONCAT('inventory.release:', o.order_no)
  )
WHERE $runWhere AND m.status = 2;
"@
) -Expected 0
$finalStock = Invoke-Scalar -Sql `
    "SELECT stock_num FROM product_sku WHERE id = $SkuId;"
if ($AllowExternalStockAdjustment) {
    $message = (
        "[SKIP] Exact final SKU stock: baseline={0}, current={1}; " +
        "external stock adjustment was explicitly allowed."
    ) -f ([long]$snapshot.baselineStock), $finalStock
    Write-Host $message
} else {
    Assert-Equal `
        -Name "SKU stock after timeout release" `
        -Actual $finalStock `
        -Expected ([long]$snapshot.baselineStock)
}
Assert-Equal -Name "negative stock rows" -Actual (
    Invoke-Scalar -Sql "SELECT COUNT(*) FROM product_sku WHERE stock_num < 0;"
) -Expected 0
Test-CartCleanup
