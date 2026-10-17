# Android benchmarks

Use this runbook for local physical-device measurements and trace analysis. For application tuning,
see the [performance guide](../../docs/performance.md); published historical measurements live in
[benchmark results](../../docs/benchmark-results.md).

## Choose a workload

| Task | Start here |
| --- | --- |
| Compare Blur and Glass performance modes | [Calibration matrix](#calibration-matrix) |
| Observe combined Blur noise/tint reconstruction | [Noise/tint construction](#noisetint-construction) |
| Diagnose one controlled scenario or run Gallery journeys | [Run a profile](#run-a-profile) |
| Compare a merged Glass cluster with independent surfaces | [Glass container comparison](#glass-container-comparison) |
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
| Stable input | `Quality`, `Balanced`, `Performance` |
| Continuously changing source input | `Quality`, `Balanced`, `Performance` |

`BenchmarkTest` owns the six Blur rows and `GlassProfilingBenchmark` owns the six Glass rows.
Each test selects a tagged scenario, waits for it to settle, then starts its fixed-duration run;
the UI navigation is part of the measurement contract. Glass calibration rows record frame timing,
frame overrun, and peak memory; Blur rows record frame timing and frame overrun.

## Controlled scenario baseline

Controlled Glass calibration scenarios start from `GlassStyle.regular` with unmodified presentation
defaults.
`stable_quality`, `stable_balanced`, and `stable_performance` therefore differ only in
`HazePerformanceMode`; they use the regular size-responsive optics, default shape, and all default
lighting, color, and rendering values. `steady_full_3` and `steady_full_9` retain the historical
controls at three and nine effects.

Scenarios named after an optical change install an explicit `GlassOptics` override for that change.
For example, `steadyNoBlur` disables depth and blur, while `steadyDepth50` fixes depth at `0.5`.
`steadyProgressive` and `steadyProgressive9` use the default fixed size values with a vertical
progressive mask. `steadyFullChroma` and `steadyFullChroma9` retain the regular responsive optics and
set Full chromatic aberration with a non-zero `0.3` strength. Other style groups remain at their
defaults.

The `resize_quality` and `resize_quality_9` workloads keep their source pixels stationary while
resizing the outer Glass grid's layout bounds from 196×126 dp to 280×180 dp and back. Each runs
three linear grow-and-shrink cycles during the existing three-second measurement window. The
one-effect and nine-effect variants use `HazeInput.Sources`, `Quality`, Regular style, and retain the
same effect nodes throughout the run; the changing bounds come from layout size, not a graphics
transform.

The `shape_update_balanced` and `shape_update_balanced_9` workloads use the same layout-bounds
resize as `resize_quality`, with `Balanced`, `HazeInput.Sources`, Regular style with default
presentation, and a stationary source. All four corner radii move in phase with the size, from a pill
(63 dp, or 21 dp per cell with nine effects) at the smallest size to 24 dp (8 dp per cell) at the
largest. Each effect keeps one remembered style whose block reads the animated radius from state,
so the node re-evaluates the style each frame without rebuilding it (ADR-0011). The resize still
recomposes the scene each frame, as in `resize_quality`. Earlier results in which the style was
rebuilt every frame (before #1427) are not directly comparable. Run them with
`GlassProfilingBenchmark#shapeUpdateBalanced` and `#shapeUpdateBalanced9`; the benchmark launches each
directly through its intent extra.

The progressive, Full chroma, interaction-update, shape-update, and source-update scenarios each
have one- and nine-effect variants. `source_update_quality`, `source_update_balanced`, and
`source_update_performance` are the controlled changing-input
calibration rows. The default steady scenario additionally has a three-effect variant.

### Source-scroll workloads

The source-scroll workloads move only the source. `source_scroll_balanced` and
`source_scroll_balanced_9` exist for both Blur (`BenchmarkTest#blurSourceScrollBalanced` and
`#blurSourceScrollBalanced9`) and Glass (`GlassProfilingBenchmark#sourceScrollBalanced` and
`#sourceScrollBalanced9`). Each offsets a full-size source vertically from 0 to 48 dp and back,
three times over the three-second window. The offset is read only during placement, so the source
is re-placed without recomposition. The one or nine `Balanced` effects keep a fixed size, position,
and style, and the source pixels themselves do not change. The workload isolates the cost of an
effect following its source's geometry (#1456). Run the comparison with
[Run the source-scroll comparison](#run-the-source-scroll-comparison).

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

### Glass container comparison

The `source_update_independent_2` and `source_update_container_2` workloads compare two Glass
surfaces drawn independently with the same two surfaces in one Glass cluster. They share every
parameter except the container:

- two surfaces 12 dp apart in the 280×180 dp grid, each about 134×180 dp;
- `HazePerformanceMode.Default` (`Balanced`), `HazeInput.Sources`, and the Regular style;
- the continuously changing source workload used by `source_update_balanced`.

`source_update_container_2` wraps the grid in `hazeGlassContainer(spacing = 32.dp)`. The 12 dp gap
is less than half of the spacing, so the two surfaces form one two-member cluster.

Both use the changing-source workload, not the steady one. `HazeSource.record` re-records the same
source layer every frame, but that does not re-enter the Glass draw: the Glass layers were recorded
once, and HWUI re-renders them on the RenderThread each frame. `hazeGlassRuntimeDrawCount` (the
`HazeGlass.runtimeDraw` marker) therefore counts UI-thread recording, not rendered frames. It is
1-2 per iteration in both rows, and only confirms that the modern runtime delegate was selected.

The per-frame signal is HWUI's `drawLayer [graphicsLayer] <width> x <height>` slice, which marks
each damaged offscreen layer that is re-rendered. The benchmark records it as
`graphicsLayerDrawCount`, next to `hazeSourceRecordCount`. Divide each by `frameCount` for the same
iteration:

| Row | `graphicsLayerDrawCount` per frame | `hazeSourceRecordCount` per frame | `hazeGlassRuntimeDrawCount` |
| --- | --- | --- | --- |
| `source_update_independent_2` | 1.8-2.2 (two layers) | 0.9-1.1 | at least 1 every iteration |
| `source_update_container_2` | 0.9-1.1 (one cluster layer) | 0.9-1.1 | at least 1 every iteration |

The slice is named by the platform and counts any offscreen Compose layer, so the bands are
row-specific. They also detect a cluster that failed to form: a container row that shows two layers
per frame does not exercise the cluster, and the comparison is invalid. `archive_result.py` enforces
these bands with `--per-frame` and `--positive`, and fails any archive in which a single-value
metric has fewer runs than iterations or an iteration recorded fewer than `--min-frames` frames
(a missing `frameCount` also fails; default 1; `--min-frames 0` disables the floor).

CPU frame time does not measure GPU shader cost. Compare the container row's layer, which covers
both members and the bridge between them, with the two independent layers using peak GPU memory and
system GPU data alongside CPU frame duration.

## Noise/tint construction

`BlurNoiseTintBenchmark` measures the Android combined noise-and-single-SrcOver-tint path.
The sample picker and benchmark intents select the same `noise_tint_*` scenarios. All use a
stationary checker source, Quality/full-resolution filtering, and a constant total visible area
of 240 × 180 dp, split across one or three independently configured nodes.

Run these methods individually: `stable1`, `stable3`, `tint1`, `tint3`, `radius1`, `radius3`,
`noise1`, `noise3`, `cold1`, and `cold3`. Warm methods settle before starting a three-second
draw/animation window; changing methods animate only their named property. Cold methods launch
inside `measureBlock`, capturing first construction in a fresh process. They do not establish
a cold GPU driver or disk cache. Direct profiling launches skip unrelated image prefetching.

```shell
./gradlew :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.BlurNoiseTintBenchmark#tint3 \
  -Pandroid.testInstrumentationRunnerArguments.haze.targetSha256="$noise_tint_target_sha256" \
  -Pandroid.testInstrumentationRunnerArguments.haze.benchmarkSha256="$noise_tint_benchmark_sha256" \
  --no-scan
python3 internal/benchmark/archive_result.py \
  --method 'dev.chrisbanes.haze.BlurNoiseTintBenchmark#tint3' \
  --destination "$noise_tint_archive/baseline-forward/tint3"
```

Follow device preparation and cleanup above, and qualify every method with the dry-run argument
below first. Save the XML from dry runs; they are automation checks, not measured results.
Measured archives must retain eight traces per method before another invocation replaces outputs.
Save frozen target/benchmark APKs and their SHA-256 hashes, source identities, run order, and
before/after thermal and refresh readbacks alongside the archives. Compare baseline then candidate,
and candidate then baseline, using the same APKs and device settings.
The two `haze.*Sha256` arguments require the frozen hashes; the benchmark checks the installed APKs
before measurement. Dry runs may omit these arguments.

`HazeRuntimeShader.construct` surrounds actual native construction. Attribute it to the nested
`HazeBlur.combinedNoiseTint` section and the target process; a factory call alone does not prove
construction. `analyze_noise_tint.py` verifies these markers, measured windows, scheduler evidence,
archive completeness, and frozen APK identities. The complete four-pass session is checked with
`--session "$noise_tint_archive" --expect-shared`; `--pass baseline-forward` checks the baseline
before reuse is implemented. Constructor duration is CPU-side native construction, not total
backend/GPU compilation cost. Report CPU frame timing and scheduler placement separately.

## Validate automation

Run every method listed in the measurement loop below once without meaningful measurements. Add
the dry-run argument to each individual Gradle invocation, and verify the XML result names that
method. Do not use a combined method selector as an automation check.

```shell
./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.BenchmarkTest#blurStableBalanced
```

## Run comparable performance-mode measurements

The examples below run in a subshell so cleanup traps do not change your interactive shell.
They disable fixed-performance mode on exit, including command failure, Ctrl-C, or termination.
A killed shell or disconnected device can prevent cleanup; reconnect and disable the mode manually
in that case. These examples assume it was off before the run.

Run the twelve controlled calibration methods individually after a successful dry run. A combined
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
  "BenchmarkTest#blurStableQuality"
  "BenchmarkTest#blurStableBalanced"
  "BenchmarkTest#blurStablePerformance"
  "BenchmarkTest#blurSourceUpdateQuality"
  "BenchmarkTest#blurSourceUpdateBalanced"
  "BenchmarkTest#blurSourceUpdatePerformance"
  "GlassProfilingBenchmark#stableQuality"
  "GlassProfilingBenchmark#stableBalanced"
  "GlassProfilingBenchmark#stablePerformance"
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

## Run the source-scroll comparison

This gate compares two builds: build A, the commit that adds the source-scroll scenarios without a
library change, and build B, the change under test. Create a worktree at each, prepare the device
as described above, and use eight measured iterations.

First dry-run every method once in each worktree, one invocation per method, and check that each
XML result names its method:

```shell
./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.BenchmarkTest#blurSourceScrollBalanced
```

Then run this loop in each worktree, in the order A, B, then B, A. The last four methods are guards:
the source-update and list workloads should not regress beyond single-pass uncertainty.

```bash
(
set -e
trap 'adb shell cmd power set-fixed-performance-mode-enabled false' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
adb shell cmd power set-fixed-performance-mode-enabled true
mkdir -p internal/benchmark/build/benchmark-results
results_dir=$(mktemp -d internal/benchmark/build/benchmark-results/source-scroll.XXXXXX)
echo "Results: $results_dir"
methods=(
  "BenchmarkTest#blurSourceScrollBalanced"
  "BenchmarkTest#blurSourceScrollBalanced9"
  "GlassProfilingBenchmark#sourceScrollBalanced"
  "GlassProfilingBenchmark#sourceScrollBalanced9"
  "BenchmarkTest#blurSourceUpdateBalanced"
  "GlassProfilingBenchmark#sourceUpdateBalanced"
  "GlassProfilingBenchmark#sourceUpdate9"
  "BenchmarkTest#imagesList"
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

Copy each archive out of its worktree before the next build replaces outputs. Record the device
model and build, refresh rate, thermal and battery state, both builds' SHAs and APK SHA-256 hashes,
and the run order. Before attributing an apparent guard regression to the code, report its
per-iteration CPU placement as described in
[Check CPU placement before attributing a regression](#check-cpu-placement-before-attributing-a-regression).

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

## Run the Glass container comparison

Run each method individually. Dry-run first, with
`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true`, and check
that each XML result names its method; do not archive a dry run as a measurement. A dry run writes
only XML, so it cannot check the gates below. Run a gated diagnostic for that, then the
two measured passes, the second in the opposite order, so that order and thermal drift do not
favour one row. Each loop archives every method with `archive_result.py`, which checks the
iteration count, a passing XML testcase, distinct traces, a full set of runs for every single-value
metric, at least `--min-frames` frames (default 1) in every iteration, and the per-method gates
from [Glass container comparison](#glass-container-comparison).

Define the per-method gates once in the shell that runs the loops:

```bash
gates_for() {
  gates=(--per-frame hazeSourceRecordCount=0.9:1.1 --positive hazeGlassRuntimeDrawCount)
  case "$1" in
    sourceUpdateIndependent2) gates+=(--per-frame graphicsLayerDrawCount=1.8:2.2) ;;
    sourceUpdateContainer2) gates+=(--per-frame graphicsLayerDrawCount=0.9:1.1) ;;
  esac
}
```

### Run the gated diagnostic

Run each method once at the standard eight iterations, about 70 s per method. The diagnostic proves
that the gates pass on the installed APKs. It is never published, because the measured passes add the
recorded device conditions and the reversed order.

```bash
(
set -e
trap 'adb shell cmd power set-fixed-performance-mode-enabled false' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
adb shell cmd power set-fixed-performance-mode-enabled true
mkdir -p internal/benchmark/build/benchmark-results
results_dir=$(mktemp -d internal/benchmark/build/benchmark-results/container.XXXXXX)
echo "Results: $results_dir"
for method in sourceUpdateIndependent2 sourceUpdateContainer2; do
  if adb shell dumpsys window | grep -q "isKeyguardShowing=true"; then
    echo "Keyguard showing before $method; unlock the device and restart"
    exit 3
  fi
  gates_for "$method"
  ./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class="dev.chrisbanes.haze.GlassProfilingBenchmark#${method}"
  python3 internal/benchmark/archive_result.py \
    --method "dev.chrisbanes.haze.GlassProfilingBenchmark#${method}" \
    "${gates[@]}" \
    --destination "$results_dir/diagnostic/$method"
done
)
```

### Run the measured passes

Before each pass, record the device model and Android build, the refresh rate (`vsyncRate`), the
thermal status and the battery temperature and level. Record the SHA-256 of the installed APKs and
the run order with the results. Check for the keyguard before each method: a locked device
interrupts the run, so restart that method into a fresh archive directory.

```bash
(
set -e
trap 'adb shell cmd power set-fixed-performance-mode-enabled false' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
adb shell cmd power set-fixed-performance-mode-enabled true
mkdir -p internal/benchmark/build/benchmark-results
results_dir=$(mktemp -d internal/benchmark/build/benchmark-results/container.XXXXXX)
echo "Results: $results_dir"
pass_one=(sourceUpdateIndependent2 sourceUpdateContainer2)
pass_two=(sourceUpdateContainer2 sourceUpdateIndependent2)
for pass in one two; do
  methods=("${pass_one[@]}")
  if [ "$pass" = two ]; then methods=("${pass_two[@]}"); fi
  {
    echo "== pass $pass: ${methods[*]}"
    adb shell getprop ro.product.model
    adb shell getprop ro.build.fingerprint
    adb shell dumpsys thermalservice | grep -E "Thermal Status|mName=(battery|soc_therm|VIRTUAL-SKIN)"
    adb shell dumpsys battery | grep -E "temperature|level|AC powered"
    adb shell dumpsys SurfaceFlinger | grep -m1 -oE "vsyncRate=[0-9.]+ Hz"
    shasum -a 256 \
      sample/android/build/outputs/apk/benchmarkRelease/android-benchmarkRelease.apk \
      internal/benchmark/build/outputs/apk/benchmarkRelease/benchmark-benchmarkRelease.apk
  } | tee -a "$results_dir/conditions.txt"
  for method in "${methods[@]}"; do
    if adb shell dumpsys window | grep -q "isKeyguardShowing=true"; then
      echo "Keyguard showing before $pass/$method; unlock the device and restart" | tee -a "$results_dir/conditions.txt"
      exit 3
    fi
    gates_for "$method"
    ./gradlew --no-scan :internal:benchmark:connectedBenchmarkReleaseAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class="dev.chrisbanes.haze.GlassProfilingBenchmark#${method}"
    python3 internal/benchmark/archive_result.py \
      --method "dev.chrisbanes.haze.GlassProfilingBenchmark#${method}" \
      "${gates[@]}" \
      --destination "$results_dir/$pass/$method"
  done
done
)
```

If Perfetto stalls waiting for the `track_event` data source, force-stop the Google app as
described in [Prepare the device](#prepare-the-device) and restart the pass.

### Report CPU placement per iteration

Report main-thread and RenderThread placement for every archived iteration, as described in
[Check CPU placement before attributing a regression](#check-cpu-placement-before-attributing-a-regression).
Save this query as `placement.sql`. It totals the running time per CPU of the app's main thread and
RenderThread inside the `measureBlock` slice of one trace:

```sql
with w as (select ts, ts + dur as te from slice where name = 'measureBlock' limit 1)
select thread.name as thread, sched.cpu as cpu,
  round(sum(min(sched.ts + sched.dur, w.te) - max(sched.ts, w.ts)) / 1e6, 1) as running_ms
from sched join thread using(utid) join process using(upid), w
where process.name = 'dev.chrisbanes.haze.sample.android'
  and (thread.tid = process.pid or thread.name = 'RenderThread')
  and sched.ts < w.te and sched.ts + sched.dur > w.ts
group by thread.name, sched.cpu order by thread.name, sched.cpu;
```

Run it over each trace with Perfetto's `trace_processor_shell`, one iteration per trace. This
covers both methods in both measured passes (32 traces). The loops above run in subshells, so set
`results_dir` to the path they printed after `Results:`:

```bash
results_dir=internal/benchmark/build/benchmark-results/container.XXXXXX  # replace XXXXXX with the printed suffix
find "$results_dir/one" "$results_dir/two" -name '*.perfetto-trace' | sort | while read -r trace; do
  echo "== $trace"
  trace_processor_shell -q placement.sql "$trace"
done
```

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
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdateBalanced
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
delegate. It counts UI-thread recording, not rendered frames: with source input, a changing source
re-renders retained Glass layers on the RenderThread, so use `graphicsLayerDrawCount` for per-frame
layer rendering. The no-Glass control intentionally omits that metric.

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

Each row records `FrameTimingMetric`, max `MemoryUsageMetric` (GPU and RSS counters), `HazeBackdrop.draw` count, and
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
