Real-time blurring is a non-trivial operation, especially for mobile devices, so developers are rightly worried about the performance impact of using something like Haze.

Haze tries to use the most performant mechanism possible on each platform, which can basically be simplified into 2: `RenderEffect` on Android, and using Skia's `ImageFilter`s directly on iOS and Desktop.

## Input Scale

You can provide an input scale value which determines how much the content is scaled in both the x and y dimensions, allowing the blur effect to be potentially applied over scaled-down content (and thus less pixels), before being scaled back up and drawn at the original size. You can find more information on how to use this [here](blur/usage.md#input-scale).

In terms of the performance benefit which scaling provides, it's fairly small. In our Android benchmark tests, using an `inputScale` set to `0.5` reduced the _cost of Haze_ by **5-20%**. You can read more about this below.

!!! abstract "Cost of Haze"
    Just to call out: the percentage that I mentioned is a reduction in the cost of Haze, not the total frame duration. Haze itself introduces a cost, which you can read more about below. The reduction in total frame duration duration will be in the region of 3-5%.

## Benchmarks

To quantify performance, we have a number of [Macrobenchmark tests](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) to measure Haze's effect on drawing performance on Android. We'll be using these on every major release to ensure that we do not unwittingly regress performance.

Anyway, in the words of Jerry Maguire, "Show Me The Money"...

We currently have 4 benchmark scenarios, each of them is one of the samples in the sample app, and picked to cover different things:

- **Scaffold**. The simple example, where the app bar and bottom navigation bar are blurred, with a scrollable list. This example uses rectangular haze areas.
- **Scaffold, with progressive**. Same as Scaffold, but using a progressive blur.
- **Images List**. Each item in the list has it's own `hazeSource` and `hazeEffect`. As each item has it's own `hazeSource`, the internal haze state does not change all that much (the list item content moves, but the `hazeEffect` doesn't in terms of local coordinates). This is more about multiple testing `RenderNode`s. This example uses rounded rectangle haze areas (i.e. we use `clipPath`).
- **Credit Card**. A simple example, where the user can drag the `hazeEffect`. This tests how fast Haze's internal state invalidates and propogates to the `RenderNode`s. This example uses rounded rectangle haze areas like 'Images List'.

!!! abstract "Test setup"
    All of the tests were ran with 16 iterations on a Pixel 6, running the latest version of Android available.

As with all benchmark tests, the results are only true for the exact things being tested. Using Haze in your own applications may result in different performance characteristics, so it is wise to write your own performance tests to validate the impact to your apps. Benchmark tests will always have variability in them too, so don't take the numbers listed below as exact values. Look at them more as a guide.

The numbers listed below the P90 frame durations in milliseconds, which tend to be a good indicator of frames where a user interaction is happening (scrolling, etc). However, as these are the P90 values, these indicate the longest 10% frame durations, and thus are (probably) not indicitive of the performance which users see most of the time. It all depends on the distribution of the frame durations, but we're quickly getting into entry-level statistics, which is beyond what we're trying to document here.

#### Cost of Haze

We can also measure the rough cost of using Haze in the same samples. Here we've ran the same tests, with Haze being completely disabled:

| Test          | v1.x (disabled)  | v1.x      | Difference   |
| ------------- | ------------------| -----------| ------------ |
| Scaffold      | 7.5 ms            | 9.7 ms     | +29%         |
| Images List   | 6.6 ms            | 9.6 ms     | +45%         |
| Credit Card   | 6.6 ms            | 13.1 ms    | +98%         |

#### Cost of features

We can also measure the rough cost of using features, such as input scale, progressive and masking:

| Test                                      | P90 frame duration (ms)  | Difference (in Haze cost) |
| -------------                             | -------------------------| -----------|
| Scaffold                                  | 9.7 ms                   | -          |
| Scaffold (inputScale = 0.5)               | 9.6 ms                   | -5%        |
| Scaffold (masked)                         | 9.8 ms                   | +5%        |
| Scaffold (progressive)                    | 9.7 ms                   | 0%         |
| Scaffold (progressive, inputScale = 0.5)  | 9.4 ms                   | -14%       |

The values are all very close, with the differences easily being within a margin of error, so don't use these differences as exact values (especially with the variability that we mentioned above). I think there's two big take aways here though:

- Masking has a negligible effect on frame durations.
- Progessive has a negligible effect on frame durations, when using using our custom blur shader (Android SDK 34+, all other platforms).
- Input Scale has a small but positive effect on frame duration.

!!! example "Full results"
    For those interested, you can find the full results in this [spreadsheet](https://docs.google.com/spreadsheets/d/1wZ9pbX0HDIa08ITwYy7BrYYwOq2sX-HUyAMQlcb3dI4/edit?usp=sharing).

### Glass on Android

Glass has a separate benchmark because several sibling Glass surfaces produce a more complicated
render graph than blur alone. The measurements below cover the modern Android `RuntimeShader` path
on API 33 and newer. They do not cover the fallback renderer.

!!! abstract "Measurement snapshot"
    These results were recorded locally on 24 July 2026. They describe one device, scene, and
    implementation revision; they are not a universal performance guarantee or a CI threshold.

#### Test setup

The focused `steadyFull9` scenario renders nine compatible Glass effects for three seconds. It uses
a release-like, non-debuggable target, `CompilationMode.Full`, warm startup, and eight measured
iterations. Navigation, initial composition, and eight settling frames happen outside the measured
block.

The device was a Pixel 6 running API 37 at 1080 × 2400 and a fixed 60 Hz refresh rate. Its thermal
status was 0, with the G3D sensor at 34 °C before the run, and the benchmark reported no thermal
throttle sleeps.

The comparable result is AndroidX Macrobenchmark's `frameDurationCpuMs`, which includes work from
the UI thread and RenderThread. Lower values are better.

| Optimization checkpoint | P50 | P90 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: |
| Shared blur | 14.91 ms | 18.49 ms | 19.33 ms | 22.37 ms |
| Shared blur and refraction-detail atlas | 14.09 ms | 17.06 ms | 18.13 ms | 20.16 ms |
| Shared blur, detail atlas, and optical/depth atlas | 11.56 ms | 13.37 ms | 14.12 ms | 15.43 ms |
| Optical/depth atlas and retained shader cuts (three runs) | **11.52–11.78 ms** | **13.23–13.85 ms** | **13.82–14.58 ms** | **15.07–15.74 ms** |

The final row is a range from three confirmation runs, rather than the faster result in isolation.
Its P90 is 25.1–28.4% lower than the shared-blur checkpoint. The median of the eight per-iteration
P90 values was 12.78–12.84 ms across those runs.

The three final `frameOverrunMs` distributions were:

| P50 | P90 | P95 | P99 |
| ---: | ---: | ---: | ---: |
| -2.07 to -1.87 ms | -0.65 to 0.03 ms | -0.09 to 0.64 ms | 1.07 to 1.70 ms |

A negative overrun means the frame completed before its deadline. A positive value means it missed
the deadline by that amount.

#### Shader sampling follow-up

The shader follow-up used adjacent A/B controls because the pooled frame tail varied between runs.
This table reports the median of each run's eight per-iteration percentiles:

| Variant | P50 | P90 | P95 | `frameOverrunMs` P90 |
| --- | ---: | ---: | ---: | ---: |
| Paired optical/depth-atlas baseline | 11.70 ms | 13.37 ms | 14.10 ms | -0.05 ms |
| Base-read elision (two runs) | 11.32–11.50 ms | 12.55 ms | 13.12–13.17 ms | -1.08 to -0.88 ms |
| Base-read elision and four-fetch prefilter (two runs) | 11.17–11.20 ms | 12.57 ms | 13.03–13.15 ms | -1.30 to -1.23 ms |
| Signed-distance reuse (three runs) | 11.56–11.73 ms | 12.78–12.84 ms | 13.12–13.42 ms | -1.18 to -1.09 ms |

The optical shader now samples the unrefracted base content only in the soft-edge band; fully
covered interior pixels return the processed sample directly. The downsample prefilter now uses
four bilinear samples at half-pixel offsets. Those samples reproduce the previous nine-tap
separable binomial weights while removing five shader evaluations per downsampled pixel. The
optical and refraction-detail shaders also reuse the rounded-rectangle signed distance already
calculated for clipping when evaluating surface height, avoiding a second rounded-rectangle
distance calculation per fragment.

The signed-distance change was retained based on an adjacent A/B/A comparison: its per-iteration
P90 was 12.80 ms, rose to 13.31 ms when the change was removed, and returned to 12.84 ms when it
was restored. A later confirmation measured 12.78 ms. Absolute values from earlier runs are not
used to judge that individual change because device-level benchmark drift was larger than the
expected shader-level improvement.

Runtime identity branches, a specialized default-material shader, a balanced atlas lookup,
hand-expanded Fresnel arithmetic, and a squared-length chromatic threshold were also measured.
They were neutral or worse in the paired tail measurements, so none were retained. JVM shader
tests, Android-host tests, and the library screenshot suite passed without baseline changes for
the retained changes.

#### What the profiler showed

The controlled feature-removal scenarios isolated the refraction graph as the main cost. Before
the atlas work, removing refraction reduced P90 to 13.64 ms, while removing the rim left it at
approximately 19.0 ms.

A representative Perfetto comparison between the detail-atlas and final optical-atlas checkpoints
showed the reduction downstream on RenderThread:

| Perfetto slice P90 | Detail atlas | Optical/depth atlas | Retained shader cuts (three traces) |
| --- | ---: | ---: | ---: |
| `Drawing` | 14.04 ms | 10.22 ms | 9.89–10.31 ms |
| `QueueSubmit` | 5.27 ms | 4.18 ms | 4.11–4.33 ms |
| `Vulkan finish frame` | 6.54 ms | 2.64 ms | 2.73–2.82 ms |

The final trace's application markers were much smaller: `HazeGlass.runtimeDraw` averaged 0.117 ms,
`HazeGlass.optical` averaged 0.028 ms, and `HazeGlass.detail` averaged 0.014 ms. These markers
measure CPU-side recording and submission, not GPU execution. Together with the RenderThread
slices, they indicate that reducing render-target and filter boundaries was more useful than
optimizing Kotlin work.

The longest final-trace `Drawing` slice spent 10.10 of its 12.17 ms running and 1.54 ms in
uninterruptible waits. All of those waits overlapped `QueueSubmit`; 0.51 ms also overlapped
`Vulkan finish frame`. RenderThread runnable P99 was 0.42 ms, while main-thread traversal P90 was
2.64 ms. Application `D` states were confined to RenderThread and the Mali backend, with no
recorded I/O wait, and there was no synchronous Binder wait. The remaining cost is therefore still
graphics execution and synchronization rather than main-thread work, I/O, IPC, or scheduler
contention.

The implementation now shares retained blur/capture work across compatible sibling effects. It
renders refraction detail, then depth mixing and the optical shader, into tiled atlases; each effect
draws its slice of the shared result without filtering. Effects with incompatible styles,
progressive blur, or interaction-driven optics continue to use a dedicated rendering path.

!!! info "AndroidX and Perfetto report different frame metrics"
    The table above uses AndroidX `frameDurationCpuMs`. A representative final trace reported a
    Perfetto FrameTimeline presentation-duration P90 of 16.90 ms. That is a different metric and
    frame population, so the two values should not be compared directly. Among its 184 application
    frames, the trace contained one application-deadline miss and one dropped frame.

#### Reproducing the measurement

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
