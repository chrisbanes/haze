# Performance

Haze uses the best available rendering path on each platform, but real-time effects still add work.
The impact depends on the device, the affected area, how many effects are visible, and how often
their input changes.

## Recommended workflow

1. Start built-in effects with the default Style and `HazePerformanceMode.Default`; custom effects
   continue to use `HazeSampling.Default`.
2. Build the real screen, including its scrolling, transitions, and interactions.
3. Measure a release-like build on representative physical devices.
4. Change one setting at a time and compare both frame timing and visual quality.
5. Keep an override only when it provides clear value on the devices you support.

<a id="input-scale"></a>

## Performance mode

`HazePerformanceMode` controls the quality and cost trade-off for built-in effects:

- **`Default` or `Adaptive`**: Recommended for most applications. Built-in effects adjust the
  quality and cost trade-off automatically.
- **`Quality`**, **`Balanced`**, or **`Performance`**: Select a named, deterministic profile.
- **`Fixed(qualityFraction)`**: Select a normalized, deterministic profile when the named
  profiles are not the right fit.

`Default` is `Adaptive`, and `Quality` replaces the previous built-in full-resolution choice.
There is no formula for translating a previous built-in fixed input-pixel fraction: remeasure an
explicit `Fixed(qualityFraction)` choice on the effect and layout you actually use. Blur and Glass
adapt differently, so compare their results independently. Custom effects instead use
`HazeSampling` to control how much input they process.

## Common cost drivers

- **Affected area:** Larger effects process more content.
- **Number of effects:** Several independent effects cost more than one.
- **Changing input:** Scrolling and animation require more work than a stable background.
- **Effect complexity:** Progressive effects, masks, and advanced optics can add cost.
- **Device and display:** Resolution, refresh rate, and GPU capability affect the result.

### Stable and changing sources

With `HazeInput.Sources`, Haze retains a source capture so unrelated sibling redraws can reuse it.
Changes to the source's drawing still refresh that capture. Keep content that changes every frame
outside the `hazeSource` subtree when the effect does not need to sample it.

Reusing a capture avoids recording the source again; drawing the effect can still require renderer
and GPU work. Measure both a stable background and scrolling or animated input. These capture
details apply to source-backed effects; native `HazeInput.Backdrop` uses a different rendering path.

## Effect-specific guidance

For Blur, see [Performance mode and layer expansion](blur/usage.md#performance-mode-and-layer-expansion). For Glass,
see the [Glass performance guide](glass/performance.md), which covers optical and interaction
choices specific to that effect.

## Measure on target devices

Use a release-like build on physical hardware and reproduce the interactions users will perform.
Keep device conditions and refresh rate consistent between runs. Measurements from another device
or layout are useful context, not a guarantee for your application.

Repeat comparisons in both build orders. Android's
[fixed-performance mode](https://developer.android.com/games/optimize/adpf/fixed-performance-mode)
still allows CPU core selection to change, which can move tail frame timings even when the code
is unchanged. Use traces to check CPU placement when repeated results disagree.

For Android, [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
is a good starting point for repeatable frame measurements.

### Haze 2 compared with Haze 1

Haze 2 was faster in both of the sample interactions shared with Haze 1. P90 CPU frame duration
improved by 37% while scrolling the Images List and by 15% while dragging the Credit Card.

| Workload | Haze 1 | Haze 2 | Improvement |
| --- | ---: | ---: | ---: |
| Images List scrolling | 14.9 ms | 9.3 ms | 37% faster |
| Credit Card dragging | 11.5 ms | 9.8 ms | 15% faster |

These results come from Android Macrobenchmarks on a Pixel 6 running Android 17 at 60 Hz, with 32
iterations per workload. P90 highlights the slower frames during each interaction. Treat these as
a useful reference rather than a guarantee for a different screen or device.

### A reference point, not a target

In Haze's 2026-08-04 Glass reference run on a Pixel 6 (Android 17/API 37, 1080×2400 at
60 Hz), the Gallery's `productPager` journey measured 7.5 ms P90 CPU frame duration and its
`playgroundTimeline` journey measured 11.2 ms. These are one workload on one device, not a
performance budget or promise for other devices and layouts. See the
[Glass reference measurements](glass/performance.md#reference-measurements) for the setup and
controlled scenarios.
