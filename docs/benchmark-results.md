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

<a id="glass-immutable-input-capture"></a>

## Glass immutable input capture

This comparison measures source-backed Android Glass with completed immutable input
images against recorded-layer replay. Immutable input preserves the displayed material
when source selection is lost or another consumer recaptures mutable descendants.
The [renderer decision](adr/0003-use-one-android-fused-glass-renderer.md) records the
correctness contract and its capture/allocation trade-off.

The Pixel 8a ran Android 17 (full SDK 37.2) at 60 Hz with fixed-performance mode
and normal CPU scheduling. Each method ran eight measured iterations. Values are
**P90 pooled across all measured frames within each method**, calculated before rounding.

| Workload and build order | Recorded input CPU P90 (ms) | Immutable input CPU P90 (ms) | Recorded input overrun P90 (ms) | Immutable input overrun P90 (ms) |
| --- | ---: | ---: | ---: | ---: |
| Stable, one effect; recorded then immutable | 4.36 | 5.26 | -10.07 | -7.38 |
| Changing source, one effect; immutable then recorded | 4.83 | 5.37 | -6.78 | -7.34 |
| Stable, nine effects; recorded then immutable | 6.20 | 8.32 | -8.26 | -2.70 |
| Changing source, nine effects; immutable then recorded | 5.99 | 8.55 | -4.83 | -2.51 |
| Stable, nine effects; immutable then recorded | 6.09 | 8.60 | -8.37 | -2.74 |

Both nine-effect steady runs and the changing-source run retained negative P90 overrun.
Every immutable-input iteration also had negative individual P90; the highest was -1.31 ms.
Some measured frames still finished late. These observations support deadline headroom for
this configuration, with higher CPU cost and less slack, rather than zero jank or parity.

CPU placement limits attribution. The first steady comparison ran all eight recorded-input
iterations predominantly on the low-capacity cluster and all eight immutable-input iterations
predominantly on higher-capacity cores. The reverse recorded-input run had only one iteration
in the latter group. Changing-source nine-effect coverage overlapped more closely: six
recorded-input and eight immutable-input iterations were in the higher-capacity group, with
CPU P90 5.96 ms versus 8.55 ms. These broad groups do not isolate scheduling or GPU cost.
Immutable-input nine-effect per-iteration CPU P90 ranged from 7.52 to 9.32 ms across the runs.

Median sampled anonymous-memory maxima were approximately 82–84 MiB for immutable input
versus 70–72 MiB for recorded input. Immutable-input median sampled bitmap maxima were
approximately 204–253 MiB. Recorded-input bitmap counters appeared in only two or three
of each run's eight windows, so they do not support a reliable bitmap-memory difference.
Bitmap accounting decreased within every immutable-input window; sampled maxima fluctuated
across iterations. These categories overlap and must not be added. GPU accounting had only
a carried-in sample per window, which establishes neither measured-window peak nor reclamation.

An identical supplemental lifetime fixture passed on both builds, releasing 36 tracked
consumers over three cycles. Immutable input reached zero logical owners at clear, trim and
detach. Five-second post-close PSS was approximately 128 MiB, 55 MiB above cold, versus
86 MiB and 13 MiB above cold for recorded input. Both builds' PSS rose across the three
cycles. This diagnostic fixture supports logical release; it does not establish leak freedom,
return to cold memory or complete native reclamation, and is separate from release frame timings.

??? info "Measurement details"

    The recorded-input baseline was main commit `8602c039`. The integrated candidate retained
    PR #1383 at `24767614`, incorporated main `96ac22b5`, and included the nested-source
    presentation repair. Its qualified source manifest SHA256 was
    `0cbef62a3ba28f060b8fd02abae5a121d08880d1b85f5d639b071dae56877d23`.
    Frozen target APK hashes were
    `c0e431f54bf50d87558f2da334dd900b95619fd5cb6ea96090886bfbe4dc0433` (recorded) and
    `c6da485b201cd3b9396a2be9917abe892c480a2e2446a3bde36a74cd5f21075b` (immutable).
    The common runner hash was
    `dd021de434895579dbc080d238a4f1266e79dc49c0536f2753f5562f0d625d87`.

    Android build `CP41.260831.007.A3` was plugged in at 100% charge, awake and unlocked.
    Thermal status was 0 before every method; battery temperature ranged from 27.4°C to
    33.0°C across the comparison. Brightness was held at setting 12 with automatic mode
    disabled. CPU affinity was not requested. Placement groups classify each thread by
    whether at least half its running time was on CPUs 0–3 (capacity 182), compared with
    CPUs 4–8 (capacities 725/1024), strictly inside each measured interval. All iterations
    are retained, including placement groups without comparable coverage.

    Ten explicit method invocations produced 80 measured iterations and 80 verified nonempty
    Perfetto traces, with matching passing XML and JSON method identities. Before the paired
    comparison, another eight baseline iterations were preserved separately: the Google app
    respawned a Chromium producer after force-stop and caused 20-second Perfetto startup
    timeouts. Its temporary-disable fallback was applied before restarting the comparison;
    that restart was decided before candidate results were available. The Google app and
    original refresh, wake and automatic-brightness settings were restored afterwards.

    The supplemental fixture used identical source SHA256
    `3e0deed58306c35a7b59e35f0f5d727272748673d3b458e8120cdc8a06b949ea`.
    Unavailable baseline capture/owner hooks were reported as null. It used natural 250 ms,
    one-second and five-second post-close observations without forced GC, bitmap recycling
    or sampler-held consumer references. It measures debug/test process memory and
    owner-thread queue timing, not release frame cost or exact bitmap reclamation.

    Raw XML, JSON, traces, per-iteration placement/capture CSVs, lifetime observations and
    frozen APK provenance are retained locally under
    `build/agent-checkpoints/1373-capture-attribution-20261007/r12-measured/`,
    `r12-lifetime/` and `r12-release-builds/`. The current qualification and delivery state
    is recorded in [issue #1373](https://github.com/chrisbanes/haze/issues/1373).

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
[blur-adr]: adr/0004-use-quality-gated-adaptive-input-scaling-for-blur.md
[glass-adr]: adr/0005-use-cadence-weighted-adaptive-input-scaling-for-glass.md
[performance-mode-adr]: adr/0006-reconcile-built-in-performance-mode-terminology.md
