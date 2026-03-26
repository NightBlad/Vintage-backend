param(
    [string]$EnvName = "dev",
    [string]$MvnCmd = "mvn",
    [string]$JmeterTestFile = "src/test/jmeter/vintage-suite.jmx"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Write-Host "Running JMeter only (no app restart/build) with env '$EnvName'..."

if (-not (Get-Command $MvnCmd -ErrorAction SilentlyContinue)) {
    Write-Error "Maven command '$MvnCmd' not found. Provide -MvnCmd path or install Maven."
    exit 1
}

# Run JMeter goals directly (no unit tests triggered here)
foreach ($item in @("$scriptDir\target\jmeter\reports\vintage-suite", "$scriptDir\target\config.json")) {
    if (Test-Path $item) {
        Remove-Item $item -Recurse -Force -ErrorAction SilentlyContinue
    }
}
foreach ($dir in @("$scriptDir\target\jmeter\reports\vintage-suite")) {
    if (Test-Path $dir) {
        Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
& $MvnCmd -f "$scriptDir\pom.xml" -Pperf -Denv=$EnvName -Djmeter.testfile=$JmeterTestFile jmeter:configure jmeter:jmeter jmeter:results
if ($LASTEXITCODE -ne 0) {
    Write-Error "JMeter run failed (exit $LASTEXITCODE)."
    exit $LASTEXITCODE
}

# Summarize latest result CSV (exclude copied data/config files)
$results = Get-ChildItem -Path "$scriptDir\target\jmeter" -Recurse -Filter *.csv -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notlike "*testFiles*" -and $_.FullName -notlike "*logs*" }
if (-not $results) {
    Write-Warning "No JMeter result CSV found under target/jmeter (excluding testFiles/logs)."
    exit 0
}
$latest = $results | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$rows = Import-Csv -Path $latest.FullName
$total = $rows.Count
$errors = ($rows | Where-Object { $_.success -eq 'false' }).Count
$avg = 0
if ($total -gt 0 -and $rows[0].PSObject.Properties.Name -contains 'elapsed') {
    $avg = [math]::Round(($rows | Measure-Object -Property elapsed -Average).Average,2)
}
Write-Host "Latest: $($latest.FullName)"
Write-Host "Requests: $total | Errors: $errors | Avg elapsed (ms): $avg"

$latestReport = Get-ChildItem -Path "$scriptDir\target\jmeter\reports" -Recurse -Filter index.html -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($latestReport) {
    Write-Host "HTML report: $($latestReport.FullName)"
}

