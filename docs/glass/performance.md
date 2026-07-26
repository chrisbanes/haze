# Glass performance

Glass is designed for real-time UI effects, but its cost depends on the device, the size and number
of Glass surfaces, and how often their source content or optical properties change.

For API and usage guidance, see the [Glass overview](../effects/glass.md).

!!! abstract "At a glance"
    On a Pixel 6 at 60 Hz, a focused scene containing nine Glass effects recorded P90 CPU frame
    durations of **9.1–9.5 ms** using the default Glass style. At P90, frames finished
    **2.2–2.5 ms before** their deadline.

    This is a whole-frame measurement, including UI-thread and RenderThread work. It is not the
    isolated GPU cost added by Glass.

## Results

| Scenario | P50 | P90 | P95 | P99 | P90 deadline margin |
| --- | ---: | ---: | ---: | ---: | ---: |
| One Glass effect (`steadyFull`) | 4.5 ms | 6.9 ms | 7.6 ms | 8.6 ms | -6.2 ms |
| Nine Glass effects (`steadyFull9`) | 7.9–8.2 ms | 9.1–9.5 ms | 9.7–10.0 ms | 11.1–11.4 ms | -2.5 to -2.2 ms |

A negative deadline margin means the frame completed before its deadline. A positive value means
it missed the deadline by that amount. The P99 deadline margin ranged from -0.9 to -0.3 ms for the
nine-effect runs and was -4.6 ms for the one-effect run, so every reported percentile remained
before the deadline.

The one-effect scenario is a whole-frame control, not the isolated or incremental cost of one Glass
effect. The nine-effect values span two clean benchmark passes; the one-effect values come from one
clean pass. These results describe two specific scenes on one device. Treat them as a guide, not a
performance guarantee or CI threshold. Applications should measure their own layouts and
interactions.

## What was tested

The `steadyFull` benchmark renders one compatible Glass effect, while `steadyFull9` renders nine.
Each scenario runs for three seconds and uses:

- A Pixel 6 running API 37 at 1080 × 2400 and a fixed 60 Hz refresh rate.
- The modern Android `RuntimeShader` renderer available on API 33 and newer.
- The unmodified `GlassDefaults.style`, including adaptive optics, default 16 dp corners, simple
  chromatic aberration, and default lighting, color, and rendering values.
- A release-like, non-debuggable build with `CompilationMode.Full`.
- Warm startup and eight measured iterations per run.
- Navigation, initial composition, and settling outside the measured block.

The device reported no thermal throttling during these runs. The fallback renderer was not
measured.

## What affects performance

- **Surface area:** Larger Glass surfaces process more pixels.
- **Number of effects:** More independent surfaces can add rendering and submission work.
- **Effect count:** Android RuntimeShader effects use the same fused base renderer for one or many
  surfaces; sibling attachment does not switch topology.
- **Changing content:** Moving or updating the captured source invalidates more retained work than
  redrawing an unchanged effect.
- **Dynamic optics:** Progressive blur and Full chromatic aberration increase sampling within the
  fused shader. Configured interaction optics use that same stable renderer, while interaction
  lighting uses a localized foreground patch to preserve content ordering. Live press, hover, and
  focus values update uniforms without replacing the fused base graph.
- **Device and display:** GPU capability, resolution, refresh rate, and thermal state all affect
  the result.

## Reproducing the measurement

Run the benchmark locally on an unlocked physical device at API 33 or newer. Pin the display to one
supported refresh rate, let the device cool, and run:

```shell
./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassProfilingBenchmark#steadyFull9
```

Replace `steadyFull9` with `steadyFull` to reproduce the one-effect control.

Results and Perfetto traces are copied to
`internal/benchmark/build/outputs/connected_android_test_additional_output/`. Run the scenario
without full composition tracing when collecting comparable metrics; opt in to full tracing only
for diagnostic attribution. The complete local runbook is in `internal/benchmark/README.md`.

A representative trace for the nine-effect results contains exactly nine effect-layer traversals
and two Vulkan submissions per frame. `HazeGlass.prepare` and `HazeGlass.runtimeDraw` average less
than 0.08 ms per effect; the remaining cost is primarily RenderThread drawing and Vulkan
submission.

The nine-effect interaction-update scenario recorded a 10.9 ms CPU P90 and a -1.2 ms
frame-overrun P90. Its live lighting and optics updates therefore remained inside the frame
deadline without constructing or switching render graphs. Interaction-only updates retain the
shader provider, source capture, effect topology, and layer allocation, but re-record the fused
output; separate local overlays measured slower because they restored layer replay and submission
cost.
