# ADR-0003: Use quality-gated adaptive input scaling for blur

## Status

Accepted

## Date

2026-07-26

## Context

Blur cost is dominated by rendering and filtering the expanded retained capture layer. Reducing its
input resolution reduces that work, but a single fixed scale is not appropriate for every blur:
weak blur reveals downsampling more readily, and scaling a small layer saves too little work to
justify resource churn.

`HazeInputScale.Default` previously resolved to explicit no scaling, preventing a visual effect
from choosing an appropriate default. Blur and Glass also have different quality constraints:
ordinary blur can hide conservative downsampling, while Glass combines refraction, fine optical
detail, and retained stages that have their own render budget.

## Decision

`HazeInputScale.Default` is semantically distinct from `None` and lets each visual effect choose:

- Blur uses one cross-platform policy based on the fully resolved blur radius in physical pixels
  and the expanded capture-layer pixel area.
- Ordinary and non-progressive masked blur select only `1.0`, `0.8`, or `0.5`. Both radius and area
  must cross the relevant workload boundary.
- Progressive blur stays at `1.0` below the medium workload boundary and is capped at `0.8`.
- Tier exits have a 12.5% hysteresis margin, preventing small radius or size changes from repeatedly
  reallocating retained layers.
- Glass remains at `1.0` for `Default`. Its explicit `Auto` policy is unchanged.
- Explicit `None`, `Auto`, and `Fixed` choices remain authoritative. Paths that do not perform a
  real blur, including scrim fallback, remain unscaled.

The physical-pixel boundaries preserve a substantial blur kernel after downsampling:
`0.8` begins at a 32px radius and 300,000px layer, while `0.5` begins at a 60px radius and
500,000px layer. This leaves at least a 25.6px or 30px downsampled kernel respectively. The area
gates avoid resizing for less than roughly 108,000 or 375,000 saved input pixels. These are
workload boundaries, not device-model checks.

## Validation

Representative screenshots passed on Desktop and Android API 28, 32, and 35 for both automatic
tiers, progressive blur, gradient and hard-edged masks, and hysteresis boundaries. The automated
perceptual-difference guard remained below its `0.01` mean-absolute-difference limit.

Three interleaved Pixel 6 Macrobenchmark pairs compared explicit unscaled input with the adaptive
default. Each measurement used 16 iterations at 90Hz. Perfetto actual-frame duration and
Macrobenchmark CPU P90 improved in every pair:

| Pair | CPU P90, unscaled → adaptive | Actual-frame P90, unscaled → adaptive |
| --- | --- | --- |
| 1 | 9.817 → 9.746ms | 11.741 → 11.366ms |
| 2 | 9.774 → 9.641ms | 11.889 → 11.388ms |
| 3 | 10.071 → 9.975ms | 12.247 → 11.654ms |
| Median | 9.817 → 9.746ms (-0.73%) | 11.889 → 11.388ms (-4.21%) |

Frame-overrun headroom also improved in every pair. Representative progressive and masked
workloads improved actual-frame P90 by 5.76% and 6.53% respectively, so the capped and masked paths
showed no material regression.

The device refresh rate and workload state were checked before accepting a pair. Runs captured at
60Hz, with a mismatched refresh rate between halves, or with invalid app navigation state were
discarded and repeated; they are not included above.

On the measured Scaffold, the 24dp blur resolved to 63px. Its expanded 637,200px top layer selected
`0.5`, while the smaller 362,880px bottom layer selected `0.8`, confirming that both workload gates
participate as intended.

To verify that the aggressive tier earns its additional quality cost, three further interleaved
Pixel 6 pairs compared the selected adaptive policy against explicit `Fixed(0.8)`. Adaptive
scaling improved app-layer actual-frame P90 in every pair:

| Pair | CPU P90, fixed `0.8` → adaptive | Actual-frame P90, fixed `0.8` → adaptive |
| --- | --- | --- |
| 1 | 9.703 → 9.814ms | 11.685 → 11.610ms (-0.64%) |
| 2 | 9.686 → 9.742ms | 11.705 → 11.411ms (-2.51%) |
| 3 | 9.846 → 9.596ms | 11.712 → 11.282ms (-3.67%) |
| Median | 9.703 → 9.742ms (+0.41%) | 11.705 → 11.411ms (-2.51%) |

The CPU metric did not improve consistently because the extra saving is predominantly in the
RenderThread/Vulkan work that motivated this policy. The actual-frame result did improve in every
pair, so `0.8` is retained as the least aggressive default tier and `0.5` is reserved for the
larger 60px/500,000px workload boundary.

## Alternatives Considered

### Keep no scaling as the default

This preserves exact historical output but leaves a repeatable performance improvement opt-in,
including on workloads where downsampling is visually hidden by strong blur.

### Default every blur to one fixed scale

This is simple but ignores the two inputs that determine benefit and perceptibility. It either
downsamples weak or small blurs unnecessarily or leaves useful savings on large, strong blurs.

### Use an Android- or device-specific policy

The original performance evidence came from Android, but the quality relationship between physical
blur radius, capture pixels, and downsampling is not device-model-specific. A common policy also
preserves cross-platform behavior. A platform-specific exception requires evidence of a backend
constraint.

### Allow scales below `0.5`

More aggressive downsampling saves additional pixels but produces a larger perceptual change and
is unnecessary for the measured improvement. Automatic scaling is therefore bounded at `0.5`.

## Consequences

- `EffectDefault` extends the public sealed `HazeInputScale` hierarchy. Existing exhaustive `when`
  expressions must handle it (or add an `else` branch), so this change requires a breaking release.
- Unspecified blur output can be slightly softer on sufficiently large, strongly blurred surfaces.
- The common policy is deterministic but stateful at tier boundaries because hysteresis depends on
  the previous automatic tier.
- Crossing a tier intentionally resizes retained resources; small boundary noise does not.
- Callers can restore full-resolution input with `HazeInputScale.None`.
- Threshold changes require paired performance evidence and representative screenshot/perceptual
  review across ordinary, progressive, and masked blur.

## References

- [Issue #1083: Explore adaptive input scaling for blur performance](https://github.com/chrisbanes/haze/issues/1083)
- [Input scale performance guidance](../performance.md#input-scale)
