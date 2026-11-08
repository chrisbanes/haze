# Copyright 2026, Christopher Banes and the Haze project contributors
# SPDX-License-Identifier: Apache-2.0

param(
  [ValidateSet('Baseline')][string]$Mode = 'Baseline',
  [ValidateSet('baseline', 'control', 'original', 'guarded')][string]$Variant = 'baseline',
  [ValidateSet('false', 'true')][string]$Continuous = 'false',
  [ValidateRange(1, 3)][int]$Trials = 3,
  [ValidateRange(1, 240)][int]$Width = 240,
  [ValidateRange(1, 160)][int]$Height = 160
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path $PSScriptRoot
$classpath = Get-Content (Join-Path $root 'haze-glass/build/windows-glass-probe-classpath.txt') -Raw
$output = Join-Path $root ('haze-glass/build/outputs/glass-probe/' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $output | Out-Null
& git -C $root rev-parse HEAD | Set-Content (Join-Path $output 'build-sha.txt')
($classpath.Trim() -split [IO.Path]::PathSeparator | ForEach-Object { [IO.Path]::GetFileName($_) }) |
  Set-Content (Join-Path $output 'runtime-classpath-names.txt')
$env:SKIKO_RENDER_API = 'DIRECT3D'
$env:SKIKO_HARDWARE_INFO_ENABLED = 'true'
$cases = @(
  if ($Variant -eq 'baseline') {
    @{ variant = 'control'; continuous = 'false' }
    @{ variant = 'original'; continuous = 'false' }
    @{ variant = 'original'; continuous = 'true' }
    @{ variant = 'guarded'; continuous = 'true' }
  } else {
    @{ variant = $Variant; continuous = $Continuous }
  }
)
$results = @()
try {
  foreach ($case in $cases) {
    for ($trial = 0; $trial -lt $Trials; $trial++) {
      $caseDir = Join-Path $output "$($case.variant)-$($case.continuous)-${Width}x${Height}-$trial"
      New-Item -ItemType Directory -Path $caseDir | Out-Null
      $info = [System.Diagnostics.ProcessStartInfo]::new('java')
      $info.WorkingDirectory = $root
      $info.UseShellExecute = $false
      $info.RedirectStandardOutput = $true
      $info.RedirectStandardError = $true
      foreach ($argument in @('-Xmx1g', '-cp', $classpath.Trim(), 'dev.chrisbanes.haze.glass.WindowsGlassProbe', $case.variant, $case.continuous, '2', $caseDir, [string]$Width, [string]$Height)) {
        $info.ArgumentList.Add($argument)
      }
      $process = [System.Diagnostics.Process]::new()
      $process.StartInfo = $info
      $verdict = 'INVALID'
      $threshold = if ($case.variant -eq 'control') { 1000 } else { 5000 }
      $reason = 'Missing successful render'
      $watch = [System.Diagnostics.Stopwatch]::StartNew()
      try {
        if (-not $process.Start()) { throw 'Could not start owned probe JVM' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        while (-not $process.WaitForExit(50)) {
          $phaseFile = Join-Path $caseDir 'phase.txt'
          if (Test-Path $phaseFile) {
            $phase = Get-Content $phaseFile -Raw
            if ($phase -match '^first-draw,([0-9]{13})$') {
              $elapsed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - [long]$Matches[1]
              if ($elapsed -gt $threshold) {
                $verdict = if ($case.variant -eq 'control') { 'INVALID' } else { 'LATENCY_FAIL' }
                $reason = "first-draw exceeded ${threshold}ms"
                break
              }
            }
          }
          if ($watch.Elapsed.TotalSeconds -gt 60) { $reason = 'Outer setup/render watchdog exceeded 60s'; break }
        }
        if (-not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
        [IO.File]::WriteAllText((Join-Path $caseDir 'stdout.txt'), $stdout.GetAwaiter().GetResult())
        [IO.File]::WriteAllText((Join-Path $caseDir 'stderr.txt'), $stderr.GetAwaiter().GetResult())
        if ($process.ExitCode -eq 0 -and (Test-Path (Join-Path $caseDir 'verdict.txt'))) {
          $timing = Import-Csv (Join-Path $caseDir 'timings.csv') | Where-Object phase -eq 'first-draw'
          if ($null -ne $timing) {
            $ms = [double]::Parse($timing.ms, [Globalization.CultureInfo]::InvariantCulture)
            $verdict = if ($ms -le $threshold) { 'PASS' } elseif ($case.variant -eq 'control') { 'INVALID' } else { 'LATENCY_FAIL' }
            $reason = "first-draw=${ms}ms"
          }
        }
      } finally {
        if ($process.Id -and -not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
        $process.Dispose()
      }
      $result = [ordered]@{ variant = $case.variant; continuous = $case.continuous; trial = $trial; width = $Width; height = $Height; verdict = $verdict; reason = $reason; directory = $caseDir }
      $results += $result
      $result | ConvertTo-Json -Compress | Write-Output
      [IO.File]::WriteAllText((Join-Path $caseDir 'result.json'), ($result | ConvertTo-Json))
    }
  }
} finally {
  ConvertTo-Json -InputObject @($results) -Depth 4 | Set-Content (Join-Path $output 'results.json')
  Write-Output "GLASS_PROBE_RESULTS $output"
}
if (@($results | Where-Object verdict -eq 'INVALID').Count -gt 0) { exit 2 }
if (@($results | Where-Object verdict -eq 'LATENCY_FAIL').Count -gt 0) { exit 1 }
exit 0
