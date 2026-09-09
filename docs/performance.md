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
- **`Fixed(qualityFraction)`**: Select a normalized, deterministic profile from `0f` through `1f`
  when the named profiles are not the right fit.

`Default` is `Adaptive`. Blur and Glass resolve these profiles differently, so a quality fraction
is not an input-pixel scale or a cross-effect resolution guarantee. Custom effects use
`HazeSampling` to control how much input they process. For older built-in sampling settings, see
the [migration guide](migrating-2.0.md).

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

Measure effect attachment separately from steady-state drawing. Glass retains runtime shaders
between draws, but renderer and submission work can remain after source capture and effect
creation have settled. A slow frame alone does not identify shader compilation as the cause;
inspect main-thread and RenderThread work before changing the material's optics.

For styling and API examples, see the [Glass overview](effects/glass.md).

## Measure on target devices

Use a release-like build on physical hardware and reproduce the interactions users will perform.
Keep device conditions and refresh rate consistent between runs. Compare frame timing and visual
quality, including on the slowest devices you support.

For Android, [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
provides repeatable frame measurements. Use frame overrun to identify deadline misses and CPU frame
duration to assess UI-thread and RenderThread cost. Neither directly measures GPU shader duration.

Repeat comparisons in both build orders. If repeated results disagree, inspect traces before
attributing the difference to the code. Haze's [Android benchmark runbook][benchmark-runbook] covers
device preparation, CPU-placement checks, workload selection, and trace interpretation.

<a id="haze-2-compared-with-haze-1"></a>
<a id="a-reference-point-not-a-target"></a>

See the [Haze 1 versus Haze 2 comparison](benchmark-results.md#haze-2-compared-with-haze-1) and
[Blur and Glass reference measurements](benchmark-results.md), with the recorded setup and
limitations for each run.

[benchmark-runbook]: https://github.com/chrisbanes/haze/blob/main/internal/benchmark/README.md
