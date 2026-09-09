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
these tables cannot establish the calibration of the current implementation.

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

[benchmark-runbook]: https://github.com/chrisbanes/haze/blob/main/internal/benchmark/README.md
[blur-adr]: adr/0004-use-quality-gated-adaptive-input-scaling-for-blur.md
[glass-adr]: adr/0005-use-cadence-weighted-adaptive-input-scaling-for-glass.md
[performance-mode-adr]: adr/0006-reconcile-built-in-performance-mode-terminology.md
