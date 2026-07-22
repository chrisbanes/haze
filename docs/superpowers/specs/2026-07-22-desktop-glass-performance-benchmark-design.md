# Desktop Glass Performance Benchmark Design

## Goal

Add an observational JVM Desktop benchmark for the Glass visual effect. The benchmark should make
large rendering regressions visible in pull requests, especially regressions in the dynamic
highlight that follows the mouse pointer, without treating noisy hosted-runner timings as a release
gate.

The benchmark targets GitHub's pinned `macos-26` runner and requires Compose Desktop to select
Skiko's Metal backend. It compares the pull request with its base commit on the same host, records
the raw results as an artifact, and maintains one summary comment on the pull request.

## Scope

- Benchmark the JVM Desktop implementation on macOS only.
- Cover one isolated Glass interaction workload and one representative full-sample workload.
- Measure Skiko render callbacks from a real Compose Desktop window.
- Compare base and pull-request revisions in alternating blocks on the same runner.
- Upload machine-readable results and publish an informational pull-request comment.
- Put the reusable Desktop runner in a dedicated internal module.
- Put the Glass scenarios, tests, and executable in a separate module so CI can select them without
  running every Desktop benchmark suite.

This design does not add Android or iOS benchmarks, enforce performance thresholds, or attempt to
share the runner across platforms. The runner is reusable by Desktop benchmark suites, but the
initial implementation does not add a `haze-blur` scenario or a multiplatform abstraction.

## Why macOS

Linux-hosted CI would normally exercise a software rasterizer or a virtual display rather than the
GPU path seen by Desktop users. The pinned macOS runner provides a known Apple Silicon hardware
class and can potentially expose the Metal path. This is closer to the reported Desktop behavior
while remaining easy to run in GitHub Actions.

GitHub documents the standard `macos-26` runner as an M1 virtual machine but does not explicitly
promise Metal acceleration for that runner class. The first implementation step is therefore a
small capability probe that launches a Compose Desktop window and records Skiko's selected render
API. The remainder of this design proceeds only if the hosted runner reports Metal. It must not
silently fall back to software rendering. If the probe fails, the workflow reports that limitation
and the benchmark design is reconsidered before further implementation.

Apple Silicon does not expose a supported facility for pinning CPU and GPU clocks. The benchmark
therefore controls the variables available to it and relies on same-host paired measurements to
reduce dynamic-frequency, thermal, and host-load noise.

`macos-26` is deliberately used instead of `macos-latest`. Every result records the observed
hardware and runner-image metadata so a GitHub runner migration is visible as a discontinuity
rather than silently mixed into the history.

## Module Boundaries

### Shared Desktop Runner

Add a JVM-only `:internal:benchmark-desktop` module. "Shared" means that its runner is reusable by
multiple Desktop visual-effect benchmark suites; it does not mean Kotlin Multiplatform common code.
The existing `sample:desktop` application and entry point remain untouched.

The new module owns:

- the Compose Desktop application and fixed-purpose benchmark window;
- Skiko render callback collection;
- warm-up and measurement scheduling;
- block aggregation and statistical calculations;
- environment metadata collection;
- JSON serialization and validation;
- command-line parsing and validation;
- the scenario contract and benchmark-suite entry function.

An internal `DesktopBenchmarkScenario` contract separates the shared runner from each workload. A
scenario supplies a stable identifier and protocol version, composes its content, performs its
deterministic warm-up and measured input sequence, and reports completion. The runner owns timing,
window lifecycle, repetition, aggregation, and output. The interface exposes no Haze or Glass
types, so adding a future `haze-blur` scenario is additive.

The runner is a JVM library. It does not depend on `haze`, `haze-blur`, `haze-glass`, any sample
module, or a concrete scenario. It exposes one internal entry function that accepts the command-line
arguments and an explicit list of scenarios, then returns a process result. Suite modules provide
their own small `main` function and runnable distribution.

### Glass Desktop Benchmark Suite

Add a JVM-only `:internal:benchmark-desktop-glass` module that depends on
`:internal:benchmark-desktop`, `haze-glass`, and `sample:shared`. It owns:

- the isolated pointer-sweep scenario;
- the Glass Playground scenario and its narrow deterministic controls;
- Glass scenario tests and the Metal smoke test;
- the Glass scenario registry and executable entry point;
- the runnable distribution invoked by local development and CI.

The module passes its scenario registry to the shared runner; it does not duplicate window,
measurement, statistics, serialization, or environment code. A future Desktop `haze-blur`
benchmark belongs in a separate suite module that depends on the same runner.

Benchmark mode must:

- request Metal and abort with a clear infrastructure error if Skiko selects another backend;
- create a fixed-size backing surface of 1280 by 720 pixels, independent of display scale;
- launch on JDK 21 with `-Xms512m`, `-Xmx512m`, and the default JDK 21 collector for every
  invocation;
- render an unoccluded, visible Compose Desktop window;
- run without Gradle, compilation, or dependency resolution active during measurement;
- close the window and process deterministically after writing the result.

The CI job builds the `:internal:benchmark-desktop-glass` runnable distributions for both revisions
first, stops their Gradle daemons, and then invokes the resulting launchers directly. Build time is
never included in a benchmark value.

## Workloads

### Isolated Pointer Sweep

The isolated scene contains a deterministic background and one interactive Glass surface using the
default hover response. It dispatches synthetic mouse events directly to the Desktop window so the
events pass through Compose's real pointer-input and Glass interaction path without depending on
the host cursor or macOS accessibility permissions.

After an unmeasured warm-up, the pointer follows a fixed path across the Glass surface at 120 input
updates per second for four seconds. The path, scene size, surface geometry, Glass style, and input
timestamps are identical for every invocation. This workload is the focused signal for the
mouse-following highlight and its dynamic shader-uniform updates.

### Glass Playground

The second workload renders the existing Glass Playground in a fixed state and viewport. Autoplay
and unrelated randomness are disabled. A deterministic mouse sequence hovers, presses, and drags a
Glass surface through a fixed path for six seconds, exercising the representative sample rendering
graph and interaction behavior together.

The benchmark may add narrow internal controls to the Playground for fixing its initial state and
driving its existing interaction path. It must not fork a benchmark-only copy of the screen or
change the behavior of the normal sample.

## Render Measurement

Attach `SkiaLayerAnalytics` to the benchmark window and timestamp its `beforeFrameRender` and
`afterFrameRender` callbacks with a monotonic clock. Compose's `withFrameNanos` drives animation but
is not a render-completion signal and is not used as the primary measurement.

The initial benchmark reports:

- render callback duration: `afterFrameRender - beforeFrameRender`;
- callback interval: elapsed time between successive `afterFrameRender` callbacks;
- p50, p95, and p99 for both distributions;
- the count and percentage of callback intervals above the 16.67 ms and 33.33 ms reference
  budgets;
- frame count and workload duration;
- variation between repeated measurement blocks.

These callbacks bracket Skiko's render path but do not prove that the GPU completed or presented a
frame. Results and comments must therefore use the terms **render callback duration** and
**callback interval**, not GPU time, presentation latency, or dropped frames.

## Same-Host Comparison

For a pull request, CI checks out the base and head SHAs into separate directories and builds both
before measurement. Each process performs its own fixed warm-up before emitting a measurement
block. For each workload, CI repeats this order three times:

```text
base -> head -> head -> base
```

This produces six blocks per revision while balancing gradual thermal drift and power-state
changes. The report includes each revision's absolute values and the head/base percentage change.
The paired comparison is the primary signal; absolute values remain useful for diagnosing a single
run and following broad trends.

Block-level medians use median absolute deviation to describe variation. If either revision's
block medians have more than 10 percent robust relative variation, the report labels that metric
`noisy`. This label is informational and never changes the workflow conclusion.

Every JSON result includes a runner schema version and a protocol version for each scenario. A
scenario protocol mismatch, including a pull request that intentionally changes workload
semantics, suppresses that scenario's percentage comparison and labels the revisions
`not comparable`. Other scenarios remain comparable. The initial landing may only benchmark the
head because the base revision has no benchmark launcher; this bootstrap case is reported without
a delta.

## Result Format

The aggregate JSON artifact contains only structured data:

- runner schema and per-scenario protocol versions;
- repository, base SHA, and head SHA;
- scenario identifiers and configuration;
- raw frame samples for every block;
- aggregate percentiles, over-budget counts, variation, and paired deltas;
- macOS, runner-image, CPU, memory, JVM, Compose, Skiko, render API, framebuffer, and display
  metadata;
- completion status and a bounded diagnostic message for infrastructure failures.

The raw artifact is retained even when aggregation or comment publication fails. Numeric values
must be finite, sample counts must be bounded, identifiers must come from a fixed allow-list, and
the entire artifact must be no larger than 5 MiB. A diagnostic message is limited to 2,048 UTF-8
bytes.

## CI and Pull-Request Reporting

Use a dedicated benchmark workflow rather than appending the benchmark to `mac_build`. A fresh
runner avoids inheriting variable heat and background work from compilation and iOS simulator
tests. The workflow is not added as a dependency of build, deployment, or release jobs.

The Glass workflow invokes only the `:internal:benchmark-desktop-glass` distribution and tests. Its
pull-request path filter includes the shared runner, Glass suite, `haze`, `haze-utils`, `haze-glass`,
`haze-materials`, `sample:shared`, Gradle build configuration, dependency versions, and the
benchmark workflows. Unrelated documentation, web, Android-sample-only, and release changes do not
start the Desktop Glass benchmark. The performance task is not attached to the root `check` task.
Ordinary unit tests for both benchmark modules may remain part of normal verification because they
do not launch or time a benchmark workload.

The reporting path uses two workflows:

1. An unprivileged `pull_request` workflow checks out and runs base and head code on `macos-26`,
   then uploads the JSON artifact. It receives no repository secrets and cannot write pull-request
   comments.
2. A default-branch `workflow_run` workflow downloads the artifact, validates it against the strict
   schema and size limits, and creates or updates one marker-tagged pull-request comment. It never
   checks out or executes pull-request code and renders only validated fields.

Do not use `pull_request_target` to execute the benchmark. The trusted workflow treats every
artifact field as untrusted input. It resolves the pull request and expected SHAs from the trusted
`workflow_run` event and verifies them against the artifact rather than trusting an artifact-provided
pull-request number or commit identity.

The comment shows the host and protocol, then one row per scenario with base and head p50/p95
render duration, p95 callback interval, over-budget percentage, paired delta, and noise label. It
links to the workflow run and raw artifact and explicitly states that the values are observational.
Updating the existing marker-tagged comment avoids posting a new comment after every push.

Performance values never fail CI. The benchmark workflow may fail when it cannot build, launch,
verify Metal, complete the workload, or produce a valid artifact; that is a broken measurement,
not a performance regression.

## Local Use

The shared runner supports a single-revision local run with scenario selection, configurable output
path, and iteration count. Defaults match CI, while a short smoke mode reduces warm-up and
repetition for development. Local results identify the actual renderer and host and are never
compared automatically with GitHub-hosted results.

## Testing

- Pure JVM tests cover percentile calculation, over-budget counts, median absolute deviation,
  paired deltas, protocol compatibility, and JSON round trips in `:internal:benchmark-desktop`.
- Runner contract tests use an injected fake host and deterministic fake scenario to verify
  lifecycle, warm-up exclusion, scenario selection, failure propagation, and clean shutdown
  without opening a native window or depending on Glass.
- Tests in `:internal:benchmark-desktop-glass` verify each scenario's exact event count, fixed path,
  terminal state, and clean shutdown.
- A short Glass-suite smoke task launches both scenarios, asserts that Metal was selected, and
  verifies nonempty finite samples and valid metadata. This explicit task is not part of root
  `check`.
- Workflow tests use fixture artifacts to cover valid comments, noisy and non-comparable labels,
  rejected identifiers, non-finite numbers, oversized input, and marker-based comment updates.
- Normal Desktop sample launch and existing Glass Gallery screenshot tests remain unchanged.

## Success Criteria

- A pull request receives one updated benchmark comment generated from a dedicated `macos-26`
  run.
- The comment contains isolated-pointer and Playground measurements for base and head when their
  protocol versions match.
- Repeated blocks run in the defined alternating order and expose their variation.
- The raw JSON is downloadable and contains enough environment data to explain runner changes.
- No performance value can fail, block, deploy, or publish the project.
- The benchmark uses the real Compose Desktop, Skiko, and Metal path and describes the callback
  boundary accurately.
- A new Desktop visual-effect scenario can use the runner without changing its window,
  measurement, aggregation, JSON, or CI-reporting code.
- CI can build, test, run, and path-filter the Glass benchmark suite independently from every other
  Desktop benchmark suite.
