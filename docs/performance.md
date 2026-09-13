# Performance

Start with Haze's defaults, then tune the effects that cause problems on your screen. Check
scrolling, transitions, and visual quality together: a setting is useful only if the screen still
looks right and feels smooth.

## Start with the defaults

Use `HazePerformanceMode.Default` for built-in Blur and Glass, and start Glass with
`GlassStyle.regular`. The default performance mode adjusts quality automatically. Custom effects
use `HazeSampling.Default`.

Build the complete screen before choosing overrides. The area covered by effects, their number,
and the content moving behind them all affect the cost.

<a id="measure-on-target-devices"></a>

## Measure your screen

Use a release-like build on physical devices, including the slowest devices you support. Test
scrolling, transitions, and the first appearance of an effect. For interactive Glass surfaces,
also test hover, focus, and press responses.

Change one setting at a time. Keep device conditions and refresh rate consistent, then repeat the
same interaction. Look for fewer missed frame deadlines and smoother motion without an
unacceptable loss of detail.

On Android, [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
can measure these interactions. Frame overrun tells you whether frames finish before their
deadline; positive values indicate misses. CPU frame duration helps assess UI-thread and
RenderThread cost, but does not directly measure GPU shader time. Check both metrics before
deciding that a change helped.

<a id="common-cost-drivers"></a>

## If frames are slow

Try these changes in order, measuring after each:

1. **Reduce the affected area.** Apply the effect only where it is visible. Smaller surfaces
   process less content.
2. **Reduce unnecessary effects.** Remove effects that add little to the design. Where the design
   allows it, compare one shared surface with several independent surfaces.
3. **Try a lower quality setting.** Test `Performance`, then increase quality if the result looks
   too soft or pixelated. See [Choose a performance mode](#performance-mode).
4. **Simplify the styling.** Try uniform blur in place of progressive blur, or reduce Glass
   features such as Full chromatic aberration. Keep the simpler style if the visual difference
   does not justify the cost.
5. **Keep unrelated animation out of captured sources.** With `HazeInput.Sources`, move animation
   outside the `hazeSource` subtree when the effect does not need to include it.

<a id="stable-and-changing-sources"></a>

A stable source can reuse captured and processed output. Scrolling or animated backgrounds need
fresh input, so always test those interactions as well as stationary content.

<a id="input-scale"></a>
<a id="performance-mode"></a>
<a id="performance-modes"></a>
<a id="choosing-a-fixed-quality-level"></a>

## Choose a performance mode

`HazePerformanceMode` controls the quality and rendering cost of built-in Blur and Glass. Choose
based on the problem you can see:

| What you need | What to try |
| --- | --- |
| A starting point for most screens | `Default` or `Adaptive`, which adjusts quality automatically. |
| Lower rendering cost | `Performance`; check for softer or more pixelated detail. |
| A consistent middle quality setting | `Balanced`. |
| Sharper detail | `Quality`; check scrolling and transitions for missed deadlines. |
| A specific fixed quality setting | `Fixed(qualityFraction)`, after comparing the named modes. |

For example, to try a lower-cost Blur setting with an existing source input:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Sources(hazeState),
  performanceMode = HazePerformanceMode.Performance,
)
```

The same `performanceMode` parameter is available on `hazeGlass`. Fixed modes keep the chosen
quality setting instead of adapting it. `Performance`, `Balanced`, and `Quality` correspond to
`Fixed(0f)`, `Fixed(0.5f)`, and `Fixed(1f)`. The fraction describes quality, not a percentage
reduction in rendering time.

!!! note "Measured quality trade-offs"

    In a Pixel 8a test at 60 Hz with a changing background, Glass `Balanced` left about **5 ms**
    of frame-deadline headroom at P90, compared with **1.7 ms** for `Quality`. Glass
    `Performance` used roughly **9% less median peak GPU memory** than `Quality` in both stable
    and changing-background tests. These observations come from one pass with eight iterations
    per case; they are useful reference points, not guaranteed savings on another screen.
    See the [full mode comparison](benchmark-results.md#performance-mode-calibration).

Keep a fixed override only when repeated measurements and visual checks show a useful benefit.
A lower quality setting does not guarantee a lower CPU frame time in every workload.

<a id="effect-specific-guidance"></a>

## Blur

If you only need to fade an effect's opacity, use a mask instead of progressive blur.
Progressive blur is useful when the blur intensity itself needs to vary across the surface.
See [Progressive Blur and masks](blur/usage.md#progressive-blur-and-masks).

Keep `expandLayerBounds` enabled unless a measured improvement justifies changing it. It lets
source-backed Blur sample surrounding content near the edges. Disabling it reduces the capture
area but can change the edge appearance. See the
[modifier example](blur/usage.md#performance-mode-and-layer-expansion).

## Glass

Start with `GlassStyle.regular` and add optical features only where they improve the design.
If Glass is expensive, compare simpler blur and chromatic aberration settings at the sizes and
effect counts your screen actually uses.

Check an effect's first appearance separately from its ongoing animation: smooth scrolling does
not rule out a pause when a Glass surface is first created. Include lighting, refraction, and
transform changes during interaction in your measurements.

For styling examples, see the [Glass overview](effects/glass.md).

<a id="backdrop-input"></a>

## Backdrop versus Sources

`HazeInput.Backdrop` lets built-in Blur and Glass consume pixels already drawn behind them in the
current window. Its native Android path can filter those pixels directly, without marking the
background with `hazeSource` or capturing a separate source layer.

The current API still takes a `HazeState` or `HazeInput.Sources` for fallback. Keep the
`hazeSource` setup if you need the effect to work when native rendering is unavailable, including
on other platforms or after a native failure. Without captured sources, the fallback has no
background content to process.

Use explicit `HazeInput.Sources` when you need to select particular captured content. Native
Backdrop uses the combined earlier pixels in the same window; it cannot include later drawing
or content from another dialog, popup, or window. See [Explicit inputs](core-concepts.md#explicit-inputs)
for examples.

!!! warning "Experimental native Backdrop"

    Native rendering requires a supported, hardware-accelerated Android 37.2 window and is
    disabled by default. Set `HazeFeatureFlags.isPlatformBackdropEnabled = true` before attaching
    the effect node to make it eligible. Unsupported configurations use source fallback.
    See [Android window backdrops](core-concepts.md#android-window-backdrops).

Compare native Backdrop with Sources before enabling it for performance reasons. Sources can
reuse processed output when their input is stable; native Backdrop filters window pixels again
when the effect draws.

!!! note "Measured Backdrop cost"

    On a Pixel 8a at 60 Hz, native Backdrop had **23–46% higher CPU frame P90** than Sources in
    the tested single-surface Glass workloads. Those figures are based on two passes in opposite
    orders at `Quality`, with eight iterations per case. Native rendering can simplify source
    capture, but these tests did not show a CPU performance benefit for Glass.
    See the [full Backdrop comparison](benchmark-results.md#native-android-backdrop).

<a id="performance-measurements"></a>

The [benchmark results](benchmark-results.md) contain the complete tables and test conditions.
Use them as reference points when measuring your own screen.
