# Benchmark results

These Android measurements record specific builds, workloads, and devices. They are historical
reference points, not performance budgets for other applications or measurements of the current
checkout. Use the [performance guide](performance.md) to tune your screen and the
[Android benchmark runbook][benchmark-runbook] to run Haze's workloads.

CPU frame duration describes UI-thread and RenderThread cost; it does not directly measure GPU
shader duration. Frame overrun describes deadline misses. The tables below retain the metrics and
conditions recorded for each run.

## Haze 2 compared with Haze 1

Haze 2 had lower P90 CPU frame duration in both sample interactions shared with Haze 1:
37% lower while scrolling the Images List and 15% lower while dragging the Credit Card.

| Workload | Haze 1 | Haze 2 | Reduction in P90 CPU frame duration |
| --- | ---: | ---: | ---: |
| Images List scrolling | 14.9 ms | 9.3 ms | 37% lower |
| Credit Card dragging | 11.5 ms | 9.8 ms | 15% lower |

These results come from Android Macrobenchmarks on a Pixel 6 running Android 17 at 60 Hz, with 32
iterations per workload. The comparison used Haze 1 at `7a2557f1` and Haze 2 at `fc46813e`. P90
highlights the slower frames during each interaction. The original summary did not record the run
date, build variant, or run order.

## Glass journeys and steady state (2026-08-04)

This run used the `benchmarkRelease` build at commit `334557df` on 2026-08-04: Pixel 6,
Android 17/API 37, 1080×2400, locked 60 Hz render rate, locked CPU frequency, and eight
Macrobenchmark iterations per scenario. The metric is P90 CPU frame duration in milliseconds.

| Scenario | Workload | P90 CPU frame duration |
| --- | --- | ---: |
| `productPager` | Gallery paging journey | 7.5 ms |
| `playgroundTimeline` | Gallery animated timeline journey | 11.2 ms |
| `steadyFull` | Controlled steady state, 1 Glass effect | 5.6 ms |
| `steadyFull3` | Controlled steady state, 3 Glass effects | 5.2 ms |
| `steadyFull9` | Controlled steady state, 9 Glass effects | 8.1 ms |

The Gallery journeys are closest to the sample's visible user work. The `steadyFull*` controls
characterize the baseline at different effect counts. Cold-initialization `effectAttach*` results
are deliberately omitted: they attach new effects at the measurement boundary and diagnose
delegate/shader creation rather than representative interaction performance. They remain covered
by the internal [Glass benchmark runbook][benchmark-runbook]. Compare the interactions, content,
and device classes that your application supports.

## Performance-mode calibration (2026-08-09)

The controlled Blur and Glass samples ran in `benchmarkRelease` on a Pixel 6 (Android 17/API 37,
1080×2400), locked to 60 Hz with Android fixed-performance mode enabled. Each row contains 16
fixed-duration iterations. The original summaries did not record the build SHA or run order;
these tables cannot establish the calibration of the current implementation. In particular,
`Balanced` was measured with the earlier discrete mapping, before fixed quality began interpolating
pixel counts. These values do not measure the current `Balanced` resolution.

Values are **P90 CPU frame duration / P90 frame overrun**, in milliseconds. A negative overrun
is margin below the 60 Hz frame budget. Each sample holds its other style choices fixed; the Glass
run keeps the other `GlassDefaults` values unchanged.

### Blur

| Workload | Adaptive | Quality | Balanced | Performance |
| --- | ---: | ---: | ---: | ---: |
| Stable source | 10.0 / -3.2 | 10.2 / -3.0 | 10.1 / -3.1 | 10.0 / -2.7 |
| Continuously changing source | 10.0 / -2.9 | 10.2 / -2.7 | 10.1 / -1.8 | 10.1 / -3.0 |

### Glass

| Workload | Adaptive | Quality | Balanced | Performance |
| --- | ---: | ---: | ---: | ---: |
| Stable source | 6.6 / -7.1 | 8.2 / -1.7 | 8.9 / -4.0 | 7.2 / -6.5 |
| Continuously changing source | 6.5 / -7.3 | 8.2 / -1.6 | 8.3 / -4.5 | 6.9 / -6.9 |

These CPU timings do not rank visual quality or establish that the named profiles have a monotonic
frame-time cost. Use a visual comparison alongside measurements before choosing an override.

The adaptive-policy decisions and their original evidence remain in [ADR-0004][blur-adr] and
[ADR-0005][glass-adr]. [ADR-0006][performance-mode-adr] records the current public terminology.

## Glass fixed quality with controlled CPU placement (2026-09-11)

On 11 September 2026, a CPU-affinity sweep compared six `qualityFraction` values using the updated
fixed-quality mapping. It used a release build on a Pixel 6 running Android 17
(full SDK 37.1), at 60 Hz with fixed-performance mode enabled. The scene contained one 280 dp ×
180 dp Regular Glass surface using `HazeInput.Sources`.

The sample process requested affinity to CPUs 4–5, the device's middle CPU cluster. Perfetto checks
verified that main and RenderThread executed only on those CPUs during the measurement interval in every trace.
Other Android processes remained unrestricted. This measures a controlled quality comparison;
applications normally leave CPU placement to Android.

Each level ran with stable and continuously changing input, first in ascending quality order,
then descending with the workload order also reversed. Eight iterations per case produced 192
iterations and 34,753 measured frames. Runs started at thermal status 0 and a battery
temperature of at most 30°C. Brightness was fixed during measurement; cooldowns used a dimmer screen
and disabled fixed-performance mode. No completed timing iteration was discarded.

The table shows the **changing-input** results. Negative frame overrun means the frame finished
before its deadline; more negative values indicate more spare time. Each value is the arithmetic
mean of the two per-pass P90 values, calculated before rounding, not the P90 of the combined
frames. CPU frame duration is not GPU shader duration.

| `qualityFraction` | CPU frame duration: mean per-pass P90 (ms) | Frame overrun: mean per-pass P90 (ms) |
| --- | ---: | ---: |
| `0` | 3.17 | -10.95 |
| `0.25` | 3.12 | -10.17 |
| `1f / 3f` | 3.11 | -9.77 |
| `0.5` (`Balanced`) | 3.17 | -9.11 |
| `0.75` | 4.87 | -6.34 |
| `1` (`Quality`) | 4.74 | -5.53 |

The largest difference in changing-input CPU P90 between the two passes was 0.12 ms.
Stable-input CPU P90 ranged from 2.60 to 2.72 ms across all levels and both passes.
No measured frame missed its deadline.

The [earlier normal-scheduling sweep](benchmark-results.md#glass-fixed-quality-under-normal-scheduling-2026-09-11)
had CPU results sensitive to slower-core placement, including a reversed ranking when two levels
were repeated. Those results are preserved separately; this rerun also fixed screen brightness,
so the difference between runs is not an isolated estimate of affinity's effect.

For this scene, `Fixed(0.75f)` used 2.77 ms more deadline margin than `Balanced`, based on the
mean per-pass P90 frame overrun. The new mapping already gives `Balanced` more resolution than the
previous Glass Balanced profile, reproduced here with `Fixed(1f / 3f)`. The earlier stationary
reference captures showed a subtle visual difference between `0.5` and `0.75`, supporting keeping
`Balanced` at `Fixed(0.5f)` for now.

These measurements do not establish the right setting for Web, Clear Glass, larger or multiple
surfaces, or higher refresh rates. Compare visual quality and frame timing in the actual screen
under normal scheduling before selecting a higher fixed level.

## Glass fixed quality under normal scheduling (2026-09-11)

These measurements and their follow-up used normal CPU placement. See the
[performance guide](performance.md#choosing-a-fixed-quality-level) for the later comparison
with CPU affinity controlled.

On 11 September 2026, a sweep compared six `qualityFraction` values using the continuous fixed-quality
mapping described above. It used a release build on a Pixel 6 running Android 17 (full SDK 37.1),
at 60 Hz with fixed-performance mode enabled. The scene contained one 280 dp × 180 dp Regular Glass
surface using `HazeInput.Sources`.

Each level ran with both stable and continuously changing input, first in ascending quality order,
then descending with the workload order also reversed. Eight iterations per case produced 192
iterations and 34,753 measured frames. Runs started at thermal status 0 and a battery temperature
of at most 30°C, with cooldowns outside measurement. Interrupted runs resumed from completed cases;
no completed iteration was discarded. Automatic brightness remained enabled during measurement.

The table shows the **changing-input** results. Negative frame overrun means the frame finished
before its deadline; more negative values indicate more spare time. Each value is the arithmetic
mean of the two per-pass P90 values, calculated before rounding, not the P90 of the combined
frames. CPU frame duration is not GPU shader duration.

| `qualityFraction` | CPU frame duration: mean per-pass P90 (ms) | Frame overrun: mean per-pass P90 (ms) |
| --- | ---: | ---: |
| `0` | 4.39 | -10.02 |
| `0.25` | 5.06 | -8.73 |
| `1f / 3f` | 4.20 | -8.83 |
| `0.5` (`Balanced`) | 4.47 | -8.00 |
| `0.75` | 5.33 | -5.76 |
| `1` (`Quality`) | 5.18 | -4.96 |

Stable-input CPU frame P90 ranged from 3.25 to 3.74 ms across all levels and both passes. One frame
missed its deadline in the entire sweep, in the ascending changing-input `Fixed(1f / 3f)` case.
Perfetto traces showed varying main-thread and RenderThread CPU placement, so the CPU differences
between passes should not be attributed solely to input resolution.

A follow-up repeated the changing-input `0f` and `0.25f` cases with the same APKs and device
settings, in `0 → 0.25 → 0.25 → 0` order. Its 32 iterations produced 5,792 frames with no missed
deadlines. These additional results are reported separately from the original sweep above:

| `qualityFraction` | CPU frame duration: mean per-pass P90 (ms) | Frame overrun: mean per-pass P90 (ms) |
| --- | ---: | ---: |
| `0` | 4.98 | -9.69 |
| `0.25` | 3.68 | -9.44 |

The CPU ranking reversed in the follow-up. In one `0f` pass, an iteration running mainly on the
smaller CPU cores had a CPU P90 of 6.05 ms, while the other seven iterations ranged from 3.52 to
3.99 ms. All iterations remain included in the reported means. These CPU results are sensitive
to scheduling and should not be read as a stable ranking of input-resolution cost; compare frame
overrun and traces alongside them.

For this scene, `Fixed(0.75f)` used 2.24 ms more deadline margin than `Balanced`, based on the
mean per-pass P90 frame overrun.
The new mapping already gives `Balanced` more resolution than the previous Glass Balanced profile,
which this sweep reproduced with `Fixed(1f / 3f)`. The visual difference between `0.5` and `0.75`
was subtle in the stationary reference scene, supporting keeping `Balanced` at `Fixed(0.5f)` for
now. This is a reference measurement for one device and workload: it does not establish the right
setting for Web, Clear Glass, larger or multiple surfaces, or higher refresh rates. Compare visual
quality and frame timing in the actual screen before selecting a higher fixed level.

[benchmark-runbook]: https://github.com/chrisbanes/haze/blob/main/internal/benchmark/README.md
[blur-adr]: adr/0004-use-quality-gated-adaptive-input-scaling-for-blur.md
[glass-adr]: adr/0005-use-cadence-weighted-adaptive-input-scaling-for-glass.md
[performance-mode-adr]: adr/0006-reconcile-built-in-performance-mode-terminology.md
