param(
    [string]$EnvName = "dev",
    [string]$MvnCmd = "",
    [switch]$SkipUnitTests
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition

# Choose Maven executable: prefer wrapper when .mvn exists, else fall back to mvn in PATH or user override
if (-not $MvnCmd) {
    $wrapper = Join-Path $scriptDir 'mvnw.cmd'
    $wrapperProps = Join-Path $scriptDir '.mvn\wrapper\maven-wrapper.properties'
    if ((Test-Path $wrapper) -and (Test-Path $wrapperProps)) {
        $MvnCmd = $wrapper
        Write-Host "Using Maven wrapper at $MvnCmd"
    } else {
        $MvnCmd = 'mvn'
        Write-Host "Wrapper not found; using mvn from PATH"
    }
} else {
    Write-Host "Using custom Maven command: $MvnCmd"
}

if (-not (Get-Command $MvnCmd -ErrorAction SilentlyContinue)) {
    Write-Error "Maven command '$MvnCmd' not found. Install Maven or provide -MvnCmd path."
    exit 1
}

# Run JMeter perf profile
Write-Host "Running perf profile with env '$EnvName'..."
$mvnArgs = @('-Pperf','verify',"-Denv=$EnvName")
if ($SkipUnitTests) { $mvnArgs += '-DskipTests' }
& $MvnCmd @mvnArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven perf run failed (exit $LASTEXITCODE)."
    exit $LASTEXITCODE
}

# Find the newest JMeter CSV result
$results = Get-ChildItem -Path "$PSScriptRoot\target\jmeter" -Recurse -Filter *.csv -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending
if (-not $results) {
    Write-Warning "No JMeter CSV results found under target/jmeter."
    exit 0
}
$latest = $results | Select-Object -First 1
Write-Host "Latest result: $($latest.FullName)"

try {
    $rows = Import-Csv -Path $latest.FullName
    $total = $rows.Count
    $errors = ($rows | Where-Object { $_.success -eq 'false' }).Count
    $avg = 0
    if ($total -gt 0) {
        $avg = [math]::Round(($rows | Measure-Object -Property elapsed -Average).Average,2)
    }
    Write-Host "Requests: $total | Errors: $errors | Avg elapsed (ms): $avg"

        # Point to latest HTML report if present
        $latestReport = Get-ChildItem -Path "$PSScriptRoot\target\jmeter\reports" -Recurse -Filter index.html -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($latestReport) {
            Write-Host "HTML report: $($latestReport.FullName)"
        } else {
            Write-Warning "No HTML report found under target/jmeter/reports"
        }
} catch {
    Write-Warning "Could not parse CSV: $($_.Exception.Message)"
}

