# Copyright 2026, Christopher Banes and the Haze project contributors
# SPDX-License-Identifier: Apache-2.0

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
trap { [Console]::Error.WriteLine($_.Exception.Message); exit 2 }
$env:SKIKO_RENDER_API = 'DIRECT3D'
$env:SKIKO_HARDWARE_INFO_ENABLED = 'true'
$output = Join-Path $PSScriptRoot ('build/results/' + [guid]::NewGuid())
$classpath = Join-Path $PSScriptRoot 'build/install/windows-glass-repro/lib/*'
$rows = @()
foreach ($case in @('control', 'retained')) {
  for ($trial = 0; $trial -lt 3; $trial++) {
    $directory = Join-Path $output "$case-$trial"
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $info = [Diagnostics.ProcessStartInfo]::new('java')
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($argument in @('-Xmx1g', '-cp', $classpath, 'ReproducerKt', $case, $directory)) {
      $info.ArgumentList.Add($argument)
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    try {
      if (-not $process.Start()) { throw 'Could not start owned JVM' }
      $stdout = $process.StandardOutput.ReadToEndAsync()
      $stderr = $process.StandardError.ReadToEndAsync()
      if (-not $process.WaitForExit(60000)) { throw 'Owned JVM exceeded 60-second watchdog; no completed duration' }
      $stdout.GetAwaiter().GetResult() | Set-Content (Join-Path $directory 'stdout.txt')
      $stderr.GetAwaiter().GetResult() | Set-Content (Join-Path $directory 'stderr.txt')
      if ($process.ExitCode -ne 0) { throw "Invalid $case-$trial; inspect $directory" }
    } finally {
      if ($process.Id -and -not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
      $process.Dispose()
    }
    $times = @(Import-Csv (Join-Path $directory 'timings.csv'))
    $cold = @($times | Where-Object phase -eq 'first-draw')
    $warm = @($times | Where-Object phase -like 'warm-*' | ForEach-Object { [double]$_.ms } | Sort-Object)
    if ($cold.Count -ne 1 -or $warm.Count -ne 6) { throw 'Missing cold/warm samples' }
    $coldMs = [double]$cold[0].ms
    $median = ($warm[2] + $warm[3]) / 2
    $stall = $coldMs -gt 1000 -and $coldMs -gt 20 * [Math]::Max(1, $median)
    $rows += @{ case = $case; trial = $trial; firstDrawMs = $coldMs; warmMedianMs = $median; coldStall = $stall }
    $rows | ConvertTo-Json | Set-Content (Join-Path $output 'results.json')
    if ($case -eq 'control' -and $coldMs -gt 1000) { throw 'Control exceeded one second; invalid comparison' }
  }
}
$stalls = @($rows | Where-Object { $_.case -eq 'retained' -and $_.coldStall }).Count
Write-Output "Results: $output; retained stalls: $stalls/3"
if ($stalls -eq 3) { exit 1 }
if ($stalls -eq 0) { exit 0 }
throw 'Mixed retained verdicts; do not classify as reproduced or fixed'
