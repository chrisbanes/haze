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

The [area-reduction run](https://github.com/chrisbanes/haze/actions/runs/42526677840) at
`1ff8bc75a39984eb03efa568e5cc03ac92df1775` passed its investigation assertions. First width was reduced
to 64 with height 160, then height to 48. Both reductions failed all three first draws at the same
five-second gate. All nine original-continuous trials across full and reduced viewports have the
same generated source and uniform hashes and reached actual Direct3D first draw. The material/input
geometry stays 240×160, so this reduces drawn area without changing those inputs:

```powershell
pwsh -File scripts/windows-glass-probe.ps1 -Variant original -Continuous true -Width 64 -Height 160
pwsh -File scripts/windows-glass-probe.ps1 -Variant original -Continuous true -Width 64 -Height 48
```

The next experiment removes one source feature at a time from the 64×48 case. For each cut, run
three fresh trials: retain the cut only if all three remain latency failures; restore the feature
if all three render within the gate; stop on invalid or mixed results. Record every proposal,
decision, retained cut list and case-result path in `cut-ledger.json`. Keep each generated source,
identity and fixture alongside its results. These altered shaders are reproduction reductions,
not production fixes or fidelity-equivalent alternatives.

The ordered cuts bypass ambient response, content-normal sampling, chromatic sampling, per-member
optics, sample offsets, refraction displacement, grading, tint, edge fading, signed distance and
member weights. Each named cut must match exactly one source site; unknown or duplicate names fail.
The runner passes `-Cuts ambient,chroma` as an explicit retained list, not a shell expression.
Native decisions remain pending; local compilation alone cannot establish a reduced failure.
No cause is ranked or production fix selected.

Normal Build and Publish docs jobs are temporarily skipped on `cb/review-1767` at Chris's request.
Remove both commented gates and restore full qualification before presenting a production fix.

The earlier continuous timeout used original source despite its intended guarded selection: the
historical `0-g` artifact retains `break`. The new guarded-continuous experiment is separately labelled and now has its own five-second failure evidence.

## First source-cut pass

The [first source-cut run](https://github.com/chrisbanes/haze/actions/runs/42527851193) at
`bdf31f700567f7fd3f83dc6aeeed8162c62b48a7` completed all three trials of every proposal with no
invalid or mixed verdicts. Its retained cut list is:

```text
ambient,content-normal,chroma,member-optics,sample-offset,grading,tint,edge-fade,signed-distance
```

With those other features removed, the last retained source still fails first draw at the diagnostic
gate. Bypassing refraction made all trials pass earlier in the chain. Replacing member weights with
a fixed weight also passed the gate, but those completed draws remain close to the five-second limit;
that is not evidence of an acceptable cold start or a production fix.

The next pass starts from the exact retained cut list, confirms it still fails on the current runner,
then repeats both final boundary checks. It splits refraction by bypassing standalone evaluation,
merged evaluation, the optical-height input, merged gradients and per-rectangle gradients one at a
time. Each newly retained cut becomes the next proposal's base. This is continued minimisation;
responsible-layer hypotheses and native attribution remain pending.

## Refraction subdivision and profiler readiness

The [refraction subdivision run](https://github.com/chrisbanes/haze/actions/runs/42529244257) at
`ab3f30650d74facd75823a41b6f1b3f137e99216` confirmed the seed and retained two more cuts:
`optical-height` and `rect-gradient`. Every retained proposal still failed all three native trials.
Bypassing complete refraction, replacing member weights, removing standalone evaluation, removing
merged evaluation or bypassing the merged gradient passed the gate in their tested contexts.
Several passing cases still take seconds, so the gate is an investigation signal rather than
proof of an acceptable cold start.

The runner provides `wpr.exe`, `wpaexporter.exe` and `xperf.exe`. WPR enumerates its `CPU` profile;
no native capture or responsible-layer attribution has been made yet.

Next, recheck the eleven-cut seed and replace its entry point with a compact equivalent. A raster
unit check compares every 64×48 fixture pixel before/after that rewrite. Only a repeatable native
failure can retain the compact entry point; raster parity alone cannot establish that it preserves
the stall. Then repeat the remaining feature boundaries against the final retained case. This
removes dead entry-point calculations before native profiling; the full original cases remain
available for final verification.
