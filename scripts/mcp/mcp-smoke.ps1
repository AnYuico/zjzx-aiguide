param(
    [string]$BaseUrl = "http://127.0.0.1:8520",
    [string]$ApiKey = $env:AGENT_MCP_API_KEY,
    [long]$SkuId = 14
)

$ErrorActionPreference = "Stop"

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

$headers = @{
    "X-MCP-API-Key" = $ApiKey
    "Accept" = "application/json, text/event-stream"
}
$endpoint = $BaseUrl.TrimEnd("/") + "/mcp"

function Invoke-McpRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Id,
        [Parameter(Mandatory = $true)]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [hashtable]$Params
    )

    $body = @{
        jsonrpc = "2.0"
        id = $Id
        method = $Method
        params = $Params
    } | ConvertTo-Json -Depth 12 -Compress

    Invoke-RestMethod `
        -Uri $endpoint `
        -Method Post `
        -Headers $headers `
        -ContentType "application/json" `
        -Body $body
}

Write-Host "MCP endpoint: $endpoint"

$toolList = Invoke-McpRequest `
    -Id "smoke-tools" `
    -Method "tools/list" `
    -Params @{}
$toolNames = @($toolList.result.tools | ForEach-Object { $_.name })

Write-Host "Tools: $($toolNames -join ', ')"

$expectedTools = @(
    "searchProducts",
    "getProductSnapshot",
    "retrieveProductKnowledge"
)
foreach ($expectedTool in $expectedTools) {
    if ($toolNames -notcontains $expectedTool) {
        throw "MCP tool is missing: $expectedTool"
    }
}
if ($toolNames.Count -ne $expectedTools.Count) {
    throw "Unexpected MCP tools were exposed"
}

$searchResult = Invoke-McpRequest `
    -Id "smoke-search" `
    -Method "tools/call" `
    -Params @{
        name = "searchProducts"
        arguments = @{
            keyword = "Mac"
            limit = 5
        }
    }
Write-Host "searchProducts:"
$searchResult.result.content | ConvertTo-Json -Depth 12

$snapshotResult = Invoke-McpRequest `
    -Id "smoke-snapshot" `
    -Method "tools/call" `
    -Params @{
        name = "getProductSnapshot"
        arguments = @{
            skuId = $SkuId
        }
    }
Write-Host "getProductSnapshot:"
$snapshotResult.result.content | ConvertTo-Json -Depth 12

$knowledgeResult = Invoke-McpRequest `
    -Id "smoke-knowledge" `
    -Method "tools/call" `
    -Params @{
        name = "retrieveProductKnowledge"
        arguments = @{
            query = "mac mini"
            limit = 5
        }
    }
Write-Host "retrieveProductKnowledge:"
$knowledgeResult.result.content | ConvertTo-Json -Depth 12

Write-Host "MCP smoke test completed."
