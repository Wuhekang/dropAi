param(
  [string]$Query = "Search the official public documentation for Volcengine Ark Web Search and cite public source URLs.",
  [int]$TimeoutSec = 60,
  [string]$EnvFile = "$env:USERPROFILE\Desktop\.env"
)

$ErrorActionPreference = "Stop"

function Import-DotEnv([string]$Path) {
  if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path $Path)) { return $false }
  Get-Content -Path $Path | ForEach-Object {
    $line = $_.Trim()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) { return }
    $equals = $line.IndexOf("=")
    if ($equals -le 0) { return }
    $name = $line.Substring(0, $equals).Trim()
    $value = $line.Substring($equals + 1).Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
      $value = $value.Substring(1, $value.Length - 2)
    }
    if (-not [string]::IsNullOrWhiteSpace($name)) {
      [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
  }
  return $true
}

$envLoaded = Import-DotEnv $EnvFile

$apiKey = [Environment]::GetEnvironmentVariable("DOUBAO_API_KEY")
$model = [Environment]::GetEnvironmentVariable("DOUBAO_WEB_SEARCH_MODEL")
if ([string]::IsNullOrWhiteSpace($model)) { $model = "doubao-2-1-trubo-60628" }
$baseUrl = [Environment]::GetEnvironmentVariable("DOUBAO_RESPONSES_BASE_URL")
$path = [Environment]::GetEnvironmentVariable("DOUBAO_RESPONSES_PATH")
if ([string]::IsNullOrWhiteSpace($baseUrl)) { $baseUrl = "https://ark.cn-beijing.volces.com/api/v3" }
if ([string]::IsNullOrWhiteSpace($path)) { $path = "/responses" }
$endpoint = $baseUrl.TrimEnd("/") + "/" + $path.TrimStart("/")

function Mask-Model([string]$value) {
  if ([string]::IsNullOrWhiteSpace($value)) { return "" }
  if ($value.Length -le 8) { return "***" }
  return $value.Substring(0, [Math]::Min(6, $value.Length)) + "***" + $value.Substring($value.Length - 4)
}

function Test-PublicUrl([string]$url) {
  if ([string]::IsNullOrWhiteSpace($url)) { return @{ accepted = $false; reason = "missing_url" } }
  if ($url -notmatch '^(https?)://([^/:?#]+)') { return @{ accepted = $false; reason = "unsupported_scheme" } }
  $urlHost = $Matches[2].ToLowerInvariant()
  if ([string]::IsNullOrWhiteSpace($urlHost)) { return @{ accepted = $false; reason = "missing_host" } }
  if ($urlHost -eq "localhost" -or $urlHost.EndsWith(".localhost")) { return @{ accepted = $false; reason = "localhost" } }
  if ($urlHost.EndsWith(".local") -or $urlHost.EndsWith(".internal") -or $urlHost.EndsWith(".lan")) { return @{ accepted = $false; reason = "internal_domain" } }
  if ($urlHost -match '^(\d{1,3}\.){3}\d{1,3}$') {
    try {
      $ip = [System.Net.IPAddress]::Parse($urlHost).GetAddressBytes()
      if ($ip[0] -eq 10 -or $ip[0] -eq 127 -or ($ip[0] -eq 172 -and $ip[1] -ge 16 -and $ip[1] -le 31) -or ($ip[0] -eq 192 -and $ip[1] -eq 168)) { return @{ accepted = $false; reason = "private_ip" } }
    } catch {
      return @{ accepted = $false; reason = "invalid_ip" }
    }
  }
  return @{ accepted = $true; reason = "" }
}

function Add-UrlEvidence($state, [string]$url, [string]$path) {
  if ([string]::IsNullOrWhiteSpace($url)) { return }
  $clean = $url.Trim() -replace '[,.;)]+$', ''
  $validation = Test-PublicUrl $clean
  if ($validation["accepted"]) {
    if (-not $state.accepted.Contains($clean)) { $state.accepted[$clean] = $path }
  } else {
    if (-not $state.rejected.Contains($clean)) { $state.rejected[$clean] = "$path|$($validation["reason"])" }
  }
}

function Walk-Json($node, [string]$path, $state) {
  if ($null -eq $node) { return }
  if ($node -is [System.Management.Automation.PSCustomObject]) {
    $typeProp = $node.PSObject.Properties["type"]
    if ($typeProp -and ($typeProp.Value -match 'web_search|tool_call|tool_use')) {
      $state.webSearchInvoked = $true
      $state.outputTypes += "$path.type=$($typeProp.Value)"
    }
    foreach ($prop in $node.PSObject.Properties) {
      $childPath = "$path.$($prop.Name)"
      $lower = $prop.Name.ToLowerInvariant()
      if ($lower -in @("url", "uri", "link", "href") -and $prop.Value -is [string]) {
        $state.sourceJsonPaths += $childPath
        Add-UrlEvidence $state $prop.Value $childPath
      }
      if ($prop.Name -eq "output" -and $prop.Value -is [array]) { $state.rawResultCount = @($prop.Value).Count }
      if ($prop.Name -eq "sources" -and $prop.Value -is [array]) { $state.rawSourceCount += @($prop.Value).Count }
      if ($prop.Name -eq "citations" -and $prop.Value -is [array]) { $state.rawSourceCount += @($prop.Value).Count }
      Walk-Json $prop.Value $childPath $state
    }
    return
  }
  if ($node -is [array]) {
    for ($i = 0; $i -lt $node.Count; $i++) { Walk-Json $node[$i] "${path}[$i]" $state }
    return
  }
  if ($node -is [string]) {
    foreach ($match in [regex]::Matches($node, 'https?://[^"\s<>\]\})]+')) {
      Add-UrlEvidence $state $match.Value $path
    }
  }
}

$report = [ordered]@{
  generatedAt = (Get-Date).ToString("s")
  endpoint = $endpoint
  httpStatus = $null
  apiType = "RESPONSES_API"
  webSearchEnabled = $true
  webSearchInvoked = $false
  envFile = $EnvFile
  envFileLoaded = $envLoaded
  includeRequested = $true
  includeUnsupported = $false
  modelConfigured = -not [string]::IsNullOrWhiteSpace($model)
  apiKeyConfigured = -not [string]::IsNullOrWhiteSpace($apiKey)
  modelMasked = Mask-Model $model
  query = $Query
  outputTypes = @()
  sourceJsonPaths = @()
  rawResultCount = 0
  rawSourceCount = 0
  acceptedUrlCount = 0
  rejectedUrlCount = 0
  rejectedUrls = @()
  providerStatus = ""
  elapsedMs = 0
  success = $false
  error = $null
}

if ([string]::IsNullOrWhiteSpace($apiKey)) {
  $report.providerStatus = "MISSING_API_KEY"
  $report.error = "DOUBAO_API_KEY is not configured; actual Web Search request was not verified."
  $report | ConvertTo-Json -Depth 10
  exit 0
}

$prompt = @"
You must use the web_search tool. Search for official public pages and return source URLs.
Do not rely on memory. Include public source links in annotations or source fields whenever available.
Query: $Query
"@

function Invoke-DoubaoResponse([bool]$includeSources) {
  $bodyMap = [ordered]@{
    model = $model
    stream = $false
    max_output_tokens = 2048
    tools = @(@{ type = "web_search" })
    tool_choice = "auto"
    input = $prompt
  }
  if ($includeSources) { $bodyMap["include"] = @("web_search_call.action.sources") }
  $body = $bodyMap | ConvertTo-Json -Depth 12
  $tempBody = Join-Path $env:TEMP ("doubao-web-search-" + [Guid]::NewGuid().ToString("N") + ".json")
  [System.IO.File]::WriteAllText($tempBody, $body, [System.Text.UTF8Encoding]::new($false))
  try {
    $rawOutput = & curl.exe -sS --max-time $TimeoutSec -w "`nHTTP_STATUS:%{http_code}`n" `
      -X POST $endpoint `
      -H "Authorization: Bearer $apiKey" `
      -H "Content-Type: application/json" `
      --data-binary "@$tempBody"
    if ($LASTEXITCODE -ne 0) { throw "curl failed with exit code $LASTEXITCODE" }
  } finally {
    Remove-Item $tempBody -Force -ErrorAction SilentlyContinue
  }
  $outputText = ($rawOutput | Out-String).Trim()
  if ($outputText -notmatch '(?s)^(.*)\r?\nHTTP_STATUS:(\d{3})\s*$') {
    throw "Unable to parse curl response status"
  }
  $content = $Matches[1].Trim()
  $statusCode = [int]$Matches[2]
  if ($statusCode -ge 400) { throw "HTTP $statusCode $content" }
  return [pscustomobject]@{
    StatusCode = $statusCode
    Content = $content
  }
}

function Get-ErrorText($exception) {
  $message = if ($exception) { $exception.Message } else { "" }
  if ($global:Error.Count -gt 0 -and $global:Error[0].ErrorDetails -and
      -not [string]::IsNullOrWhiteSpace($global:Error[0].ErrorDetails.Message)) {
    $message = "$message $($global:Error[0].ErrorDetails.Message)"
  }
  try {
    if ($exception.Response) {
      $stream = $exception.Response.GetResponseStream()
      if ($stream) {
        $body = (New-Object IO.StreamReader($stream)).ReadToEnd()
        if (-not [string]::IsNullOrWhiteSpace($body)) { return "$message $body" }
      }
    }
  } catch {
  }
  return $message
}

$started = Get-Date
try {
  try {
    $raw = Invoke-DoubaoResponse $true
  } catch {
    $message = Get-ErrorText $_.Exception
    if ($message -match '(?i)include|unsupported|unknown type|not support') {
      $report.includeUnsupported = $true
      $raw = Invoke-DoubaoResponse $false
    } else {
      throw
    }
  }
  $report.httpStatus = [int]$raw.StatusCode
  $response = $raw.Content | ConvertFrom-Json
  $state = @{
    webSearchInvoked = $false
    accepted = [ordered]@{}
    rejected = [ordered]@{}
    sourceJsonPaths = @()
    outputTypes = @()
    rawResultCount = 0
    rawSourceCount = 0
  }
  Walk-Json $response '$' $state
  $report.webSearchInvoked = $state.webSearchInvoked
  $report.sourceJsonPaths = @($state.sourceJsonPaths | Select-Object -Unique)
  $report.outputTypes = @($state.outputTypes | Select-Object -Unique)
  $report.rawResultCount = $state.rawResultCount
  $report.rawSourceCount = $state.rawSourceCount
  $report.acceptedUrlCount = $state.accepted.Count
  $report.rejectedUrlCount = $state.rejected.Count
  $report.rejectedUrls = @($state.rejected.GetEnumerator() | ForEach-Object {
    $parts = $_.Value -split '\|', 2
    @{ url = $_.Key; path = $parts[0]; reason = if ($parts.Count -gt 1) { $parts[1] } else { "" } }
  })
  if (-not $report.webSearchInvoked) {
    $report.providerStatus = "TOOL_NOT_INVOKED"
  } elseif ($report.acceptedUrlCount -gt 0) {
    $report.providerStatus = "AVAILABLE"
  } elseif ($report.rejectedUrlCount -gt 0) {
    $report.providerStatus = "SOURCE_URL_REJECTED"
  } elseif ($report.includeUnsupported) {
    $report.providerStatus = "SOURCE_INCLUDE_UNSUPPORTED"
  } else {
    $report.providerStatus = "SOURCE_FIELD_NOT_FOUND"
  }
  $report.success = $report.providerStatus -eq "AVAILABLE"

  New-Item -ItemType Directory -Force -Path "logs" | Out-Null
  $sanitized = [ordered]@{
    includeRequested = $report.includeRequested
    includeUnsupported = $report.includeUnsupported
    diagnostics = $report
    response = $response
  } | ConvertTo-Json -Depth 50
  $sanitized = $sanitized -replace '(?i)Bearer\s+[A-Za-z0-9._\-]+', 'Bearer ***'
  $sanitized = $sanitized -replace '(?i)(ark-[A-Za-z0-9._\-]{8})[A-Za-z0-9._\-]+', '$1***'
  Set-Content -Path "logs/doubao-web-search-response-sanitized.json" -Value $sanitized -Encoding UTF8
} catch {
  $report.providerStatus = "RESPONSE_PARSE_FAILED"
  $report.error = (Get-ErrorText $_.Exception) -replace '(?i)Bearer\s+[A-Za-z0-9._\-]+', 'Bearer ***'
} finally {
  $report.elapsedMs = [int]((Get-Date) - $started).TotalMilliseconds
}

$report | ConvertTo-Json -Depth 12
