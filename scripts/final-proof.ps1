param(
  [string]$BaseUrl = "http://localhost:8081",
  [int]$Dataset = 100000,
  [int]$Iterations = 5
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path docs\proof | Out-Null

function Invoke-JsonPost([string]$url) {
  return Invoke-RestMethod -Method Post -Uri $url -ContentType "application/json"
}

function Invoke-JsonGet([string]$url) {
  return Invoke-RestMethod -Method Get -Uri $url -ContentType "application/json"
}

Write-Host "[1/8] Applying baseline indexes..."
Invoke-JsonPost "$BaseUrl/api/dev/indexes/apply?profile=baseline" | ConvertTo-Json -Depth 10 |
  Set-Content -Encoding UTF8 docs\proof\indexes-baseline-apply.json

Write-Host "[2/8] Seeding dataset..."
Invoke-JsonPost "$BaseUrl/api/dev/seed?n=$Dataset&mode=jdbc&days=14" | ConvertTo-Json -Depth 10 |
  Set-Content -Encoding UTF8 docs\proof\seed-jdbc.json

Write-Host "[3/8] Running baseline benchmarks..."
Invoke-JsonPost "$BaseUrl/api/dev/benchmark/run?scenario=errorRate&iterations=$Iterations" | ConvertTo-Json -Depth 10 |
  Set-Content -Encoding UTF8 docs\proof\benchmark-baseline-errorRate.json
Invoke-JsonPost "$BaseUrl/api/dev/benchmark/run?scenario=topIps&iterations=$Iterations" | ConvertTo-Json -Depth 10 |
  Set-Content -Encoding UTF8 docs\proof\benchmark-baseline-topIps.json

Write-Host "[4/8] Capturing baseline explain..."
Invoke-JsonGet "$BaseUrl/api/dev/explain?scenario=errorRate&profile=baseline&variant=date_trunc_bucket" | ConvertTo-Json -Depth 10 |
  Set-Content -Encoding UTF8 docs\proof\explain-baseline-errorRate.json

Write-Host "[5/8] Applying optimized indexes..."
Invoke-JsonPost "$BaseUrl/api/dev/indexes/apply?profile=optimized" | ConvertTo-Json -Depth 10 |
  Set-Content -Encoding UTF8 docs\proof\indexes-optimized-apply.json

Write-Host "[6/8] Running optimized benchmarks..."
Invoke-JsonPost "$BaseUrl/api/dev/benchmark/run?scenario=errorRate&iterations=$Iterations" | ConvertTo-Json -Depth 10 |
  Set-Content -Encoding UTF8 docs\proof\benchmark-optimized-errorRate.json
Invoke-JsonPost "$BaseUrl/api/dev/benchmark/run?scenario=topIps&iterations=$Iterations" | ConvertTo-Json -Depth 10 |
  Set-Content -Encoding UTF8 docs\proof\benchmark-optimized-topIps.json

Write-Host "[7/8] Capturing optimized explain..."
Invoke-JsonGet "$BaseUrl/api/dev/explain?scenario=errorRate&profile=optimized&variant=date_trunc_bucket" | ConvertTo-Json -Depth 10 |
  Set-Content -Encoding UTF8 docs\proof\explain-optimized-errorRate.json
Invoke-JsonGet "$BaseUrl/api/dev/explain?scenario=topIps&profile=optimized&variant=base" | ConvertTo-Json -Depth 10 |
  Set-Content -Encoding UTF8 docs\proof\explain-optimized-topIps.json

Write-Host "[8/8] Capturing sample analytics outputs..."
Invoke-JsonGet "$BaseUrl/api/analytics/health" | ConvertTo-Json -Depth 10 |
  Set-Content -Encoding UTF8 docs\proof\analytics-health.json
Invoke-JsonGet "$BaseUrl/api/analytics/top-endpoints?service=api&limit=5" | ConvertTo-Json -Depth 10 |
  Set-Content -Encoding UTF8 docs\proof\analytics-top-endpoints.json
Invoke-JsonGet "$BaseUrl/api/analytics/sliding-window-errors?windowMinutes=15&stepMinutes=5" | ConvertTo-Json -Depth 10 |
  Set-Content -Encoding UTF8 docs\proof\analytics-sliding-window-errors.json

Write-Host "Proof artifacts generated in docs/proof"
