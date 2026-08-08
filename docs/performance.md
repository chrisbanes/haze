# Performance

Haze uses the best available rendering path on each platform, but real-time effects still add work.
The impact depends on the device, the affected area, how many effects are visible, and how often
their input changes.

## Recommended workflow

1. Start with the default Style and `HazeSampling.Default`.
2. Build the real screen, including its scrolling, transitions, and interactions.
3. Measure a release-like build on representative physical devices.
4. Change one setting at a time and compare both frame timing and visual quality.
5. Keep an override only when it provides clear value on the devices you support.

## Input scale

Sampling controls how much input an effect processes:

- **`Default` or `Adaptive`**: Recommended for most applications. Built-in effects adjust the
  quality and cost trade-off automatically.
- **`FullResolution`**: Use when target-device comparisons show that adaptive sampling loses
  important detail.
- **`Fixed(pixelFraction)`**: Use when you want an explicit, stable pixel budget. Higher values
  preserve more detail and cost more to render.

Blur and Glass adapt differently, so compare the result on the effect and layout you actually use.

## Common cost drivers

- **Affected area:** Larger effects process more content.
- **Number of effects:** Several independent effects cost more than one.
- **Changing input:** Scrolling and animation require more work than a stable background.
- **Effect complexity:** Progressive effects, masks, and advanced optics can add cost.
- **Device and display:** Resolution, refresh rate, and GPU capability affect the result.

## Effect-specific guidance

For Blur, see [Performance mode and layer expansion](blur/usage.md#performance-mode-and-layer-expansion). For Glass,
see the [Glass performance guide](glass/performance.md), which covers optical and interaction
choices specific to that effect.

## Measure on target devices

Use a release-like build on physical hardware and reproduce the interactions users will perform.
Keep device conditions and refresh rate consistent between runs. Measurements from another device
or layout are useful context, not a guarantee for your application.

For Android, [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
is a good starting point for repeatable frame measurements.

### A reference point, not a target

In Haze's 2026-08-04 Glass reference run on a Pixel 6 (Android 17/API 37, 1080×2400 at
60 Hz), the Gallery's `productPager` journey measured 7.5 ms P90 CPU frame duration and its
`playgroundTimeline` journey measured 11.2 ms. These are one workload on one device, not a
performance budget or promise for other devices and layouts. See the
[Glass reference measurements](glass/performance.md#reference-measurements) for the setup and
controlled scenarios.
