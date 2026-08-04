# Android benchmarks

## Glass profiling requirements

- Physical Android device on API 33 or newer.
- Release-like, non-debuggable target build.
- Device sufficiently charged and cool before measurement.
- Display fixed at 60 Hz when supported; otherwise use one fixed supported rate.
- Android fixed-performance mode enabled for stable CPU and GPU clocks.
- Debugger detached and unrelated background work minimized.

Record the device model, API level, and selected refresh rate with saved results.

## Controlled scenario baseline

Controlled Glass scenarios start from the unmodified `GlassDefaults.style`. The `steadyFull`,
`steadyFull3`, and `steadyFull9` benchmarks therefore use adaptive optics, the default shape, and
all default lighting, color, and rendering values.

Scenarios named after an optical change install an explicit `GlassOptics.Fixed` override for
that change. For example, `steadyNoBlur` disables depth and blur, while `steadyDepth50` fixes depth
at `0.5`. `steadyProgressive` and `steadyProgressive9` use the default fixed optical values
with a vertical progressive mask because adaptive optics does not expose a progressive property.
`steadyFullChroma` and `steadyFullChroma9` retain adaptive optics and set Full chromatic aberration
with a non-zero `0.3` strength. Other style groups remain at their defaults.

The progressive, Full chroma, interaction-update, and source-update scenarios each have one- and
nine-effect variants. The default steady scenario additionally has a three-effect variant.

## Validate automation

Run all Glass benchmarks without meaningful measurements:

```shell
./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassGalleryBenchmark,dev.chrisbanes.haze.GlassProfilingBenchmark
```

## Run comparable Glass measurements

Run the Glass Gallery and controlled Glass profiling suites together when recording the current
local baseline. This is the 34-test Glass suite: two realistic Gallery journeys and 32 controlled
profiling scenarios. It deliberately excludes `BaselineProfileGenerator` and `BenchmarkTest`,
which cover baseline-profile generation and the older Blur samples respectively.

```shell
adb shell cmd power set-fixed-performance-mode-enabled true
./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassGalleryBenchmark,dev.chrisbanes.haze.GlassProfilingBenchmark
adb shell cmd power set-fixed-performance-mode-enabled false
```

`connectedCheck` without a class filter runs all 45 instrumentation tests, including
`BaselineProfileGenerator`, and executes both the non-minified and benchmark-release variants.
Use the filtered command above for Glass measurements so those workloads do not mix with the
recorded results.

## Run a profile

Run one controlled scenario:

```shell
adb shell cmd power set-fixed-performance-mode-enabled true
./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.fullTracing.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdate
adb shell cmd power set-fixed-performance-mode-enabled false
```

Always disable fixed-performance mode after profiling, including after a failed run.

The cold-initialization scenarios attach 1, 3, or 9 independent Glass effects while keeping their
combined surface area constant. Run each method separately from the same initial thermal state:

```shell
./gradlew :internal:benchmark:connectedCheck \
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
./gradlew :internal:benchmark:connectedCheck \
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
