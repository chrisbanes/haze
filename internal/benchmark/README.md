# Android benchmarks

Use this runbook for local physical-device measurements and trace analysis. For application tuning,
see the [performance guide](../../docs/performance.md); published historical measurements live in
[benchmark results](../../docs/benchmark-results.md).

## Choose a workload

| Task | Start here |
| --- | --- |
| Compare Blur and Glass performance modes | [Calibration matrix](#calibration-matrix) |
| Diagnose one controlled scenario or run Gallery journeys | [Run a profile](#run-a-profile) |
| Compare source and native backdrop paths | [Android 37.2 comparisons](#android-372-sourcebackdrop-comparisons) |
| Find artifacts and interpret metrics or traces | [Results](#results) |
| Generate and verify library baseline profiles | [Baseline-profile maintenance](BASELINE_PROFILES.md) |

<a id="emulator-correctness-evidence-2026-09-05"></a>

Saved preview-emulator correctness evidence is recorded separately in
[Backdrop validation](BACKDROP_VALIDATION.md). It does not establish physical-device performance.

<a id="android-baseline-profiles"></a>

For managed-device profile collection and artifact checks, use
[baseline-profile maintenance](BASELINE_PROFILES.md).

<a id="performance-mode-benchmark-requirements"></a>

## Prepare the device

- Physical Android device on API 33 or newer.
- Release-like, non-debuggable target build.
- Device sufficiently charged and cool before measurement.
- Display fixed at 60 Hz when supported; otherwise use one fixed supported rate.
- Android fixed-performance mode enabled for stable CPU and GPU clocks.
- Debugger detached and unrelated background work minimized.

Record the device model, API level, and selected refresh rate with saved results.

### Check CPU placement before attributing a regression

Android [fixed-performance mode](https://developer.android.com/games/optimize/adpf/fixed-performance-mode)
constrains clocks but does not pin threads to CPU cores. Retain every iteration and report the
aggregate frame metrics, then use Perfetto scheduler data within `measureBlock` to report main-thread
and RenderThread CPU placement per iteration. Supplement the aggregate with comparable-placement
groups; document their definitions and sample counts rather than dropping slower iterations.

Repeat comparisons in both build orders with the same APKs and benchmark configuration. A small
change in the proportion of slow-core iterations can move pooled P90 substantially. If placement
coverage differs, repeat before attributing the aggregate difference to the code. Record thermal
state and retain the original benchmark JSON, traces, build identities, and run order.

## Calibration matrix

The controlled Blur and Glass calibration suites measure every built-in
`HazePerformanceMode` under the same two workloads:

| Workload | Modes |
| --- | --- |
| Stable input | `Adaptive`, `Quality`, `Balanced`, `Performance` |
| Continuously changing source input | `Adaptive`, `Quality`, `Balanced`, `Performance` |

`BenchmarkTest` owns the eight Blur rows and `GlassProfilingBenchmark` owns the eight Glass rows.
Each test selects a tagged scenario, waits for it to settle, then starts its fixed-duration run;
the UI navigation is part of the measurement contract. Glass calibration rows record frame timing,
frame overrun, and peak memory; Blur rows record frame timing and frame overrun.

## Controlled scenario baseline

Controlled Glass calibration scenarios start from `GlassStyle.regular` with unmodified presentation
defaults.
`stable_adaptive`, `stable_quality`, `stable_balanced`, and `stable_performance` therefore differ
only in `HazePerformanceMode`; they use the regular size-responsive optics, default shape, and all default
lighting, color, and rendering values. `steady_full_3` and `steady_full_9` retain the historical
controls at three and nine effects.

Scenarios named after an optical change install an explicit `GlassOptics` override for that change.
For example, `steadyNoBlur` disables depth and blur, while `steadyDepth50` fixes depth at `0.5`.
`steadyProgressive` and `steadyProgressive9` use the default fixed size values with a vertical
progressive mask. `steadyFullChroma` and `steadyFullChroma9` retain the regular responsive optics and
set Full chromatic aberration with a non-zero `0.3` strength. Other style groups remain at their
defaults.

The progressive, Full chroma, interaction-update, and source-update scenarios each have one- and
nine-effect variants. `source_update_adaptive`, `source_update_quality`,
`source_update_balanced`, and `source_update_performance` are the controlled changing-input
calibration rows. The default steady scenario additionally has a three-effect variant.

### Fixed-quality sweep

The Glass-only fixed-quality sweep measures one regular-style effect at each requested
`HazePerformanceMode.Fixed` input level: `0`, `0.25`, `1/3`, `0.5`, `0.75`, and `1`. It runs the
same six levels under both the stable source workload (`stable_fixed_*`) and the continuously
changing source workload (`source_update_fixed_*`). These rows retain the calibration geometry and
metrics, so their results can be compared within each workload without changing style or effect
count.

The input levels are benchmark labels. Interpret their resolution using the Glass implementation
under test and preserve its build identity with the result. The sweep does not establish a public
minimum-resolution guarantee.

## Validate automation

Run the Blur and Glass calibration automation without meaningful measurements:

```shell
./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.BenchmarkTest,dev.chrisbanes.haze.GlassProfilingBenchmark
```

## Run comparable performance-mode measurements

Run the sixteen controlled calibration methods together after a successful dry run. This excludes
`BaselineProfileGenerator` and unrelated sample benchmarks; each result remains labeled with its
individual Blur or Glass scenario.

```shell
adb shell cmd power set-fixed-performance-mode-enabled true
./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.BenchmarkTest#blurStableAdaptive,dev.chrisbanes.haze.BenchmarkTest#blurStableQuality,dev.chrisbanes.haze.BenchmarkTest#blurStableBalanced,dev.chrisbanes.haze.BenchmarkTest#blurStablePerformance,dev.chrisbanes.haze.BenchmarkTest#blurSourceUpdateAdaptive,dev.chrisbanes.haze.BenchmarkTest#blurSourceUpdateQuality,dev.chrisbanes.haze.BenchmarkTest#blurSourceUpdateBalanced,dev.chrisbanes.haze.BenchmarkTest#blurSourceUpdatePerformance,dev.chrisbanes.haze.GlassProfilingBenchmark#stableAdaptive,dev.chrisbanes.haze.GlassProfilingBenchmark#stableQuality,dev.chrisbanes.haze.GlassProfilingBenchmark#stableBalanced,dev.chrisbanes.haze.GlassProfilingBenchmark#stablePerformance,dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdateAdaptive,dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdateQuality,dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdateBalanced,dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdatePerformance
adb shell cmd power set-fixed-performance-mode-enabled false
```

## Run the fixed-quality sweep

Run each Glass fixed-quality method explicitly and verify its result label. A combined selector
can execute only the first method on some runner/tooling combinations; a successful Gradle exit
alone does not establish complete coverage. First run the loop with
`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true` added to
validate all twelve cases without treating those runs as measurements.

```shell
adb shell cmd power set-fixed-performance-mode-enabled true
for level in 0 25 33 50 75 100; do
  for workload in stable sourceUpdate; do
    ./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class="dev.chrisbanes.haze.GlassProfilingBenchmark#${workload}Fixed${level}"
    # Preserve this method's JSON and traces before the next invocation replaces output files.
  done
done
adb shell cmd power set-fixed-performance-mode-enabled false
```

For paired order checks, repeat with reversed level and workload order using the same APKs.
Record actual iteration counts, thermal state, and run order. Restore temporary device settings
and disable fixed-performance mode even when a run fails.

### Control CPU placement

For a controlled Glass profiling comparison, the optional instrumentation argument
`haze.cpuAffinityMask` passes a hexadecimal CPU mask to the sample. The sample applies it to its
process threads before rendering starts; new threads inherit their creator's affinity. For
example, `-Pandroid.testInstrumentationRunnerArguments.haze.cpuAffinityMask=30` requests CPUs 4–5.
Check the device's CPU topology before choosing a mask; this example identifies the middle cluster
on the Pixel 6 used for the quality sweep.

This control is opt-in and failures stop the sample rather than silently continuing without the
requested affinity. Verify main-thread and RenderThread placement in every measured Perfetto trace,
because Android may change scheduling constraints. Keep these results separate from measurements
under normal scheduling. Stop the sample process after the run to clear its affinity:

```shell
adb shell am force-stop dev.chrisbanes.haze.sample.android
```

`connectedBenchmarkReleaseAndroidTest` runs only this module's release benchmark variant. Do not
use `connectedCheck` for calibration: it also schedules the non-minified and baseline-profile
instrumentation work, which can change device state or mix artifact output with the recorded run.

## Run a profile

Run one controlled scenario:

```shell
adb shell cmd power set-fixed-performance-mode-enabled true
./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.fullTracing.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdateAdaptive
adb shell cmd power set-fixed-performance-mode-enabled false
```

Always disable fixed-performance mode after profiling, including after a failed run.

The cold-initialization scenarios attach 1, 3, or 9 independent Glass effects while keeping their
combined surface area constant. Run each method separately from the same initial thermal state:

```shell
./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassProfilingBenchmark#effectAttach
```

Repeat with `effectAttach3` and `effectAttach9`, allowing the device to return to the same thermal
state between runs. Running all three in one instrumentation session is useful for automation
validation, but later cases may be frequency-throttled.

The cold-initialization scenarios, `depthUpdate`, and `playgroundTimeline` reports include
`HazeGlass.createRenderEffect` count and total duration, plus total `HazeGlass.prepareEffects` and
`HazeGlass.prepareLayers` durations. Compare creation counts across 1, 3, and 9 effects rather than
assuming a fixed count: blur-plan topology and optional interaction or rim stages affect the
number of composed effects. Linear duration growth points to per-effect shader/delegate
construction; a mostly fixed cost points to shared process or renderer initialization. Also
inspect RenderThread `DrawFrames`, `flush layers`, and `Vulkan finish frame` slices: independent
Glass nodes can add render-graph submission work even when their combined visible area is
constant.

Full tracing adds composable function slices to Perfetto traces and is intended for diagnostic
profiling. Omit `androidx.benchmark.fullTracing.enable` from runs used for comparable benchmark
metrics.

AndroidX Tracing Perfetto 1.0.1 cannot currently enable full tracing on API 37 because the platform
rejects its sideloaded native library as writable. Use an API 33–36 device for full-tracing runs;
ordinary benchmark tracing remains available on API 37.

Run the realistic journeys:

```shell
./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassGalleryBenchmark
```

## Results

JSON reports and Perfetto traces are copied to:

```text
internal/benchmark/build/outputs/connected_android_test_additional_output/
```

Use `frameOverrunMs` for deadline misses and `frameDurationCpuMs` for UI-thread and RenderThread
cost. A positive `hazeGlassRuntimeDrawCount` confirms that a Glass scenario used the modern runtime
delegate. The no-Glass control intentionally omits that metric.

Open representative traces in Android Studio or Perfetto. Application markers describe CPU-side
preparation, recording, and submission; they do not directly measure GPU shader duration. Inspect
system frame-timeline and GPU data when attributing GPU cost.

For stable source-backed scenarios, check `HazeSource.record` separately from effect drawing:
an unchanged source can remain captured while the effect continues to draw. For draw-only source
updates, also verify output pixels; retained layers can propagate changes without re-recording
every effect stage.

Separate elapsed slice duration from running CPU time using scheduler states. `DrawFrames`,
`Vulkan finish frame`, and `QueueSubmit` are nested scopes, so their durations cannot be added.
Label stage averages separately from frame P90. Time inside `QueueSubmit` can include both CPU
execution and waiting; the slice name alone does not identify the driver work or wait reason.

Attribute compilation markers to their owning process and thread. `HazeGlass.createRenderEffect`
measures Haze's effect construction, not every possible backend compilation step. Shader-related
markers in SurfaceFlinger do not establish app shader compilation, and absent markers cannot
rule out untraced driver work. Use native call stacks when finer CPU attribution is needed.

Expected Glass markers include:

- `HazeGlass.prepare`
  - `HazeGlass.prepareBudget`
  - `HazeGlass.selectDelegate`
  - `HazeGlass.delegatePrepare`
    - `HazeGlass.prepareEffects`
      - `HazeGlass.createRenderEffect` on shader cache misses
    - `HazeGlass.prepareLayers`
- `HazeGlass.runtimeDraw`
- `HazeGlass.source`
- `HazeGlass.blur`
- `HazeGlass.depth`
- `HazeGlass.optical`
- `HazeGlass.detail`
- `HazeGlass.rim`
- `HazeGlass.interactionOptical`
- `HazeGlass.interactionDetail`
- `HazeGlass.interactionLighting`
- `HazeGlass.groupAlpha`
- `HazeGlass.compose`

## Android 37.2 source/backdrop comparisons

Backdrop comparisons require a physical, hardware-accelerated Android 37.2 device. Record the full
SDK level (including the minor release), build SHA and variant, display refresh rate, fixed-
performance state, starting battery level, and thermal status with every JSON/Perfetto result.

The paired Quality workloads are:

| Effect | Source | Backdrop |
| --- | --- | --- |
| Blur stable | `blurStableQuality` | `blurBackdropStableQuality` |
| Blur updating | `blurSourceUpdateQuality` | `blurBackdropSourceUpdateQuality` |
| Glass stable | `stableQuality` | `backdropStableQuality` |
| Glass updating | `sourceUpdateQuality` | `backdropSourceUpdateQuality` |
| Nine Glass nodes updating | `sourceUpdate9` | `backdropSourceUpdate9` |

Each row records `FrameTimingMetric`, max `MemoryUsageMetric`, `HazeBackdrop.draw` count, and
`HazeSource.record` count. A healthy backdrop result has native backdrop draws and zero source
records; its source control has source records and no required backdrop draw.

Run a dry run first on the same physical 37.2 device:

```shell
./gradlew --no-scan :sample:shared:testAndroidHostTest \
  :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.BenchmarkTest#blurStableQuality,dev.chrisbanes.haze.BenchmarkTest#blurBackdropStableQuality,dev.chrisbanes.haze.BenchmarkTest#blurSourceUpdateQuality,dev.chrisbanes.haze.BenchmarkTest#blurBackdropSourceUpdateQuality,dev.chrisbanes.haze.GlassProfilingBenchmark#stableQuality,dev.chrisbanes.haze.GlassProfilingBenchmark#backdropStableQuality,dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdateQuality,dev.chrisbanes.haze.GlassProfilingBenchmark#backdropSourceUpdateQuality,dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdate9,dev.chrisbanes.haze.GlassProfilingBenchmark#backdropSourceUpdate9
```

Measure three source/backdrop pairs, then repeat three pairs in backdrop/source order. Return the
device to the same thermal envelope before each pair. Keep all JSON and Perfetto outputs; compare
CPU P90, actual-frame P90, frame overrun, and peak memory against the order-reversed control
envelope rather than one run.

```shell
adb shell cmd power set-fixed-performance-mode-enabled true
# Run one source/backdrop pair with the focused class argument above, then reverse its order.
adb shell cmd power set-fixed-performance-mode-enabled false
```

Always run the final cleanup command, including after a failed or interrupted benchmark. Verify
fixed-performance mode is off before returning the device to normal use.
