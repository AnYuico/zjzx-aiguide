[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$JtlFile,
    [Parameter(Mandatory)]
    [string]$DataFile,
    [string]$GatewayUrl = "http://127.0.0.1:8500",
    [ValidateRange(1, 600)]
    [int]$WaitSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$JtlFile = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath(
    $JtlFile
)
$DataFile = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath(
    $DataFile
)

foreach ($path in @($JtlFile, $DataFile)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required file not found: $path"
    }
}

$samples = @(Import-Csv -LiteralPath $JtlFile -Encoding UTF8)
$dataRows = @(Import-Csv -LiteralPath $DataFile -Encoding UTF8)
$requiredSampleColumns = @(
    "responseCategory",
    "requestId",
    "userAddressId",
    "seckillOrderNo"
)
foreach ($column in $requiredSampleColumns) {
    if ($samples.Count -eq 0 -or
            $samples[0].PSObject.Properties.Name -notcontains $column) {
        throw "JTL is missing '$column'; rerun with the current JMeter runner."
    }
}

$rowsByAddress = @{}
foreach ($row in $dataRows) {
    $addressId = [string]$row.userAddressId
    if ([string]::IsNullOrWhiteSpace($addressId) -or
            [string]::IsNullOrWhiteSpace([string]$row.token)) {
        throw "Data CSV contains an invalid token or userAddressId."
    }
    if ($rowsByAddress.ContainsKey($addressId)) {
        throw "Data CSV contains duplicate userAddressId '$addressId'."
    }
    $rowsByAddress[$addressId] = $row
}

$accepted = @($samples | Where-Object {
    $_.responseCategory -eq "SUCCESS" -and
    -not [string]::IsNullOrWhiteSpace($_.seckillOrderNo)
} | Sort-Object seckillOrderNo -Unique)
if ($accepted.Count -eq 0) {
    throw "No accepted seckill order was found in the JTL."
}

function Invoke-Cancel {
    param(
        [string]$OrderNo,
        [string]$Token
    )

    $encodedOrderNo = [Uri]::EscapeDataString($OrderNo)
    $response = Invoke-RestMethod `
        -Method Post `
        -Uri "$GatewayUrl/api/order/orderInfo/auth/$encodedOrderNo/cancel" `
        -Headers @{ token = $Token } `
        -TimeoutSec 10
    if ($null -eq $response -or [string]$response.code -ne "200") {
        throw "Cancel was rejected for order $OrderNo (code=$($response.code))."
    }
}

$deadline = (Get-Date).AddSeconds($WaitSeconds)
$pending = [System.Collections.Generic.List[object]]::new()
foreach ($sample in $accepted) {
    $pending.Add($sample)
}
$cancelled = 0

while ($pending.Count -gt 0 -and (Get-Date) -lt $deadline) {
    for ($index = $pending.Count - 1; $index -ge 0; $index--) {
        $sample = $pending[$index]
        $addressId = [string]$sample.userAddressId
        if (-not $rowsByAddress.ContainsKey($addressId)) {
            throw "No CSV identity matches userAddressId '$addressId'."
        }
        try {
            Invoke-Cancel `
                -OrderNo ([string]$sample.seckillOrderNo) `
                -Token ([string]$rowsByAddress[$addressId].token)
            $pending.RemoveAt($index)
            $cancelled++
        } catch {
            if ((Get-Date).AddSeconds(2) -ge $deadline) {
                throw
            }
        }
    }
    if ($pending.Count -gt 0) {
        Start-Sleep -Seconds 2
    }
}

if ($pending.Count -gt 0) {
    throw "Timed out cancelling $($pending.Count) seckill orders."
}

Write-Host "[PASS] Cancelled $cancelled accepted seckill orders through the public API."
Write-Host "Run Test-ZjzxSeckillRun.ps1 -Phase AfterTimeout to verify stock restoration."
