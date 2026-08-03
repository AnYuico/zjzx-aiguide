[CmdletBinding()]
param(
    [string]$SourceFile = (Join-Path $PSScriptRoot "..\data\users.csv"),
    [string]$OutputDirectory = (Join-Path $PSScriptRoot "..\data"),
    [ValidateRange(1, 1000)]
    [int]$OrderUserCount = 20,
    [ValidateRange(1, [long]::MaxValue)]
    [long]$SkuId = 14,
    [long]$SeckillActivity50 = 0,
    [long]$SeckillActivity100 = 0,
    [long]$SeckillActivity200 = 0,
    [long]$SeckillInvariantActivity = 0,
    [string]$Keyword = "Mac mini"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$SourceFile = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath(
    $SourceFile
)
$OutputDirectory =
    $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath(
        $OutputDirectory
    )

if (-not (Test-Path -LiteralPath $SourceFile -PathType Leaf)) {
    throw "Source CSV file not found: $SourceFile"
}

$rows = @(Import-Csv -LiteralPath $SourceFile -Encoding UTF8)
if ($rows.Count -lt 1000) {
    throw "At least 1000 verified users are required; found $($rows.Count)."
}

$invalidRows = @($rows | Where-Object {
    [string]::IsNullOrWhiteSpace($_.token) -or
    [string]::IsNullOrWhiteSpace($_.userAddressId)
})
if ($invalidRows.Count -gt 0) {
    throw "Source CSV contains rows without token or userAddressId."
}

$uniqueTokens = @($rows.token | Sort-Object -Unique)
if ($uniqueTokens.Count -ne $rows.Count) {
    throw "Source CSV tokens must be unique."
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

function Export-Cohort {
    param(
        [object[]]$SourceRows,
        [int]$Offset,
        [int]$Count,
        [long]$ActivityId,
        [string]$FileName
    )

    if ($Offset + $Count -gt $SourceRows.Count) {
        throw "Cohort '$FileName' exceeds the available source rows."
    }

    $cohort = @($SourceRows[$Offset..($Offset + $Count - 1)] |
        ForEach-Object {
            [PSCustomObject]@{
                token = $_.token
                userAddressId = $_.userAddressId
                skuId = $SkuId
                activityId = $ActivityId
                keyword = $Keyword
            }
        })
    $target = Join-Path $OutputDirectory $FileName
    $cohort | Export-Csv -LiteralPath $target -NoTypeInformation -Encoding UTF8
    Write-Host ("{0,-32} {1,4} users" -f $target, $cohort.Count)
}

Export-Cohort `
    -SourceRows $rows `
    -Offset 0 `
    -Count $rows.Count `
    -ActivityId 0 `
    -FileName "read-users.csv"

Export-Cohort `
    -SourceRows $rows `
    -Offset 0 `
    -Count $OrderUserCount `
    -ActivityId 0 `
    -FileName "order-users.csv"

$activityCohorts = @(
    @{ Count = 50; ActivityId = $SeckillActivity50; Offset = 0 },
    @{ Count = 100; ActivityId = $SeckillActivity100; Offset = 50 },
    @{ Count = 200; ActivityId = $SeckillActivity200; Offset = 150 }
)

foreach ($definition in $activityCohorts) {
    if ($definition.ActivityId -gt 0) {
        Export-Cohort `
            -SourceRows $rows `
            -Offset $definition.Offset `
            -Count $definition.Count `
            -ActivityId $definition.ActivityId `
            -FileName "seckill-users-$($definition.Count).csv"
    } else {
        Write-Warning (
            "Skipped seckill-users-$($definition.Count).csv: " +
            "provide the corresponding published activity ID."
        )
    }
}

if ($SeckillInvariantActivity -gt 0) {
    Export-Cohort `
        -SourceRows $rows `
        -Offset 350 `
        -Count 1 `
        -ActivityId $SeckillInvariantActivity `
        -FileName "seckill-idempotency-user.csv"
    Export-Cohort `
        -SourceRows $rows `
        -Offset 351 `
        -Count 1 `
        -ActivityId $SeckillInvariantActivity `
        -FileName "seckill-user-limit-user.csv"
} else {
    Write-Warning (
        "Skipped seckill invariant files: provide a separate published " +
        "activity ID with at least two units of stock."
    )
}

Write-Host ""
Write-Host "Cohort files contain credentials and remain local/ignored."
Write-Host "No token value was printed."
