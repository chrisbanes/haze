# Glass Direct3D first-draw investigation

Tracks [#1767](https://github.com/chrisbanes/haze/issues/1767). No production shader change or
compiler attribution is established yet.

## Feedback command

On Windows, with Java 21 and a visible desktop, from the repository root:

```powershell
./gradlew.bat :haze-glass:prepareWindowsGlassProbe :haze-glass:jvmTest --tests '*WindowsGlassProbeTest' --no-scan
pwsh -File scripts/windows-glass-probe.ps1 -Mode Baseline
```

The first command compiles and exports a runtime classpath. The second starts each case in a fresh
JVM directly, avoiding repeated Gradle startup. It runs three trials each of a cheap patterned
control, original rounded optical, original continuous optical, and guarded continuous optical.
All cases use the same isolated native SkiaLayer path, 240×160 content and two merged members.
This is not a full Compose scene or a physical-GPU benchmark.

Exit codes: `0` means every selected case passed, `1` means at least one real first draw exceeded
five seconds, and `2` means an invalid experiment. Controls must finish first draw within one second.
An incorrect backend, failed control, missing output or outer 60-second watchdog is invalid.
The feedback command intentionally returns `1` when the known stall reproduces; the investigation
workflow checks for three passing controls and six repeatable original-case latency failures.

Select one case for a short rerun after setup:

```powershell
pwsh -File scripts/windows-glass-probe.ps1 -Variant original -Continuous true -Trials 1
```

The parent terminates only its own probe JVM if first draw exceeds the limit. Completed cases retain
six warm draws and a nonblank-output check. Timed-out cases have no completed first-draw duration or
warm measurements; the timeout is only a lower bound. The five-second threshold is a diagnostic gate,
not a cross-device performance guarantee.

## Evidence and current boundary

Outputs are under `haze-glass/build/outputs/glass-probe/<run-id>/`: generated shader and hash,
resolved variant, fixture uniforms/child shader, renderer, build/runtime identity, phase records,
timings, any completed image, process output and structured verdicts. The workflow always uploads
partial artifacts. Fresh JVM/context does not prove an empty driver/disk cache.

Local selection checks pass: unknown `g` is rejected, original source equals production source, and
both guarded corner variants compile.

The [native calibration run](https://github.com/chrisbanes/haze/actions/runs/42525801965) at
`86133897375dcd27b3cea380d554d508c1026af4` ran the baseline feedback command above. Its retained
artifacts establish three passing controls, three rounded-original first-draw failures, three
continuous-original failures, and three explicitly guarded-continuous failures. Every optical
failure reached the first-draw marker on actual Direct3D through Microsoft Basic Render Driver
and exceeded five seconds. All resolved variants, generated source hashes, uniforms and child
sources were retained. These are censored first-draw measurements, not completed timings.

The workflow check failed after those results because GitHub Actions propagated the feedback
command's expected exit code `1`; the workflow now exits `0` only after its result assertions pass.
This repairs workflow classification, not the Glass stall.

Next, reduce only the viewport width to 64 while keeping height 160 and all shader source/uniforms
unchanged. Only if all three trials remain red, reduce height to 48 as a second change. Keep the
240×160 material/input geometry so this tests drawn area separately from shader inputs:

```powershell
pwsh -File scripts/windows-glass-probe.ps1 -Variant original -Continuous true -Width 64 -Height 160
pwsh -File scripts/windows-glass-probe.ps1 -Variant original -Continuous true -Width 64 -Height 48
```

Area-reduction results are pending. No cause is ranked or production fix selected.

The earlier continuous timeout used original source despite its intended guarded selection: the
historical `0-g` artifact retains `break`. The new guarded-continuous experiment is separately labelled and now has its own five-second failure evidence.
