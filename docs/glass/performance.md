# Glass performance

Glass is designed for real-time UI effects, but its cost depends on the device, the size and number
of Glass surfaces, and how often their source content or optical properties change.

For API and usage guidance, see the [Glass overview](../effects/glass.md).

!!! abstract "At a glance"
    On a Pixel 6 at 60 Hz, a focused scene containing nine compatible Glass effects recorded a P90
    CPU frame duration of **13.23–13.85 ms** across three confirmation runs. At P90, frames finished
    between **0.65 ms before** and **0.03 ms after** their deadline.

    This is a whole-frame measurement, including UI-thread and RenderThread work. It is not the
    isolated GPU cost added by Glass.

## Results

| Frame metric | Observed range |
| --- | ---: |
| Typical frame (P50) | 11.52–11.78 ms |
| Slower frame (P90) | 13.23–13.85 ms |
| Slow-tail frame (P95) | 13.82–14.58 ms |
| Slowest tail (P99) | 15.07–15.74 ms |
| Deadline margin (P90) | -0.65 to +0.03 ms |

A negative deadline margin means the frame completed before its deadline. A positive value means
it missed the deadline by that amount. The P99 deadline margin was 1.07–1.70 ms, so the slowest
frames still included occasional deadline misses.

These results describe one specific scene and device. Treat them as a guide, not a performance
guarantee or CI threshold. Applications should measure their own layouts and interactions.

## What was tested

The `steadyFull9` benchmark renders nine compatible Glass effects for three seconds. It uses:

- A Pixel 6 running API 37 at 1080 × 2400 and a fixed 60 Hz refresh rate.
- The modern Android `RuntimeShader` renderer available on API 33 and newer.
- A release-like, non-debuggable build with `CompilationMode.Full`.
- Warm startup and eight measured iterations per run.
- Navigation, initial composition, and settling outside the measured block.

The device reported no thermal throttling during these runs. The fallback renderer was not
measured.

## What affects performance

- **Surface area:** Larger Glass surfaces process more pixels.
- **Number of effects:** More independent surfaces can add rendering and submission work.
- **Compatible siblings:** Effects with compatible optics can share captured source, blur, and
  tiled optical work.
- **Changing content:** Moving or updating the captured source invalidates more retained work than
  redrawing an unchanged effect.
- **Dynamic optics:** Progressive blur, incompatible styles, and interaction-driven optics may use
  dedicated rendering paths instead of shared work.
- **Device and display:** GPU capability, resolution, refresh rate, and thermal state all affect
  the result.

## Reproducing the measurement

Run the benchmark locally on an unlocked physical device at API 33 or newer. Pin the display to one
supported refresh rate, let the device cool, and run:

```shell
./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassProfilingBenchmark#steadyFull9
```

Results and Perfetto traces are copied to
`internal/benchmark/build/outputs/connected_android_test_additional_output/`. Run the scenario
without full composition tracing when collecting comparable metrics; opt in to full tracing only
for diagnostic attribution. The complete local runbook is in `internal/benchmark/README.md`.
