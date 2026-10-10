# Benchmark results

The latest available Android measurements for each comparison are collected here. Use the
[performance guide](performance.md) to choose what to try on your screen, and the
[benchmark runbook][benchmark-runbook] to reproduce Haze's workloads. Sections use different
devices and builds: compare configurations within a table, not timings across sections.

## Reading the results

- **CPU frame duration** measures UI-thread and RenderThread cost, not GPU shader duration.
- **Frame overrun** measures how far a frame finishes past its deadline. Negative values mean
  spare time; more negative is better.
- **P90** describes the slower end of the measured frames. Negative P90 overrun does not mean
  every frame met its deadline.

These are measurements of specific sample workloads, not performance budgets for other apps.
Each section records its aggregation method and measurement conditions.

<a id="performance-mode-calibration"></a>

## Performance modes

This page reports the fixed-mode results for `Quality`, `Balanced`, and `Performance` with
stable and continuously changing source input. It used a Pixel 8a running Android 17 (full SDK 37.2)
at 60 Hz, with fixed-performance mode enabled and normal Android CPU scheduling.

Values are **P90 CPU frame duration / P90 frame overrun**, in milliseconds. Each value is the
arithmetic mean of two per-pass P90s, calculated before rounding, not the P90 of pooled frames.
Each method ran eight measured iterations per pass. Other style choices stay fixed within each
sample.

### Blur

| Workload | Quality | Balanced | Performance |
| --- | ---: | ---: | ---: |
| Stable source | 4.60 / -6.85 | 4.67 / -6.93 | 4.58 / -7.02 |
| Continuously changing source | 5.49 / -4.91 | 5.35 / -5.50 | 5.47 / -5.70 |

### Glass

| Workload | Quality | Balanced | Performance |
| --- | ---: | ---: | ---: |
| Stable source | 4.53 / -9.80 | 4.10 / -10.13 | 3.93 / -10.07 |
| Continuously changing source | 4.45 / -6.95 | 4.27 / -8.08 | 4.08 / -8.99 |

All configurations had spare time at P90. For changing-input Glass, `Balanced` left about
8.1 ms of deadline margin, compared with 7.0 ms for `Quality`. `Performance` used roughly
9% less peak GPU memory than `Quality` in both workloads. For each workload and mode, this
compares the mean of the two pass-level `memoryGpuMaxKb` medians: 100,148 KB versus 110,226 KB
for the stable source, and 100,467 KB versus 110,223 KB for the changing source.

!!! warning "These results are not a universal mode ranking"

    Reversing the run order moved some per-pass CPU P90s by more than 2 ms. Averaging the two
    passes reduces order bias, but normal Android scheduling still allowed CPU-placement and
    background-load variation. CPU frame duration also does not measure GPU shader cost. Compare
    appearance and repeat measurements on your screen before choosing an override.

??? info "Measurement details"

    The `benchmarkRelease` build used commit `b4688410`; the Android build was
    `CP41.260814.003.B1`. The device was on AC power at 100% charge and reported thermal
    status 0 before both passes. The first pass started at a battery temperature of 28.1°C;
    after cooling, the reverse pass started at 28.6°C. Automatic brightness remained enabled
    with the setting value unchanged at 10. Glass kept the other `GlassDefaults` values unchanged.

    The original calibration ran 16 methods in both orders, producing 256 measured iterations
    and 256 Perfetto traces. The first pass ran Blur stable, Blur changing, Glass stable, then
    Glass changing; each group ran the then-configured adaptive input-scale policy, Quality,
    Balanced, then Performance. This page retains the fixed-mode results only. The second pass
    reversed the complete method order.

    Every method was dry-run and measured through its own selector. JSON method identity, XML
    success, iteration count, and trace count were verified before continuing. The first reverse
    attempt failed before measurement because the secure keyguard prevented activity-launch
    confirmation. Its failed XML was preserved and excluded; the exact retry passed after the
    device was unlocked. The Google app was temporarily disabled to avoid its known Chromium
    trace-producer delay, then restored after measurement.

    Blur rows did not all record GPU memory. Raw JSON, benchmark messages, and traces are
    retained locally under `internal/benchmark/build/benchmark-results/` in
    `performance-levels-20260918-forward/` and `performance-levels-20260918-reverse/`.

    See [ADR-0004][blur-adr] and [ADR-0005][glass-adr] for adaptive-policy decisions, and
    [ADR-0006][performance-mode-adr] for the public terminology.

<a id="native-android-backdrop"></a>

## Backdrop versus Sources

Native Android Backdrop reads pixels behind the effect from the window, instead of using
captured `hazeSource` content. See [Backdrop guidance](performance.md#backdrop-input) for
its practical benefits, fallback requirements, and experimental feature flag.

This comparison used `Quality` on a Pixel 8a running Android 17 (full SDK 37.2) at 60 Hz,
with fixed-performance mode enabled and normal CPU scheduling. Each method ran eight
iterations in each of two passes, reversing both method and workload order.

Values are the **arithmetic mean of the two per-pass P90s**, not the P90 of pooled frames.

| Quality workload | Sources CPU P90 (ms) | Backdrop CPU P90 (ms) | Backdrop CPU difference | Sources overrun P90 (ms) | Backdrop overrun P90 (ms) |
| --- | ---: | ---: | ---: | ---: | ---: |
| Blur, stable input | 4.59 | 5.07 | 10% higher | -7.75 | -2.44 |
| Blur, changing input | 4.59 | 5.12 | 12% higher | -5.40 | -2.21 |
| Glass, stable input | 3.54 | 5.18 | 46% higher | -10.69 | -0.48 |
| Glass, changing input | 3.46 | 4.24 | 23% higher | -1.84 | -0.48 |
| Nine Glass nodes, changing input | 3.94 | 4.74 | 20% higher | -6.85 | -5.18 |

Backdrop had higher CPU P90 in both passes for changing-input Blur and all Glass workloads.
All rows retained negative P90 overrun, but Backdrop left less deadline margin. Stable Blur's
ranking reversed between passes, so its average does not establish a consistent difference.

!!! note "Stable input is not an idle screen"

    These workloads continuously invalidate the effect while leaving source pixels unchanged.
    They compare retained source output with repeated native Backdrop composition.
    Use the changing-input rows as the closer reference for scrolling or animated content.
    These results do not establish that Backdrop is slower on every screen.

??? info "Measurement details"

    The `benchmarkRelease` build used commit `f06cd64a`; the Android build was
    `CP41.260814.003.B1`. The device was on AC power at 100% charge. The first pass started
    at thermal status 0 and battery temperature 26.0°C; after cooling, the reverse pass
    started at thermal status 0 and 28.1°C. Brightness was unchanged and not recorded.

    Ten methods, eight iterations each, and two passes produced 160 measured iterations and
    160 Perfetto traces. The first pass ran Sources then Backdrop for each workload.
    One interrupted second-pass attempt was replaced; no completed comparison was discarded.
    The Google app was force-stopped to prevent its Chromium trace producer from adding a
    known collection delay. Table values and percentage differences were calculated before
    rounding.

    Native Backdrop trace counters were present with zero source records, confirming that
    the native rows did not measure source fallback. Backdrop also used less peak GPU memory:
    the recorded difference was approximately 6–10 MB, based on the mean of each pass's
    median `memoryGpuMaxKb` value.

    Perfetto showed scheduler variation: the mean per-iteration RenderThread time share on
    the little CPU cluster ranged from 0.9% to 49.6% across method executions. Stable Glass
    remained slower with comparable placement in the first pass and more favourable
    Backdrop placement in the reverse pass. This supports a narrower conclusion: native
    Backdrop was not a CPU performance win for this Glass scene on this device.

    Raw JSON, benchmark messages, and traces are retained locally under
    `internal/benchmark/build/pixel8a-2026-09-12-full-backdrop-2pass/`.

<a id="glass-fixed-quality"></a>

## Glass fixed quality

This comparison isolates six `qualityFraction` settings for one 280 dp × 180 dp Regular
Glass surface using `HazeInput.Sources`. It used a Pixel 6 running Android 17 (full SDK 37.1)
at 60 Hz, with fixed-performance mode enabled.

!!! warning "Controlled CPU placement"

    The sample's main thread and RenderThread were restricted to the middle CPU cluster.
    These are controlled quality comparisons, not expected timings under normal Android
    scheduling. Do not combine them with the Pixel 8a mode results above.

The table shows **changing-input** results. Each value is the arithmetic mean of two
per-pass P90s, calculated before rounding, not the P90 of pooled frames.

| `qualityFraction` | CPU frame duration: mean per-pass P90 (ms) | Frame overrun: mean per-pass P90 (ms) |
| --- | ---: | ---: |
| `0` | 3.17 | -10.95 |
| `0.25` | 3.12 | -10.17 |
| `1f / 3f` | 3.11 | -9.77 |
| `0.5` (`Balanced`) | 3.17 | -9.11 |
| `0.75` | 4.87 | -6.34 |
| `1` (`Quality`) | 4.74 | -5.53 |

For this scene, `Fixed(0.75f)` left 2.77 ms less deadline margin than `Balanced`.
No measured frame missed its deadline. These results do not establish the right setting
for Web, Clear Glass, larger or multiple surfaces, or higher refresh rates. Check both
appearance and frame timing on the actual screen under normal scheduling.

??? info "Measurement details"

    The sweep used a release build. The sample process requested affinity to CPUs 4–5;
    every Perfetto trace confirmed that main and RenderThread executed only on those CPUs
    during measurement. Other Android processes remained unrestricted.

    Six levels ran with stable and changing input, first in ascending quality order, then
    descending with workload order reversed. Eight iterations per case produced 192
    iterations and 34,753 measured frames. No completed timing iteration was discarded.
    Runs started at thermal status 0 and a battery temperature at most 30°C. Brightness
    was fixed during measurement; cooldowns dimmed the screen and disabled fixed-performance
    mode.

    The largest changing-input CPU P90 difference between passes was 0.12 ms. Stable-input
    CPU P90 ranged from 2.60 to 2.72 ms across all levels and both passes.

    An earlier normal-scheduling experiment showed CPU rankings sensitive to slower-core
    placement, including a reversed ranking when two levels were repeated. That experiment
    remains in git history. This controlled sweep also fixed brightness, so differences
    between the experiments cannot be attributed to CPU affinity alone.

<a id="blur-noise-tint"></a>

## Blur noise and tint shader reuse

On Android 13 and newer, Blur combines nonzero noise with one `SrcOver` color tint in a single
runtime shader. Animating radius, tint, or noise creates a new effect for almost every frame.
This comparison measured constructing that shader for each new effect, then compiling it once
per process and reusing it for every effect.

It used a Pixel 8a running Android 17 (full SDK 37.2) at 60 Hz, with fixed-performance mode
enabled and normal CPU scheduling. Each workload animates one property for three seconds over
a constant 240 × 180 dp source-backed area, split across one or three nodes.

Values are the **arithmetic mean of two per-pass P90s**, not the P90 of pooled frames.
Constructions and their elapsed time are per three-second measured window.

| Workload | Constructions before → after | Construction time before (ms) | CPU P90 before → after (ms) | Overrun P90 before → after (ms) |
| --- | ---: | ---: | ---: | ---: |
| Tint, 1 node | 127 → 0 | 51.1 | 5.11 → 5.29 | -7.46 → -7.70 |
| Tint, 3 nodes | 381 → 0 | 99.4 | 5.71 → 4.75 | -6.22 → -7.07 |
| Radius, 1 node | 180 → 0 | 70.1 | 5.53 → 5.01 | -7.78 → -8.31 |
| Radius, 3 nodes | 540 → 0 | 134.2 | 6.61 → 5.75 | -7.03 → -7.77 |
| Noise, 1 node | 180 → 0 | 77.6 | 6.66 → 6.26 | -6.56 → -7.00 |
| Noise, 3 nodes | 540 → 0 | 132.5 | 5.97 → 5.38 | -6.01 → -6.30 |

Reuse removed every warm construction. Before, each new effect constructed the shader on the
main thread, taking roughly 0.25–0.45 ms each. A fresh process still constructs it once on
first use. Stable styles constructed nothing in either build.

CPU frame-duration changes are inconclusive. The two passes of one build differed by up to
1.47 ms in CPU P90, more than any before/after difference in the table. Three-node rows also
ran with different CPU placement in each build, in both run orders, so their P90 differences
cannot be attributed to shader reuse alone. Neither build missed its deadline at P90 in these
workloads.

??? info "Measurement details"

    The `benchmarkRelease` builds used commits `0f81d5f4` (before) and `92168671` (after) with
    an identical benchmark APK; the Android build was `CP41.260831.007.A3`. The device was on
    AC power at 90% charge. Every method started at thermal status 0, with the big CPU at most
    34°C and the battery at most 28.5°C. Brightness was unchanged and not recorded.

    Ten methods (the six rows above, stable one- and three-node controls, and one- and
    three-node process-cold launches) ran eight measured iterations in four passes: before
    then after, then after then before with method order reversed. This produced 320
    iterations and 320 Perfetto traces. No completed iteration was discarded. The Google app
    was temporarily disabled to prevent its Chromium trace producer from delaying collection.

    Construction counts come from a `HazeRuntimeShader.construct` section nested in
    `HazeBlur.combinedNoiseTint` in the sample process. Its duration measures CPU-side
    construction, not backend or GPU shader compilation; GPU timings were not analyzed.

    Without reuse, three-node main-thread and RenderThread time ran about 97% on the middle
    CPU cluster. With reuse, more main-thread time ran on the little cluster and 22–30% of
    RenderThread time ran on the big core. Main-thread running time still fell by 58–90 ms per
    window for three nodes. Process-cold launches exceeded their deadline at P90 in both
    builds; their mean CPU P90 differed by less than 1 ms, passes differed by up to 3.49 ms,
    and the ranking varied between passes.

    Raw JSON, benchmark messages, traces, frozen APKs, and per-method device readbacks are
    retained locally under `internal/benchmark/build/benchmark-results/noise-tint.eO1A65/`.

<a id="placement-update-deduplication"></a>

## Placement update deduplication

When an effect's size changed, Haze updated the effect twice in the same frame: once when Compose
placed the node, and again when it reported the node's global position. The second callback now
skips its update if the first already applied the same position, size, and transform and its source
reads completed ([#1437](https://github.com/chrisbanes/haze/issues/1437)). This comparison counts
`HazeEffectNode-updateEffect` trace slices before and after that change in four Glass profiling
workloads.

It used a Pixel 8a running Android 17 (full SDK 37.2) at 60 Hz, with fixed-performance mode
enabled and normal CPU scheduling. Each workload resizes one or nine Glass effects over a stationary
source for three seconds; `shapeUpdateBalanced` also animates the corner radii with the size. See
the [controlled scenario baseline][benchmark-runbook-scenarios] for the exact workloads. Each
method ran eight iterations in each of two passes, in opposite build order.

Values are **before → after**. Update counts pool both passes (16 iterations per build). Frame
timings are the **arithmetic mean of two per-pass P90s**, not the P90 of pooled frames.

| Workload | Effects | `updateEffect` per effect per frame | per animating frame | CPU P90 (ms) | Overrun P90 (ms) |
| --- | ---: | ---: | ---: | ---: | ---: |
| `resizeQuality` | 1 | 1.90 → 0.96 | 1.99 → 1.00 | 5.35 → 5.40 | -6.14 → -6.17 |
| `resizeQuality9` | 9 | 1.84 → 0.93 | 1.99 → 1.00 | 12.72 → 12.53 | 0.58 → 0.62 |
| `shapeUpdateBalanced` | 1 | 2.81 → 1.91 | 2.98 → 1.99 | 5.47 → 5.38 | -6.83 → -7.06 |
| `shapeUpdateBalanced9` | 9 | 2.76 → 1.85 | 2.99 → 1.99 | 12.94 → 12.75 | 0.47 → 0.24 |

"Per frame" counts every main-thread frame in the measured window, including idle frames at its
edges. "Per animating frame" counts only frames that ran at least one update.

The change removed one update from each animating frame. The resize workloads went from two
updates per effect to one. The `shapeUpdateBalanced` workloads went from about three to about two.
In both builds, the remaining update in those workloads ran inside `Recomposer:animation` instead
of during placement. That is consistent with the single update Glass runs when state read by its
style changes, here the animated corner radius. A style-only frame already ran one update after
[#1434](https://github.com/chrisbanes/haze/pull/1434) and
[#1439](https://github.com/chrisbanes/haze/pull/1439), and this change does not merge that update
with the placement update. The traces show where the update ran, not which code path requested
it, so treat that attribution as likely rather than proven.

The measurements show fewer updates, not a frame-time change. Build differences were at most
0.19 ms in CPU P90 and 0.23 ms in overrun P90, while the two passes of the same build differed by
up to 0.42 ms and 0.50 ms. Both nine-effect workloads missed the frame deadline at P90 (positive
overrun) in both builds. CPU frame duration does not measure GPU cost.

??? info "Measurement details"

    The `benchmarkRelease` builds used commits `903b02e9` (before) and `1f1c8a3a` (after); the
    Android build was `CP41.260831.007.A3`. The commits differ only in `HazeEffectNode` and its
    tests. `internal/benchmark` and `sample` are identical, and both builds produced a
    byte-identical benchmark APK. The published pull request head adds a follow-up commit,
    `f745384f`. It only makes the skip rarer when sources join during layout and releases
    references. It does not affect these workloads, whose sources are stationary and attached from
    the start, and it was not measured.

    The device was on AC power at 90% charge, and every readback reported thermal status 0. Before
    each measured run, all CPU sensors read 35°C or less and the battery 28.4°C or less; the
    runs waited until the CPU sensors read at most 33°C and the battery at most 28.5°C. The
    hottest CPU reading after a run was 48°C. The display stayed in its 60 Hz mode, and automatic
    brightness stayed enabled with the setting unchanged at 2. Fixed-performance mode pinned each
    CPU cluster to its maximum frequency during the passes. Force-stopping the Google app did not
    stop it from restarting immediately, so it was temporarily disabled to avoid its Chromium
    trace-producer delay. Fixed-performance mode was then turned off and the Google app was
    re-enabled.

    Four workloads, two builds, two passes, and eight iterations produced 128 measured iterations
    and 128 Perfetto traces. No completed iteration was discarded. Each method was dry-run on
    both builds and measured through its own selector. JSON method identity, XML success,
    iteration count, and trace count were verified before continuing. Pass 1 ran the before build
    and then the after build, in the order shown above. Pass 2 reversed both the build order and
    the method order.

    Per-pass CPU P90 / overrun P90, in milliseconds:

    | Workload | Build | Pass 1 | Pass 2 |
    | --- | --- | ---: | ---: |
    | `resizeQuality` | before | 5.40 / -6.23 | 5.30 / -6.05 |
    | | after | 5.61 / -6.04 | 5.19 / -6.31 |
    | `resizeQuality9` | before | 12.84 / 0.61 | 12.59 / 0.55 |
    | | after | 12.43 / 0.47 | 12.64 / 0.78 |
    | `shapeUpdateBalanced` | before | 5.46 / -7.08 | 5.47 / -6.58 |
    | | after | 5.27 / -7.16 | 5.49 / -6.95 |
    | `shapeUpdateBalanced9` | before | 12.87 / 0.29 | 13.02 / 0.65 |
    | | after | 12.80 / 0.39 | 12.70 / 0.09 |

    Each measured window is the benchmark's `measureBlock` slice, about 3.3 s long. Updates are the
    `HazeEffectNode-updateEffect` slices that start in that window in the sample process. Frames are
    its main-thread `Choreographer#doFrame` slices. Animating frames are the frames that contain at
    least one update. Totals over both passes:

    | Workload | Build | Updates | Frames | Animating frames |
    | --- | --- | ---: | ---: | ---: |
    | `resizeQuality` | before | 5,772 | 3,039 | 2,899 |
    | | after | 2,897 | 3,015 | 2,897 |
    | `resizeQuality9` | before | 51,942 | 3,136 | 2,897 |
    | | after | 26,082 | 3,132 | 2,897 |
    | `shapeUpdateBalanced` | before | 8,648 | 3,076 | 2,899 |
    | | after | 5,776 | 3,020 | 2,896 |
    | `shapeUpdateBalanced9` | before | 77,823 | 3,129 | 2,896 |
    | | after | 51,984 | 3,130 | 2,896 |

    The name of a slice's parent shows where each update ran. Before the change, each animating
    frame had one update inside `AndroidOwner:measureAndLayout` (placement) and one inside
    `Compose:onPositionedCallbacks` (the global-position callback). After it, the second one fell
    to fewer than two per effect per iteration. These counts add up to the update totals above.

    | Workload | Build | `measureAndLayout` | `onPositionedCallbacks` | `Recomposer:animation` |
    | --- | --- | ---: | ---: | ---: |
    | `resizeQuality` | before | 2,873 | 2,899 | 0 |
    | | after | 2,875 | 22 | 0 |
    | `resizeQuality9` | before | 25,866 | 26,076 | 0 |
    | | after | 25,884 | 198 | 0 |
    | `shapeUpdateBalanced` | before | 2,869 | 2,899 | 2,880 |
    | | after | 2,874 | 22 | 2,880 |
    | `shapeUpdateBalanced9` | before | 25,839 | 26,064 | 25,920 |
    | | after | 25,812 | 252 | 25,920 |

    Perfetto scheduler data showed the main thread and RenderThread running on the middle CPU
    cluster (CPUs 4–7) for most of the window in every run. The one-effect workloads sometimes ran
    more on the little cluster (CPUs 0–3). An iteration counts as slow-core if either thread spent
    more than 25% of its running time in the window on the little cluster. Before the change, 2 of
    16 `resizeQuality` and 2 of 16 `shapeUpdateBalanced` iterations were slow-core; after it, 4 and
    3. No nine-effect iteration was. Excluding slow-core iterations, the pooled-frame CPU P90 moved
    from 5.23 to 5.20 ms for `resizeQuality` (14 and 12 iterations) and from 5.43 to 5.26 ms for
    `shapeUpdateBalanced` (14 and 13 iterations), which is also within the pass-to-pass variation.
    The table above includes slow-core iterations.

    Two passes per build is a small sample. Update counts were stable across iterations and passes
    (for example, 0.92–0.93 per effect per frame for `resizeQuality9` after the change), but frame
    timings varied more between passes than between builds. Resolving a difference smaller than
    about 0.5 ms would need more passes, again in alternating order.

    Raw JSON, benchmark messages, XML, and traces are retained locally, outside the repository,
    under `~/dev/haze-worktrees/1437-artifacts/`: `before-903b02e9/` and `after-1f1c8a3a/`, each
    with `pass1/` and `pass2/`. That directory also holds the dry-run XML, per-run device
    readbacks, the two builds' APK hashes, and the trace-analysis scripts.

## Representative workloads

These Glass sample measurements cover paging, an animated timeline, and steady-state scenes
with different effect counts. They used a Pixel 6 running Android 17/API 37 at 60 Hz, with
locked CPU frequency and eight iterations per scenario.

| Scenario | Workload | P90 CPU frame duration |
| --- | --- | ---: |
| `productPager` | Gallery paging journey | 7.5 ms |
| `playgroundTimeline` | Gallery animated timeline journey | 11.2 ms |
| `steadyFull` | Controlled steady state, 1 Glass effect | 5.6 ms |
| `steadyFull3` | Controlled steady state, 3 Glass effects | 5.2 ms |
| `steadyFull9` | Controlled steady state, 9 Glass effects | 8.1 ms |

The Gallery journeys are closer to visible user interactions than the steady-state controls.
The effect-count rows are separate workload measurements, not a per-effect cost formula:
three effects were not slower than one in this run. Measure the content and interactions your
application actually uses.

??? info "Measurement details"

    The `benchmarkRelease` build used commit `334557df`. Display resolution was 1080×2400
    and render rate was locked to 60 Hz.

    Cold-initialization `effectAttach*` results are omitted because they attach effects at
    the measurement boundary and diagnose delegate/shader creation rather than representative
    interactions. They remain covered by the [benchmark runbook][benchmark-runbook].

For the historical scrolling and dragging comparison between Haze 1 and Haze 2, see the
[migration guide](migrating-2.0.md#performance-compared-with-haze-1).

[benchmark-runbook]: https://github.com/chrisbanes/haze/blob/main/internal/benchmark/README.md
[benchmark-runbook-scenarios]: https://github.com/chrisbanes/haze/blob/main/internal/benchmark/README.md#controlled-scenario-baseline
[blur-adr]: adr/0004-use-quality-gated-adaptive-input-scaling-for-blur.md
[glass-adr]: adr/0005-use-cadence-weighted-adaptive-input-scaling-for-glass.md
[performance-mode-adr]: adr/0006-reconcile-built-in-performance-mode-terminology.md
