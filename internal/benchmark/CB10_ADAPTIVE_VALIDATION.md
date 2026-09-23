# CB-10 Adaptive Glass validation attempt — 2026-09-23

## Verdict

Plan r2 and the approved r3 Skiko probe have not cleared the default-release gate. Keep the
initial thresholds and deterministic fallback. The original Android and browser attempts below
are historical diagnostics; the newer integrated and matched-corner evidence is qualified here.
No paired physical mode comparison has been run on the final integrated revision.

## Integrated r3 candidate and matched corners

- Candidate lineage: r3 host-policy and Skiko probe, the opt-in completed-draw scale diagnostic,
  and the short-callback repair `52c824f7` share base `bb6f2607`. The diagnostic is off by
  default. The last repair changes only Skiko cadence and its JVM tests: after a 6.1 ms callback
  in a healthy 60 Hz trace, the controller retains Full rather than resetting to Balanced.
  Three consecutive, similarly short callbacks still relearn a genuinely faster cadence;
  skipped callbacks and stale gaps retain their existing downgrade/fallback behaviour. The
  isolated-callback test failed before the repair and passed afterward. Glass JVM tests, Android,
  iOS simulator, macOS, JS and Wasm Glass compilation, Glass Spotless, and the production web
  webpack task passed at `52c824f7`.
- Final verification exposed an Android host invalidation test that counted source draws without
  first driving a rendered frame. Its test-only fixture now captures the root before and after
  the source colour change, confirms that both source nodes redraw, and retains the assertion
  that the effect receives one coalesced invalidation. The focused Android host test passes;
  the production source-notification path is unchanged.
- A 16-second fast, DPR-1 Glass Playground run at `52c824f7` with diagnostics off had 950 RAF
  intervals (median 16.7 ms, P95 16.8 ms, one over 20 ms) and no page exceptions. It did not
  record applied tiers. The raw trace and screenshot are attached to CB-10 comment
  `01a0cf88-c846-706d-84cc-6510fa602c30`.
- Richard's earlier fast Chrome 153 captures at `c90b2c59` used a 900 × 700 CSS Playground
  area on macOS arm64. At DPR 1 and 2, Adaptive and Fixed Quality each had six immediately
  preceding completed-draw markers at scale 1.0; every marker during the selected screenshot
  was also 1.0 (Adaptive/Quality: 48/42 markers at DPR 1, 54/54 at DPR 2). The selected
  uncovered-source strips differed by a mean 0.095 and 0.049 RGB levels on a 0–255 scale.
  Source landmarks shifted by up to 2 CSS px at DPR 1 and 1.5 CSS px at DPR 2. One selected
  lossless Glass corner pair per DPR showed no obvious extra stair-step at Adaptive Full.
  The 900 × 900 attempt that never reached Full in 45 seconds was excluded. The raw captures,
  controls, markers, and conditions are in CB-10 attachment
  `01a0cf88-165d-707f-8d2c-667492d2a0c4` (SHA-256
  `d1d2877f06e877d1bc4037482df0fe3171fb4c23d514ab063766d7cc2bd68c46`).
- A separate Pixel 8a equal-tier fixture at earlier r2 head `eb10e56c` alternated three
  Adaptive and three Fixed Quality launches. Its three matched late-active pairs were RGB
  pixel-identical across the whole Glass rectangle and four corner crops, after the recorded
  Balanced → Full transition and a completed draw at scale 1.0. This is forced-draw fixture
  evidence for that build, not a paired frame-timing or allocation comparison of the final head.

The web pairs cover one selected source phase per DPR; their screenshots span 114–208 ms and
source version is unavailable. The small landmark shifts prevent pixel-equivalence or a
release-wide corner-quality claim. Completed-draw markers and RAF intervals do not measure GPU
completion. Neither browser retained-memory data nor final-head physical allocation/GPU-memory
parity is available. Moving the Playground lens also changes seven retained layer heights by
one pixel at a time, legitimately resetting stable-workload comparisons. These limits keep the
Adaptive default unvalidated for release.

## Integrated r2 validity check

- Code integration: `ec6df06d` (source-only runtime repair `32601172` followed by Richard's
  benchmark repair `1d50a429`, cherry-picked without conflicts). This record update changes no
  runtime or benchmark behavior.
- Source-only production path: on the Pixel 8a, the Android device test passed 1/1 after the
  Adaptive demand lease expired. A source-only orange-to-navy change renewed demand and changed
  retained Glass pixels. JVM tests also cover selected and unrelated sources, multiple consumers
  and hosts, detach/rebind, stop/restart, exact Fixed behavior, retained reuse at unchanged tier,
  a tier change on a real source update, and one coalesced invalidation without source feedback.
- Forced-draw benchmark path: one release-like `sourceUpdateAdaptiveDiagnostic` iteration passed on
  the same physical Pixel 8a (Android 17, 60 Hz, battery 90%, thermal status 0; fixed-performance
  mode was disabled afterward). Its archived result has 180 source records, 181 Glass runtime draws,
  181 Glass prepares, 180 frame samples, and one nonempty Perfetto trace. The diagnostic's paired
  screenshots changed 9,840/9,840 sampled interior pixels. This is a validity fixture with
  screenshot overhead, **not** a performance-mode ranking or calibrated timing result.
- `archive_result.py` accepted that one-iteration diagnostic and rejected sparse runs in its unit
  tests (13 passed). The ignored local archive is
  `internal/benchmark/build/benchmark-results/cb10-r2-integrated-ec6df06d-diagnostic/`; its
  trace and raw JSON are not committed. The runbook now requires this validity gate before a
  paired matrix.
- Combined JVM and Android host/sample tests, macOS and iOS simulator Glass tests, JS/Wasm
  compilation, and the web sample bundle passed. The Haze Apple test tasks compiled but were
  skipped by their target configuration.

The runtime source-only test and the forced-draw benchmark diagnostic establish different paths.
Neither supplies the eight-iteration, order-reversed Adaptive/Fixed comparison, GPU completion,
matched corner quality, or live native window/view lifecycle needed for release validation.

## Combined pre-matrix diagnostic

Richard's benchmark gate `9edff463` was cherry-picked after the policy repair `8889fb3d`; the
combined benchmark commit is `706379d8`. An internal host decision-change trace marker was added
to observe tiers on the same Perfetto timeline as the six-second diagnostic window. It does not
alter the controller decision, renderer scale, or public API.

On the physical Pixel 8a (Android 17, fixed 60 Hz, battery 90%, thermal status 0), one
release-like `sourceUpdateAdaptiveDiagnostic` iteration passed. The measured trace contains
`CB10DiagnosticActive` for 6,020.713 ms, 359 source records, 360 Glass draws and prepares, and
360 frame-duration samples. Paired Glass captures changed 9,840/9,840 sampled interior pixels.
The trace processor showed the following slices within that same active interval:

| Slice | Offset from active start | Android report sequence |
| --- | ---: | ---: |
| `HazeGlass.tier.BALANCED.sample1` | 0.056 s | 1 |
| `HazeGlass.tier.FULL_RESOLUTION.sample212` | 3.581 s | 212 |

The one-iteration archiver accepted a tier record transcribed from those slices and bound by trace
SHA-256 `56a719a1fb21eb32a52ae35049cf6c9f299f495d88de60d96d6c818031f2fc4e`. The raw JSON,
trace, XML, and tier record are in the ignored local
`internal/benchmark/build/benchmark-results/cb10-r2-combined-tier-gate-20260923/` directory and
are not committed. The archiver checks counts, duration, event shape, and trace digest, but does
not independently parse tier slices; this manual-observation limit remains. Android report sequence
is not proof that every report was valid. Fixed-performance mode was disabled after the run.

This passes the narrow pre-matrix active-workload gate. It is one screenshot-bearing diagnostic,
not a calibrated performance comparison or evidence that the Adaptive default is release-ready.

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

1. With the one-iteration workload gate now passing, run Adaptive and matching Fixed controls in
   both orders with eight complete metric runs each,
   active tier logs, retained traces, frame distributions, allocation counts, GPU peaks, and
   synchronized still/active corner captures.
2. Preserve the earlier fast/full and constrained/low DPR 1/2 captures as historical qualitative
   evidence. Repeat tier-backed, source-synchronised Full/Fixed corner captures on the final r3
   head across scenes and DPRs; the selected pairs above do not settle temporal quality. Browser
   GPU completion and retained memory remain unavailable in the current evidence.
3. Exercise a real desktop window and iOS view/window lifecycle, including disposal/rebind.
   Retain fallback wherever a supported host identity or timing source cannot be verified.

The original invalid Android workload and fast DPR-2 outcome prompted bounded repairs within plan
r2. Any further material render invalidation or timing-design change needs a revised plan before
implementation.
