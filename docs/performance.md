# Performance

Start with Haze's defaults, then tune the effects that cause problems on your screen. Check
scrolling, transitions, and visual quality together: a setting is useful only if the screen still
looks right and feels smooth.

## Start with the defaults

Use `HazePerformanceMode.Default` for built-in Blur and Glass, and start Glass with
`GlassStyle.regular`. `Default` selects the fixed `Balanced` profile. Custom effects use
`HazeSampling.Default`.

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
| A starting point for most screens | `Default` (`Balanced`). |
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

The same `performanceMode` parameter is available on `hazeGlass`. To set a mode for a whole
subtree, provide `LocalHazePerformanceMode`; an explicit `performanceMode` argument overrides it.

`Performance`, `Balanced`, and `Quality` correspond to `Fixed(0f)`, `Fixed(0.5f)`, and
`Fixed(1f)`. The fraction describes quality, not a percentage reduction in rendering time.

!!! note "Measured quality trade-offs"

    In a two-pass test on a Pixel 8a (Android 17, 60 Hz, fixed-performance mode) with a changing
    background, Glass `Balanced` left about **7.7 ms** of frame-deadline headroom at P90,
    compared with **6.6 ms** for `Quality`. Glass `Performance` used roughly **9% less peak GPU
    memory** than `Quality` in both stable and changing-background tests, comparing the mean of
    the two pass-level medians for each mode and workload. Each method ran eight iterations per
    pass. These are reference points, not guaranteed savings on another screen. See the [full mode
    comparison](benchmark-results.md#performance-mode-calibration).

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

Use a [Glass container](effects/glass/containers.md) only where surfaces should merge. A merged
cluster draws one larger surface that covers its members and the bridges between them, so
measure clustered screens against independent surfaces before adopting them. A container also
shares one optical size, so one large member changes the diffusion of the small members next to
it.

In a two-pass test on a Pixel 8a (Android 17, 60 Hz, fixed-performance mode), merging two nearby
Regular Glass surfaces into one cluster over a changing source left about 4.5 ms less frame
deadline margin at P90 and used about 7% more peak GPU memory than drawing them independently,
even though its CPU frame duration P90 was 0.35 ms lower
([conditions and limits](benchmark-results.md#glass-containers)).

For styling examples, see the [Glass overview](effects/glass/index.md).

<a id="backdrop-input"></a>

## Backdrop versus Sources

`HazeInput.Backdrop` lets built-in Blur and Glass use pixels already drawn behind them in the
current window. On supported Android devices it can filter those pixels directly, without marking
the background with `hazeSource`.

Pass a `HazeState` or `HazeInput.Sources` to `Backdrop` and keep the `hazeSource` setup if the
effect must work when native rendering is unavailable, including on other platforms or after a
native failure. Without captured sources, the fallback has no background content to process.

Use explicit `HazeInput.Sources` when you need to select particular captured content. Native
Backdrop uses the combined earlier pixels in the same window; it cannot include later drawing
or content from another dialog, popup, or window. See [Explicit inputs](core-concepts.md#explicit-inputs)
for examples.

!!! warning "Experimental native Backdrop"

    Native rendering requires a hardware-accelerated window on Android 37.2 or later and
    is disabled by default. Set `HazeFeatureFlags.isPlatformBackdropEnabled = true` before the
    effect is first composed to make it eligible. Unsupported configurations use source fallback.
    See [Android window backdrops](core-concepts.md#android-window-backdrops).

Compare native Backdrop with Sources before enabling it for performance reasons. Sources can
reuse processed output when their input is stable; native Backdrop filters window pixels again
when the effect draws.

!!! note "Measured Backdrop cost"

    On a Pixel 8a (Android 17, 60 Hz), native Backdrop had **23–46% higher CPU frame P90** than
    Sources in the tested single-surface Glass workloads. Those figures are based on two passes in
    opposite orders at `Quality`, with eight iterations per method per pass. Native rendering can
    simplify setup, but these tests did not show a CPU performance benefit for Glass.
    See the [full Backdrop comparison](benchmark-results.md#native-android-backdrop).

<a id="performance-measurements"></a>

The [benchmark results](benchmark-results.md) contain the complete tables and test conditions.
Use them as reference points when measuring your own screen.
