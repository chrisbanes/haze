# Android Glass Benchmark Profiling Design

## Goal

Add a local, physical-device profiling suite for the modern Android `haze-glass` runtime-shader
path. The suite should combine realistic Glass Gallery journeys with controlled scenarios that
isolate retained-rendering work, while producing frame, memory, and Perfetto evidence suitable for
manual diagnosis.

The initial suite is diagnostic. It does not compare Git revisions, enforce performance thresholds,
or run in CI.

## Constraints

- Run locally on a physical Android device.
- Require API 33 or newer and profile the `RuntimeShader` path.
- Use release-like, non-debuggable benchmark builds.
- Keep benchmark and profiling support internal to the repository.
- Do not change the public `haze-glass` API or its rendering behavior.
- Prefer deterministic in-app workloads over variable-speed synthetic gestures.

## Architecture

Keep `internal/benchmark` as the sole Macrobenchmark driver and continue targeting the Android
sample application. Do not add another benchmark framework, module, or target application.

The suite has two layers:

1. **Realistic Gallery journeys** drive the existing Product and Playground destinations. These
   screens may gain stable test tags or readiness semantics, but their rendering must not be
   modified specifically for benchmark results.
2. **Controlled profiling scenarios** live in one Android-only `GlassProfilingSample`. The sample
   owns deterministic scene state, generated artwork, animations, and the automation protocol.
   Macrobenchmark owns repetition, metrics, setup, and result collection.

Register the profiling destination alongside the existing Android-only samples. This keeps
profiling code out of common sample registrations and public library APIs while allowing the
existing sample application to remain the benchmark target.

## Realistic Scenarios

### Product pager

Advance the Product Gallery using its existing in-app "Next artwork" action. Measure the pager and
backdrop transition while the screen displays multiple simultaneous Glass surfaces.

This scenario represents overlapping Glass, captured-source movement, content updates, and
production-like Compose layout and animation.

### Playground timeline

Run one fixed Playground animation cycle with its four differently shaped Glass surfaces. The
cycle includes continuous position, lighting, and captured-source changes.

The workload must be started and completed through stable semantics rather than timing a manual
drag or arbitrary sleep.

## Controlled Scenarios

All controlled scenarios use generated local artwork, fixed geometry, a fixed animation duration,
and a stable easing curve. Except for the named input, scene and material state remain constant.

### `effectAttach`

Attach a fresh `GlassVisualEffect` to a settled scene and allocate its retained layers in an
already-running process. This measures warm-process effect preparation and attachment; it does not
claim to measure cold app startup or first-process shader compilation.

### `retainedReuse`

Continue producing frames through an unrelated animation while the source, geometry, and Glass
inputs remain unchanged. The Glass renderer should reuse its retained output.

### `interactionUpdate`

Run one deterministic press-and-release response through the dynamic interaction path while
retaining the base material output.

### `opticalUpdate`

Animate lighting or refraction inputs that affect the optical work without changing blur, depth,
source content, or geometry.

### `depthUpdate`

Animate depth while keeping blur and source content stable. This exercises depth and downstream
optical work.

### `blurUpdate`

Animate blur radius while keeping source content and geometry stable. This exercises blur and its
downstream retained stages.

### `sourceUpdate`

Animate the captured source while keeping the Glass surface and material stable. This forces the
broadest retained-stage invalidation.

### No-Glass control

Provide a source-animation control with the Glass surface disabled. It is a profiling reference for
the scene's Compose and drawing cost, not a pass/fail baseline or cross-revision comparator.

## Automation Protocol

Controlled scenarios share one explicit state machine:

1. The Macrobenchmark `setupBlock` launches the sample and selects a scenario.
2. The sample constructs and settles the scene.
3. The sample exposes `phase=ready` through resource-id-compatible semantics.
4. The measured block triggers `start`.
5. The sample runs its frame-clock-driven workload.
6. The sample exposes `phase=complete`, ending the measured block.

The semantics surface must also expose the selected scenario so automation cannot accidentally
measure a stale destination. Readiness and completion waits use bounded timeouts and fail with the
scenario and expected phase in the error message.

Realistic journeys should use existing in-app actions and animations where possible. In particular,
Product uses the "Next artwork" action rather than a variable-speed raw swipe.

## Benchmark Configuration

- Use `CompilationMode.Full` to reduce JIT variation during renderer profiling.
- Use warm process startup for the measured scenarios.
- Start with eight measured iterations per scenario.
- Support AndroidX Benchmark dry-run mode for validating navigation and completion quickly.
- Keep navigation, initial composition, and scene settling outside the measured block.
- Require a physical device on API 33 or newer.
- Use conservative controlled-scene geometry that stays within the runtime render budget.

Before a profiling session, use a sufficiently charged device, allow it to cool, pin the minimum
and peak display refresh rates to 60 Hz when the device supports it, minimize unrelated background
work, and avoid attaching a debugger. If 60 Hz is unavailable, use one other fixed supported rate.
Record the device model, API level, and selected refresh rate with the results.

## Metrics and Tracing

### Frame timing

Collect `FrameTimingMetric` for every scenario. Use:

- `frameOverrunMs` percentiles for user-visible deadline misses.
- `frameDurationCpuMs` percentiles for UI-thread and RenderThread pressure.

Treat the automatically captured Perfetto trace as the primary diagnostic artifact for explaining
slow frames.

### Glass trace sections

Reuse the existing internal `dev.chrisbanes.haze.trace` abstraction. Add short, stable trace
sections around existing Glass work boundaries:

- Render preparation and budget resolution.
- Source capture.
- Blur prefilter, horizontal, and vertical passes.
- Depth mix.
- Optical and refraction work.
- Detail and rim work.
- Interaction work.
- Final composition.

Include one stable RuntimeShader marker that acts as a profiling sentinel. If it is absent, the
trace must not be interpreted as a valid modern runtime-path profile.

These application trace sections measure CPU-side preparation, recording, and submission. They do
not directly measure shader execution time. Diagnose GPU cost using the corresponding system GPU
and frame-timeline data in Perfetto rather than treating an application section's duration as GPU
duration.

Do not initially emit every section through `TraceSectionMetric`. Inspect the sections in Perfetto
first, then promote only consistently useful markers to aggregate count, maximum, average, or total
metrics.

### Memory

Collect experimental `MemoryUsageMetric` in maximum-value mode for:

- `effectAttach`.
- Product pager.
- Playground timeline.
- `sourceUpdate`.

Inspect process heap, anonymous RSS, file-backed RSS, and GPU memory. These measurements are
profiling evidence, not assertions.

Power metrics are deferred because they are experimental, system-wide, and restricted to supported
Pixel hardware.

## Code Ownership

### `internal/benchmark`

- Add `GlassGalleryBenchmark` for Product and Playground.
- Add `GlassProfilingBenchmark` for controlled scenarios.
- Add shared setup and scenario-driving helpers without hiding each test's measured action.
- Extend UI Automator helpers for semantic phase waits and fixed actions.

### `sample/shared/src/androidMain`

- Add `GlassProfilingSample`.
- Keep the scenario state machine and rendering in one focused file initially; split pure scenario
  models only if the file becomes difficult to understand.
- Register the destination in `Samples.android.kt`.

### `sample/shared/src/androidHostTest`

- Verify the ready/start/complete semantics protocol.
- Verify fixed animation progression.
- Verify each controlled scenario mutates only its intended Glass input.

### `haze-glass`

- Add internal trace calls around existing work boundaries.
- Do not add public diagnostics, benchmark callbacks, or behavior changes.

## Error Handling

- Fail automation when the expected destination, scenario, ready phase, or complete phase is not
  observed within its timeout.
- Treat missing RuntimeShader trace evidence as an invalid profiling run.
- Keep controlled geometry below the automatic fallback boundary.
- Let invalid scenario configuration fail immediately rather than coercing it into a different
  workload.
- Keep trace section names stable so saved traces remain comparable and understandable.

## Verification

Before collecting performance numbers:

- Run Android host tests for the profiling sample's state machine and semantics.
- Run the relevant `haze-glass` tests for trace-wrapped code paths.
- Run Macrobenchmark dry mode for every scenario.
- Open at least one trace from every scenario family and verify that the measured window and
  expected Glass sections are present.
- Confirm that the controlled source, depth, blur, optical, and interaction scenarios show their
  intended invalidation boundaries.

Performance numbers are valid only after the functional and trace-shape checks pass.

## Deferred Work

- Commit-to-commit comparison or result-diff tooling.
- CI execution and performance thresholds.
- API 28–32 fallback profiling.
- Cold startup and first-process shader compilation.
- Power or energy profiling.
- Surface-count, size, material-style, and input-scale sweeps.
- Baseline Profile changes.
- A separate benchmark target application.

## References

- [Write a Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
- [Capture Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)
- [TraceSectionMetric](https://developer.android.com/reference/kotlin/androidx/benchmark/macro/TraceSectionMetric)
- [MemoryUsageMetric](https://developer.android.com/reference/kotlin/androidx/benchmark/macro/MemoryUsageMetric)
