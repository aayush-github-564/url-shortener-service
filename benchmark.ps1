# benchmark.ps1

param(
    [string]$BaseUrl = "http://localhost:8080"
)

Write-Host ""
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "  URL Shortener - Redis Benchmark" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host ""

# Create a fresh short URL
Write-Host "Creating a new short URL..." -ForegroundColor Yellow
$body = '{"longUrl": "https://www.github.com"}'
$createResponse = Invoke-WebRequest -Uri "$BaseUrl/api/urls" -Method POST -ContentType "application/json" -Body $body -UseBasicParsing
$json = $createResponse.Content | ConvertFrom-Json
$ShortCode = $json.shortCode
Write-Host "Short code: $ShortCode" -ForegroundColor Green
Write-Host ""

$redirectUrl = "$BaseUrl/r/$ShortCode"

# Request 1: cache MISS (hits PostgreSQL)
Write-Host "Request 1: Cache MISS (PostgreSQL)" -ForegroundColor Red
$sw = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-WebRequest -Uri $redirectUrl -Method GET -MaximumRedirection 0 -ErrorAction SilentlyContinue -UseBasicParsing | Out-Null
$sw.Stop()
$cacheMissMs = $sw.Elapsed.TotalMilliseconds
Write-Host "  Time: $([math]::Round($cacheMissMs, 2)) ms" -ForegroundColor Red
Write-Host ""

# Requests 2-11: cache HITs (served from Redis)
Write-Host "Requests 2-11: Cache HITs (Redis)" -ForegroundColor Green
$hitTimes = @()

for ($i = 1; $i -le 10; $i++) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-WebRequest -Uri $redirectUrl -Method GET -MaximumRedirection 0 -ErrorAction SilentlyContinue -UseBasicParsing | Out-Null
    $sw.Stop()
    $ms = $sw.Elapsed.TotalMilliseconds
    $hitTimes += $ms
    Write-Host "  Request $($i + 1): $([math]::Round($ms, 2)) ms"
}

$avgHitMs = ($hitTimes | Measure-Object -Average).Average
$minHitMs = ($hitTimes | Measure-Object -Minimum).Minimum
$maxHitMs = ($hitTimes | Measure-Object -Maximum).Maximum
$improvement = (($cacheMissMs - $avgHitMs) / $cacheMissMs) * 100

Write-Host ""
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "  RESULTS" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Cache MISS (PostgreSQL):  $([math]::Round($cacheMissMs, 2)) ms" -ForegroundColor Red
Write-Host "  Cache HIT avg (Redis):    $([math]::Round($avgHitMs, 2)) ms" -ForegroundColor Green
Write-Host "  Cache HIT min:            $([math]::Round($minHitMs, 2)) ms" -ForegroundColor Green
Write-Host "  Cache HIT max:            $([math]::Round($maxHitMs, 2)) ms" -ForegroundColor Green
Write-Host ""
Write-Host "  Latency reduction: $([math]::Round($improvement, 1))%" -ForegroundColor Cyan
Write-Host ""