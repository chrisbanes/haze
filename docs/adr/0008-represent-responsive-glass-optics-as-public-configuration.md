---
status: accepted
---

# Represent responsive Glass optics as public configuration

`GlassOptics` will be one immutable configuration value rather than a sealed choice between
`Adaptive` and `Fixed`. Its blur radius and depth each own an independent `OpticalSizeValue<T>`:
either a `Fixed` value or a `Responsive` value containing two or more
`OpticalSizePoint<T>` values. Each point pairs a strictly increasing shortest dimension with one
parameter value; values interpolate with smoothstep and clamp outside the authored range. This makes
responsive optics a public customization capability, lets parameters use different size points, and
lets Regular and Clear use the same model as caller-authored styles without subtype or identity-based
behavior.

## Consequences

Regular uses authored refraction constants of `48.dp` displacement and `0.6f` height rather than its
previous private aspect-ratio and roundness adjustments. `refractionDetailIntensity` becomes a
validated public optical value so Regular can select `0f` and Clear can select `0.76f` without hidden
runtime distinctions. Responsive-value points are snapshotted and rejected unless they contain at
least two valid points in strictly increasing dimension order. This revises ADR-0007's optics
boundary without changing its decision to expose Regular and Clear as built-in Glass styles.

`OpticalSizeValue.Fixed<T>` and `OpticalSizeValue.Responsive<T>` are single-field domain values and
therefore use `@JvmInline value class`; the responsive variant snapshots its public constructor
input before the `init` block validates it. `OpticalSizePoint<T>` and `GlassOptics` remain data
classes because they contain multiple independent values. The generic containers deliberately do
not carry `@Immutable`, because callers may instantiate them with a mutable `T`; `GlassOptics`
retains that annotation because its public properties constrain `T` to `Float` and `Dp`. Using the
variants through `OpticalSizeValue<T>` may box them, so this choice states the domain model rather
than promising an allocation optimization.
