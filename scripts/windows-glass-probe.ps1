# Copyright 2026, Christopher Banes and the Haze project contributors
# SPDX-License-Identifier: Apache-2.0

param(
  [ValidateSet('Baseline', 'Diagnose', 'Profile', 'SelfTest')][string]$Mode = 'Baseline',
  [ValidateSet('baseline', 'control', 'original', 'guarded')][string]$Variant = 'baseline',
  [ValidateSet('false', 'true')][string]$Continuous = 'false',
  [ValidateRange(1, 3)][int]$Trials = 3,
  [ValidateRange(1, 240)][int]$Width = 240,
  [ValidateRange(1, 160)][int]$Height = 160,
  [string]$Cuts = 'none'
)
$ErrorActionPreference = 'Stop'
trap { [Console]::Error.WriteLine('GLASS_PROBE INVALID: ' + $_.Exception.Message); exit 2 }
Set-StrictMode -Version Latest
function Test-ColdStall([double]$ColdMs, [double]$WarmMedianMs) {
  return $ColdMs -gt 1000 -and $ColdMs -gt (20 * [Math]::Max(1, $WarmMedianMs))
}
function Get-FirstDrawRange([string]$TraceUtc, [long]$FirstEpochMs, [double]$DurationMs) {
  $start = [DateTimeOffset]::ParseExact($TraceUtc, 'yyyy/MM/dd:HH:mm:ss.fffffff', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::AssumeUniversal)
  $traceMs = ([decimal]$start.UtcTicks - [DateTimeOffset]::UnixEpoch.UtcTicks) / 10000
  $relativeUs = ([decimal]$FirstEpochMs - $traceMs) * 1000
  if ($relativeUs -lt 0 -or $DurationMs -le 0) { throw 'Invalid first-draw trace interval' }
  return @{ start = [long]$relativeUs; end = [long]($relativeUs + [decimal]$DurationMs * 1000) }
}
if ($Mode -eq 'SelfTest') {
  if (-not (Test-ColdStall 4590.5181 15.08435)) { throw 'Observed cold stall must be detected' }
  if (Test-ColdStall 99.2655 15) { throw 'Healthy control must pass' }
  if (Test-ColdStall 2000 500) { throw 'Warm execution cost must not masquerade as a cold stall' }
  if (Test-ColdStall 999 1) { throw 'Subsecond cold draws are outside this diagnostic' }
  if (-not (Test-ColdStall 1500 15)) { throw 'Reduced seconds-long cold stalls must stay detectable' }
  $range = Get-FirstDrawRange '2026/11/08:23:00:46.7148106' 1794178848400 2941
  if ($range.start -ne 1685189 -or $range.end -ne 4626189) { throw 'Observed first-draw interval must map correctly to trace microseconds' }
  Write-Output 'Cold/warm and first-draw trace-window self-checks passed'
  exit 0
}
if ($Mode -eq 'Profile' -and ($Trials -ne 1 -or $Variant -eq 'baseline')) {
  throw 'Profile mode requires one explicit case and -Trials 1'
}
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
$executionFailed = $false
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
      foreach ($argument in @('-Xmx1g', '-cp', $classpath.Trim(), 'dev.chrisbanes.haze.glass.WindowsGlassProbe', $case.variant, $case.continuous, '2', $caseDir, [string]$Width, [string]$Height, $Cuts)) {
        $info.ArgumentList.Add($argument)
      }
      $process = [System.Diagnostics.Process]::new()
      $process.StartInfo = $info
      $verdict = 'INVALID'
      $threshold = if ($case.variant -eq 'control') { 1000 } else { 5000 }
      $reason = 'Missing successful render'
      $coldStall = $false
      $firstDrawMs = $null
      $firstDrawEpochMs = $null
      $warmMedianMs = $null
      $drawLimit = if ($Mode -eq 'Profile') { 30000 } else { $threshold }
      $started = $false
      $recording = $false
      $instance = 'Glass1767-' + [guid]::NewGuid().ToString('N')
      $trace = Join-Path $caseDir 'cpu.etl'
      $watch = [System.Diagnostics.Stopwatch]::StartNew()
      try {
        if ($Mode -eq 'Profile') {
          wpr -start CPU -filemode -instancename $instance *> (Join-Path $caseDir 'wpr-start.txt')
          if ($LASTEXITCODE -ne 0) { throw 'Could not start owned WPR CPU session; do not cancel other sessions' }
          $recording = $true
          [IO.File]::WriteAllText((Join-Path $caseDir 'trace-start-epoch-ms.txt'), [string][DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
        }
        if (-not $process.Start()) { throw 'Could not start owned probe JVM' }
        $started = $true
        [IO.File]::WriteAllText((Join-Path $caseDir 'process-id.txt'), [string]$process.Id)
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        while (-not $process.WaitForExit(50)) {
          $phaseFile = Join-Path $caseDir 'phase.txt'
          if (Test-Path $phaseFile) {
            $phase = Get-Content $phaseFile -Raw
            if ($phase -match '^first-draw,([0-9]{13})$') {
              $firstDrawEpochMs = [long]$Matches[1]
              $elapsed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - [long]$Matches[1]
              if ($elapsed -gt $drawLimit) {
                $verdict = if ($case.variant -eq 'control') { 'INVALID' } else { 'LATENCY_FAIL' }
                $reason = "first-draw exceeded ${drawLimit}ms"
                $coldStall = $case.variant -ne 'control'
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
          $timings = @(Import-Csv (Join-Path $caseDir 'timings.csv'))
          $timing = $timings | Where-Object phase -eq 'first-draw'
          if ($null -ne $timing) {
            $ms = [double]::Parse($timing.ms, [Globalization.CultureInfo]::InvariantCulture)
            $verdict = if ($ms -le $threshold) { 'PASS' } elseif ($case.variant -eq 'control') { 'INVALID' } else { 'LATENCY_FAIL' }
            $reason = "first-draw=${ms}ms"
            $firstDrawMs = $ms
            $firstDrawEpochMs = [long]$timing.startEpochMs
            $warm = @($timings | Where-Object { $_.phase -like 'warm-*' } | ForEach-Object {
              [double]::Parse($_.ms, [Globalization.CultureInfo]::InvariantCulture)
            } | Sort-Object)
            if ($warm.Count -ne 6) { throw 'Completed draw is missing six warm observations' }
            $warmMedianMs = ($warm[2] + $warm[3]) / 2
            $coldStall = Test-ColdStall $firstDrawMs $warmMedianMs
          }
        }
      } finally {
        if ($started -and -not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
        $process.Dispose()
        if ($recording) {
          wpr -stop $trace -skipPdbGen -instancename $instance *> (Join-Path $caseDir 'wpr-stop.txt')
          if ($LASTEXITCODE -ne 0 -or -not (Test-Path $trace)) { throw 'Owned WPR capture could not be saved; inspect partial artifacts' }
        }
      }
      $result = [ordered]@{ variant = $case.variant; continuous = $case.continuous; trial = $trial; width = $Width; height = $Height; cuts = $Cuts; mode = $Mode; profiled = ($Mode -eq 'Profile'); coldStall = $coldStall; firstDrawMs = $firstDrawMs; warmMedianMs = $warmMedianMs; verdict = $verdict; reason = $reason; directory = $caseDir }
      $results += $result
      $result | ConvertTo-Json -Compress | Write-Output
      [IO.File]::WriteAllText((Join-Path $caseDir 'result.json'), ($result | ConvertTo-Json))
      if ($Mode -eq 'Profile') {
        $probePid = [int](Get-Content (Join-Path $caseDir 'process-id.txt') -Raw)
        $env:_NT_SYMBOL_PATH = 'srv*' + (Join-Path $root 'haze-glass/build/symbols') + '*https://msdl.microsoft.com/download/symbols'
        $env:_NT_SYMCACHE_PATH = Join-Path $root 'haze-glass/build/symcache'
        xperf -i $trace -o (Join-Path $caseDir 'trace-stats.txt') -a tracestats *> (Join-Path $caseDir 'xperf-stats-log.txt')
        if ($LASTEXITCODE -ne 0) { throw 'Could not inspect trace quality' }
        $stats = Get-Content (Join-Path $caseDir 'trace-stats.txt') -Raw
        $startMatch = [regex]::Match($stats, 'Start time \(UTC\)\s*:\s*(\S+)')
        if (-not $startMatch.Success -or $null -eq $firstDrawEpochMs) { throw 'Missing trace/first-draw timestamp' }
        $duration = if ($null -ne $firstDrawMs) { $firstDrawMs } else { $drawLimit }
        $range = Get-FirstDrawRange $startMatch.Groups[1].Value $firstDrawEpochMs $duration
        $range | ConvertTo-Json | Set-Content (Join-Path $caseDir 'first-draw-range-us.json')
        xperf -i $trace -o (Join-Path $caseDir 'cpu-modules.txt') -a profile -detail -range $range.start $range.end *> (Join-Path $caseDir 'xperf-profile-log.txt')
        if ($LASTEXITCODE -ne 0) { throw 'Could not export CPU module samples' }
        xperf -i $trace -symbols -o (Join-Path $caseDir 'cpu-stacks.txt') -a stack -butterfly 1 -pid $probePid -event '.*Profile.*' -range $range.start $range.end *> (Join-Path $caseDir 'xperf-stack-log.txt')
        if ($LASTEXITCODE -ne 0) { throw 'Could not export target CPU stacks' }
        xperf -i $trace -symbols -o (Join-Path $caseDir 'wait-stacks.txt') -a stack -butterfly 1 -pid $probePid -event '.*CSwitch.*' -range $range.start $range.end *> (Join-Path $caseDir 'xperf-wait-log.txt')
        if ($LASTEXITCODE -ne 0) { throw 'Could not export target wait stacks' }
        foreach ($report in @('cpu-stacks.txt', 'cpu-modules.txt', 'trace-stats.txt')) {
          if ((Get-Item (Join-Path $caseDir $report)).Length -eq 0) { throw "Empty profile report: $report" }
        }
      }
    }
  }
} catch {
  $executionFailed = $true
  [IO.File]::WriteAllText((Join-Path $output 'runner-error.txt'), $_.Exception.ToString())
  [Console]::Error.WriteLine("GLASS_PROBE INVALID: " + $_.Exception.Message)
} finally {
  ConvertTo-Json -InputObject @($results) -Depth 4 | Set-Content (Join-Path $output 'results.json')
  Write-Output "GLASS_PROBE_RESULTS $output"
}
if ($executionFailed) { exit 2 }
if (@($results | Where-Object verdict -eq 'INVALID').Count -gt 0) { exit 2 }
if ($Mode -in @('Diagnose', 'Profile')) {
  if (@($results | Where-Object coldStall -eq $true).Count -gt 0) { exit 1 }
  exit 0
}
if (@($results | Where-Object verdict -eq 'LATENCY_FAIL').Count -gt 0) { exit 1 }
exit 0
