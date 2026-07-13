param(
  [string]$Query = "人工智能 职业教育 就业能力 期刊",
  [int]$TimeoutSec = 60
)

$ErrorActionPreference = "Stop"

$apiKey = [Environment]::GetEnvironmentVariable("DOUBAO_API_KEY")
$model = [Environment]::GetEnvironmentVariable("DOUBAO_WEB_SEARCH_MODEL")
$baseUrl = [Environment]::GetEnvironmentVariable("DOUBAO_RESPONSES_BASE_URL")
$path = [Environment]::GetEnvironmentVariable("DOUBAO_RESPONSES_PATH")
if ([string]::IsNullOrWhiteSpace($baseUrl)) { $baseUrl = "https://ark.cn-beijing.volces.com/api/v3" }
if ([string]::IsNullOrWhiteSpace($path)) { $path = "/responses" }
$endpoint = $baseUrl.TrimEnd("/") + "/" + $path.TrimStart("/")

$report = [ordered]@{
  generatedAt = (Get-Date).ToString("s")
  endpoint = $endpoint
  apiType = "RESPONSES_API"
  webSearchEnabled = $true
  modelConfigured = -not [string]::IsNullOrWhiteSpace($model)
  apiKeyConfigured = -not [string]::IsNullOrWhiteSpace($apiKey)
  modelMasked = if ([string]::IsNullOrWhiteSpace($model)) { "" } elseif ($model.Length -le 8) { "***" } else { $model.Substring(0, [Math]::Min(6, $model.Length)) + "***" + $model.Substring($model.Length - 4) }
  query = $Query
  rawResultCount = 0
  withUrlCount = 0
  extractedCount = 0
  verifiedPrimaryPublicCount = 0
  partiallyVerifiedCount = 0
  rejectedCount = 0
  elapsedMs = 0
  success = $false
  error = $null
}

if ([string]::IsNullOrWhiteSpace($apiKey) -or [string]::IsNullOrWhiteSpace($model)) {
  $report.error = "DOUBAO_API_KEY or DOUBAO_WEB_SEARCH_MODEL is not configured; actual Web Search request was not verified."
  $report | ConvertTo-Json -Depth 8
  exit 0
}

$prompt = @"
Use the web_search tool. Search public bibliographic pages for Chinese academic references.
Do not log in, bypass captcha, or download full text. Return JSON array only.
Every item must include title, authors, year, journalOrPublisher, url, sourceType, sourceSnippet.
Query: $Query
"@

$body = @{
  model = $model
  stream = $false
  max_output_tokens = 2048
  tools = @(@{ type = "web_search" })
  tool_choice = @{ type = "web_search" }
  input = $prompt
} | ConvertTo-Json -Depth 8

$started = Get-Date
try {
  $response = Invoke-RestMethod -Method Post -Uri $endpoint -TimeoutSec $TimeoutSec -Headers @{
    Authorization = "Bearer $apiKey"
    "Content-Type" = "application/json"
  } -Body $body
  $json = $response | ConvertTo-Json -Depth 20
  $urls = [regex]::Matches($json, 'https?://[^"\s<>]+' ) | ForEach-Object { $_.Value } | Select-Object -Unique
  $report.rawResultCount = if ($response.output) { @($response.output).Count } else { 1 }
  $report.withUrlCount = @($urls).Count
  $report.extractedCount = @($urls).Count
  $report.partiallyVerifiedCount = @($urls).Count
  $report.success = @($urls).Count -gt 0
} catch {
  $report.error = $_.Exception.Message -replace '(?i)Bearer\s+[A-Za-z0-9._\-]+', 'Bearer ***'
} finally {
  $report.elapsedMs = [int]((Get-Date) - $started).TotalMilliseconds
}

$report | ConvertTo-Json -Depth 8
