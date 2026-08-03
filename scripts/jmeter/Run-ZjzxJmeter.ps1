param(
    [string]$JMeterHome = $env:JMETER_HOME,
    [string]$BaseUrl = "http://127.0.0.1:8500",
    [string]$PrometheusUrl = "http://127.0.0.1:9090",
    [ValidateSet(
        "product_list",
        "product_detail",
        "seckill_activity_list",
        "cart_read",
        "order_list",
        "checkout_trade",
        "order_submit",
        "seckill_submit",
        "seckill_same_request",
        "seckill_user_limit",
        "mixed_read",
        "agent_search",
        "agent_chat"
    )]
    [string[]]$Scenarios = @(
        "product_list",
        "seckill_activity_list",
        "agent_search"
    ),
    [int[]]$ThreadLevels = @(10, 30, 60),
    [ValidateRange(10, 3600)]
    [int]$DurationSeconds = 60,
    [ValidateRange(0, 600)]
    [int]$RampSeconds = 10,
    [ValidateRange(0, 60000)]
    [int]$ThinkTimeMs = 0,
    [ValidateRange(1, 50)]
    [int]$PageSize = 20,
    [ValidateRange(1, 1000)]
    [int]$SkuNum = 1,
    [ValidateSet(1, 2)]
    [int]$OrderSource = 1,
    [ValidateRange(1, 1000)]
    [int]$WriteIterationsPerThread = 1,
    [ValidateRange(1, 100)]
    [int]$AgentChatIterationsPerThread = 1,
    [string]$Keyword = "",
    [string]$FixedRequestId = "",
    [string]$RunId = "",
    [string]$DataFile = "",
    [string]$ResultRoot = "",
    [switch]$IncludeWrites,
    [switch]$IncludeAgentChat,
    [switch]$GenerateHtml,
    [switch]$PlanOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = $PSScriptRoot
$testPlan = Join-Path $scriptRoot "zjzx-critical-api.jmx"
$exampleData = Join-Path $scriptRoot "data\users.csv.example"

if ([string]::IsNullOrWhiteSpace($RunId)) {
    $RunId = Get-Date -Format "yyyyMMddHHmmss"
}
if ($RunId -notmatch "^[A-Za-z0-9_-]{1,32}$") {
    throw "RunId must contain 1-32 letters, digits, underscores or hyphens."
}

if ([string]::IsNullOrWhiteSpace($DataFile)) {
    $DataFile = $exampleData
}
if ([string]::IsNullOrWhiteSpace($ResultRoot)) {
    $ResultRoot = Join-Path $scriptRoot "results"
}

$testPlan = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($testPlan)
$DataFile = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($DataFile)
$ResultRoot = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ResultRoot)

if (-not (Test-Path -LiteralPath $testPlan -PathType Leaf)) {
    throw "JMeter test plan not found: $testPlan"
}
if (-not (Test-Path -LiteralPath $DataFile -PathType Leaf)) {
    throw "CSV data file not found: $DataFile"
}

$uri = [Uri]$BaseUrl
if (-not $uri.IsAbsoluteUri -or @("http", "https") -notcontains $uri.Scheme) {
    throw "BaseUrl must be an absolute HTTP(S) URL"
}
if ($uri.AbsolutePath -ne "/") {
    throw "BaseUrl must not contain a path: $BaseUrl"
}
$port = if ($uri.IsDefaultPort) {
    if ($uri.Scheme -eq "https") { 443 } else { 80 }
} else {
    $uri.Port
}

$writeScenarios = @(
    "order_submit",
    "seckill_submit",
    "seckill_same_request",
    "seckill_user_limit"
)
$authenticatedScenarios = @(
    "cart_read",
    "order_list",
    "checkout_trade",
    "order_submit",
    "seckill_submit",
    "seckill_same_request",
    "seckill_user_limit",
    "mixed_read",
    "agent_chat"
)

foreach ($scenario in $Scenarios) {
    if ($writeScenarios -contains $scenario -and -not $IncludeWrites) {
        throw "Scenario '$scenario' writes business data. Re-run with -IncludeWrites."
    }
    if ($scenario -eq "agent_chat" -and -not $IncludeAgentChat) {
        throw "agent_chat calls the configured model API. Re-run with -IncludeAgentChat."
    }
}

$dataRows = @(Import-Csv -LiteralPath $DataFile -Encoding UTF8)

function Test-PositiveInteger {
    param([object]$Value)

    $parsed = 0L
    return [long]::TryParse(
        [string]$Value,
        [Globalization.NumberStyles]::Integer,
        [Globalization.CultureInfo]::InvariantCulture,
        [ref]$parsed
    ) -and $parsed -gt 0
}

$needsAuthentication = @($Scenarios | Where-Object {
    $authenticatedScenarios -contains $_
}).Count -gt 0

if ($needsAuthentication) {
    if ($dataRows.Count -eq 0) {
        throw "Authenticated scenarios require at least one CSV data row."
    }
    $invalidRows = @($dataRows | Where-Object {
        [string]::IsNullOrWhiteSpace($_.token) -or
        $_.token.StartsWith("REPLACE_")
    })
    if ($invalidRows.Count -gt 0) {
        throw "Authenticated scenarios require real mall tokens in the CSV data file."
    }
}

if ($Scenarios -contains "product_detail") {
    $invalidRows = @($dataRows | Where-Object {
        -not (Test-PositiveInteger $_.skuId)
    })
    if ($dataRows.Count -eq 0 -or $invalidRows.Count -gt 0) {
        throw "product_detail requires a positive skuId in every CSV data row."
    }
}

if ($Scenarios -contains "order_submit") {
    $invalidRows = @($dataRows | Where-Object {
        -not (Test-PositiveInteger $_.userAddressId) -or
        -not (Test-PositiveInteger $_.skuId)
    })
    if ($dataRows.Count -eq 0 -or $invalidRows.Count -gt 0) {
        throw "order_submit requires positive userAddressId and skuId values in every CSV data row."
    }
}

if (@($Scenarios | Where-Object {
    $_ -in @("seckill_submit", "seckill_same_request", "seckill_user_limit")
}).Count -gt 0) {
    if ($ThreadLevels.Count -ne 1) {
        throw "Each seckill write run accepts exactly one thread level."
    }
    $maxThreads = ($ThreadLevels | Measure-Object -Maximum).Maximum
    $validRows = @($dataRows | Where-Object {
        -not [string]::IsNullOrWhiteSpace($_.token) -and
        -not $_.token.StartsWith("REPLACE_") -and
        (Test-PositiveInteger $_.userAddressId) -and
        (Test-PositiveInteger $_.skuId) -and
        (Test-PositiveInteger $_.activityId)
    })
    if ($validRows.Count -ne $dataRows.Count) {
        throw "Seckill writes require token, userAddressId, skuId and activityId in every CSV data row."
    }
    $uniqueTokens = @($validRows.token | Sort-Object -Unique)
    if ($Scenarios -contains "seckill_submit" -and
            $uniqueTokens.Count -lt $maxThreads) {
        throw "seckill_submit requires at least $maxThreads distinct valid user rows."
    }
    if (@($Scenarios | Where-Object {
        $_ -in @("seckill_same_request", "seckill_user_limit")
    }).Count -gt 0 -and $dataRows.Count -ne 1) {
        throw "Seckill idempotency and user-limit tests require a one-row CSV data file."
    }
}

if ($Scenarios -contains "seckill_same_request") {
    if ([string]::IsNullOrWhiteSpace($FixedRequestId) -or
            $FixedRequestId.Length -gt 64) {
        throw "seckill_same_request requires FixedRequestId with 1-64 characters."
    }
}

foreach ($threads in $ThreadLevels) {
    if ($threads -le 0) {
        throw "Thread levels must contain only positive integers."
    }
}

$slaP95 = @{
    product_list = 500
    product_detail = 500
    seckill_activity_list = 500
    cart_read = 800
    order_list = 800
    checkout_trade = 1000
    order_submit = 1500
    seckill_submit = 1000
    seckill_same_request = 1000
    seckill_user_limit = 1000
    mixed_read = 1000
    agent_search = 2000
    agent_chat = 15000
}

Write-Host "JMeter plan:       $testPlan"
Write-Host "Gateway:           $($uri.Scheme)://$($uri.Host):$port"
Write-Host "Scenarios:         $($Scenarios -join ', ')"
Write-Host "Thread levels:     $($ThreadLevels -join ', ')"
Write-Host "Duration per stage: $DurationSeconds seconds"
Write-Host "CSV data:          $DataFile"
if ($Scenarios -contains "order_submit") {
    $plannedOrders = ($ThreadLevels | Measure-Object -Sum).Sum * $WriteIterationsPerThread
    $plannedUnits = $plannedOrders * $SkuNum
    Write-Host "Planned orders:    $plannedOrders"
    Write-Host "Planned SKU units: $plannedUnits"
    Write-Host "Order source:      $OrderSource"
    Write-Host "Order run ID:      $RunId"
}
if ($Scenarios -contains "agent_chat") {
    $plannedChats = ($ThreadLevels | Measure-Object -Sum).Sum * $AgentChatIterationsPerThread
    Write-Host "Planned AI calls:  $plannedChats"
}
if ($Scenarios -contains "seckill_same_request") {
    Write-Host "Fixed request ID:  $FixedRequestId"
}

if ($PlanOnly) {
    Write-Host "Plan validation completed. No load was generated."
    return
}

function Resolve-JMeterCommand {
    param([string]$InstallHome)

    if (-not [string]::IsNullOrWhiteSpace($InstallHome)) {
        $candidate = Join-Path $InstallHome "bin\jmeter.bat"
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
        throw "jmeter.bat not found under JMeterHome: $InstallHome"
    }

    foreach ($name in @("jmeter.bat", "jmeter")) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($null -ne $command) {
            return $command.Source
        }
    }
    throw "JMeter was not found. Set JMETER_HOME or pass -JMeterHome."
}

function Get-Percentile {
    param(
        [double[]]$Values,
        [ValidateRange(0, 100)]
        [double]$Percentile
    )

    if ($Values.Count -eq 0) {
        return 0
    }
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
    $index = [Math]::Max(0, [Math]::Min($sorted.Count - 1, $index))
    return [double]$sorted[$index]
}

function Read-JMeterMetrics {
    param(
        [string]$JtlPath,
        [string]$Scenario,
        [int]$Threads,
        [int]$P95Limit
    )

    if (-not (Test-Path -LiteralPath $JtlPath -PathType Leaf)) {
        throw "JMeter result file was not created: $JtlPath"
    }
    $rows = @(Import-Csv -LiteralPath $JtlPath -Encoding UTF8)
    if ($rows.Count -eq 0) {
        throw "No samples were written to $JtlPath"
    }

    $starts = @($rows | ForEach-Object { [double]$_.timeStamp })
    $ends = @($rows | ForEach-Object {
        [double]$_.timeStamp + [double]$_.elapsed
    })
    $start = ($starts | Measure-Object -Minimum).Minimum
    $end = ($ends | Measure-Object -Maximum).Maximum
    $wallSeconds = [Math]::Max(0.001, ($end - $start) / 1000.0)
    $categorizedRows = @($rows | ForEach-Object {
        $category = if ($_.PSObject.Properties.Name -contains "responseCategory") {
            [string]$_.responseCategory
        } else {
            ""
        }
        if ([string]::IsNullOrWhiteSpace($category)) {
            $category = if ($_.success -eq "true") {
                "SUCCESS"
            } else {
                "TECHNICAL_ERROR"
            }
        }
        [PSCustomObject]@{
            Row = $_
            Category = $category
        }
    })
    $successCount = @($categorizedRows | Where-Object {
        $_.Category -eq "SUCCESS"
    }).Count
    $businessRejectedCount = @($categorizedRows | Where-Object {
        $_.Category -eq "BUSINESS_REJECTED"
    }).Count
    $businessErrorCount = @($categorizedRows | Where-Object {
        $_.Category -eq "BUSINESS_ERROR"
    }).Count
    $technicalErrorCount = @($categorizedRows | Where-Object {
        $_.Category -eq "TECHNICAL_ERROR"
    }).Count
    $http429Count = @($rows | Where-Object {
        [string]$_.responseCode -eq "429"
    }).Count
    $http5xxCount = @($rows | Where-Object {
        [string]$_.responseCode -match "^5\d\d$"
    }).Count
    $agentFallbackResponseCount = @($rows | Where-Object {
        $_.PSObject.Properties.Name -contains "agentMode" -and
        [string]$_.agentMode -eq "DETERMINISTIC_FALLBACK"
    }).Count
    $errorCount = $businessErrorCount + $technicalErrorCount
    $elapsed = [double[]]@($rows | ForEach-Object { [double]$_.elapsed })
    $p95 = Get-Percentile -Values $elapsed -Percentile 95
    $errorRate = 100.0 * $errorCount / $rows.Count
    $businessRejectedRate = 100.0 * $businessRejectedCount / $rows.Count
    $businessErrorRate = 100.0 * $businessErrorCount / $rows.Count
    $technicalErrorRate = 100.0 * $technicalErrorCount / $rows.Count

    [PSCustomObject]@{
        Scenario = $Scenario
        Threads = $Threads
        Samples = $rows.Count
        Successes = $successCount
        BusinessRejected = $businessRejectedCount
        BusinessErrors = $businessErrorCount
        TechnicalErrors = $technicalErrorCount
        Http429 = $http429Count
        Http5xx = $http5xxCount
        ModelFallbacks = 0
        UpstreamRateLimits = 0
        AgentFallbackResponses = $agentFallbackResponseCount
        Errors = $errorCount
        ErrorRatePct = [Math]::Round($errorRate, 3)
        BusinessRejectRatePct = [Math]::Round($businessRejectedRate, 3)
        BusinessErrorRatePct = [Math]::Round($businessErrorRate, 3)
        TechnicalErrorRatePct = [Math]::Round($technicalErrorRate, 3)
        Qps = [Math]::Round($rows.Count / $wallSeconds, 2)
        SuccessQps = [Math]::Round($successCount / $wallSeconds, 2)
        BusinessRejectQps = [Math]::Round(
            $businessRejectedCount / $wallSeconds,
            2
        )
        AverageMs = [Math]::Round(
            ($elapsed | Measure-Object -Average).Average,
            2
        )
        P50Ms = [Math]::Round(
            (Get-Percentile -Values $elapsed -Percentile 50),
            2
        )
        P95Ms = [Math]::Round($p95, 2)
        P99Ms = [Math]::Round(
            (Get-Percentile -Values $elapsed -Percentile 99),
            2
        )
        Stable = ($errorRate -le 1.0 -and $p95 -le $P95Limit)
    }
}

function Get-AgentModelFallbackCounters {
    $counters = @{}
    try {
        $query = [Uri]::EscapeDataString(
            "sum by(reason) (zjzx_agent_model_fallbacks_total)"
        )
        $response = Invoke-RestMethod `
            -Uri "$PrometheusUrl/api/v1/query?query=$query" `
            -TimeoutSec 5
        if ($response.status -ne "success") {
            return $counters
        }
        foreach ($item in $response.data.result) {
            $reason = [string]$item.metric.reason
            if (-not [string]::IsNullOrWhiteSpace($reason)) {
                $counters[$reason] = [double]$item.value[1]
            }
        }
    } catch {
        Write-Warning "Cannot query Agent fallback metrics from Prometheus."
    }
    return $counters
}

function Get-CounterDelta {
    param(
        [hashtable]$Before,
        [hashtable]$After,
        [string]$Reason = ""
    )

    if (-not [string]::IsNullOrWhiteSpace($Reason)) {
        $beforeValue = if ($Before.ContainsKey($Reason)) {
            [double]$Before[$Reason]
        } else {
            0D
        }
        $afterValue = if ($After.ContainsKey($Reason)) {
            [double]$After[$Reason]
        } else {
            0D
        }
        return [long][Math]::Max(0D, $afterValue - $beforeValue)
    }

    $reasons = @(@($Before.Keys) + @($After.Keys) |
        Sort-Object -Unique)
    $sum = 0D
    foreach ($name in $reasons) {
        $sum += Get-CounterDelta -Before $Before -After $After -Reason $name
    }
    return [long]$sum
}

if (-not [string]::IsNullOrWhiteSpace($JMeterHome)) {
    $JMeterHome = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath(
        $JMeterHome
    )
    $env:JMETER_HOME = $JMeterHome
}
$jmeter = Resolve-JMeterCommand -InstallHome $JMeterHome
$runRoot = Join-Path $ResultRoot (Get-Date -Format "yyyyMMdd-HHmmss")
New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
$metrics = [System.Collections.Generic.List[object]]::new()

foreach ($scenario in $Scenarios) {
    foreach ($threads in $ThreadLevels) {
        $stageName = "$scenario-$($threads)t"
        $stageRoot = Join-Path $runRoot $stageName
        New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null
        $jtl = Join-Path $stageRoot "samples.jtl"
        $jmeterLog = Join-Path $stageRoot "jmeter.log"
        $iterations = switch ($scenario) {
            "seckill_submit" { 1 }
            "seckill_same_request" { 1 }
            "seckill_user_limit" { 1 }
            "order_submit" { $WriteIterationsPerThread }
            "agent_chat" { $AgentChatIterationsPerThread }
            default { -1 }
        }
        $responseTimeout = if ($scenario -eq "agent_chat") { 60000 } else { 10000 }
        $fallbackBefore = if ($scenario -eq "agent_chat") {
            Get-AgentModelFallbackCounters
        } else {
            @{}
        }

        $arguments = @(
            "-n",
            "-t", $testPlan,
            "-l", $jtl,
            "-j", $jmeterLog,
            "-Jscenario=$scenario",
            "-Jprotocol=$($uri.Scheme)",
            "-Jhost=$($uri.Host)",
            "-Jport=$port",
            "-Jthreads=$threads",
            "-JrampSeconds=$RampSeconds",
            "-JdurationSeconds=$DurationSeconds",
            "-Jiterations=$iterations",
            "-JthinkTimeMs=$ThinkTimeMs",
            "-JpageSize=$PageSize",
            "-JskuNum=$SkuNum",
            "-JorderSource=$OrderSource",
            "-Jkeyword=$Keyword",
            "-JfixedRequestId=$FixedRequestId",
            "-JrunId=$RunId",
            "-JdataFile=$DataFile",
            "-JconnectTimeoutMs=3000",
            "-JresponseTimeoutMs=$responseTimeout",
            "-Jjmeter.save.saveservice.output_format=csv",
            "-Jjmeter.save.saveservice.print_field_names=true",
            "-Jjmeter.save.saveservice.timestamp_format=ms",
            "-Jsample_variables=businessCode,responseCategory,requestId,userAddressId,seckillOrderNo,agentMode",
            "-Jjmeter.reportgenerator.overall_granularity=1000"
        )
        if ($GenerateHtml) {
            $arguments += @("-e", "-o", (Join-Path $stageRoot "html"))
        }

        Write-Host ""
        Write-Host "=== $stageName ==="
        & $jmeter @arguments
        $jmeterExitCode = $LASTEXITCODE
        if ($jmeterExitCode -ne 0) {
            throw "JMeter failed for $stageName with exit code $jmeterExitCode. See $jmeterLog"
        }
        if (-not (Test-Path -LiteralPath $jtl -PathType Leaf)) {
            throw "JMeter did not create samples for $stageName. Check JMETER_HOME and $jmeterLog"
        }

        $stageMetrics = Read-JMeterMetrics `
            -JtlPath $jtl `
            -Scenario $scenario `
            -Threads $threads `
            -P95Limit $slaP95[$scenario]
        if ($scenario -eq "agent_chat") {
            Start-Sleep -Seconds 6
            $fallbackAfter = Get-AgentModelFallbackCounters
            $stageMetrics.ModelFallbacks = Get-CounterDelta `
                -Before $fallbackBefore `
                -After $fallbackAfter
            $stageMetrics.UpstreamRateLimits = Get-CounterDelta `
                -Before $fallbackBefore `
                -After $fallbackAfter `
                -Reason "upstream_rate_limit"
        }

        if ($scenario -eq "seckill_same_request") {
            $rows = @(Import-Csv -LiteralPath $jtl -Encoding UTF8)
            $orderNos = @($rows.seckillOrderNo | Where-Object {
                -not [string]::IsNullOrWhiteSpace($_)
            } | Sort-Object -Unique)
            if ($stageMetrics.Errors -gt 0 -or
                    $stageMetrics.BusinessRejected -gt 0 -or
                    $orderNos.Count -ne 1) {
                throw "Seckill idempotency failed: expected all requests to return one orderNo."
            }
        }
        if ($scenario -eq "seckill_user_limit") {
            if ($stageMetrics.Successes -ne 1 -or
                    $stageMetrics.BusinessRejected -ne ($threads - 1) -or
                    $stageMetrics.Errors -gt 0) {
                throw "Seckill one-user-one-order failed: expected one success and $($threads - 1) code-239 rejections."
            }
        }
        $metrics.Add($stageMetrics)
        $stageMetrics | Format-Table -AutoSize
    }
}

$summaryCsv = Join-Path $runRoot "qps-summary.csv"
$metrics | Export-Csv -LiteralPath $summaryCsv -NoTypeInformation -Encoding UTF8

$markdown = [System.Collections.Generic.List[string]]::new()
$markdown.Add("# ZJZX JMeter QPS Report")
$markdown.Add("")
$markdown.Add("- Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$markdown.Add("- Gateway: $($uri.Scheme)://$($uri.Host):$port")
$markdown.Add("- Stable rule: technical plus unexpected business error rate <= 1% and scenario P95 within SLA")
$markdown.Add("")
$markdown.Add("| Scenario | Threads | Samples | Success QPS | Expected reject % | Business error % | Technical error % | HTTP 429 | HTTP 5xx | Fallback responses | Model fallback metrics | Upstream limits | Avg ms | P95 ms | P99 ms | Stable |")
$markdown.Add("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |")
foreach ($item in $metrics) {
    $markdown.Add(
        "| $($item.Scenario) | $($item.Threads) | $($item.Samples) | " +
        "$($item.SuccessQps) | $($item.BusinessRejectRatePct) | " +
        "$($item.BusinessErrorRatePct) | $($item.TechnicalErrorRatePct) | " +
        "$($item.Http429) | $($item.Http5xx) | " +
        "$($item.AgentFallbackResponses) | " +
        "$($item.ModelFallbacks) | $($item.UpstreamRateLimits) | " +
        "$($item.AverageMs) | " +
        "$($item.P95Ms) | $($item.P99Ms) | $($item.Stable) |"
    )
}
$markdown.Add("")
$markdown.Add("## Highest stable stage")
$markdown.Add("")
foreach ($scenario in $Scenarios) {
    $stable = @($metrics | Where-Object {
        $_.Scenario -eq $scenario -and $_.Stable
    } | Sort-Object Threads)
    if ($stable.Count -eq 0) {
        $markdown.Add("- ${scenario}: no stable stage")
    } else {
        $best = $stable[-1]
        $markdown.Add(
            "- ${scenario}: $($best.SuccessQps) success QPS at " +
            "$($best.Threads) threads, P95 $($best.P95Ms) ms"
        )
    }
}

$reportPath = Join-Path $runRoot "qps-report.md"
$markdown | Set-Content -LiteralPath $reportPath -Encoding UTF8

Write-Host ""
Write-Host "=== Final summary ==="
$metrics | Format-Table -AutoSize
Write-Host "CSV report:      $summaryCsv"
Write-Host "Markdown report: $reportPath"
