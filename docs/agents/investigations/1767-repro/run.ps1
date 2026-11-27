# Copyright 2026, Christopher Banes and the Haze project contributors
# SPDX-License-Identifier: Apache-2.0

param(
  [string]$Install = (Join-Path $PSScriptRoot 'build/install/windows-glass-repro'),
  [string]$ExpectedSkiko = '0.150.2',
  # Comma-separated; `pwsh -File` passes arrays as one string.
  [string]$Cases = 'control,retained',
  [string]$Output = (Join-Path $PSScriptRoot ('build/results/' + [guid]::NewGuid())),
  [int]$Trials = 3
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
trap { [Console]::Error.WriteLine($_.Exception.Message); exit 2 }
$env:SKIKO_RENDER_API = 'DIRECT3D'
$env:SKIKO_HARDWARE_INFO_ENABLED = 'true'
$classpath = Join-Path $Install 'lib/*'
$rows = @()
foreach ($case in $Cases.Split(',')) {
  if ($case -notin @('control', 'retained', 'original')) { throw "Unknown case: $case" }
  for ($trial = 0; $trial -lt $Trials; $trial++) {
    $directory = Join-Path $Output "$case-$trial"
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
    $started = $false
    $timedOut = $false
    try {
      if (-not $process.Start()) { throw 'Could not start owned JVM' }
      $started = $true
      $stdout = $process.StandardOutput.ReadToEndAsync()
      $stderr = $process.StandardError.ReadToEndAsync()
      $timedOut = -not $process.WaitForExit(60000)
      # Only the unreduced original may be censored; its expiry is evidence of a stall, never a pass.
      if ($timedOut -and $case -ne 'original') { throw 'Owned JVM exceeded 60-second watchdog; no completed duration' }
      if (-not $timedOut -and $process.ExitCode -ne 0) { throw "Invalid $case-$trial; inspect $directory" }
    } finally {
      try {
        if ($started) {
          if (-not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
          [IO.File]::WriteAllText((Join-Path $directory 'stdout.txt'), $stdout.GetAwaiter().GetResult())
          [IO.File]::WriteAllText((Join-Path $directory 'stderr.txt'), $stderr.GetAwaiter().GetResult())
        }
      } finally { $process.Dispose() }
    }
    $identity = Get-Content (Join-Path $directory 'identity.txt')
    $version = @($identity | Where-Object { $_ -like 'skiko=*' })[0].Substring(6)
    if ($version -ne $ExpectedSkiko) { throw "Runtime Skiko $version does not match requested $ExpectedSkiko" }
    $row = [ordered]@{
      case = $case; trial = $trial; version = $version; firstDrawMs = $null; constructionMs = $null
      warmMedianMs = $null; coldStall = $null; verdict = 'INVALID'; reason = 'censored >= 60 s; lower bound, not a duration'
    }
    if (-not $timedOut) {
      $times = @(Import-Csv (Join-Path $directory 'timings.csv'))
      $construction = @($times | Where-Object phase -eq 'construction')
      $cold = @($times | Where-Object phase -eq 'first-draw')
      $warm = @($times | Where-Object phase -like 'warm-*' | ForEach-Object { [double]$_.ms } | Sort-Object)
      if ($construction.Count -ne 1 -or $cold.Count -ne 1 -or $warm.Count -ne 6) { throw 'Missing construction/cold/warm samples' }
      $row.constructionMs = [double]$construction[0].ms
      $row.firstDrawMs = [double]$cold[0].ms
      $row.warmMedianMs = ($warm[2] + $warm[3]) / 2
      $row.coldStall = $row.firstDrawMs -gt 1000 -and $row.firstDrawMs -gt 20 * [Math]::Max(1, $row.warmMedianMs)
      $row.reason = ''
      $row.verdict = switch ($case) {
        'control' { if ($row.firstDrawMs -gt 1000) { 'INVALID' } else { 'pass' } }
        'retained' { if ($row.coldStall) { 'stall' } else { 'no-stall' } }
        'original' { if ($row.firstDrawMs -gt 5000) { 'over-5s' } else { 'within-5s' } }
      }
      if ($row.verdict -eq 'INVALID') { $row.reason = 'control exceeded one second' }
    }
    $rows += $row
    ConvertTo-Json -InputObject $rows | Set-Content (Join-Path $Output 'results.json')
    if ($case -eq 'control' -and $row.verdict -eq 'INVALID') { throw 'Control exceeded one second; invalid comparison' }
  }
}
$retained = @($rows | Where-Object { $_.case -eq 'retained' })
$stalls = @($retained | Where-Object { $_.coldStall }).Count
Write-Output "Results: $Output; retained stalls: $stalls/$($retained.Count)"
if ($retained.Count -eq 0 -or $stalls -eq 0) { exit 0 }
if ($stalls -eq $retained.Count) { exit 1 }
throw 'Mixed retained verdicts; do not classify as reproduced or fixed'
