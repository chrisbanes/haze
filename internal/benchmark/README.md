# Android benchmarks

## Android baseline profiles

The library baseline profile generator exercises the current core, Blur, and Glass paths through
the sample's Images List, Scaffold, Credit Card, Glass Product, and Glass Playground journeys. It
runs on both AOSP managed devices: `pixel5Api30` (API 30) and `pixel5Api34` (API 34). The profile
filter is limited to Haze-owned packages under `dev.chrisbanes.haze.**`.

Generation requires JDK 21 and an Android SDK selected by `ANDROID_HOME` or `ANDROID_SDK_ROOT`.
Verify the environment with `java -version`, `echo "$ANDROID_HOME"` (or
`echo "$ANDROID_SDK_ROOT"`), and `command -v sdkmanager`; the latter checks that SDK package
management tooling is available, while Gradle uses the configured SDK environment. The managed-
device definitions and AOSP images are declared in `internal/benchmark/build.gradle.kts`.

Run generation from the repository root:

```shell
./gradlew --no-scan :haze:generateBaselineProfile
```

The checked-in output is
`haze/src/androidMain/generated/baselineProfiles/baseline-prof.txt`. Generation and packaging
coverage verify which classes and methods are exercised and shipped; they do not measure startup,
frame time, or any other performance improvement.

After generating, package the six existing Haze Kotlin Multiplatform `androidMain` AARs and the
non-minified sample consumer. The package task is `bundleAndroidMainAar`; skip profile generation
when validating packaging because the checked-in profile is the input to this check:

```shell
./gradlew --no-scan \
  :haze-utils:bundleAndroidMainAar \
  :haze:bundleAndroidMainAar \
  :haze-blur:bundleAndroidMainAar \
  :haze-glass:bundleAndroidMainAar \
  :haze-materials:bundleAndroidMainAar \
  :haze-glass-material3:bundleAndroidMainAar \
  :sample:android:assembleNonMinifiedRelease \
  -Pandroidx.baselineprofile.skipgeneration
```

The six AARs are written to `build/outputs/aar/<module>.aar` in their respective module
directories. The consumer APK is
`sample/android/build/outputs/apk/nonMinifiedRelease/android-nonMinifiedRelease.apk`. Verify the
profile against exact defined class and method members in every AAR and every consumer
`classes*.dex` file:

```shell
python3 internal/benchmark/verify_baseline_profile.py \
  --profile haze/src/androidMain/generated/baselineProfiles/baseline-prof.txt \
  --aar haze/build/outputs/aar/haze.aar \
  --aar haze-blur/build/outputs/aar/haze-blur.aar \
  --aar haze-glass/build/outputs/aar/haze-glass.aar \
  --aar haze-utils/build/outputs/aar/haze-utils.aar \
  --aar haze-materials/build/outputs/aar/haze-materials.aar \
  --aar haze-glass-material3/build/outputs/aar/haze-glass-material3.aar \
  --apk sample/android/build/outputs/apk/nonMinifiedRelease/android-nonMinifiedRelease.apk
```

For the retained final profile, the verifier reports 2,241 rules (208 class and 2,033 method), zero
missing ordinary AAR members, and zero missing consumer-Dex members. Its ordinary counts are `433`
(`haze`), `342` (`haze-blur`), `1,092` (`haze-glass`), `36` (`haze-utils`), `7`
(`haze-materials`), and `3` (`haze-glass-material3`). The expected generated counts are 296
external-synthetic entries, 15 lambda bridges, 16 `$-CC` interface companions, and one
namespaced `R$drawable` class. Generated entries still require exact consumer-Dex definitions;
`$-CC` entries also require an interface owner with the interface access flag in an AAR, and
`R$drawable` requires the matching AAR manifest namespace.

Only the root `haze` AAR may contain `baseline-prof.txt`, and its bytes must match the checked-in
file exactly. The other five AARs must contain no profile asset. The verifier preserves HSP flags,
nested `$` names, synthetic names, and complete method descriptors. It rejects malformed,
foreign/sample, missing, or unexplained rules instead of deleting or renaming them. Keep the
generated profile as collection output; do not hand-author rules or retain obsolete Haze 1 rules.
The API 30 and API 34 device collections need no rerun for verifier or README changes while the
collection-relevant generator, sample/runtime, and toolchain inputs remain unchanged. Generation,
packaging, and descriptor verification establish artifact coverage only; they do not measure
startup, frame time, or any other performance improvement.

## Performance-mode benchmark requirements

- Physical Android device on API 33 or newer.
- Release-like, non-debuggable target build.
- Device sufficiently charged and cool before measurement.
- Display fixed at 60 Hz when supported; otherwise use one fixed supported rate.
- Android fixed-performance mode enabled for stable CPU and GPU clocks.
- Debugger detached and unrelated background work minimized.

Record the device model, API level, and selected refresh rate with saved results.

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
