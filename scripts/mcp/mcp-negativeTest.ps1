param(
    [string]$BaseUrl = "http://127.0.0.1:8520",
    [string]$GatewayBaseUrl = "http://127.0.0.1:8500",
    [string]$EndpointPath = "/mcp",
    [string]$ApiKey = $env:AGENT_MCP_API_KEY,
    [long]$SkuId = 14,
    [ValidateSet(
        "All",
        "Authentication",
        "Discovery",
        "Positive",
        "Validation",
        "Security",
        "Gateway"
    )]
    [string]$Suite = "All"
)

$ErrorActionPreference = "Stop"

# ------------------------------------------------------------
# API Key
# ------------------------------------------------------------

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    $secureKey = Read-Host "MCP API key" -AsSecureString
    $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR(
        $secureKey
    )

    try {
        $ApiKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR(
            $keyPointer
        )
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
    }
}

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    throw "AGENT_MCP_API_KEY is required"
}

# ------------------------------------------------------------
# Global values
# ------------------------------------------------------------

$endpoint = $BaseUrl.TrimEnd("/") + "/" + $EndpointPath.TrimStart("/")
$gatewayEndpoint = $GatewayBaseUrl.TrimEnd("/") + "/" +
    $EndpointPath.TrimStart("/")

$headers = @{
    "X-MCP-API-Key" = $ApiKey
    "Accept"        = "application/json, text/event-stream"
}

$script:Passed = 0
$script:Failed = 0
$script:Skipped = 0

$allowedProductFields = @(
    "skuId",
    "productName",
    "skuName",
    "thumbImg",
    "salePrice",
    "marketPrice",
    "skuSpec",
    "unitName",
    "inStock"
)

$forbiddenFields = @(
    "costPrice",
    "stockNum",
    "userId",
    "orderNo",
    "addressId",
    "paymentId",
    "token"
)

$expectedTools = @(
    "searchProducts",
    "getProductSnapshot",
    "retrieveProductKnowledge"
)

# ------------------------------------------------------------
# Test reporting
# ------------------------------------------------------------

function Write-TestPass {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $script:Passed++
    Write-Host "[PASS] $Name" -ForegroundColor Green
}

function Write-TestFail {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    $script:Failed++
    Write-Host "[FAIL] $Name" -ForegroundColor Red
    Write-Host "       $Message" -ForegroundColor Red
}

function Write-TestSkip {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    $script:Skipped++
    Write-Host "[SKIP] $Name" -ForegroundColor Yellow
    Write-Host "       $Message" -ForegroundColor Yellow
}

function Invoke-TestCase {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [scriptblock]$Test
    )

    try {
        & $Test
        Write-TestPass -Name $Name
    }
    catch {
        Write-TestFail `
            -Name $Name `
            -Message $_.Exception.Message
    }
}

# ------------------------------------------------------------
# JSON-RPC helpers
# ------------------------------------------------------------

function New-McpRequestBody {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Id,

        [Parameter(Mandatory = $true)]
        [string]$Method,

        [Parameter(Mandatory = $true)]
        [hashtable]$Params
    )

    @{
        jsonrpc = "2.0"
        id      = $Id
        method  = $Method
        params  = $Params
    } | ConvertTo-Json -Depth 20 -Compress
}

function Invoke-McpRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Id,

        [Parameter(Mandatory = $true)]
        [string]$Method,

        [Parameter(Mandatory = $true)]
        [hashtable]$Params,

        [hashtable]$RequestHeaders = $headers,

        [string]$RequestEndpoint = $endpoint
    )

    $body = New-McpRequestBody `
        -Id $Id `
        -Method $Method `
        -Params $Params

    Invoke-RestMethod `
        -Uri $RequestEndpoint `
        -Method Post `
        -Headers $RequestHeaders `
        -ContentType "application/json" `
        -Body $body
}

function Invoke-McpHttpRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Id,

        [Parameter(Mandatory = $true)]
        [string]$Method,

        [Parameter(Mandatory = $true)]
        [hashtable]$Params,

        [hashtable]$RequestHeaders = @{},

        [string]$RequestEndpoint = $endpoint
    )

    $body = New-McpRequestBody `
        -Id $Id `
        -Method $Method `
        -Params $Params

    try {
        $response = Invoke-WebRequest `
            -Uri $RequestEndpoint `
            -Method Post `
            -Headers $RequestHeaders `
            -ContentType "application/json" `
            -Body $body `
            -UseBasicParsing

        return [PSCustomObject]@{
            StatusCode = [int]$response.StatusCode
            Headers    = $response.Headers
            Content    = [string]$response.Content
        }
    }
    catch {
        $errorResponse = $_.Exception.Response
        if ($null -eq $errorResponse) {
            throw
        }

        $content = [string]$_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($content)) {
            $responseStream = $null
            $reader = $null
            try {
                if (
                    $errorResponse.PSObject.Methods.Name -contains
                    "GetResponseStream"
                ) {
                    $responseStream = $errorResponse.GetResponseStream()
                    if ($null -ne $responseStream) {
                        $reader = New-Object System.IO.StreamReader(
                            $responseStream
                        )
                        $content = $reader.ReadToEnd()
                    }
                }
                elseif ($null -ne $errorResponse.Content) {
                    $contentTask =
                        $errorResponse.Content.ReadAsStringAsync()
                    $contentAwaiter = $contentTask.GetAwaiter()
                    $content = $contentAwaiter.GetResult()
                }
            }
            finally {
                if ($null -ne $reader) {
                    $reader.Dispose()
                }
                elseif ($null -ne $responseStream) {
                    $responseStream.Dispose()
                }
            }
        }

        return [PSCustomObject]@{
            StatusCode = [int]$errorResponse.StatusCode
            Headers    = $errorResponse.Headers
            Content    = [string]$content
        }
    }
}

# ------------------------------------------------------------
# Assertions
# ------------------------------------------------------------

function Assert-True {
    param(
        [Parameter(Mandatory = $true)]
        [bool]$Condition,

        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Assert-Equal {
    param(
        [AllowNull()]
        $Actual,

        [AllowNull()]
        $Expected,

        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    if ($Actual -ne $Expected) {
        throw "$Message. Expected=[$Expected], Actual=[$Actual]"
    }
}

function Test-HasProperty {
    param(
        [AllowNull()]
        $Object,

        [Parameter(Mandatory = $true)]
        [string]$PropertyName
    )

    if ($null -eq $Object) {
        return $false
    }

    return $Object.PSObject.Properties.Name -contains $PropertyName
}

function Assert-JsonRpcSuccess {
    param(
        [Parameter(Mandatory = $true)]
        $Response
    )

    Assert-Equal `
        -Actual $Response.jsonrpc `
        -Expected "2.0" `
        -Message "Unexpected JSON-RPC version"

    if (Test-HasProperty -Object $Response -PropertyName "error") {
        throw "Unexpected JSON-RPC error: $(
            $Response.error | ConvertTo-Json -Depth 10 -Compress
        )"
    }

    Assert-True `
        -Condition (Test-HasProperty -Object $Response -PropertyName "result") `
        -Message "JSON-RPC response does not contain result"
}

function Test-IsToolError {
    param(
        [Parameter(Mandatory = $true)]
        $Response
    )

    if (Test-HasProperty -Object $Response -PropertyName "error") {
        return $true
    }

    if (
        (Test-HasProperty -Object $Response -PropertyName "result") -and
        (Test-HasProperty `
            -Object $Response.result `
            -PropertyName "isError")
    ) {
        return $Response.result.isError -eq $true
    }

    return $false
}

function Assert-ToolSuccess {
    param(
        [Parameter(Mandatory = $true)]
        $Response
    )

    Assert-JsonRpcSuccess -Response $Response

    if (
        (Test-HasProperty `
            -Object $Response.result `
            -PropertyName "isError") -and
        $Response.result.isError -eq $true
    ) {
        throw "Tool returned isError=true: $(
            $Response.result | ConvertTo-Json -Depth 20 -Compress
        )"
    }
}

function Assert-ToolError {
    param(
        [Parameter(Mandatory = $true)]
        $Response
    )

    if (-not (Test-IsToolError -Response $Response)) {
        throw "Expected a tool or JSON-RPC error, but invocation succeeded"
    }
}

# ------------------------------------------------------------
# MCP content parsing
# ------------------------------------------------------------

function Get-McpTextContents {
    param(
        [Parameter(Mandatory = $true)]
        $Response
    )

    if (-not (Test-HasProperty -Object $Response -PropertyName "result")) {
        return @()
    }

    if (
        -not (Test-HasProperty `
            -Object $Response.result `
            -PropertyName "content")
    ) {
        return @()
    }

    @(
        $Response.result.content |
        Where-Object {
            $_.type -eq "text" -and
            -not [string]::IsNullOrWhiteSpace($_.text)
        } |
        ForEach-Object {
            $_.text
        }
    )
}

function ConvertFrom-McpToolContent {
    param(
        [Parameter(Mandatory = $true)]
        $Response
    )

    $texts = @(Get-McpTextContents -Response $Response)

    if ($texts.Count -eq 0) {
        throw "Tool response does not contain text content"
    }

    if ($texts.Count -gt 1) {
        throw "Tool returned more than one text content item"
    }

    try {
        return $texts[0] | ConvertFrom-Json
    }
    catch {
        throw "Tool text content is not valid JSON: $($texts[0])"
    }
}

function Assert-ProductFieldBoundary {
    param(
        [AllowNull()]
        $Products
    )

    if ($null -eq $Products) {
        return
    }

    $items = @($Products)

    foreach ($item in $items) {
        if ($null -eq $item) {
            continue
        }

        $actualFields = @($item.PSObject.Properties.Name)

        foreach ($field in $actualFields) {
            if ($allowedProductFields -notcontains $field) {
                throw "Unexpected product field exposed: $field"
            }
        }

        foreach ($field in $forbiddenFields) {
            if ($actualFields -contains $field) {
                throw "Forbidden product field exposed: $field"
            }
        }
    }
}

function Assert-NoForbiddenText {
    param(
        [Parameter(Mandatory = $true)]
        $Response
    )

    $json = $Response | ConvertTo-Json -Depth 30 -Compress

    foreach ($field in $forbiddenFields) {
        if ($json -match [regex]::Escape('"' + $field + '"')) {
            throw "Response contains forbidden field: $field"
        }
    }
}

# ------------------------------------------------------------
# Tool-call helpers
# ------------------------------------------------------------

function Invoke-ToolCall {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Id,

        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [hashtable]$Arguments
    )

    Invoke-McpRequest `
        -Id $Id `
        -Method "tools/call" `
        -Params @{
            name      = $Name
            arguments = $Arguments
        }
}

# ============================================================
# Authentication tests
# ============================================================

function Test-AuthenticationSuite {
    Write-Host ""
    Write-Host "=== Authentication tests ===" -ForegroundColor Cyan

    Invoke-TestCase "Missing API key returns HTTP 401" {
        $response = Invoke-McpHttpRequest `
            -Id "auth-no-key" `
            -Method "tools/list" `
            -Params @{} `
            -RequestHeaders @{
                Accept = "application/json, text/event-stream"
            }

        Assert-Equal `
            -Actual $response.StatusCode `
            -Expected 401 `
            -Message "Missing API key did not return HTTP 401"

        Assert-True `
            -Condition (-not $response.Content.Contains($ApiKey)) `
            -Message "Response unexpectedly contains the MCP API key"
    }

    Invoke-TestCase "Wrong API key returns HTTP 401" {
        $response = Invoke-McpHttpRequest `
            -Id "auth-wrong-key" `
            -Method "tools/list" `
            -Params @{} `
            -RequestHeaders @{
                "X-MCP-API-Key" = "invalid-mcp-key-value"
                Accept          = "application/json, text/event-stream"
            }

        Assert-Equal `
            -Actual $response.StatusCode `
            -Expected 401 `
            -Message "Wrong API key did not return HTTP 401"

        Assert-True `
            -Condition (-not $response.Content.Contains($ApiKey)) `
            -Message "Response unexpectedly contains the MCP API key"
    }

    Invoke-TestCase "Correct API key reaches JSON-RPC layer" {
        $response = Invoke-McpRequest `
            -Id "auth-correct-key" `
            -Method "tools/list" `
            -Params @{}

        Assert-JsonRpcSuccess -Response $response
    }
}

# ============================================================
# Discovery tests
# ============================================================

function Test-DiscoverySuite {
    Write-Host ""
    Write-Host "=== Tool discovery tests ===" -ForegroundColor Cyan

    Invoke-TestCase "tools/list returns exactly three approved tools" {
        $response = Invoke-McpRequest `
            -Id "discovery-tools" `
            -Method "tools/list" `
            -Params @{}

        Assert-JsonRpcSuccess -Response $response

        $toolNames = @(
            $response.result.tools |
            ForEach-Object { $_.name }
        )

        Assert-Equal `
            -Actual $toolNames.Count `
            -Expected $expectedTools.Count `
            -Message "Unexpected number of MCP tools"

        foreach ($expectedTool in $expectedTools) {
            Assert-True `
                -Condition ($toolNames -contains $expectedTool) `
                -Message "Missing MCP tool: $expectedTool"
        }

        foreach ($actualTool in $toolNames) {
            Assert-True `
                -Condition ($expectedTools -contains $actualTool) `
                -Message "Unexpected MCP tool exposed: $actualTool"
        }
    }

    Invoke-TestCase "Tool schemas do not expose forbidden arguments" {
        $response = Invoke-McpRequest `
            -Id "discovery-schemas" `
            -Method "tools/list" `
            -Params @{}

        Assert-JsonRpcSuccess -Response $response

        $json = $response.result.tools |
            ConvertTo-Json -Depth 30 -Compress

        foreach ($field in $forbiddenFields) {
            Assert-True `
                -Condition (
                    $json -notmatch [regex]::Escape('"' + $field + '"')
                ) `
                -Message "Tool schema exposes forbidden argument: $field"
        }
    }
}

# ============================================================
# Positive invocation tests
# ============================================================

function Test-PositiveSuite {
    Write-Host ""
    Write-Host "=== Positive tool invocation tests ===" `
        -ForegroundColor Cyan

    Invoke-TestCase "searchProducts returns public product fields" {
        $response = Invoke-ToolCall `
            -Id "positive-search" `
            -Name "searchProducts" `
            -Arguments @{
                keyword = "Mac"
                limit   = 5
            }

        Assert-ToolSuccess -Response $response
        Assert-NoForbiddenText -Response $response

        $products = ConvertFrom-McpToolContent -Response $response
        Assert-ProductFieldBoundary -Products $products

        Assert-True `
            -Condition (@($products).Count -le 5) `
            -Message "searchProducts returned more than five products"
    }

    Invoke-TestCase "searchProducts supports omitted keyword" {
        $response = Invoke-ToolCall `
            -Id "positive-search-no-keyword" `
            -Name "searchProducts" `
            -Arguments @{
                limit = 5
            }

        Assert-ToolSuccess -Response $response
        Assert-NoForbiddenText -Response $response

        $products = ConvertFrom-McpToolContent -Response $response
        Assert-ProductFieldBoundary -Products $products

        Assert-True `
            -Condition (@($products).Count -le 5) `
            -Message "searchProducts returned more than five products"
    }

    Invoke-TestCase "searchProducts accepts limit 1" {
        $response = Invoke-ToolCall `
            -Id "positive-search-limit-min" `
            -Name "searchProducts" `
            -Arguments @{
                keyword = "Mac"
                limit   = 1
            }

        Assert-ToolSuccess -Response $response

        $products = ConvertFrom-McpToolContent -Response $response
        Assert-ProductFieldBoundary -Products $products

        Assert-True `
            -Condition (@($products).Count -le 1) `
            -Message "searchProducts returned more than one product"
    }

    Invoke-TestCase "searchProducts accepts limit 20" {
        $response = Invoke-ToolCall `
            -Id "positive-search-limit-max" `
            -Name "searchProducts" `
            -Arguments @{
                keyword = "Mac"
                limit   = 20
            }

        Assert-ToolSuccess -Response $response

        $products = ConvertFrom-McpToolContent -Response $response
        Assert-ProductFieldBoundary -Products $products

        Assert-True `
            -Condition (@($products).Count -le 20) `
            -Message "searchProducts returned more than twenty products"
    }

    Invoke-TestCase "getProductSnapshot returns one public snapshot" {
        $response = Invoke-ToolCall `
            -Id "positive-snapshot" `
            -Name "getProductSnapshot" `
            -Arguments @{
                skuId = $SkuId
            }

        Assert-ToolSuccess -Response $response
        Assert-NoForbiddenText -Response $response

        $product = ConvertFrom-McpToolContent -Response $response
        Assert-ProductFieldBoundary -Products $product

        Assert-Equal `
            -Actual ([long]$product.skuId) `
            -Expected $SkuId `
            -Message "Returned SKU does not match requested SKU"
    }

    Invoke-TestCase "retrieveProductKnowledge returns validated products" {
        $response = Invoke-ToolCall `
            -Id "positive-knowledge" `
            -Name "retrieveProductKnowledge" `
            -Arguments @{
                query = "mac mini"
                limit = 5
            }

        Assert-ToolSuccess -Response $response
        Assert-NoForbiddenText -Response $response

        $products = ConvertFrom-McpToolContent -Response $response
        Assert-ProductFieldBoundary -Products $products

        Assert-True `
            -Condition (@($products).Count -le 5) `
            -Message (
                "retrieveProductKnowledge returned more than five products"
            )
    }
}

# ============================================================
# Argument validation tests
# ============================================================

function Test-ValidationSuite {
    Write-Host ""
    Write-Host "=== Argument validation tests ===" -ForegroundColor Cyan

    $invalidSearchLimits = @(
        @{
            Name  = "searchProducts rejects limit 0"
            Id    = "validation-search-limit-zero"
            Limit = 0
        },
        @{
            Name  = "searchProducts rejects limit 21"
            Id    = "validation-search-limit-large"
            Limit = 21
        },
        @{
            Name  = "searchProducts rejects negative limit"
            Id    = "validation-search-limit-negative"
            Limit = -1
        }
    )

    foreach ($case in $invalidSearchLimits) {
        Invoke-TestCase $case.Name {
            $response = Invoke-ToolCall `
                -Id $case.Id `
                -Name "searchProducts" `
                -Arguments @{
                    keyword = "Mac"
                    limit   = $case.Limit
                }

            Assert-ToolError -Response $response
        }
    }

    $invalidKnowledgeLimits = @(
        @{
            Name  = "retrieveProductKnowledge rejects limit 0"
            Id    = "validation-knowledge-limit-zero"
            Limit = 0
        },
        @{
            Name  = "retrieveProductKnowledge rejects limit 21"
            Id    = "validation-knowledge-limit-large"
            Limit = 21
        },
        @{
            Name  = "retrieveProductKnowledge rejects negative limit"
            Id    = "validation-knowledge-limit-negative"
            Limit = -1
        }
    )

    foreach ($case in $invalidKnowledgeLimits) {
        Invoke-TestCase $case.Name {
            $response = Invoke-ToolCall `
                -Id $case.Id `
                -Name "retrieveProductKnowledge" `
                -Arguments @{
                    query = "mac mini"
                    limit = $case.Limit
                }

            Assert-ToolError -Response $response
        }
    }

    $invalidSkuIds = @(
        @{
            Name  = "getProductSnapshot rejects skuId 0"
            Id    = "validation-sku-zero"
            SkuId = 0
        },
        @{
            Name  = "getProductSnapshot rejects negative skuId"
            Id    = "validation-sku-negative"
            SkuId = -1
        }
    )

    foreach ($case in $invalidSkuIds) {
        Invoke-TestCase $case.Name {
            $response = Invoke-ToolCall `
                -Id $case.Id `
                -Name "getProductSnapshot" `
                -Arguments @{
                    skuId = $case.SkuId
                }

            Assert-ToolError -Response $response
        }
    }
}

# ============================================================
# Security tests
# ============================================================

function Test-SecuritySuite {
    Write-Host ""
    Write-Host "=== Security boundary tests ===" -ForegroundColor Cyan

    Invoke-TestCase "searchProducts rejects forbidden identity argument" {
        $response = Invoke-ToolCall `
            -Id "security-user-id" `
            -Name "searchProducts" `
            -Arguments @{
                keyword = "Mac"
                limit   = 5
                userId  = 10001
            }

        Assert-ToolError -Response $response
    }

    Invoke-TestCase "getProductSnapshot rejects transaction arguments" {
        $response = Invoke-ToolCall `
            -Id "security-transaction-fields" `
            -Name "getProductSnapshot" `
            -Arguments @{
                skuId     = $SkuId
                orderNo   = "ORDER-SECURITY-CANARY"
                paymentId = 123
                addressId = 456
            }

        Assert-ToolError -Response $response
    }

    Invoke-TestCase "retrieveProductKnowledge rejects token argument" {
        $response = Invoke-ToolCall `
            -Id "security-token-field" `
            -Name "retrieveProductKnowledge" `
            -Arguments @{
                query = "mac mini"
                limit = 5
                token = "MCP-SECRET-CANARY"
            }

        Assert-ToolError -Response $response
    }

    Invoke-TestCase "Unknown write tool cannot be called" {
        $response = Invoke-ToolCall `
            -Id "security-create-order" `
            -Name "createOrder" `
            -Arguments @{
                skuId    = $SkuId
                quantity = 1
            }

        Assert-ToolError -Response $response
    }

    Invoke-TestCase "Inventory mutation tool cannot be called" {
        $response = Invoke-ToolCall `
            -Id "security-update-inventory" `
            -Name "updateInventory" `
            -Arguments @{
                skuId    = $SkuId
                stockNum = 999
            }

        Assert-ToolError -Response $response
    }

    Invoke-TestCase "Cart mutation tool cannot be called" {
        $response = Invoke-ToolCall `
            -Id "security-add-cart" `
            -Name "addToCart" `
            -Arguments @{
                skuId    = $SkuId
                quantity = 1
            }

        Assert-ToolError -Response $response
    }

    Invoke-TestCase "resources/list capability is unavailable" {
        $response = Invoke-McpRequest `
            -Id "security-resources-list" `
            -Method "resources/list" `
            -Params @{}

        Assert-ToolError -Response $response
    }

    Invoke-TestCase "prompts/list capability is unavailable" {
        $response = Invoke-McpRequest `
            -Id "security-prompts-list" `
            -Method "prompts/list" `
            -Params @{}

        Assert-ToolError -Response $response
    }

    Invoke-TestCase "completion capability is unavailable" {
        $response = Invoke-McpRequest `
            -Id "security-completion" `
            -Method "completion/complete" `
            -Params @{}

        Assert-ToolError -Response $response
    }
}

# ============================================================
# Gateway route test
# ============================================================

function Test-GatewaySuite {
    Write-Host ""
    Write-Host "=== Gateway exposure tests ===" -ForegroundColor Cyan

    Invoke-TestCase "Gateway does not expose MCP endpoint" {
        $response = Invoke-McpHttpRequest `
            -Id "gateway-tools-list" `
            -Method "tools/list" `
            -Params @{} `
            -RequestHeaders $headers `
            -RequestEndpoint $gatewayEndpoint

        Assert-Equal `
            -Actual $response.StatusCode `
            -Expected 404 `
            -Message (
                "Gateway must return HTTP 404 for /mcp. " +
                "A 401 or 403 response only proves authentication rejection, " +
                "not that the route is absent."
            )
    }
}

# ============================================================
# Main
# ============================================================

Write-Host "MCP endpoint: $endpoint"
Write-Host "Gateway MCP endpoint: $gatewayEndpoint"
Write-Host "Suite: $Suite"

switch ($Suite) {
    "Authentication" {
        Test-AuthenticationSuite
    }

    "Discovery" {
        Test-DiscoverySuite
    }

    "Positive" {
        Test-PositiveSuite
    }

    "Validation" {
        Test-ValidationSuite
    }

    "Security" {
        Test-SecuritySuite
    }

    "Gateway" {
        Test-GatewaySuite
    }

    "All" {
        Test-AuthenticationSuite
        Test-DiscoverySuite
        Test-PositiveSuite
        Test-ValidationSuite
        Test-SecuritySuite
        Test-GatewaySuite
    }
}

Write-Host ""
Write-Host "=== MCP verification summary ===" -ForegroundColor Cyan
Write-Host "Passed : $script:Passed"
Write-Host "Failed : $script:Failed"
Write-Host "Skipped: $script:Skipped"

if ($script:Failed -gt 0) {
    Write-Host "MCP verification failed." -ForegroundColor Red
    exit 1
}

Write-Host "MCP verification completed successfully." `
    -ForegroundColor Green
exit 0
