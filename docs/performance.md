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

`HazePerformanceMode` controls the quality and rendering cost of built-in Blur and Glass:

- **`Default` or `Adaptive`**: Adjusts quality automatically. Start here for most screens.
- **`Quality`**: Prioritises visual detail and generally requires more rendering work.
- **`Balanced`**: Offers a middle ground between detail and rendering cost.
- **`Performance`**: Prioritises lower rendering cost; fine detail may look softer or more pixelated.
- **`Fixed(qualityFraction)`**: Choose a quality level from `0f` (lowest supported quality) to `1f`
  (highest supported quality). `Performance`, `Balanced`, and `Quality` correspond to `0f`, `0.5f`,
  and `1f` respectively.

`qualityFraction` is a quality setting, not a percentage of pixels or a promised reduction in
rendering time. A fixed level keeps your chosen setting instead of adjusting it automatically;
its appearance and cost still depend on the effect, surface size, and device.

If an effect looks too pixelated, try a higher quality level. Compare it while scrolling and
animating, since a sharper effect may also make it harder to keep frames smooth. Custom effects
use `HazeSampling`; see the [migration guide](migrating-2.0.md) for older built-in sampling settings.

## Common cost drivers

- **Affected area:** Larger effects process more content.
- **Number of effects:** Several independent effects cost more than one.
- **Changing input:** Scrolling and animation require more work than a stable background.
- **Effect complexity:** Progressive effects, masks, and advanced optics can add cost.
- **Device and display:** Resolution, refresh rate, and GPU capability affect the result.

### Stable and changing sources

With `HazeInput.Sources`, a stable background generally costs less than one that changes every
frame. Keep animations outside the `hazeSource` subtree when the effect does not need to include
them. A stable background does not make the effect free: measure both stationary content and the
scrolling or animated content users will see.

<a id="effect-specific-guidance"></a>

## Blur

Progressive Blur varies blur intensity across the surface. If you only need to fade opacity, use a
mask; see [Progressive Blur and masks](blur/usage.md#progressive-blur-and-masks).

For source-backed Blur, `expandLayerBounds` allows the capture layer to expand by the resolved blur
radius. This gives the blur surrounding input to sample, at the cost of a larger capture area.
Disabling it limits that area and can change the result near the edges. Keep the default unless
visual and performance comparisons justify changing it. See the
[modifier example](blur/usage.md#performance-mode-and-layer-expansion).

## Glass

Start with `GlassStyle.regular`. Progressive blur and Full chromatic aberration can add work;
measure them at the surface sizes and effect counts your screen uses. Animated lighting,
refraction, and transforms also add work during hover, focus, and press responses. Include those
states, background scrolling, and layout transitions in your comparison.

Check both the first appearance of a Glass surface and its ongoing interactions. A screen that
scrolls smoothly can still pause when an effect first appears.

For styling and API examples, see the [Glass overview](effects/glass.md).

### Choosing a fixed quality level

Higher quality can make fine detail and glass edges clearer, but leaves less time for the rest of
your screen to render. If you need a fixed level, start with `Balanced` and increase it only when
it visibly improves your screen.

For reference, these measurements used one 280 dp × 180 dp Regular Glass surface with continuously
changing source content on a Pixel 6 running Android 17 at 60 Hz. CPU placement was controlled to
make the levels easier to compare. Each number averages the P90 from two runs; negative frame
overrun means the frame finished before its deadline, with more negative values indicating more
spare time.

| `qualityFraction` | CPU frame duration: mean per-pass P90 (ms) | Frame overrun: mean per-pass P90 (ms) |
| --- | ---: | ---: |
| `0` | 3.17 | -10.95 |
| `0.25` | 3.12 | -10.17 |
| `1f / 3f` | 3.11 | -9.77 |
| `0.5` (`Balanced`) | 3.17 | -9.11 |
| `0.75` | 4.87 | -6.34 |
| `1` (`Quality`) | 4.74 | -5.53 |

In this scene, increasing from `Balanced` to `Fixed(0.75f)` used another 2.77 ms of frame deadline
margin. All levels met their deadlines, but a screen with more effects or a higher refresh rate
may have less time to spare. These Android results do not predict performance on Web or other
devices. See the [full measurements and test conditions](benchmark-results.md#glass-fixed-quality-with-controlled-cpu-placement-2026-09-11).

## Measure on target devices

Use a release-like build on physical hardware and reproduce the interactions users will perform.
Keep device conditions and refresh rate consistent between runs. Compare frame timing and visual
quality, including on the slowest devices you support.

For Android, [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
provides repeatable frame measurements. Use frame overrun to identify deadline misses and CPU frame
duration to assess UI-thread and RenderThread cost. Neither directly measures GPU shader duration.

Repeat comparisons to check that an improvement holds across runs. For details of Haze's own
measurements, see the [benchmark results](benchmark-results.md).

<a id="haze-2-compared-with-haze-1"></a>
<a id="a-reference-point-not-a-target"></a>

See the [Haze 1 versus Haze 2 comparison](benchmark-results.md#haze-2-compared-with-haze-1) and
[Blur and Glass reference measurements](benchmark-results.md), with the recorded setup and
limitations for each run.
