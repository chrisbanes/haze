# CB-10 Adaptive Glass validation attempt — 2026-09-23

## Verdict

Plan r2 slice 5 did not clear the default-release gate. Keep the initial thresholds and the
deterministic fallback. The Android benchmark measured only one Glass runtime draw per iteration,
and the visible fast DPR-2 browser run did not demonstrate a stable higher tier. The remaining
comparisons need a valid active workload before calibration.

## Physical Android attempt

- Revision: `2456665498b74a9b7b4535b358a1ff6b29fd748b` (before this documentation commit).
- Pixel 8a, Android 17 / SDK 37, display fixed at 60 Hz, fixed-performance mode enabled,
  battery 100%, thermal status 0. Original refresh settings were restored after the runs.
- Release-like `GlassProfilingBenchmark` APK. Separate dry runs passed for
  `sourceUpdateAdaptive`, `sourceUpdateQuality`, `sourceUpdateBalanced`, and
  `sourceUpdatePerformance`. One forward pass followed that order. Each method produced
  eight nonempty Perfetto iteration traces. No reverse pass was used for comparison.

| Method | Reported frame-metric runs | Frame count median | CPU frame P50 / P90 (ms) | Frame overrun P90 (ms) | Peak GPU memory median (KiB) | Glass draws per reported run |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Adaptive | 8 | 181 | 2.79 / 4.67 | -9.06 | 104,596 | 1 |
| Quality | 5 | 180 | 2.29 / 4.77 | -7.20 | 110,444 | 1 |
| Balanced | 8 | 180.5 | 2.36 / 4.58 | -8.28 | 106,560 | 1 |
| Performance | 7 | 181 | 2.98 / 4.68 | -9.32 | 100,368 | 1 |

These are diagnostic values, **not** a mode ranking. The Quality run recorded a median of
180 source records while the Glass runtime draw marker appeared once in each reported run.
All methods had one Glass draw in every reported metric run. Quality and Performance emitted
five and seven frame-metric runs respectively despite eight trace files, an additional coverage
gap. Source recording may not be invalidating the effect draw, but the cause has not been
established. A single draw cannot exercise feedback, repeated tier transitions, or allocation
churn. Peak memory values therefore cannot establish parity with a matching active Fixed tier.
The retained traces and original JSON are under the generated
`internal/benchmark/build/benchmark-results/cb10-r2-24566654-20260923/` directory in this
checkout; that ignored directory is not part of this commit.

## Visible browser attempt

The Glass Playground sample ran in a visible Chrome window at 1440 × 900 CSS pixels, DPR 1 and
DPR 2. “Constrained” used Chrome's 4× CPU throttle on the same machine, not a separate slow GPU.
Each run collected 179 `requestAnimationFrame` intervals and a screenshot. Callback intervals
do not measure GPU completion. The temporary diagnostic prints used to read host tier changes
were removed before this documentation commit.

| Browser condition | RAF P50 / P95 (ms) | Intervals over 20 ms | Host transitions seen in diagnostic run |
| --- | ---: | ---: | --- |
| Fast, DPR 1 | 16.7 / 17.2 | 0 / 179 | unavailable → middle → unavailable → middle → low |
| Fast, DPR 2 | 16.7 / 17.4 | 0 / 179 | unavailable → middle → unavailable → middle → low |
| 4× CPU, DPR 1 | 16.7 / 17.6 | 5 / 179 | unavailable → middle → low |
| 4× CPU, DPR 2 | 16.7 / 33.4 | 17 / 179 | unavailable → middle → low → unavailable → low |

The fast DPR-2 run did not reach or retain full resolution; its six observed Glass renderers
eventually used the low `0.5` scale. This conflicts with the desired fast-host outcome,
although the separate RAF sample, console instrumentation, and unknown GPU completion prevent
attributing the transition to a specific fault. The screenshots are unsynchronized scene
captures, with no matched Fixed-mode or still-versus-active corner reference. They cannot
establish corner quality. No browser allocation or GPU-memory measurement was collected.

## Platform and fallback evidence

`iosSimulatorArm64Test` and `macosArm64Test` passed with Apple tests enabled. Android host
tests cover matched Activity and Compose Dialog roots and reject detached or unmatched roots.
Common tests cover sharing, separation, rebind, last release, stale evidence, and Fixed mappings.
The Apple tests use controlled host values; they do not verify live iOS reparenting or macOS
window close/reopen. Desktop's `LocalAwtWindow`, iOS's `LocalUIView`, and the web/native macOS
sample-root wrapper are present, but real desktop/iOS lifecycle behavior remains unobserved.

When no host identity, lifecycle, verified Android `Window`, supported timing source, or recent
valid sample is available, Adaptive uses the existing retained-workload fallback. Android does
not substitute Skiko cadence for missing `FrameMetrics`. Skiko cadence on desktop, iOS, web,
and native macOS is lower-confidence callback timing, not rendered-frame completion. Fixed modes
remain exact; no threshold or renderer-topology change was made in this slice.

## Required next evidence

1. Diagnose why the physical changing-source benchmark records only one Glass draw. Then run
   Adaptive and matching Fixed controls in both orders with eight complete metric runs each,
   active tier logs, retained traces, frame distributions, allocation counts, GPU peaks, and
   synchronized still/active corner captures.
2. On a visible fast and a genuinely constrained web host at DPR 1/2, collect tier transitions,
   rendered-frame or GPU timing where available, and matched Fixed/still/active corner captures.
   Investigate the fast-host low-tier transition before changing thresholds.
3. Exercise a real desktop window and iOS view/window lifecycle, including disposal/rebind.
   Retain fallback wherever a supported host identity or timing source cannot be verified.

The invalid Android workload and unmet fast DPR-2 outcome are plan r2 re-plan signals. Any
material render invalidation or timing-design repair needs Churchill's next assignment before
implementation.
