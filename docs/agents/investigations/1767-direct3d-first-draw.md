# Glass Direct3D first-draw investigation

Tracks [#1767](https://github.com/chrisbanes/haze/issues/1767). First-draw stacks identify backend shader compilation,
including WARP JIT work. No production workaround has been selected.

## Outcome and qualification

The [standalone exported ZIP](https://github.com/chrisbanes/haze/actions/runs/42536087987), head
`78ff8d6f399df500a0409784bc4cb0cb68b3bbb7`, compiles and reproduces outside the Haze checkout.
All three retained trials have the cold signal; all three paired controls pass. The forced watchdog
returns INVALID and preserves both captured streams. Its shader bytes match the profiled fixture.
The [upstream report draft](1767-upstream-report.md) is ready for review and has not been filed.

Independent review checked fixture/package identity, process/WPR ownership, verdict classification
and scoped profile claims. Its two findings—timeout stream retention and a one-sample table error—
are fixed and the native watchdog test covers the repaired path. Normal Build and Publish docs gates
are restored; the dedicated investigation workflow is manual-only.

The restored [Build](https://github.com/chrisbanes/haze/actions/runs/42536945901) and
[Publish docs](https://github.com/chrisbanes/haze/actions/runs/42536945931) workflows pass on cleanup
head `fa9d8ca607b212d773327b3fa8c3f053755ae320`. Coverage includes macOS compilation, library/sample
checks, Glass desktop tests, desktop/Android screenshots and API 30–34 emulator tests. Deployment
jobs are intentionally skipped on a pull request. This qualifies the investigation tooling alongside
the existing suite; it does not establish a production fix. No production shader/API changed, and
physical GPUs remain unmeasured.

The sections below retain the experiments and corrections that led to this outcome.

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

## Compact-case result and attribution hypotheses

The [compact-case run](https://github.com/chrisbanes/haze/actions/runs/42530372885) at
`236814d733556f3aab2fd90162aba251c46ddcf9` retained the compact entry point: all three native trials
still fail first draw at the five-second gate. Each remaining tested feature boundary passes when
bypassed in that final context: complete refraction, member weights, standalone evaluation, merged
evaluation and merged gradient. Raster parity is exact and rejects a refraction-free image.

This completed feature-level minimisation on that runner; the cross-runner check below later reopened it. It does not establish a
mathematically smallest shader; further lexical/helper minimisation can follow attribution for an
upstream report. Capturing now avoids treating a seconds-long threshold-passing cut as a fix.

Ranked hypotheses, presented before profiling:

1. Backend shader compilation: sampled CPU during first draw is concentrated in D3D compiler
   functions, and the refraction-free control avoids that cost.
2. Skia generation/lowering: Skia/Skiko work dominates before backend compilation.
3. Software rendering: WARP execution dominates target-process CPU samples.
4. Synchronization/presentation: first-draw wall time is mostly waiting, with little active probe CPU.

Capture the retained twelve-cut case and a case differing only by the `refraction` cut. Keep profiles
separate from the latency acceptance loop. `-Mode Profile -Trials 1` uses an owned, uniquely named WPR
CPU session and allows first draw up to 30 seconds, while the five-second verdict stays unchanged.
It saves in `finally`, never cancels another session, and preserves source, PID, epoch timestamps,
ETL, trace-quality statistics, module samples and symbol-decoded target CPU/wait stack reports.
All runner/tool exceptions return INVALID, not the expected latency-failure exit code.

An unprofiled control and the retained case must pass their established gates first. Missing tools,
recording permissions, events or symbols are explicit qualification gaps; merely producing an ETL
or a nonempty report is not layer attribution. No shader/pipeline fix is selected yet.

Commands follow Microsoft's [WPR reference](https://learn.microsoft.com/en-us/windows-hardware/test/wpt/wpr-command-line-options),
[xperf profile action](https://learn.microsoft.com/en-us/previous-versions/windows/desktop/xperf/profile),
[stack action](https://learn.microsoft.com/en-us/previous-versions/windows/desktop/xperf/the-stack-action), and
[symbol decoding](https://learn.microsoft.com/en-us/previous-versions/windows/desktop/xperf/symbols).

## Cross-runner diagnostic correction

The [first profiling attempt](https://github.com/chrisbanes/haze/actions/runs/42531647588) at
`a7717b38f86d68ac9799ffd1dc0b8847580f506c` stopped before recording, as required: its unprofiled
compact draw completed just below the five-second gate. Source hash
`e90d69b97bc577f25aa578d0634321d20240726f77a0520efccea91774a7d6d8` and the fixture-uniform hash match
the previously failing compact trials exactly. The cold draw still took seconds while the six warm
draws took milliseconds. No WPR recording or native layer attribution was obtained.

The five-second gate was too coarse for minimising this still-slow case across shared runners.
Retain it in `Baseline` mode as the original qualification check. Add a separate `Diagnose` signal:
a completed first draw must exceed one second AND twenty times the warm median to count as a cold
stall. A first-draw timeout still records a censored stall; missing/invalid output remains INVALID.
`verdict` continues to describe the original latency gate; `mode`, `coldStall`, `firstDrawMs` and
`warmMedianMs` make the diagnostic decision explicit. Passing the five-second gate alone never proves
that the original seconds-long cold-start problem is fixed.

Self-check the new predicate against this observed cold/warm pair, the healthy control, a
steady-state-heavy case and a subsecond case before any native experiment. Re-run three trials of
the compact seed and continue feature cuts using this signal. Re-test every remaining feature
against the final retained source; stop on mixed results or a still-reducible boundary. Only then
capture the final case and refraction-free control, using the same separate diagnostic signal.
The five-second acceptance gate and all rendering-fidelity requirements remain in place.

## Native capture result and next probe

The [calibrated capture run](https://github.com/chrisbanes/haze/actions/runs/42532549849) at
`7a1c1c3f37dc0e05e0fbaab1c2207d858a94a1f2` passed the predicate self-check, repeated cold/warm trials,
final feature-boundary rechecks and both native recordings. The retained case additionally removes
member weights and standalone refraction; the final cold signal is repeatable under the corrected
diagnostic. Both ETLs report zero lost events and buffers.

The decoded target-process CPU stack report identifies two backend compilation stages:

- `d3dcompiler_47.dll`, including `CProgram::ReadWriteInfo`.
- `D3D10Warp.dll!Task_CompileShader` → `PixelJITProcessor::InitializeJIT` → `CProgram::Compile`,
  with exclusive samples in `CProgram::ComputeUsage`, `CProgram::Optimize` and related analysis.

This supports backend compilation, including WARP's pixel-shader JIT. It does not support describing
the WARP samples as steady-state shader execution. The initial exports cover the entire target JVM
lifetime, including startup; do not present their percentages as a first-draw-only split. JVM/Skiko
private symbols have gaps. The paired refraction-free control has little backend compiler activity.
Physical-GPU behavior remains unmeasured.

Next, export only the first-draw interval using the ETL UTC start plus retained phase epochs, with a
self-check against this actual trace interval. Repeat a capture of the original unreduced continuous
source to verify that the reduced and original cases point at the same layer. Keep captured timings
separate from unprofiled acceptance. A timeout remains censored, not a completed compiler duration.

Test one compiler-hint prediction: if repeated SkSL inlining expands the costly backend program,
`noinline` on `shapeSd` will reduce cold cost with unchanged pixels. Skia's
[function modifier validation](https://skia.googlesource.com/skia/+/d2b9e48baf1697760afc1dc8ea3ad40110b8cacc/src/sksl/ir/SkSLFunctionDeclaration.cpp)
and [inliner](https://skia.googlesource.com/skia/+/ffeb6f23394c9391041e68200598debc2f8bd93b/src/sksl/SkSLInliner.cpp)
describe this modifier; the installed RuntimeEffect compiler and native experiment must qualify it.
This is a test-only hint, not an established workaround. Compare exact raster output for both original
corner types and the retained fixture before native trials. Expand to the original native cases only
if the retained cold signal disappears; a negative experimental result is valid evidence, not a fix.


## First-draw attribution and negative hint result

The [scoped capture](https://github.com/chrisbanes/haze/actions/runs/42534101240) at
`1bf6fdce790c1de76e34864cfbe04c196d59a861` confirms the same layer for the original continuous shader
and the reduced case. All three ETLs report zero lost events/buffers. The exports select the
first-draw epoch interval, not the JVM lifetime; `first-draw-range-us.json` preserves each range.

| Case | First-draw interval | Target exclusive CPU samples | Compiler attribution |
| --- | --- | --- | --- |
| Original continuous, unmodified | 30-second censored interval | 29,944 | WARP 23,469; D3DCompiler 5,434 |
| Retained fourteen-cut case | 2,717.47 ms | 2,701 | WARP 1,735; D3DCompiler 777 |
| Same retained source, refraction bypassed | 64.40 ms | 54 | WARP 1; D3DCompiler 25 |

These are sampled CPU counts, not wall-time shares. The control has too few samples for a useful
percentage comparison. Both failing cases resolve `D3D10Warp!Task_CompileShader` →
`PixelJITProcessor::InitializeJIT` → `CProgram::Compile`. WARP analysis/optimization frames dominate:
`ComputeUsage`, `Optimize`, `BuildDependencies`, and, in the original case, `SeparateVariables`.
D3DCompiler includes `CProgram::ReadWriteInfo`. This attributes the first-draw cost to backend
compilation; it does not identify the exact compiler algorithm defect or prove which upstream
component should change. The original did not finish within 30 seconds, so its compilation duration
remains unknown. JVM/Skiko private-symbol gaps and software-adapter coverage remain limits.

Three unprofiled retained trials complete in 2,450–2,552 ms with warm medians 2.09–15.18 ms.
The test-only `noinline shapeSd` trials still take 2,499–2,598 ms with the same cold-stall diagnosis.
The hint preserves raster pixels but does not remove the native signal; its code and workflow probe
have been removed. Guarding member-loop bodies also did not fix the original symptom. No production
shader change is justified by these experiments.

The [standalone reproducer](1767-repro/README.md) freezes the measured reduced source, its paired
refraction-free control and exact uniforms. It depends only on Kotlin/Skiko and draws through a
native SkiaLayer, with no Haze or Compose runtime. Fixture-fidelity tests bind it to the measured
fourteen-cut case. Its exported ZIP is compiled and run outside the checkout by the investigation
workflow before it can be called upstream-ready. The [upstream report draft](1767-upstream-report.md)
is for review; it has not been filed externally.
