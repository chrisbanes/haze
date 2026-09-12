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

!!! note "Backdrop performance"

    Do not assume that an eligible native Android `HazeInput.Backdrop` is faster than
    `HazeInput.Sources`. A source-backed effect can retain captured and processed output while its
    input is unchanged. The native path instead filters the earlier pixels in the current window
    when the effect is drawn, so repeated redraws can cost more even when those pixels appear stable.
    The balance depends on the effect, invalidation pattern, surface area, and device. Compare both
    inputs with the real screen behaviour before choosing one for performance reasons.

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
devices. See the [full measurements and test conditions](benchmark-results.md#glass-fixed-quality).

## Performance measurements

The following measurements provide a reference point for built-in Blur and Glass using
`HazeInput.Sources` and `HazePerformanceMode.Quality`. They were recorded on a Pixel 8a at 60 Hz.
Each value is the arithmetic mean of the P90 from two order-reversed passes, with eight measured
iterations per workload. Negative frame overrun means the frame finished before its deadline; more
negative values indicate more spare time.

| Workload | CPU frame P90 (ms) | Frame overrun P90 (ms) |
| --- | ---: | ---: |
| Blur, stable input | 4.59 | -7.75 |
| Blur, changing input | 4.59 | -5.40 |
| Glass, stable input | 3.54 | -10.69 |
| Glass, changing input | 3.46 | -1.84 |
| Nine Glass nodes, changing input | 3.94 | -6.85 |

All workloads retained spare frame-deadline time at P90. These normal-scheduling results describe
this device and these workloads; they are not performance guarantees for other screens or devices.

### Backdrop versus Sources

On eligible Android devices, the same workloads can use native `HazeInput.Backdrop` instead of
`HazeInput.Sources`. This comparison uses the same runs and aggregation as the general measurements.

!!! warning "Experimental native Backdrop"

    Native Backdrop is disabled by default. Set
    `HazeFeatureFlags.isPlatformBackdropEnabled = true` before attaching the effect node. Enabling
    the flag makes native rendering eligible, not guaranteed; unsupported configurations use the
    source fallback. See [Android window backdrops](core-concepts.md#android-window-backdrops).

| Quality workload | Sources CPU P90 (ms) | Backdrop CPU P90 (ms) | Backdrop CPU difference | Sources overrun P90 (ms) | Backdrop overrun P90 (ms) |
| --- | ---: | ---: | ---: | ---: | ---: |
| Blur, stable input | 4.59 | 5.07 | 10% higher | -7.75 | -2.44 |
| Blur, changing input | 4.59 | 5.12 | 12% higher | -5.40 | -2.21 |
| Glass, stable input | 3.54 | 5.18 | 46% higher | -10.69 | -0.48 |
| Glass, changing input | 3.46 | 4.24 | 23% higher | -1.84 | -0.48 |
| Nine Glass nodes, changing input | 3.94 | 4.74 | 20% higher | -6.85 | -5.18 |

Backdrop had higher CPU frame P90 in both passes for changing-input Blur and all three Glass
workloads. Stable Blur changed order between the passes, so its mean does not establish a stable
ranking. Backdrop used approximately 6–10 MB less median peak GPU memory. See the
[complete setup and measurements](benchmark-results.md#native-android-backdrop).

## Measure on target devices

Use a release-like build on physical hardware and reproduce the interactions users will perform.
Keep device conditions and refresh rate consistent between runs. Compare frame timing and visual
quality, including on the slowest devices you support.

For Android, [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
provides repeatable frame measurements. Use frame overrun to identify deadline misses and CPU frame
duration to assess UI-thread and RenderThread cost. Neither directly measures GPU shader duration.

Repeat comparisons to check that an improvement holds across runs. For details of Haze's own
measurements, see the [benchmark results](benchmark-results.md).
