# ADR-0005: Use cadence-weighted adaptive input scaling for Glass

## Status

Accepted

## Date

2026-08-03

## Context

Glass retains several platform-specific rendering layers. Their cost depends on both the pixels in
the active layer graph and how often that graph consumes new input. Raw material area is therefore
insufficient: a large retained result can be cheap when reused, while a smaller result updated on
every animation frame can remain fill-rate bound.

Glass also exposes optical detail more readily than Blur, so automatic scaling must keep a
high-quality tier for light or stable work. Frame-time feedback and a scheduler spanning multiple
Glass nodes remain out of scope.

## Decision

`HazeSampling.Default` points to `HazeSampling.Adaptive`, which selects only pixel fractions `0.5`
or `0.25` (linear scales `sqrt(0.5)`, approximately `0.707`, or `0.5`) from a deterministic
workload score:

- The runtime builds the real platform-specific retained-layer plan at `sqrt(0.5)`. The plan
  includes expanded sample dimensions and every active blur, depth, refraction-detail, rim,
  interaction, and group-composite layer.
- A distinct input capture, style/runtime dirty version, material or layer geometry, or interaction
  state counts as an update. Repeated draws of the same retained input do not.
- Updates no more than 100ms apart form a burst. The retained pixel count is multiplied by up to
  three updates, producing a retained pixel-update score. A quiet interval resets the multiplier.
- Scores at or above 1,500,000 select the `0.25` pixel-fraction tier; lower scores select `0.5`.
  This means the aggressive tier can be entered by 1.5M pixels once, 750k pixels twice, or 500k
  pixels three times.
- Once selected, the `0.25` tier is retained until the score falls below 1,312,500, a 12.5% exit
  margin. This absorbs small geometry and cadence changes without repeatedly reallocating layers.

`FullResolution` remains exactly `1.0`, and `Fixed(pixelFraction)` remains authoritative. Selecting
any non-adaptive policy resets adaptive history. Blur uses the same adaptive default mode and
cadence mechanism with its own radius-and-area workload ladder.

## Validation

On the retained-shader implementation, an eight-iteration release Pixel 6 Macrobenchmark of the
same 280dp by 180dp Glass profiling surface measured linear scale `0.75` at 5.9ms CPU P50 / 8.2ms
P90 and linear scale `0.5` at 5.4ms P50 / 7.9ms P90. Both measurements were comfortably within the
frame budget for this isolated, stable-sized workload, and they bracket the selected balanced
linear scale of approximately `0.707`.

The matched animated Glass Playground workload made the cadence cost visible. At `0.75`, CPU frame
time measured 22.9ms P50 / 30.1ms P90 and frame overrun measured 14.4ms P50 / 29.2ms P90. At `0.5`,
CPU frame time fell to 13.8ms P50 / 19.5ms P90, while frame overrun fell to 1.6ms P50 / 8.2ms P90.
That is a 39.7% P50 and 35.2% P90 CPU-frame reduction. Median peak GPU memory also fell from
133,346KB to 92,468KB (30.7%). These measurements compared linear scales `0.75` and `0.5` under the
same eight-iteration benchmark and device conditions; Macrobenchmark JSON and all sixteen Perfetto
traces were captured locally. The public total-pixel scenarios now use `Fixed(0.5)` for the
balanced tier and `Fixed(0.25)` for the aggressive tier.

Deterministic policy tests cover the tier boundary, rapid-update accumulation, quiet-period reset,
hysteresis, and explicit-policy reset. Runtime integration tests cover small and large retained
plans through `GlassRuntimeEffect`. Android and Skiko screenshot checks cover rounded clipping,
progressive blur, refraction detail, both explicit tiers, and the transition back to the balanced
tier.

## Consequences

- A continuously changing Glass surface can move to the `0.25` pixel-fraction tier after at most
  three observed updates.
- Stable retained output remains at the `0.5` pixel-fraction tier unless a single plan is large
  enough to cross the score boundary.
- The first update after a quiet interval may return to the `0.5` tier; a new rapid burst can
  promote it again without oscillating on every frame.
- Backend topology participates naturally: fused Android and multi-stage Skiko plans can select
  different tiers when their actual retained work differs.
- Threshold changes require paired physical-device benchmarks and representative visual review.

## References

- [Issue #1178: Make Glass adaptive sampling workload-aware](https://github.com/chrisbanes/haze/issues/1178)
- [ADR-0002: Use a shared retained-stage graph for Glass](0002-use-a-shared-retained-stage-graph-for-glass.md)
- [ADR-0003: Use one Android fused Glass renderer](0003-use-one-android-fused-glass-renderer.md)
