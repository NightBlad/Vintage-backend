<#
.SYNOPSIS
    Chạy JMeter test plan cho Vintage Pharmacy và tóm tắt kết quả.

.PARAMETER JmeterHome
    Thư mục cài đặt JMeter (mặc định: biến môi trường JMETER_HOME, hoặc 'jmeter' nếu đã có trong PATH).

.PARAMETER JmeterTestFile
    Đường dẫn đến file test plan JMeter (mặc định: src/test/jmeter/vintage-suite.jmx).

.PARAMETER ResultsDir
    Thư mục lưu kết quả (mặc định: target/jmeter/results).

.PARAMETER LoginUsername
    Tên đăng nhập cho Thread Group 3 (truyền qua JMeter property login.username).

.PARAMETER LoginPassword
    Mật khẩu cho Thread Group 3 (truyền qua JMeter property login.password).

.EXAMPLE
    .\run-jmeter-only.ps1
    .\run-jmeter-only.ps1 -JmeterHome "C:\apache-jmeter-5.6.3" -LoginUsername "admin" -LoginPassword "secret"
#>

param(
    [string]$JmeterHome      = $env:JMETER_HOME,
    [string]$JmeterTestFile  = "src/test/jmeter/vintage-suite.jmx",
    [string]$ResultsDir      = "target/jmeter/results",
    [string]$LoginUsername   = $env:JMETER_LOGIN_USERNAME,
    [string]$LoginPassword   = $env:JMETER_LOGIN_PASSWORD
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# ── Resolve JMeter executable ───────────────────────────────────────────────
if ($JmeterHome) {
    $jmeterCmd = Join-Path $JmeterHome "bin\jmeter.bat"
    if (-not (Test-Path $jmeterCmd)) {
        $jmeterCmd = Join-Path $JmeterHome "bin/jmeter"
    }
} else {
    $jmeterCmd = "jmeter"
}

# ── Resolve absolute paths ───────────────────────────────────────────────────
if (-not [System.IO.Path]::IsPathRooted($JmeterTestFile)) {
    $JmeterTestFile = Join-Path $scriptDir $JmeterTestFile
}
if (-not [System.IO.Path]::IsPathRooted($ResultsDir)) {
    $ResultsDir = Join-Path $scriptDir $ResultsDir
}

if (-not (Test-Path $JmeterTestFile)) {
    Write-Error "JMeter test file not found: $JmeterTestFile"
    exit 1
}

# ── Clean previous results ───────────────────────────────────────────────────
if (Test-Path $ResultsDir) {
    Write-Host "Removing previous results at: $ResultsDir" -ForegroundColor Yellow
    Remove-Item -Recurse -Force $ResultsDir
}
New-Item -ItemType Directory -Force -Path $ResultsDir | Out-Null

$summaryFile = Join-Path $ResultsDir "vintage-suite-summary.csv"
$allResults  = Join-Path $ResultsDir "vintage-suite-results.csv"
$logFile     = Join-Path $ResultsDir "jmeter.log"

# ── Run JMeter ───────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Running JMeter test plan"               -ForegroundColor Cyan
Write-Host "   Plan   : $JmeterTestFile"             -ForegroundColor Cyan
Write-Host "   Results: $ResultsDir"                 -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

& $jmeterCmd -n -t $JmeterTestFile -l $allResults -j $logFile `
    "-Jlogin.username=$LoginUsername" "-Jlogin.password=$LoginPassword"
if ($LASTEXITCODE -ne 0) {
    Write-Error "JMeter exited with code $LASTEXITCODE. See log: $logFile"
    exit $LASTEXITCODE
}

# ── Summarize CSV results ─────────────────────────────────────────────────────
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host " Results Summary"                         -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

$csvFile = if (Test-Path $allResults) { $allResults } elseif (Test-Path $summaryFile) { $summaryFile } else { $null }

if ($csvFile) {
    $rows = Import-Csv $csvFile
    $total   = $rows.Count
    $errors  = ($rows | Where-Object { $_.success -eq "false" }).Count
    $errRate = if ($total -gt 0) { [math]::Round($errors / $total * 100, 2) } else { 0 }

    if ($rows.Count -gt 0 -and $rows[0].PSObject.Properties["elapsed"]) {
        $elapsedVals = $rows | Where-Object { $_.elapsed -match '^\d+$' } | ForEach-Object { [int]$_.elapsed }
        if ($elapsedVals.Count -gt 0) {
            $avgElapsed = [math]::Round(($elapsedVals | Measure-Object -Average).Average, 0)
            $minElapsed = ($elapsedVals | Measure-Object -Minimum).Minimum
            $maxElapsed = ($elapsedVals | Measure-Object -Maximum).Maximum
            Write-Host "  Total Requests : $total"
            Write-Host "  Errors         : $errors  ($errRate %)"
            Write-Host "  Response Time  : avg=${avgElapsed}ms  min=${minElapsed}ms  max=${maxElapsed}ms"
        }
    } else {
        Write-Host "  Total Requests : $total"
        Write-Host "  Errors         : $errors  ($errRate %)"
    }
    Write-Host ""
    Write-Host "  CSV results saved to: $csvFile" -ForegroundColor Gray
} else {
    Write-Host "  No CSV results file found in $ResultsDir" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "  JMeter log: $logFile" -ForegroundColor Gray
Write-Host "========================================" -ForegroundColor Green
