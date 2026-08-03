[CmdletBinding()]
param(
    [ValidateRange(1, 10000)]
    [int]$Count = 20,

    [ValidateRange(1, 100)]
    [int]$BatchSize = 100,

    [ValidatePattern('^1[3-9]\d$')]
    [string]$PhonePrefix = '199',

    [ValidateRange(0, 99999999)]
    [int]$SequenceStart = 10000000,

    [ValidateRange(1, [long]::MaxValue)]
    [long]$SkuId = 15,

    [ValidateRange(1, [long]::MaxValue)]
    [long]$ActivityId = 8,

    [string]$GatewayBaseUrl = 'http://127.0.0.1:8500',

    [string]$UserServiceBaseUrl = 'http://127.0.0.1:8512',

    [string]$DefaultPassword = $env:ZJZX_TEST_USER_PASSWORD,

    [string]$InternalToken = $env:ZJZX_INTERNAL_API_TOKEN,

    [string]$TestDataApiKey = $env:ZJZX_TEST_DATA_API_KEY,

    [ValidatePattern('^[A-Za-z0-9._-]{1,40}$')]
    [string]$Tag = 'jmeter-load-test'
)

$ErrorActionPreference = 'Stop'

function Read-SecretText {
    param(
        [string]$Value,
        [string]$Prompt
    )

    if (-not [string]::IsNullOrWhiteSpace($Value)) {
        return $Value
    }

    $secureValue = Read-Host -Prompt $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Invoke-ZjzxJsonApi {
    param(
        [ValidateSet('GET', 'POST')]
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [object]$Body
    )

    $request = @{
        Method      = $Method
        Uri         = $Url
        Headers     = $Headers
        ContentType = 'application/json'
        TimeoutSec  = 20
    }
    if ($null -ne $Body) {
        $request.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }

    try {
        $response = Invoke-RestMethod @request
    }
    catch {
        throw "$Method $Url failed: $($_.Exception.Message)"
    }

    if ($null -eq $response -or [int]$response.code -ne 200) {
        $code = if ($null -eq $response) { 'no-response' } else { $response.code }
        $message = if ($null -eq $response) { 'empty response' } else { $response.message }
        throw "$Method $Url returned business error code=$code message=$message"
    }
    return $response.data
}

function Write-CsvAtomic {
    param(
        [object[]]$Rows,
        [string]$Path
    )

    $temporaryPath = "$Path.tmp"
    $csvLines = @($Rows | ConvertTo-Csv -NoTypeInformation)
    $utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllLines($temporaryPath, $csvLines, $utf8WithoutBom)
    Move-Item -LiteralPath $temporaryPath -Destination $Path -Force
}

$DefaultPassword = Read-SecretText $DefaultPassword 'JMeter test user password'
$InternalToken = Read-SecretText $InternalToken 'ZJZX internal API token'
$TestDataApiKey = Read-SecretText $TestDataApiKey 'Test-data API key'

if (($DefaultPassword.Length -lt 8) -or
        ($DefaultPassword.Length -gt 72) -or
        ($DefaultPassword -notmatch '^[\x21-\x7E]+$')) {
    throw 'The test password must contain 8-72 non-space ASCII characters.'
}

$lastSequence = [long]$SequenceStart + $Count - 1
if ($lastSequence -gt 99999999) {
    throw 'The phone sequence range exceeds eight digits.'
}

$GatewayBaseUrl = $GatewayBaseUrl.TrimEnd('/')
$UserServiceBaseUrl = $UserServiceBaseUrl.TrimEnd('/')
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$dataDirectory = Join-Path $repoRoot 'scripts\jmeter\data'
$usersFile = Join-Path $dataDirectory 'users.csv'
$accountsFile = Join-Path $dataDirectory 'test-accounts.csv'
New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null

$internalHeaders = @{
    'X-Internal-Token' = $InternalToken
    'X-Test-Data-Key'  = $TestDataApiKey
}

Write-Host "Preparing $Count marked test accounts in batches of at most $BatchSize..."
$accounts = New-Object System.Collections.Generic.List[object]
$createdCount = 0
$resetCount = 0
for ($batchOffset = 0; $batchOffset -lt $Count; $batchOffset += $BatchSize) {
    $currentBatchSize = [Math]::Min($BatchSize, $Count - $batchOffset)
    $batchRequest = @{
        count           = $currentBatchSize
        phonePrefix     = $PhonePrefix
        sequenceStart   = $SequenceStart + $batchOffset
        defaultPassword = $DefaultPassword
        nickNamePrefix  = '压测用户'
        tag             = $Tag
    }
    $batchResult = Invoke-ZjzxJsonApi `
        -Method POST `
        -Url "$UserServiceBaseUrl/api/user/internal/test-data/users/batch" `
        -Headers $internalHeaders `
        -Body $batchRequest
    $batchAccounts = @($batchResult.accounts)
    if ($batchAccounts.Count -ne $currentBatchSize) {
        throw "Batch at offset $batchOffset returned $($batchAccounts.Count) accounts; expected $currentBatchSize."
    }
    foreach ($account in $batchAccounts) {
        $accounts.Add($account)
    }
    $createdCount += [int]$batchResult.createdCount
    $resetCount += [int]$batchResult.resetCount
    Write-Host "Account batch $([Math]::Floor($batchOffset / $BatchSize) + 1) completed: $($accounts.Count)/$Count"
}

if ($accounts.Count -ne $Count) {
    throw "The internal endpoint returned $($accounts.Count) accounts; expected $Count."
}

$questions = @(
    '预算5000元的开发电脑',
    '适合日常办公的小型电脑',
    '适合视频剪辑的电脑配置',
    '能运行多个虚拟机的电脑',
    '适合大学生编程的笔记本',
    '适合图像设计工作的电脑',
    '预算8000元的游戏电脑',
    '适合远程办公的轻薄本'
)

$jmeterRows = New-Object System.Collections.Generic.List[object]
$accountRows = New-Object System.Collections.Generic.List[object]

for ($index = 0; $index -lt $accounts.Count; $index++) {
    $account = $accounts[$index]
    $loginData = Invoke-ZjzxJsonApi `
        -Method POST `
        -Url "$GatewayBaseUrl/api/user/userInfo/login" `
        -Body @{
            username = [string]$account.username
            password = $DefaultPassword
        }
    $token = [string]$loginData
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Login returned an empty token for account $($account.username)."
    }

    $userHeaders = @{ token = $token }
    $null = Invoke-ZjzxJsonApi `
        -Method GET `
        -Url "$GatewayBaseUrl/api/user/userInfo/auth/getCurrentUserInfo" `
        -Headers $userHeaders

    $addressData = Invoke-ZjzxJsonApi `
        -Method GET `
        -Url "$GatewayBaseUrl/api/user/userAddress/auth/findUserAddressList" `
        -Headers $userHeaders
    $addresses = @($addressData)
    $selectedAddress = $addresses |
        Where-Object { [int]$_.isDefault -eq 1 } |
        Select-Object -First 1
    if ($null -eq $selectedAddress) {
        $selectedAddress = $addresses | Select-Object -First 1
    }

    if ($null -eq $selectedAddress) {
        $selectedAddress = Invoke-ZjzxJsonApi `
            -Method POST `
            -Url "$GatewayBaseUrl/api/user/userAddress/auth" `
            -Headers $userHeaders `
            -Body @{
                name         = "压测用户$('{0:D4}' -f ($index + 1))"
                phone        = [string]$account.phone
                tagName      = '压测'
                provinceCode = '110000'
                cityCode     = '110100'
                districtCode = '110101'
                address      = "测试路$($index + 1)号，仅限压测"
                isDefault    = 1
            }
    }

    $addressId = [string]$selectedAddress.id
    if ([string]::IsNullOrWhiteSpace($addressId)) {
        throw "No address ID was returned for account $($account.username)."
    }

    $keyword = "$($questions[$index % $questions.Count])，测试用户$('{0:D4}' -f ($index + 1))"
    $jmeterRows.Add([pscustomobject]@{
        token         = $token
        userAddressId = $addressId
        skuId         = [string]$SkuId
        activityId    = [string]$ActivityId
        keyword       = $keyword
    })
    $accountRows.Add([pscustomobject]@{
        username = [string]$account.username
        password = $DefaultPassword
    })
    if ((($index + 1) % 25 -eq 0) -or ($index + 1 -eq $Count)) {
        Write-Host "Login and address verification: $($index + 1)/$Count"
    }
}

Write-CsvAtomic -Rows $jmeterRows.ToArray() -Path $usersFile
Write-CsvAtomic -Rows $accountRows.ToArray() -Path $accountsFile

Write-Host ''
Write-Host "Created users: $createdCount"
Write-Host "Reset test users: $resetCount"
Write-Host "Verified tokens and addresses: $($jmeterRows.Count)"
Write-Host "JMeter data written to: $usersFile"
Write-Host "Local account data written to: $accountsFile"
Write-Host 'Token and password values were not printed.'
