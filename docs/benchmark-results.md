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

These are the latest source-backed results for `Quality`, `Balanced`, and `Performance` with
stable and continuously changing source input. They used a Pixel 8a running Android 17 (full SDK
37.2) at 60 Hz, with fixed-performance mode enabled and normal Android CPU scheduling.

Values are **P90 CPU frame duration / P90 frame overrun**, in milliseconds, as reported by
AndroidX for one pass of eight measured iterations per method. These are single-pass values,
not averages across passes. Other style choices stay fixed within each sample.

### Blur

| Workload | Quality | Balanced | Performance |
| --- | ---: | ---: | ---: |
| Stable source | 4.74 / -9.09 | 4.78 / -9.15 | 4.83 / -9.07 |
| Continuously changing source | 5.10 / -6.10 | 4.98 / -6.80 | 5.35 / -7.44 |

### Glass

| Workload | Quality | Balanced | Performance |
| --- | ---: | ---: | ---: |
| Stable source | 4.43 / -10.18 | 4.31 / -10.28 | 4.44 / -10.18 |
| Continuously changing source | 4.51 / -6.63 | 5.02 / -7.76 | 5.03 / -8.87 |

All configurations had spare time at P90. For changing-input Glass, `Balanced` left about
7.8 ms of deadline margin, compared with 6.6 ms for `Quality`. `Performance` used roughly
9% less peak GPU memory than `Quality` in both workloads. This compares each method's
`memoryGpuMaxKb` median: 96,460 KB versus 106,532 KB for the stable source, and 96,660 KB
versus 106,532 KB for the changing source. CPU frame duration did not follow one mode ordering
across workloads.

!!! warning "Single-pass results are not a universal mode ranking"

    The complete mode matrix ran once. Normal-scheduling repeats of Blur stable `Balanced`
    and Glass changing `Balanced` recorded CPU P90s of 4.76 ms and 4.94 ms, compared with
    4.78 ms and 5.02 ms in the table. Other modes were not repeated on this build.
    Perfetto showed different CPU-placement mixes between passes; fixed-performance mode
    does not fix CPU placement. CPU frame duration also does not measure GPU shader cost.
    Repeat with reversed run order and check appearance on your screen before choosing an override.

??? info "Measurement details"

    The measured code and regenerated 3,043-rule baseline profile match commit `68439df3`.
    Both target and benchmark used `benchmarkRelease`; the target APK was minified. The sample
    APK SHA-256 was `212441675ec83042accef15b823e0ec48bb6880186eba35d54e86cf202e407ef`
    and the benchmark APK `4fff9aa2c5650d09292584aafeea828519441790ad7cf54a0333f2be13f96e9b`.
    The Android build was `CP41.260831.016`. The device was on AC power at 80% charge,
    with manual brightness 32. Each invocation began at thermal status 0 and battery
    temperature at or below 30.0°C; after-run thermal status was also 0.

    The twelve methods ran Blur stable, Blur changing, Glass stable, then Glass changing;
    each group ran Quality, Balanced, then Performance. Each method ran in its own invocation.
    The table's complete pass produced 96 measured iterations and 96 Perfetto traces. JSON
    method identity, XML success, eight metric runs, and eight distinct non-empty trace
    references were verified for every method. Slower completed iterations were retained.

    This pass was part of a release-regression investigation with 256 accepted iterations and
    256 traces across both builds and follow-ups. Only the current build's normal-scheduling
    mode pass supplies the tables above. Two selected methods received normal-scheduling
    repeats; changing-input Balanced Glass also received two passes restricted to the middle
    CPU cluster, with main-thread and RenderThread placement verified in all 16 current-build
    traces. Those controlled results are excluded from these tables.

    An incomplete attempt with a Google-app Chromium trace-producer delay was preserved
    separately and excluded. The Google app was temporarily disabled for accepted measurements.
    Its original enabled state, refresh settings, brightness, and stay-awake setting were
    restored and read back afterward. Fixed-performance mode was disabled and the sample
    processes stopped, clearing temporary affinity.

    Blur rows did not all record GPU memory. Raw JSON, XML, benchmark messages, APK hashes,
    and traces are retained locally under
    `internal/benchmark/build/benchmark-results/regression-2.0.1-20261020/`.
    `candidate-forward/` contains the complete mode pass; `candidate-repeat-normal/` contains
    the two normal-scheduling repeats. The report and `validation.json` record the full
    experiment and excluded attempts.

    See [ADR-0004][blur-adr] and [ADR-0005][glass-adr] for adaptive-policy decisions, and
    [ADR-0006][performance-mode-adr] for the public terminology.

<a id="native-android-backdrop"></a>

## Backdrop versus Sources

Native Android Backdrop reads pixels behind the effect from the window, instead of using
captured [`hazeSource`][hazeSource] content. See [Backdrop guidance](performance.md#backdrop-input) for
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
Glass surface using [`HazeInput.Sources`][HazeInput.Sources]. It used a Pixel 6 running Android 17 (full SDK 37.1)
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

<a id="glass-containers"></a>

## Glass containers

These comparisons measure `hazeGlassContainer` against independent Glass surfaces on a Pixel 8a
running Android 17 (full SDK 37.2) at 60 Hz, with fixed-performance mode enabled and normal CPU
scheduling. Every row uses [`HazePerformanceMode.Default`][HazePerformanceMode.Default],
`HazeInput.Sources` and the Regular style, and runs eight measured iterations per pass. The steady
and animated comparisons use a continuously changing source; the lazy-scroll and first-appearance
rows use a stationary source. Each per-pass value pools the frames of that pass's eight iterations;
the tables show the **arithmetic mean of the two per-pass values**. Peak GPU memory uses each
pass's median `memoryGpuMaxKb`. A second pass ran every method in the reverse order of the first.

### Two members: merged against independent

The independent row draws two Regular Glass surfaces, each about 134 × 180 dp and 12 dp apart,
separately. The container row wraps the same surfaces in `hazeGlassContainer(mergeDistance =
32.dp)`, so they merge into one cluster.

| Row | CPU frame duration P50 (ms) | CPU frame duration P90 (ms) | Frame overrun P90 (ms) | Peak GPU memory (KB) |
| --- | ---: | ---: | ---: | ---: |
| Independent surfaces | 2.70 | 4.62 | -7.18 | 99,476 |
| One container cluster | 2.64 | 4.70 | -2.54 | 113,264 |

HWUI's `drawLayer [graphicsLayer]` slice shows what each row drew: 2.00–2.01 layers per frame for
the independent surfaces and 1.00–1.01 for the cluster, which draws one layer that covers both
members and the bridge between them.

CPU frame duration P90 was 0.08 ms higher for the container, within the 0.13 ms spread between the
independent row's passes. The container row finished about 4.6 ms closer to the frame deadline at P90 and used 13,788 KB (about 14%) more peak GPU
memory. CPU frame duration does not measure GPU shader cost. These runs did not measure GPU
timings, so they do not show why the container finished closer to its deadline. Neither row missed
its deadline at P90.

### Animated and resting container workloads

Each row below changes one variable from the 12 dp container row above, except the three-member
group and join and leave. The group has a different member count. Join and leave uses 60 × 120 dp
members, against about 134 × 180 dp for the others, and varies their count from two to four. They
share its Glass settings and changing source.

| Workload | What changes | CPU frame duration P90 (ms) | Per-pass CPU P90 (ms) | Frame overrun P90 (ms) | Peak GPU memory (KB) |
| --- | --- | ---: | --- | ---: | ---: |
| Resting gap | Two members rest 24 dp apart, between half and the full merge distance | 4.70 | 4.76, 4.65 | -1.17 | 113,264 |
| Group | Three members 48 dp apart, beyond the merge distance, share one `groupKey` | 4.58 | 4.62, 4.55 | -2.36 | 111,000 |
| Press | The first member is pressed and released three times | 6.19 | 5.79, 6.59 | -1.60 | 113,484 |
| Join and leave | Two to four 60 × 120 dp members; one joins or leaves every half second | 5.30 | 5.61, 5.00 | -2.15 | 117,102 |

The 12 dp container row's CPU frame duration P90 was 4.70 ms (4.69 and 4.72 ms per pass), with
-2.54 ms frame overrun P90. Every row drew about one Glass layer per frame (0.99–1.02).

- A resting gap between half and the full merge distance matched the 12 dp pair's CPU frame
  duration and peak GPU memory, but finished about 1.4 ms closer to the deadline at P90. The two
  passes agreed within 0.01 ms.
- The three-member group matched the two-member container within pass spread.
- Pressing a merged member raised CPU frame duration P90 by about 1.5 ms. The join and leave
  workload raised it by about 0.6 ms, and its P99 was 10.1 ms against 7.1 ms. Both rows were above
  the container row in both passes, but their passes differed by 0.8 ms and 0.6 ms, so treat the
  size as approximate.
- The join and leave workload used 3,838 KB (about 3%) more peak GPU memory than the two-member
  container. Its member size and count differ from the control, so this compares workloads and does
  not isolate the cost of membership changes.

CPU placement differed in these two rows. The main thread spent 42–71% of its running time on the
little cluster in the press and join-and-leave rows, against 81–86% in the other two-member rows,
so part of their CPU difference may come from where the threads ran rather than from the workload.

<a id="glass-container-lazy-scroll"></a>

### Lazy container scroll

This comparison scrolls a five-column `LazyVerticalGrid` with 40–45 visible members and a 12 dp gap
over a stationary source. The container row wraps the grid in
`hazeGlassContainer(mergeDistance = 16.dp, scrollState = gridState)`, so adjacent members join
clusters of at most four members. The independent row draws the same members as separate Glass
surfaces, with no container.

| Row | CPU frame duration P50 (ms) | CPU frame duration P90 (ms) | CPU frame duration P99 (ms) | Frame overrun P50 (ms) | Frame overrun P90 (ms) | Peak GPU memory (KB) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Independent members | 16.5 | 25.3 | 31.7 | 7.8 | 17.2 | 159,230 |
| Container | 24.7 | 32.6 | 36.9 | 16.6 | 25.1 | 179,448 |

The container ranked higher in CPU frame duration P90 in both passes, at 33.3 and 31.9 ms against
22.1 and 28.5 ms for the independent members. The gap was 11.2 ms in the first pass and 3.4 ms in
the second, so treat the 7.3 ms mean as approximate. Peak GPU memory was about 13% higher.

Mean main-thread running time inside the measured window was about 1,440 ms for the container and
1,180 ms for the independent members, a 22% increase. RenderThread running time was similar, at
about 2,020 ms and 2,040 ms. Both rows ran the RenderThread mostly (79–84%) on the big core and
the main thread almost entirely on the middle and big cores (36–43% on the big core). Placement
was not controlled.

Neither row met the 60 Hz deadline: the median frame overran in both. These runs did not measure
GPU timings, so they do not show where the remaining frame time goes. The result describes this
deliberately dense scene, not a typical container.

### First appearance

The cold-initialization rows attach the Glass at the start of the measured window. The control
attaches one independent surface, and the container row attaches a two-member container, so the
member counts differ.

| Row | CPU frame duration P50 (ms) | CPU frame duration P90 (ms) | Frame overrun P90 (ms) | Peak GPU memory (KB) |
| --- | ---: | ---: | ---: | ---: |
| One independent surface | 4.64 | 8.02 | -6.00 | 95,028 |
| Two-member container | 6.31 | 9.95 | -1.79 | 107,791 |

Each pass recorded only 10 frames across its eight iterations, so these percentiles come from 20
frames per row and are indicative only. The container had a CPU frame duration P50 about 1.7 ms
higher and about 4.2 ms less deadline margin at P90. Neither row missed its deadline at P90.

These results describe a few two- and three-member layouts and one dense grid on one device. They
do not establish the cost of other cluster sizes, other styles, or higher refresh rates. Measure
clustered screens against independent surfaces before adopting a container.

??? info "Measurement details"

    The `benchmarkRelease` APKs were built from commit `a4803f6a1` (`2.1.0-SNAPSHOT`). The sample
    APK SHA-256 was `3936fc8d6608d53a980662f965faf1cbf5cd8b330dcf76b7846712ccb8f061d5` and the
    benchmark APK SHA-256 was
    `ff92fbf3289f15c5ddf42ff32da3ef5c1e9b44f5e80da987ca6106c8e1aeaf2f`. The Android build was
    `CP41.260831.019`. The device was on AC power and reported thermal status 0 throughout.

    The second pass ran the first pass's ten methods in exactly the opposite order. Each method ran
    eight measured iterations in its own Gradle invocation, which produced 160 iterations and 160
    Perfetto traces. No completed iteration was discarded. The per-frame layer gates passed on the
    two-member, resting-gap, group and press rows. The Google app was temporarily disabled to
    prevent its Chromium trace producer from delaying collection.

    CPU placement was not controlled. In the steady two-member rows the main thread ran 81–86% on
    the little cluster. The RenderThread ran 11–23% on the big core in every container row and
    27–31% in the independent row, with similar total running time (352–371 ms). Two lazy-scroll
    traces had no scheduler data and are excluded from the running-time means.

    Raw JSON, traces and device readbacks are retained locally under
    `internal/benchmark/build/benchmark-results/container-2.1.9TQlOX/`.

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

<a id="input-sharing"></a>

## Input sharing

This comparison measures [`HazeInputSharing`](performance.md#input-sharing) on two nested
`hazeSource` modifiers. An outer host source fills the screen and contains an inner feed source:
a `LazyColumn` of full-width cards with artwork, text and translucent chips, where every third
card animates like a playing video and every fourth card is faded. Four 40–44 dp controls in a
header sample the feed, and two 40 dp controls at the bottom sample the host. Every control sits
outside both captured subtrees. The video rows animate the video cards; the scroll rows also
scroll the feed at a fixed speed. Each row draws Glass or Blur controls.

Within a row the content, geometry, animation, styles and duration are identical. Only the two
sharing policies change: neither source shares (`Never`/`Never`), only the feed shares
(`Auto`/`Never`), or both share (`Auto`/`Auto`). The Pixel 8a ran Android 17 at 60 Hz with
fixed-performance mode enabled and normal CPU scheduling.

Each value is the **arithmetic mean of a forward and a reverse pass** of eight measured
iterations per method. Within a pass, CPU P90 and frame overrun P90 come from the pooled frames of
its eight iterations, so the means are not percentiles of all frames. Peak GPU memory is the median
of the per-iteration `memoryGpuMaxKb`, in MiB. Replays per frame, feed then host, come from the replay oracle in the measurement
details.

| Workload | Controls | Sharing | Replays per frame | CPU P90 (ms) | Overrun P90 (ms) | `DrawFrames` avg (ms) | Peak GPU (MiB) |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| Video | Glass | Neither | 7, 3 | 7.05 | -4.77 | 3.56 | 115.6 |
| Video | Glass | Feed only | 1, 3 | 6.62 | -5.61 | 3.29 | 148.7 |
| Video | Glass | Both | 1, 1 | 6.73 | -5.15 | 3.37 | 181.5 |
| Video | Blur | Neither | 7, 3 | 6.84 | -5.63 | 3.40 | 113.1 |
| Video | Blur | Feed only | 1, 3 | 6.69 | -6.33 | 3.17 | 146.0 |
| Video | Blur | Both | 1, 1 | 6.61 | -5.87 | 3.30 | 177.9 |
| Scroll | Glass | Neither | 7, 3 | 8.18 | -3.55 | 3.99 | 142.2 |
| Scroll | Glass | Feed only | 1, 3 | 7.68 | -4.53 | 3.73 | 173.1 |
| Scroll | Glass | Both | 1, 1 | 7.99 | -4.04 | 3.87 | 198.0 |
| Scroll | Blur | Neither | 7, 3 | 7.80 | -4.72 | 3.83 | 141.8 |
| Scroll | Blur | Feed only | 1, 3 | 7.41 | -5.52 | 3.60 | 174.7 |
| Scroll | Blur | Both | 1, 1 | 7.67 | -4.78 | 3.75 | 199.5 |

The replay counts matched the intended topology in every one of the 192 measured iterations.
With only the feed sharing, the feed's display list was replayed once per frame instead of seven
times. With both sharing, the host's replay drew the feed's texture, so the feed was still replayed
once and the host once.

Feed-only sharing had the lowest `DrawFrames` average and frame overrun in all four rows, and the
lowest CPU P90 in three. Against `Neither`, it lowered CPU P90 by 0.15–0.50 ms (2–6%) and the
`DrawFrames` average by 0.23–0.27 ms (6–8%). Sharing both sources also beat `Neither` in every
row, but by less than feed-only sharing, except for CPU P90 in the Blur video row, where it was
0.08 ms lower than feed-only, inside the pass-to-pass variation. In the scroll rows, `Both` was
only 0.13–0.19 ms below `Neither` on CPU P90. This comparison does not show why sharing the host
as well gained less.

Each active full-screen source added memory: the feed raised peak GPU memory by 31–33 MiB, and the
host a further 25–33 MiB.

These are small differences on one device and one synthetic feed. The CPU P90 gains are similar in
size to the pass-to-pass variation (up to 0.42 ms), so a repeat could change the ranking of close
rows. CPU frame duration measures UI-thread and RenderThread cost, not GPU shader time, and no GPU
timing was recorded, so these results do not show how much GPU work sharing adds or saves. They
also say nothing about sources that are more expensive to replay than this feed, or about a screen
whose RenderThread is already close to its budget. Measure a source before opting it in.

??? info "Measurement details"

    **Builds.** The same two `benchmarkRelease` APKs ran every invocation. They were built from code at
    commit `cb12b9e35`, with the runbook at commit `0d3c1eb0a`. The sample APK
    SHA-256 was `c2ee22c206113120eccb5ff3de3ce2cbb194c5e8092dfc368614fc4d50321349` and the
    benchmark APK SHA-256 was
    `04c07f07c1840afb96a0e71bb151938cf683a582eaa17537c134b36e9865d2a2`. The device was a Pixel 8a
    running `google/akita_beta/akita:17/CP41.260831.016/16521119` (API 37).

    **Run order.** Each method ran eight measured iterations in its own Gradle invocation, using
    `GlassFeedBenchmark`. The forward pass ran the twelve methods in the order Glass video, Glass
    scroll, Blur video, Blur scroll, each as `Neither`, `Feed only`, `Both`. The reverse pass ran
    them in exactly the opposite order. That is 24 invocations, 192 iterations, 192 Perfetto
    traces and 34,558 measured frames (180 per iteration, except four iterations that
    differed by one frame). No completed iteration was discarded. One invocation, forward
    `feedVideoGlassNestedUnshared`, was set aside and rerun after the Google app's trace producer
    timed out (see conditions); the original is kept separately.

    **Aggregation.** The table averages the two per-pass values; a mean of per-pass P90s is not a
    pooled percentile. The per-pass values (forward / reverse) were:

    | Workload | Controls | Sharing | CPU P90 (ms) | Overrun P90 (ms) | `DrawFrames` avg (ms) | Peak GPU (MiB) |
    | --- | --- | --- | ---: | ---: | ---: | ---: |
    | Video | Glass | Neither | 7.12 / 6.97 | -4.62 / -4.92 | 3.59 / 3.52 | 115.7 / 115.6 |
    | Video | Glass | Feed only | 6.68 / 6.56 | -5.58 / -5.64 | 3.36 / 3.22 | 148.5 / 148.8 |
    | Video | Glass | Both | 6.57 / 6.89 | -5.23 / -5.07 | 3.34 / 3.41 | 181.4 / 181.5 |
    | Video | Blur | Neither | 6.81 / 6.87 | -5.59 / -5.67 | 3.34 / 3.47 | 113.1 / 113.1 |
    | Video | Blur | Feed only | 6.48 / 6.90 | -6.53 / -6.13 | 3.18 / 3.16 | 146.0 / 146.0 |
    | Video | Blur | Both | 6.50 / 6.72 | -5.88 / -5.85 | 3.29 / 3.30 | 178.9 / 176.9 |
    | Scroll | Glass | Neither | 8.18 / 8.19 | -3.61 / -3.50 | 3.96 / 4.02 | 142.2 / 142.3 |
    | Scroll | Glass | Feed only | 7.63 / 7.74 | -4.46 / -4.59 | 3.70 / 3.75 | 173.1 / 173.1 |
    | Scroll | Glass | Both | 8.01 / 7.97 | -3.98 / -4.10 | 3.86 / 3.88 | 198.1 / 198.0 |
    | Scroll | Blur | Neither | 7.75 / 7.85 | -4.83 / -4.61 | 3.78 / 3.89 | 141.7 / 141.9 |
    | Scroll | Blur | Feed only | 7.42 / 7.40 | -5.43 / -5.61 | 3.57 / 3.63 | 174.8 / 174.7 |
    | Scroll | Blur | Both | 7.67 / 7.68 | -4.73 / -4.83 | 3.77 / 3.72 | 199.5 / 199.5 |

    Between the two passes, the largest differences were 0.42 ms in CPU P90, 0.40 ms in frame
    overrun P90, 0.14 ms in the `DrawFrames` average and 2.0 MiB in peak GPU memory. Feed-only
    sharing had lower CPU P90 than `Neither` in seven of the eight pass-level comparisons (Blur
    video in the reverse pass was 0.03 ms higher), and lower `DrawFrames` average and frame overrun
    in all eight.

    **Device conditions.** The device was on AC power at 80% charge, with a fixed 60 Hz display
    (`peak_refresh_rate` and `min_refresh_rate` both 60) and fixed-performance mode enabled. Thermal status was 0 at the
    start of each pass and after the session; the battery temperature rose from 28.0 to 33.7 °C
    over the session. The first invocation hit a Perfetto `track_event` producer timeout caused by
    the Google app, so the Google app was disabled with `pm disable-user` for the rest of the
    session, following the runbook's Android 17 fallback. Afterwards, fixed-performance mode and
    the refresh-rate settings were reset and the Google app re-enabled. A readback confirmed the refresh-rate settings and the
    Google app; fixed-performance mode has no getter.

    **Replay oracle.** Source replays are counted in the Perfetto traces, not from application
    recording. Each source holds a fixed alpha marker that is replayed with the source. A replay of
    the feed's display list emits one RenderThread slice named `alpha caused saveLayer 494x105`,
    and a replay of the host's emits `alpha caused saveLayer 231x105`. The `oracle.sql` query
    counted both inside each `DrawFrames` slice in the measured window, over 34,849 frames. In
    every iteration the most common frame was 7 feed and 3 host replays for `Neither`, 1 and 3 for
    `Feed only`, and 1 and 1 for `Both`, and 180 frames matched it. The other 289 frames in total,
    one to three per trace, replayed fewer markers. The [runbook](https://github.com/chrisbanes/haze/blob/main/internal/benchmark/README.md#nested-input-sharing-comparison)
    has the query and its calibration.

    **CPU placement.** `placement.sql` summed the running time of the main thread and RenderThread
    on each CPU during the measured window. Fixed-performance mode does not pin threads to cores.
    The RenderThread spent 49–97% of its running time on its most-used cluster, 51–62% in most
    iterations. That was the middle cluster (CPUs 4–7) in 159 of 192 iterations and the big core
    (CPU 8) in the other 33: 15 of 64 `Neither` iterations, 5 of 64 `Feed only` and 13 of 64
    `Both`. The main thread's most-used cluster was the middle cluster in 184 iterations, taking
    89% of its running time on average, and the little cluster (CPUs 0–3) in the other 8. Placement was not
    controlled and differed between configurations, so it may have contributed to the differences
    in the table; the iterations were not split by placement.

    **Artifacts.** Raw benchmark JSON and messages, XML, Perfetto traces, per-method device
    readbacks and oracle and placement outputs are retained locally, not published, under
    `internal/benchmark/build/benchmark-results/input-sharing-1615.cb12b9e35/`, with the two
    APKs, `conditions.txt`, `run-order.txt` and `summary.md`, which records 192 traces
    verified.

    **Reporter's real-app retest.** The report in
    [#1615](https://github.com/chrisbanes/haze/issues/1615) came from a different app and device.
    These results come from the benchmark app, not that app, so they neither confirm nor
    contradict its findings.

    **Earlier results.** Input-sharing results measured before #1615 described a separate
    rendered copy of the source, which retained a second replay of it, and are no longer
    published. The earlier global offscreen flag from
    [#1611](https://github.com/chrisbanes/haze/issues/1611) was not equivalent to that copy, and
    neither is evidence for the current implementation. The flat, single-source feed scenarios
    have not been re-measured on it. The superseded tables remain in git history.

<a id="placement-update-deduplication"></a>

## Placement update deduplication

When an effect's size changed, Haze updated the effect twice in the same frame: once when Compose
placed the node, and again when it reported the node's global position. The second callback now
skips its update if the first already applied the same position, size, and transform, its source
reads completed, and its sources haven't changed since
([#1437](https://github.com/chrisbanes/haze/issues/1437)). This comparison counts
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
    byte-identical benchmark APK. The merged change also contains later commits that were not
    measured. One restructures the placement record and drops a per-placement array copy. The
    others make the skip rarer when sources join, leave, or change window during layout, and
    release references. These workloads keep their sources attached, stationary, and in one
    window, so the added conditions should not change their update counts, but the final code was
    not re-measured.

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
    for the `before-903b02e9` and `after-1f1c8a3a` builds, each with two passes. The retained set
    also holds the dry-run XML, per-run device readbacks, the two builds' APK hashes, and the
    trace-analysis scripts.

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

[HazeInput.Sources]: api/haze/dev.chrisbanes.haze/-haze-input/-sources/index.html
[HazePerformanceMode.Default]: api/haze/dev.chrisbanes.haze/-haze-performance-mode/-companion/-default.html
[hazeSource]: api/haze/dev.chrisbanes.haze/haze-source.html
