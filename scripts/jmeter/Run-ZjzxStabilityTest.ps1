[CmdletBinding()]
param(
    [string]$JMeterHome = $env:JMETER_HOME,
    [string]$BaseUrl = "http://127.0.0.1:8500",
    [string]$PrometheusUrl = "http://127.0.0.1:9090",
    [string]$DataFile = (Join-Path $PSScriptRoot "data\read-users.csv"),
    [ValidateRange(1, 1000)]
    [int]$Threads = 100,
    [ValidateRange(60, 7200)]
    [int]$DurationSeconds = 1800,
    [ValidateRange(1, 60)]
    [int]$SampleIntervalSeconds = 15,
    [ValidateRange(0, 60000)]
    [int]$ThinkTimeMs = 100,
    [string]$MySqlContainer = "mysql8030",
    [string]$RedisContainer = "redis7010",
    [string]$MySqlPassword = $env:MYSQL_PASSWORD,
    [string]$RedisPassword = $env:REDIS_PASSWORD
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$runner = Join-Path $PSScriptRoot "Run-ZjzxJmeter.ps1"
$DataFile =
    $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath(
        $DataFile
    )
if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
    throw "JMeter runner not found: $runner"
}
if (-not (Test-Path -LiteralPath $DataFile -PathType Leaf)) {
    throw "JMeter data file not found: $DataFile"
}

function Assert-PrometheusTargets {
    try {
        $response = Invoke-RestMethod `
            -Uri "$PrometheusUrl/api/v1/targets" `
            -TimeoutSec 5
    } catch {
        throw "Prometheus target API is unavailable: $($_.Exception.Message)"
    }

    $targets = @($response.data.activeTargets)
    $expectedJobs = [ordered]@{
        "zjzx-server-gateway" = 1
        "zjzx-business-services" = 5
        "zjzx-rabbitmq" = 1
    }
    $failures = [System.Collections.Generic.List[string]]::new()
    foreach ($job in $expectedJobs.Keys) {
        $jobTargets = @($targets | Where-Object {
            $_.labels.job -eq $job
        })
        $healthy = @($jobTargets | Where-Object {
            $_.health -eq "up"
        }).Count
        if ($jobTargets.Count -ne $expectedJobs[$job] -or
                $healthy -ne $expectedJobs[$job]) {
            $failures.Add(
                "$job expected $($expectedJobs[$job]) UP, found $healthy UP"
            )
        }
    }
    if ($failures.Count -gt 0) {
        throw (
            "Prometheus preflight failed: " +
            ($failures -join "; ") +
            ". Restart services with Actuator configuration before testing."
        )
    }
    Write-Host "[PASS] Prometheus targets: Gateway 1, business services 5, RabbitMQ 1."
}

Assert-PrometheusTargets

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

$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$runRoot = Join-Path $PSScriptRoot "results\stability-$runId"
$jmeterRoot = Join-Path $runRoot "jmeter"
$metricsCsv = Join-Path $runRoot "runtime-metrics.csv"
$runnerOut = Join-Path $runRoot "jmeter-runner.out.log"
$runnerErr = Join-Path $runRoot "jmeter-runner.err.log"
New-Item -ItemType Directory -Path $runRoot -Force | Out-Null

function Get-PrometheusScalar {
    param([string]$Query)

    try {
        $encoded = [Uri]::EscapeDataString($Query)
        $response = Invoke-RestMethod `
            -Uri "$PrometheusUrl/api/v1/query?query=$encoded" `
            -TimeoutSec 5
        if ($response.status -ne "success" -or
                $response.data.result.Count -eq 0) {
            return 0
        }
        $sum = 0.0
        foreach ($item in $response.data.result) {
            $sum += [double]$item.value[1]
        }
        return $sum
    } catch {
        return [double]::NaN
    }
}

function Get-MySqlStatus {
    $names = @(
        "Threads_connected",
        "Threads_running",
        "Slow_queries",
        "Created_tmp_disk_tables",
        "Aborted_connects"
    )
    $sql = @"
SELECT variable_name, variable_value
FROM performance_schema.global_status
WHERE variable_name IN ('$($names -join "','")');
"@
    $output = @(& docker exec `
        -e "MYSQL_PWD=$MySqlPassword" `
        $MySqlContainer `
        mysql `
        -uroot `
        -D db_zjzx `
        --batch `
        --skip-column-names `
        -e $sql)
    if ($LASTEXITCODE -ne 0) {
        throw "Cannot sample MySQL status."
    }
    $result = @{}
    foreach ($row in $output) {
        $parts = ([string]$row) -split "`t"
        if ($parts.Count -eq 2) {
            $result[$parts[0].ToLowerInvariant()] = [long]$parts[1]
        }
    }
    return $result
}

function Get-RedisStatus {
    $output = @(& docker exec `
        $RedisContainer `
        redis-cli `
        "--no-auth-warning" `
        "-a" `
        $RedisPassword `
        "INFO")
    if ($LASTEXITCODE -ne 0) {
        throw "Cannot sample Redis INFO."
    }
    $result = @{}
    foreach ($line in $output) {
        if ($line -match "^([^:#]+):(.+)$") {
            $result[$Matches[1]] = $Matches[2].Trim()
        }
    }
    return $result
}

function Get-LongValue {
    param(
        [hashtable]$Values,
        [string]$Name
    )

    if (-not $Values.ContainsKey($Name)) {
        return 0L
    }
    $parsed = 0L
    if ([long]::TryParse([string]$Values[$Name], [ref]$parsed)) {
        return $parsed
    }
    return 0L
}

function Add-RuntimeSample {
    $mysql = Get-MySqlStatus
    $redis = Get-RedisStatus
    $sample = [PSCustomObject]@{
        Timestamp = (Get-Date).ToString("o")
        JvmHeapUsedBytes = [long](Get-PrometheusScalar `
            'sum(jvm_memory_used_bytes{area="heap"})')
        JvmLiveThreads = [long](Get-PrometheusScalar `
            'sum(jvm_threads_live_threads)')
        HikariActive = [long](Get-PrometheusScalar `
            'sum(hikaricp_connections_active)')
        HikariPending = [long](Get-PrometheusScalar `
            'sum(hikaricp_connections_pending)')
        HikariMax = [long](Get-PrometheusScalar `
            'sum(hikaricp_connections_max)')
        RabbitReady = [long](Get-PrometheusScalar `
            'sum(rabbitmq_queue_messages_ready)')
        RabbitUnacked = [long](Get-PrometheusScalar `
            'sum(rabbitmq_queue_messages_unacked)')
        RabbitConnections = [long](Get-PrometheusScalar `
            'sum(rabbitmq_connections)')
        MySqlThreadsConnected = Get-LongValue `
            -Values $mysql `
            -Name "threads_connected"
        MySqlThreadsRunning = Get-LongValue `
            -Values $mysql `
            -Name "threads_running"
        MySqlSlowQueries = Get-LongValue `
            -Values $mysql `
            -Name "slow_queries"
        MySqlDiskTempTables = Get-LongValue `
            -Values $mysql `
            -Name "created_tmp_disk_tables"
        MySqlAbortedConnects = Get-LongValue `
            -Values $mysql `
            -Name "aborted_connects"
        RedisConnectedClients = Get-LongValue `
            -Values $redis `
            -Name "connected_clients"
        RedisUsedMemoryBytes = Get-LongValue `
            -Values $redis `
            -Name "used_memory"
        RedisOpsPerSecond = Get-LongValue `
            -Values $redis `
            -Name "instantaneous_ops_per_sec"
        RedisRejectedConnections = Get-LongValue `
            -Values $redis `
            -Name "rejected_connections"
        RedisEvictedKeys = Get-LongValue `
            -Values $redis `
            -Name "evicted_keys"
    }
    $sample | Export-Csv `
        -LiteralPath $metricsCsv `
        -Append `
        -NoTypeInformation `
        -Encoding UTF8
    $statusLine = (
        "[{0}] heap={1:N0}MB hikari={2}/{3} mysql={4}/{5} " +
        "redis={6}/s rabbit={7}+{8}"
    ) -f @(
        (Get-Date -Format "HH:mm:ss"),
        ($sample.JvmHeapUsedBytes / 1MB),
        $sample.HikariActive,
        $sample.HikariMax,
        $sample.MySqlThreadsRunning,
        $sample.MySqlThreadsConnected,
        $sample.RedisOpsPerSecond,
        $sample.RabbitReady,
        $sample.RabbitUnacked
    )
    Write-Host $statusLine
}

$powerShell = (Get-Process -Id $PID).Path
$arguments = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$runner`"",
    "-JMeterHome", "`"$JMeterHome`"",
    "-BaseUrl", $BaseUrl,
    "-Scenarios", "mixed_read",
    "-ThreadLevels", $Threads,
    "-DurationSeconds", $DurationSeconds,
    "-RampSeconds", ([Math]::Min(30, $Threads)),
    "-ThinkTimeMs", $ThinkTimeMs,
    "-DataFile", "`"$DataFile`"",
    "-ResultRoot", "`"$jmeterRoot`"",
    "-RunId", "stability_$($runId.Replace('-', '_'))"
)

Write-Host "Starting $DurationSeconds-second mixed-read stability test."
Write-Host "Runtime metrics: $metricsCsv"
$process = Start-Process `
    -FilePath $powerShell `
    -ArgumentList $arguments `
    -PassThru `
    -WindowStyle Hidden `
    -RedirectStandardOutput $runnerOut `
    -RedirectStandardError $runnerErr

try {
    while (-not $process.HasExited) {
        Add-RuntimeSample
        Start-Sleep -Seconds $SampleIntervalSeconds
        $process.Refresh()
    }
} finally {
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id
    }
}

if ($process.ExitCode -ne 0) {
    throw "JMeter stability run failed. See $runnerErr"
}

$samples = @(Import-Csv -LiteralPath $metricsCsv -Encoding UTF8)
$peakHikariPending = ($samples.HikariPending |
    ForEach-Object { [long]$_ } |
    Measure-Object -Maximum).Maximum
$peakMySqlRunning = ($samples.MySqlThreadsRunning |
    ForEach-Object { [long]$_ } |
    Measure-Object -Maximum).Maximum
$peakRabbitBacklog = ($samples |
    ForEach-Object { [long]$_.RabbitReady + [long]$_.RabbitUnacked } |
    Measure-Object -Maximum).Maximum

$summary = @"
# ZJZX 30-minute Stability Summary

- Run: $runId
- Threads: $Threads
- Duration: $DurationSeconds seconds
- Runtime samples: $($samples.Count)
- Peak Hikari pending: $peakHikariPending
- Peak MySQL running threads: $peakMySqlRunning
- Peak RabbitMQ ready plus unacked: $peakRabbitBacklog
- JMeter output: $jmeterRoot
- Runtime metrics: $metricsCsv
"@
$summaryPath = Join-Path $runRoot "stability-summary.md"
$summary | Set-Content -LiteralPath $summaryPath -Encoding UTF8
Write-Host "Stability summary: $summaryPath"
