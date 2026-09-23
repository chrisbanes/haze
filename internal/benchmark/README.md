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

On Android 17 beta devices, verify that force-stopping the Google app actually removes all of its
processes before starting Perfetto. Some builds immediately restart the app and its Chromium trace
producer. If that happens, bring another app such as Settings to the foreground, record the Google
app's enabled state, temporarily disable it with
`adb shell pm disable-user --user 0 com.google.android.googlequicksearchbox`, and verify its
processes are gone. Restore it with `adb shell pm enable com.google.android.googlequicksearchbox`
after the run, including after a failure.

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

Run every method listed in the measurement loop below once without meaningful measurements. Add
the dry-run argument to each individual Gradle invocation, and verify the XML result names that
method. Do not use a combined method selector as an automation check.

```shell
./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.BenchmarkTest#blurStableAdaptive
```

## Run comparable performance-mode measurements

The examples below run in a subshell so cleanup traps do not change your interactive shell.
They disable fixed-performance mode on exit, including command failure, Ctrl-C, or termination.
A killed shell or disconnected device can prevent cleanup; reconnect and disable the mode manually
in that case. These examples assume it was off before the run.

Run the sixteen controlled calibration methods individually after a successful dry run. A combined
comma-separated method selector can silently execute only the first method on some runner/tooling
combinations, even though Gradle exits successfully. Verify each XML result, JSON method label,
`repeatIterations`, and trace count, then preserve its output before starting the next method.

The loops require Python 3.9 or newer and one connected device. Each invocation copies its JSON,
XML, messages, and traces into a new per-method directory, then verifies the selected method,
eight measured iterations, a passing XML testcase, and eight distinct non-empty trace files.
Verification failure stops the loop but retains the copied evidence. Each run gets a fresh archive
directory; existing results are never overwritten.

```bash
(
set -e
trap 'adb shell cmd power set-fixed-performance-mode-enabled false' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
adb shell cmd power set-fixed-performance-mode-enabled true
mkdir -p internal/benchmark/build/benchmark-results
results_dir=$(mktemp -d internal/benchmark/build/benchmark-results/modes.XXXXXX)
echo "Results: $results_dir"
methods=(
  "BenchmarkTest#blurStableAdaptive"
  "BenchmarkTest#blurStableQuality"
  "BenchmarkTest#blurStableBalanced"
  "BenchmarkTest#blurStablePerformance"
  "BenchmarkTest#blurSourceUpdateAdaptive"
  "BenchmarkTest#blurSourceUpdateQuality"
  "BenchmarkTest#blurSourceUpdateBalanced"
  "BenchmarkTest#blurSourceUpdatePerformance"
  "GlassProfilingBenchmark#stableAdaptive"
  "GlassProfilingBenchmark#stableQuality"
  "GlassProfilingBenchmark#stableBalanced"
  "GlassProfilingBenchmark#stablePerformance"
  "GlassProfilingBenchmark#sourceUpdateAdaptive"
  "GlassProfilingBenchmark#sourceUpdateQuality"
  "GlassProfilingBenchmark#sourceUpdateBalanced"
  "GlassProfilingBenchmark#sourceUpdatePerformance"
)
for method in "${methods[@]}"; do
  ./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class="dev.chrisbanes.haze.${method}"
  python3 internal/benchmark/archive_result.py \
    --method "dev.chrisbanes.haze.${method}" \
    --destination "$results_dir/$method"
done
)
```

## Verify the Glass source diagnostic

Before a physical comparison, run the single-iteration Glass source diagnostic and archive it.
This diagnostic alone animates for six seconds; comparison scenarios retain their three-second
window. The test requires visibly changed Glass pixels between two screenshots of the same
animation. The archiver requires at least 120 source records, Glass draws, Glass prepares, and
frames, plus a `CB10DiagnosticActive` trace metric of at least four seconds. Four seconds covers the
30-sample warm-up and three-second promotion window with margin. It also requires observed
BALANCED and later FULL_RESOLUTION tier events, with promotion at least 3.5 seconds into the
active window. The accepted BALANCED event must precede the promotion in both timestamp and
reported sequence when present. The screenshots are saved under `Pictures/CB10` on the device,
with their names logged as `CB10BenchmarkDiagnostic`.

Supply a reviewed `tier-evidence.json` with `traceSha256`, `activeStartNanos`, `activeEndNanos`,
and `tierEvents` entries containing `tier`, `atNanos`, and `sampleCount`. Read the internal
`HazeGlass.tier.<TIER>.sample<SEQUENCE>` decision-change slices and `CB10DiagnosticActive` slice
from the same Perfetto trace, and record the SHA-256 digest of the JSON report's referenced trace.
Use the trace's monotonic nanosecond clock for all timestamps. The archiver checks the digest,
timing, event structure, and window against the measured trace duration, then retains the evidence
file. It does **not** parse the tier slices or authenticate a manually transcribed tier record;
inspect the raw trace and compare every recorded event before accepting the gate. The sequence in
the marker counts Android frame reports, including invalid or dropped reports; verify recent valid
timing separately. Do not run the paired matrix if pixels, trace, tier evidence, or archive
verification fails.

```shell
./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdateAdaptiveDiagnostic
python3 internal/benchmark/archive_result.py \
  --method dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdateAdaptiveDiagnostic \
  --iterations 1 \
  --diagnostic-evidence tier-evidence.json \
  --destination internal/benchmark/build/benchmark-results/source-update-diagnostic
```

## Verify the corner and allocation diagnostic

The opt-in `sourceUpdate{Adaptive,Quality,Balanced,Performance}CornerDiagnostic` methods and
`sourceUpdateNoGlassCornerDiagnostic` are one-iteration **diagnostics**, not comparable timing
rows. Run each selected method separately and archive with `--iterations 1`. Each six-second
fixture uses the same checkerboard crossing the Glass corners and holds source version 0 at the
ready screenshot, then version 1 during the active screenshot. The PNGs are saved losslessly under
`Pictures/CB10` and named in `CB10CornerDiagnostic` logcat lines. Pull both files for every mode;
check that their source-reference pixels and surface bounds match before comparing corner crops.
The benchmark APK opts into the probe only for these scenario IDs; ordinary samples and comparison
rows keep their existing rendering.

The target-process trace exposes `CB10GlassPrepareJavaObjectsPerCall` and
`CB10GlassDrawJavaObjectsPerCall` as **same-thread Java object counts** inside each named Glass
trace scope. Sum samples in the measured trace; each value belongs to one call and excludes warm-up
calls outside that trace. `CB10AppJavaAllocDelta` counts all target-process Java objects during the active
window and is also present in the no-Glass control. The control lacks Glass source recording, so
subtracting its process count does not isolate Glass. These counters exclude native and GPU
allocations, and the probe itself adds some Java/trace overhead. `CB10GlassAppliedScalePermille`
records the actual scale at each runtime draw; correlate it and `HazeGlass.tier.*` slices with
`CB10DiagnosticActive` before labelling an active capture. A single pair validates the measurement
seam, not mode ranking or release corner quality.

## Run the fixed-quality sweep

Run each Glass fixed-quality method explicitly and verify its result label. A combined selector
can execute only the first method on some runner/tooling combinations; a successful Gradle exit
alone does not establish complete coverage. For automation validation, invoke each method with
`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true` and check
its XML result. Do not use the measured-result archiver for dry runs: they do not provide the
eight measured iterations and traces required below.

```bash
(
set -e
trap 'adb shell cmd power set-fixed-performance-mode-enabled false' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
adb shell cmd power set-fixed-performance-mode-enabled true
mkdir -p internal/benchmark/build/benchmark-results
results_dir=$(mktemp -d internal/benchmark/build/benchmark-results/fixed-quality.XXXXXX)
echo "Results: $results_dir"
for level in 0 25 33 50 75 100; do
  for workload in stable sourceUpdate; do
    ./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class="dev.chrisbanes.haze.GlassProfilingBenchmark#${workload}Fixed${level}"
    python3 internal/benchmark/archive_result.py \
      --method "dev.chrisbanes.haze.GlassProfilingBenchmark#${workload}Fixed${level}" \
      --destination "$results_dir/${workload}Fixed${level}"
  done
done
)
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

```bash
(
set -e
trap 'adb shell cmd power set-fixed-performance-mode-enabled false' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
adb shell cmd power set-fixed-performance-mode-enabled true
./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.fullTracing.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdateAdaptive
)
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

### Interpret source/backdrop results

Source and native backdrop inputs have different reuse boundaries. A stable source can retain its
captured pixels and processed effect stages across later draws. Native backdrop rendering instead
applies the platform effect to the earlier pixels in the current window whenever the backdrop node
is drawn. It avoids Haze source capture, but it does not use the source path's retained-output
policy. Native rendering is therefore not inherently the faster path.

The stable comparison scenarios intentionally keep invalidating the effect while leaving the source
pixels unchanged. They measure retained-source reuse against repeated native backdrop composition;
they are not static-screen idle measurements. Read them alongside the updating-source rows, where
both inputs must consume changing pixels. Preserve this workload distinction when reporting a
result.

`HazeBackdrop.draw` measures CPU-side preparation and submission around the backdrop `RenderNode`.
Its duration is not the complete backdrop cost and does not measure GPU shader duration. In a
representative trace, compare app `RenderThread` `DrawFrames`, `Vulkan finish frame`, `QueueSubmit`,
and Skia operation counts between the paired cases. Keep nested slice durations separate, and check
main-thread and RenderThread CPU placement before attributing their duration difference to the
rendering path. A higher operation or submission count establishes more RenderThread work; exact GPU
cost still requires GPU timeline or profiler evidence.

Run a dry run first on the same physical 37.2 device. Repeat this one-method invocation for every
source and backdrop method in the table above, using `BenchmarkTest` for Blur and
`GlassProfilingBenchmark` for Glass. Verify the exact result label before measuring; do not combine
the methods into one comma-separated selector.

```shell
./gradlew --no-scan :sample:shared:testAndroidHostTest \
  :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.BenchmarkTest#blurStableQuality
```

Measure three source/backdrop pairs, then repeat three pairs in backdrop/source order. Return the
device to the same thermal envelope before each pair. Keep all JSON and Perfetto outputs; compare
CPU P90, actual-frame P90, frame overrun, and peak memory against the order-reversed control
envelope rather than one run.

```bash
(
set -e
trap 'adb shell cmd power set-fixed-performance-mode-enabled false' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
adb shell cmd power set-fixed-performance-mode-enabled true
# Run one source/backdrop pair with the focused class argument above, then reverse its order.
)
```

Verify fixed-performance mode is off before returning the device to normal use, including after
failed or interrupted benchmarks.
