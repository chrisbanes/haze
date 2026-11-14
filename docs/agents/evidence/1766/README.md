# Glass shader runtime profiling

[Issue #1766](https://github.com/chrisbanes/haze/issues/1766) asks for a measured way to reduce
Glass shader execution cost without changing rendering quality. This is the first bounded slice,
under the [approved plan](https://github.com/chrisbanes/haze/issues/1766#issuecomment-6824294701)
(revision 1). It records a baseline that separates CPU preparation, cold first draw and warm GPU
execution on a Pixel 6 and an M1 Max, and the measured outcome of one shader-source candidate.

## Outcome

**No shader change lands.** The one candidate, skipping the lighting gradient where the circular
surface is flat, passed every `haze-glass` `jvmTest` pixel test unchanged. It improved the
Android primary row, but its Metal primary row was inconclusive, so it was reverted under the
[acceptance rule](t5-acceptance-rule.md) fixed before measurement. `haze-glass/src/commonMain` is byte-identical to base
`95c0c48a915d3593e7f8d34039404f54f4e538e7`. The candidate is kept as
[candidate.patch](candidate.patch) with its raw numbers below.

The investigation tooling is retained deliberately: four Android benchmark rows (continuous corners
and a four-member cluster) and the `GlassMetalProfileScene` launcher for macOS.

Only this report, the acceptance rule and the candidate patch are kept in the tree. The
per-iteration summaries, placement and marker tables, the trace manifest and the shader list are in
[commit `0693289`](https://github.com/chrisbanes/haze/tree/0693289577942e0ed28328bf16066c59e61cbb58/docs/agents/evidence/1766)
of [PR #1788](https://github.com/chrisbanes/haze/pull/1788), and the data links below point there.

## Paths and devices

The two platforms render different programs, so every comparison names its path.

| Platform | Path | Device | Backend and driver |
| --- | --- | --- | --- |
| Android | Fused (`supportsFusedGlassRenderEffect = true`) | Pixel 6, `google/oriole_beta/oriole:17/CP31.260623.012/16064790`, API 37 beta | HWUI on Vulkan, Mali-G78, Vulkan driver version 226504704 |
| macOS | Split retained stages (Skiko) | Apple M1 Max, macOS 27.0.1 (26A434) | Skiko 0.150.2 on Metal, `renderInfo` "Video card: Apple M1 Max" |

Metal results describe the split stages. They are not evidence for the fused path, and the Pixel 6
results are not evidence for the split path.

## Proofs that gate GPU attribution

**T2, Pixel 6 GPU render stages: FAIL.** A concurrent Perfetto session with `gpu.renderstages`,
`gpu.counters`, frame timeline and process stats ran through one `sourceUpdateContainer2` dry run.
The trace contained 345 actual frames for the app and two GPU counter tracks, but 0 `gpu_slice`
rows. `perfetto --query` listed no `gpu.renderstages` producer, although the device advertises
`graphics.gpu.profiler.support.render_stages`. Android warm GPU execution is therefore **pending
qualification**. Android results below use `frameOverrunMs` (end-to-end frame deadline) and
`frameDurationCpuMs` (UI thread plus RenderThread CPU) under those names; neither is GPU time.

**T3, Metal System Trace: PASS.** The launcher ran on `METAL`. A `Metal System Trace` capture of a
two-member merged circular scene exported 5,106 depth-0 GPU intervals for the launched `java`
process inside its 5 s measured window (0.63 ms of GPU busy time per frame over 297 frames). The
same template records Metal compiler intervals per process, which the cold analysis uses.

## Baseline (T4)

### Android, fused path

Build `fd85578d9` (base plus the new rows), APKs
`android-benchmarkRelease.apk` `ec90648488a770c5bb039270e84c0377957bbc3252f8a834e04890e3aea26c66`
and `benchmark-benchmarkRelease.apk`
`21f9688879a15f6269ec5282179829c63238131a0608d56447175adc6a070f98`. Each row is one
eight-iteration invocation per pass: pass one in the listed order, pass two reversed. Container
rows passed the `gates_for` per-frame gates, including `graphicsLayerDrawCount` 0.9-1.1 for every
cluster row, so each cluster formed. Values are the median across the eight iterations of each
iteration's own P50 or P90, with the IQR of those eight values. Fixed-performance mode was on,
thermal status 0, battery 27-34 °C, on AC. The display was not pinned. Pass headers read 60 Hz,
but those readings were taken before the app launched. In the traces themselves, every warm
iteration in T4 and T5 has a median interval of 11.07-11.11 ms between the app's actual frames,
which is 90 Hz. That matches 271 frames over the 3 s animation
([frame rate][android-frame-rate]). The cold-attach rows draw only 2-3 frames per
iteration, so their intervals are not a rate.

All warm rows, and both arms of every T5 comparison, therefore ran at 90 Hz. This has two
consequences:

- `frameOverrunMs` is headroom against the app's FrameTimeline expected frame. In every measured
  trace, that expected frame lasted 16.5-16.6 ms (`expected_frame_timeline_slice`).
- Differences between rows and between baseline and candidate are same-rate comparisons. Absolute
  headroom may differ at another refresh rate.

| Row | Pass | `frameOverrunMs` P50 | `frameOverrunMs` P90 | `frameDurationCpuMs` P50 | `frameDurationCpuMs` P90 | Frames |
| --- | --- | --- | --- | --- | --- | --- |
| `containerPress2` | one | -7.90 (IQR 0.47) | -5.60 (IQR 0.43) | 3.87 (IQR 0.44) | 6.19 (IQR 1.15) | 271 |
| `containerPress2` | two | -7.96 (IQR 0.30) | -5.93 (IQR 0.36) | 3.99 (IQR 0.27) | 6.27 (IQR 0.61) | 271 |
| `effectAttach` | one | -9.49 (IQR 0.75) | -9.07 (IQR 1.39) | 4.94 (IQR 0.69) | 5.54 (IQR 0.70) | 2 |
| `effectAttach` | two | -9.94 (IQR 0.52) | -9.18 (IQR 0.59) | 4.74 (IQR 0.53) | 5.47 (IQR 0.56) | 2 |
| `effectAttachContainer2` | one | -6.64 (IQR 0.74) | -5.12 (IQR 1.72) | 5.58 (IQR 0.75) | 7.06 (IQR 2.40) | 2 |
| `effectAttachContainer2` | two | -7.30 (IQR 0.54) | -6.21 (IQR 0.36) | 5.21 (IQR 0.41) | 6.35 (IQR 0.21) | 2 |
| `interactionUpdate` | one | -11.05 (IQR 0.71) | -8.72 (IQR 0.78) | 3.52 (IQR 0.66) | 6.24 (IQR 1.30) | 48 |
| `interactionUpdate` | two | -10.76 (IQR 0.50) | -8.58 (IQR 0.29) | 3.61 (IQR 0.46) | 6.19 (IQR 0.51) | 48 |
| `sourceUpdateBalanced` | one | -11.50 (IQR 0.09) | -10.28 (IQR 0.69) | 2.88 (IQR 0.08) | 4.28 (IQR 1.33) | 271 |
| `sourceUpdateBalanced` | two | -11.42 (IQR 0.28) | -9.66 (IQR 0.23) | 2.95 (IQR 0.29) | 5.26 (IQR 0.27) | 271 |
| `sourceUpdateBalancedContinuous` | one | -11.36 (IQR 0.16) | -9.75 (IQR 0.84) | 2.83 (IQR 0.14) | 5.00 (IQR 1.35) | 271 |
| `sourceUpdateBalancedContinuous` | two | -11.34 (IQR 0.09) | -10.39 (IQR 0.74) | 2.88 (IQR 0.09) | 3.83 (IQR 1.14) | 271 |
| `sourceUpdateContainer2` | one | -9.10 (IQR 0.07) | -7.14 (IQR 0.28) | 2.99 (IQR 0.18) | 5.37 (IQR 0.61) | 271 |
| `sourceUpdateContainer2` | two | -8.92 (IQR 0.50) | -6.86 (IQR 0.50) | 3.29 (IQR 0.49) | 5.80 (IQR 0.54) | 271 |
| `sourceUpdateContainer2Continuous` | one | -7.81 (IQR 1.07) | -6.39 (IQR 1.13) | 2.68 (IQR 0.12) | 3.51 (IQR 0.97) | 271 |
| `sourceUpdateContainer2Continuous` | two | -7.86 (IQR 0.22) | -6.51 (IQR 0.72) | 2.72 (IQR 0.15) | 3.65 (IQR 1.42) | 271 |
| `sourceUpdateContainer4` | one | -7.02 (IQR 0.10) | -6.24 (IQR 0.32) | 2.86 (IQR 0.16) | 3.54 (IQR 0.47) | 271 |
| `sourceUpdateContainer4` | two | -7.02 (IQR 0.39) | -5.84 (IQR 1.34) | 2.78 (IQR 0.17) | 3.57 (IQR 2.05) | 271 |
| `sourceUpdateContainer4Continuous` | one | -6.06 (IQR 0.34) | -4.46 (IQR 0.80) | 2.98 (IQR 0.62) | 5.00 (IQR 1.14) | 271 |
| `sourceUpdateContainer4Continuous` | two | -5.67 (IQR 0.57) | -4.31 (IQR 0.56) | 3.44 (IQR 1.50) | 5.28 (IQR 0.71) | 271 |
| `sourceUpdateIndependent2` | one | -10.05 (IQR 0.08) | -9.11 (IQR 0.16) | 4.19 (IQR 0.09) | 5.17 (IQR 0.23) | 271 |
| `sourceUpdateIndependent2` | two | -10.16 (IQR 0.04) | -9.15 (IQR 0.15) | 4.12 (IQR 0.05) | 5.24 (IQR 0.17) | 271 |
| `stableBalanced` | one | -12.89 (IQR 0.10) | -12.23 (IQR 0.12) | 3.00 (IQR 0.10) | 3.68 (IQR 0.14) | 271 |
| `stableBalanced` | two | -12.80 (IQR 0.71) | -12.09 (IQR 0.65) | 3.03 (IQR 0.10) | 3.75 (IQR 0.09) | 271 |

RenderThread ran mostly on the middle cluster (CPUs 4-5) in 146 of 192 iterations and on the
little cluster in 46; the main thread ran on the little cluster in almost every iteration
([placement][android-t4-placement]). `stableBalanced` and pass-two `interactionUpdate` are
mostly little-cluster RenderThread iterations, so compare them with that in mind.

Pass two was interrupted twice by a lost start click on `sourceUpdateContainer2`'s first
iteration (a `phase=complete` timeout, not a measurement). The remaining reverse-order methods
were rerun into the same archive after returning the device to the home screen.

### macOS, split path

Build `fd85578d9`. Warm: three fresh-JVM launches per scene under `Metal System Trace`, 240 warm
frames, then a 5 s window; GPU busy time is the union of the process's depth-0 GPU intervals in the
window divided by the frames drawn. Cold: five fresh-JVM launches per scene under `Time Profiler`;
`firstFrameCallbackCpuWallMs` is taken when the scene's `LaunchedEffect` starts, after the first
composition and before any frame callback; `secondFrameCallbackCpuWallMs` follows the next frame
callback, after the first frame was drawn. Neither proves that frame contained Glass pixels. Rounds were
interleaved across scenes. `s1` is one surface, `s2m`/`s4m` are two or four merged members,
`s2i` is two independent surfaces, all with a changing source unless marked static.

| Scene | GPU busy ms/frame | Fragment ms/frame | Frames (5 s) | Metal compiler ms before first frame |
| --- | --- | --- | --- | --- |
| `s1-circular` | 0.369 (IQR 0.028) | 0.282 (IQR 0.010) | 299 | 3.120 (IQR 0.465) |
| `s1-circular-static` | 0.390 (IQR 0.032) | 0.291 (IQR 0.011) | 298 | 3.160 (IQR 0.555) |
| `s1-continuous` | 0.467 (IQR 0.033) | 0.341 (IQR 0.009) | 290 | 2.990 (IQR 2.840) |
| `s2i-circular` | 1.031 (IQR 0.020) | 0.566 (IQR 0.008) | 299 | 4.840 (IQR 1.560) |
| `s2m-circular` | 0.751 (IQR 0.011) | 0.607 (IQR 0.039) | 299 | 3.500 (IQR 1.450) |
| `s2m-continuous` | 1.095 (IQR 0.028) | 0.992 (IQR 0.039) | 299 | 3.800 (IQR 0.765) |
| `s4m-circular` | 0.921 (IQR 0.013) | 0.768 (IQR 0.022) | 296 | 5.700 (IQR 0.940) |
| `s4m-continuous` | 1.402 (IQR 0.028) | 1.278 (IQR 0.012) | 299 | 4.830 (IQR 1.505) |

| Scene | Process start to effect start (ms) | To first frame callback (ms) | JVM/JIT CPU ms | Skia/Skiko CPU ms | Metal compiler CPU ms | Other CPU ms |
| --- | --- | --- | --- | --- | --- | --- |
| `s1-circular` | 2004 (IQR 1004) | 2815 (IQR 1163) | 1478 (IQR 470) | 36 (IQR 8) | 8 (IQR 7) | 206 (IQR 42) |
| `s1-circular-static` | 1430 (IQR 281) | 1657 (IQR 276) | 1386 (IQR 245) | 36 (IQR 5) | 5 (IQR 2) | 183 (IQR 15) |
| `s1-continuous` | 1433 (IQR 155) | 1629 (IQR 226) | 1360 (IQR 81) | 31 (IQR 7) | 4 (IQR 3) | 172 (IQR 5) |
| `s2i-circular` | 1604 (IQR 563) | 1764 (IQR 668) | 1411 (IQR 388) | 33 (IQR 9) | 6 (IQR 3) | 195 (IQR 24) |
| `s2m-circular` | 1529 (IQR 499) | 1689 (IQR 554) | 1366 (IQR 356) | 32 (IQR 6) | 8 (IQR 1) | 184 (IQR 14) |
| `s2m-continuous` | 1446 (IQR 119) | 1602 (IQR 114) | 1354 (IQR 111) | 37 (IQR 7) | 6 (IQR 2) | 196 (IQR 9) |
| `s4m-circular` | 1398 (IQR 126) | 1552 (IQR 143) | 1358 (IQR 26) | 33 (IQR 4) | 6 (IQR 0) | 195 (IQR 7) |
| `s4m-continuous` | 1438 (IQR 88) | 1608 (IQR 114) | 1399 (IQR 246) | 37 (IQR 7) | 8 (IQR 6) | 196 (IQR 15) |

The Metal cache directory under `$(getconf DARWIN_USER_CACHE_DIR)com.apple.metal` held 8 files
(40,368 KB) before every launch and was not cleared. The T3 proof launch, the first after the
launcher was built, recorded 581 ms of Metal compiler intervals before its first frame; later
launches recorded scene medians of 3-6 ms, with individual launches up to 13 ms. That is one observation of a cold driver cache, not a controlled
measurement.

### Cost ranking

1. **Warm GPU execution, split path (Metal).** Continuous corners cost most: they raise fragment
   time by about 60% in merged clusters (0.61 to 0.99 ms per frame for two members, 0.77 to
   1.28 ms for four) and about 20% for one surface. Merging costs less GPU busy time than drawing
   the same members independently (two merged 0.75 ms versus two independent 1.03 ms), but not
   less fragment time (0.61 versus 0.57 ms); the saving is outside the fragment channel. Four
   merged members cost about 23% more than two.
2. **Warm frame deadline, fused path (Pixel 6).** The same order for member count and corner style
   appears in `frameOverrunMs` while
   `frameDurationCpuMs` P50 stays at 2.7-3.4 ms for every source-update container row: four continuous members lose
   about 5.5 ms of headroom (at 90 Hz) against one circular surface, four circular members about 4.5 ms, and
   continuous corners about 1 ms on top. This is consistent with GPU cost, but T2 could not
   attribute GPU time, so it is reported as frame deadline only. Merging does not follow the Metal
   order here: two independent surfaces keep about 1 ms more headroom than a two-member cluster
   (-10.05 and -10.16 against -9.10 and -8.92), although they cost more CPU (item 3).
3. **CPU preparation and recording.** Small and flat. Android `frameDurationCpuMs` P50 is
   2.7-4.2 ms across the warm rows (5.2-5.6 ms in the cold-attach rows); two independent surfaces
   cost 1.2 ms (pass one) and 0.8 ms (pass two) more than one cluster, plausibly from recording two
   layers. Only `prepare`, `runtimeDraw` and `compose` markers (plus container markers)
   appear in steady rows ([markers][android-t4-markers]).
4. **Cold first draw.** On macOS a fresh process reaches the frame callback after its first frame
   in 1.55-2.8 s (scene medians), and about 1.4 s of the sampled CPU before it is JVM
   start-up and JIT; Skia/Skiko native code is about 35 ms,
   in-process Metal compiler work 4-8 ms and Metal compiler intervals 3-6 ms (medians) with a warm
   cache. On
   Android, `effectAttach` and `effectAttachContainer2` measure first attachment in a warm process:
   no iteration started its process inside the trace, so none is process-cold. Haze's
   `createRenderEffect` ran 3 (one surface) or 2 (container) times per iteration for about 0.02 ms
   in total; deferred backend work is not attributed by that marker.

Desktop emitted no `HazeGlass.*` markers: a `Time Profiler` capture of `s2m-circular` had none in
its signpost, Points of Interest or log tables. Skiko's `trace` delegates to
`androidx.compose.ui.util.trace`, which does not reach an Instruments signpost here.

### Hypothesis selected for T5

The default regular style lights every fragment with `surfaceGradient`, which evaluates the
field's signed distance at four neighbours: four member distances each in a four-member cluster.
Beyond the refraction zone the surface is flat, the four heights are equal and the gradient is
exactly zero. For circular corners the field and its smooth union are 1-Lipschitz, so the gradient
can be skipped when the centre is a pixel past the zone plus `sampleStep`, with no pixel change.
Expected affected rows: every circular row, most in merged clusters.

Continuous corners are the larger measured cost, but their corner distance is an approximation
without a Lipschitz bound, so the same skip could change pixels. Reducing continuous-corner cost
(for example a bounded superellipse distance) needs a separate fidelity decision and is recorded
as a recommendation, not attempted.

## Candidate (T5)

The candidate ([candidate.patch](candidate.patch), commit `1c0bdb323`) returns a zero lighting
gradient when `fieldWeight <= 0` and `-sd >= max(refractionHeight, 0.0001) + sampleStep + 1`,
for circular sources only. It changed only the fused and optical sources (nine
`GlassShadersGoldenTest` digests). All 1,019 `haze-glass` `jvmTest` tests passed with every other
digest, pixel test and tolerance unchanged. Because it did not land, `testAndroidHostTest`, the
screenshot matrix and the Pixel 6 device tests were not run on it.

The [acceptance rule](t5-acceptance-rule.md) was written at 15:02 UTC, before the first
candidate result (15:11 UTC), per local file modification times; it was committed after the
measurements. It is stricter than the plan, which names a primary metric per
affected path. The primary values are
per-iteration P50 `frameOverrunMs` on Android (frame deadline, not GPU time) and per-launch GPU
busy ms per frame on Metal. A row improves when the candidate median beats the baseline median by
more than the larger IQR, in each Android pass separately or across the three alternating macOS
rounds. The change lands only if the primary rows improve on **both** affected paths:
`sourceUpdateContainer4` in both Android passes and `s4m-circular` on Metal. No affected row may
regress, and Android CPU placement must be comparable. Continuous rows are unchanged controls.

**Android, fused path.** Baseline APK `ec906484…` (identical to T4) and candidate APK
`eafa8a46…`, run in the order baseline forward, candidate forward, candidate reverse, baseline
reverse. Eight iterations per row, 271 frames each. The traces show the app rendering at 90 Hz
in every iteration of both arms, as it did in T4, so the comparison holds at a single rate. The T5
pass headers read 90 Hz from the launcher and the T4 headers read 60 Hz; neither reading was
taken while the app was running. RenderThread placement was comparable: for
`sourceUpdateContainer4`, all 16 baseline and 16 candidate iterations ran on the middle cluster
except one baseline iteration ([placement][android-t5-placement]).

| Row | Pass | Baseline | Candidate | Baseline - candidate | Margin | Result |
| --- | --- | --- | --- | --- | --- | --- |
| `sourceUpdateContainer4` | one | -7.040 (IQR 0.092) | -7.357 (IQR 0.056) | +0.317 | 0.092 | improved |
| `sourceUpdateContainer4` | two | -7.076 (IQR 0.100) | -7.423 (IQR 0.114) | +0.347 | 0.114 | improved |
| `sourceUpdateContainer2` | one | -9.084 (IQR 0.732) | -9.344 (IQR 0.249) | +0.260 | 0.732 | no difference |
| `sourceUpdateContainer2` | two | -8.974 (IQR 0.194) | -9.018 (IQR 0.897) | +0.044 | 0.897 | no difference |
| `sourceUpdateBalanced` | one | -11.491 (IQR 0.436) | -11.625 (IQR 0.417) | +0.134 | 0.436 | no difference |
| `sourceUpdateBalanced` | two | -11.513 (IQR 0.055) | -11.517 (IQR 0.416) | +0.004 | 0.416 | no difference |
| `sourceUpdateContainer4Continuous` | one | -6.222 (IQR 0.291) | -5.964 (IQR 0.398) | -0.258 | 0.398 | no difference |
| `sourceUpdateContainer4Continuous` | two | -6.118 (IQR 0.600) | -6.036 (IQR 0.467) | -0.082 | 0.600 | no difference |

**macOS, split path.** Frozen baseline and candidate `haze-glass` classes, otherwise identical
classpaths. Three warm launches each, in the order B C, C B, B C. Baseline launches drew 286-300
frames in their 5 s windows (297-299 for `s4m-circular` and `s2m-circular`).

| Scene | Baseline | Candidate | Baseline - candidate | Margin | Result |
| --- | --- | --- | --- | --- | --- |
| `s4m-circular` | 0.911, 0.880, 0.918 | 0.908, 0.850 (third launch invalid) | - | - | inconclusive |
| `s2m-circular` | 0.779, 0.714, 0.718 | 0.673, 0.740 (third launch invalid) | - | - | inconclusive |
| `s1-circular` | 0.389 (IQR 0.008) | 0.360 (IQR 0.014) | +0.029 | 0.014 | improved |
| `s2i-circular` | 0.893 (IQR 0.123) | 0.850 (IQR 0.178) | +0.043 | 0.178 | no difference |
| `s4m-continuous` | 1.577 (IQR 0.096) | 1.561 (IQR 0.079) | +0.016 | 0.096 | no difference |

The candidate's third-round launches of `s4m-circular` and `s2m-circular` drew only 16 and 84
frames, so they are not valid measurements. Each was repeated once. The repeats drew 591 and 519
frames, so the display was then running at about 120 Hz instead of 60 Hz. Their per-frame values
(0.798 and 0.696) are therefore not comparable with the 60 Hz baseline launches. Both rows are
inconclusive. The two valid `s4m-circular` launches alone would clear the margin (0.032 against
0.029 ms), but two launches do not meet the rule's three alternating rounds. The rule's "repeat
once, then inconclusive" step is written for Android CPU
placement; it was applied to Metal frame validity by analogy. The remaining scenes are context
only. The summary keeps both launches out of its values, median and IQR and lists them
under `excludedLaunches`; the manifest lists both traces by SHA-256. The T5 capture script also
ran one unrequested round of cold launches per label; they are hashed in the manifest and unused.

**Result: not landed.** The Android primary row improved by about 0.3 ms of frame-deadline P50
in both passes, beyond its 0.09-0.11 ms IQR, and its continuous control did not move. The Metal
primary row was inconclusive after one repeat. Only the single circular surface clearly improved,
by 0.029 ms (about 7%). The rule requires both paths to qualify, so the candidate was reverted. P90 values and the two-member and single-surface Android
rows showed no difference beyond variability ([raw summary][android-t5-summary],
[Metal summary][mac-t5-summary]).

A follow-up could re-measure this exact change on the fused path alone with GPU attribution, for
example on a device that exposes `gpu.renderstages`. That would need its own acceptance decision;
the Android improvement here is frame deadline on one device, not GPU time.

## Untested and limits

- Physical-GPU qualification covers only the Pixel 6 (Mali-G78, Vulkan, API 37 beta) and the
  M1 Max (Metal). Android GPU execution is pending qualification (T2).
- Untested: iOS, web/wasm, Linux/OpenGL, Windows/Direct3D (cold start is owned by
  [#1767](https://github.com/chrisbanes/haze/issues/1767)), other Android GPUs (Adreno, PowerVR,
  Xclipse) and Android below 37.
- The Mac was shared with other work during captures (other processes, including iOS simulators,
  were present); this widens warm-launch spread, for example `s2i-circular` between sessions.
- The Pixel 6 display was not pinned to 60 Hz as the runbook asks. Every warm iteration ran at
  90 Hz per its trace, with a FrameTimeline expected frame of 16.5-16.6 ms, so absolute
  `frameOverrunMs` headroom applies only to that configuration. Pass-header rates were read before
  launch and do not describe the measurement.
- Device handling followed the runbook. Fixed-performance mode was enabled per pass and disabled
  on exit. Each method checked the keyguard first. The Google app respawned after a force-stop, so
  it was disabled with `pm disable-user` for the session and re-enabled afterwards.
- The T1 gated diagnostic (`sourceUpdateContainer2Continuous`, `sourceUpdateContainer4`,
  `sourceUpdateContainer4Continuous`, eight iterations each) passed every `gates_for` gate,
  including `graphicsLayerDrawCount` 0.9-1.1 per frame. Its archive hashes are in the manifest; it
  is not published as a measurement.
- [shaders.json][shaders] lists the base sources with `coverageOutset = false`. Runtime
  selects the outset variants for pixel-aligned surfaces, and the dump does not show which variant
  each scene ran.
- Cold Android rows are warm-process first attachment. macOS cold launches include JVM start-up
  and ran with a warm Metal cache; the cold-cache observation is a single launch.
- Benchmark JSON (about 130 KB per invocation) and all traces stay in the local archive, listed
  by SHA-256 in the [manifest][manifest]; per-iteration values are in the summaries.

## Reproduce

Android rows follow [the benchmark runbook](../../../../internal/benchmark/README.md), using
`gates_for` for the container rows. The macOS launcher:

```sh
./gradlew --no-scan -Phaze.disableAppleTargets :haze-glass:prepareGlassMetalProfile
cp=$(cat haze-glass/build/glass-metal-profile-classpath.txt)
java=$(readlink -f "$(which java)")  # xctrace --launch needs the real binary, not a shim
xcrun xctrace record --template 'Metal System Trace' --time-limit 60s --output warm.trace --launch -- \
  "$java" -cp "$cp" dev.chrisbanes.haze.glass.GlassMetalProfileSceneKt \
  --members 4 --merged true --corners circular --source changing \
  --warm-frames 240 --measure-seconds 5 --build-sha "$(git rev-parse --short HEAD)" --output warm
xcrun xctrace record --template 'Time Profiler' --time-limit 60s --output cold.trace --launch -- \
  "$java" -cp "$cp" dev.chrisbanes.haze.glass.GlassMetalProfileSceneKt \
  --members 4 --merged true --corners circular --source changing \
  --warm-frames 30 --measure-seconds 1 --output cold
java -cp "$cp" dev.chrisbanes.haze.glass.GlassMetalProfileSceneKt --dump-shaders shaders
```

The launcher exits with status 2 unless the window renders on `METAL`. It writes `run.json` with
the scene, `renderApi`, `renderInfo`, display scale, OS, JVM, frame-callback times and the
measured window. Export GPU intervals with
`xcrun xctrace export --input warm.trace --xpath '/trace-toc/run[@number="1"]/data/table[@schema="metal-gpu-intervals"]'`
and compiler intervals with the `graphics-compiler-activity-intervals` schema. `xctrace`
occasionally hung while saving after the target exited; such launches were stopped and repeated.
[shaders.json][shaders] lists the dumped split and fused sources at base with SHA-256.

[android-frame-rate]: https://github.com/chrisbanes/haze/blob/0693289577942e0ed28328bf16066c59e61cbb58/docs/agents/evidence/1766/android-frame-rate.json
[android-t4-markers]: https://github.com/chrisbanes/haze/blob/0693289577942e0ed28328bf16066c59e61cbb58/docs/agents/evidence/1766/android-t4-markers.json
[android-t4-placement]: https://github.com/chrisbanes/haze/blob/0693289577942e0ed28328bf16066c59e61cbb58/docs/agents/evidence/1766/android-t4-placement.json
[android-t5-placement]: https://github.com/chrisbanes/haze/blob/0693289577942e0ed28328bf16066c59e61cbb58/docs/agents/evidence/1766/android-t5-placement.json
[android-t5-summary]: https://github.com/chrisbanes/haze/blob/0693289577942e0ed28328bf16066c59e61cbb58/docs/agents/evidence/1766/android-t5-summary.json
[mac-t5-summary]: https://github.com/chrisbanes/haze/blob/0693289577942e0ed28328bf16066c59e61cbb58/docs/agents/evidence/1766/mac-t5-summary.json
[manifest]: https://github.com/chrisbanes/haze/blob/0693289577942e0ed28328bf16066c59e61cbb58/docs/agents/evidence/1766/manifest.json
[shaders]: https://github.com/chrisbanes/haze/blob/0693289577942e0ed28328bf16066c59e61cbb58/docs/agents/evidence/1766/shaders.json
