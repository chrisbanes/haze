# Glass Size Adaptation Design

## Goal

Make Haze's default Glass effect respond continuously to component geometry in the same way as
Apple's iOS 26 Liquid Glass Regular material. The response should be calibrated from native
captures, remain deterministic across Android and Skiko, and preserve explicit caller values as
the baseline for adaptation.

## Context

The current renderer already contains a narrow geometry response in
`calculateRegularGeometryProfile()`. It derives blur, tone, and neutral lift from the shortest
dimension and normalized corner radii. The committed iOS reference captures also show materially
different displacement, blur attenuation, and luminance for capsule, card, and panel surfaces.

Those references confound size, aspect ratio, and corner radius, however. The current response was
therefore calibrated as implementation math rather than as an explicit, independently measured
size policy. This change will isolate the relevant geometry variables, fit a small continuous
model, and expose a simple opt-out for callers that need literal optical values.

## Public API

Add a two-value policy:

```kotlin
@ExperimentalHazeApi
public enum class GlassSizing {
  Adaptive,
  Fixed,
}
```

`GlassOptics` gains a nullable `sizing` value so `null` continues to mean unspecified within a
partial style. `GlassVisualEffect` exposes a resolved `sizing` property with the existing
precedence chain:

1. Direct value on `GlassVisualEffect`
2. Value in its `GlassStyle`
3. Value from `LocalGlassStyle`
4. `GlassDefaults.sizing`

`GlassDefaults.sizing` is `GlassSizing.Adaptive`. `GlassVisualEffect.clearSizingOverride()` clears
a direct override and restores inherited behavior, matching the existing enum override APIs.

`Adaptive` treats explicitly configured optical values as baselines and applies the fitted
geometry response proportionally. `Fixed` uses those values literally and returns an identity
geometry response. Calibration coefficients and curve customization remain internal.

`Fixed` disables only the fitted size-response multipliers. Geometry inherent to the rounded-rect
SDF and the size-relative meaning of `refractionHeight` remains active.

## Shape Ownership

`GlassVisualEffect.shape` is the authoritative material shape. The effect uses its resolved shape
for shader masking, refraction normals, fallback clipping, layer bounds, and adaptive geometry.

The effect cannot and must not infer shape from `Modifier.clip()`. Compose does not expose arbitrary
modifier-chain clip outlines through `VisualEffectContext`, and multiple surrounding clips may
intersect the output without representing the intended glass surface.

An external clip is unnecessary when it exists only to establish the Glass boundary. Callers may
still use one to clip child content; in that case they should pass the same shape instance to both
the clip modifier and `GlassVisualEffect.shape`. If they differ, the external clip only intersects
the final output while Glass optics continue to use `GlassVisualEffect.shape`.

This design does not add shape to the generic `hazeEffect` API or `VisualEffectContext`. Glass keeps
its existing `RoundedCornerShape` support. Supporting arbitrary `Shape` implementations would also
require new shader geometry and calibration and is a separate feature.

## Geometry Model

Replace `RegularGeometryProfile` with an internal `RegularGeometryResponse`. The model uses
orientation-independent logical geometry:

- Shortest side in dp
- Aspect ratio as `max(width, height) / min(width, height)`
- Symmetric roundness as the minimum resolved corner radius divided by half the shortest side

The minimum corner radius remains the global support bound. One highly rounded corner must not
change the response of an otherwise square surface. Layout direction can permute corners without
changing the result.

The response contains only values needed by the measured optical core:

- Blur multiplier
- Refraction displacement multiplier
- Refraction reach multiplier
- Tone gain
- Neutral lift weight

The formula must use a small number of smooth, bounded terms. Inputs are clamped to the calibrated
domain before evaluation, and every output is bounded. There are no semantic size classes,
breakpoints, runtime tables, or shader branches.

The model is appearance-independent. Light and dark native references are fitted jointly. If
geometry alone cannot satisfy both appearances, appearance adaptation requires a separate design
rather than being added implicitly to this scope.

## Baseline Resolution

Style values resolve before geometry adaptation. The resulting values are baselines:

- Effective blur radius is the baseline `blurRadius` multiplied by the resolved blur multiplier.
- Effective displacement scale is the baseline `refractionScale` multiplied by the resolved
  displacement multiplier.
- Effective refraction reach is the shortest side multiplied by baseline `refractionHeight` and
  the resolved reach multiplier, clamped to the shortest side.
- Tone gain and neutral lift are internal post-color-grading adjustments, as they are today.

Adaptive response remains coupled to `refractionStrength`. A strength of `0f` resolves every
geometry-derived multiplier to identity and every additive tone response to zero. Intermediate
strengths blend continuously between identity and the full response. This preserves the existing
meaning of refraction strength as the master control for coupled Regular-material optics.

Zero baselines remain zero. Adaptation cannot re-enable a blur or displacement that the caller
disabled. Progressive blur uses the adapted radius as its maximum while preserving the caller's
progressive mask.

## Runtime Data Flow

Keep adaptation in common Kotlin before render effects are created:

1. Resolve public style and direct values.
2. Resolve unscaled component size and `GlassVisualEffect.shape` corner radii in logical dp.
3. Return the fitted response for `Adaptive` or identity for `Fixed`.
4. Apply the response to baseline values.
5. Scale the resolved physical values for `HazeInputScale`.
6. Build the existing blur, optical, refraction-detail, and rim keys.

Both `GlassVisualEffect.calculateLayerBounds()` and
`RuntimeShaderGlassDelegate.buildRenderParams()` must use the same pure resolver. Padding therefore
uses the same effective blur and displacement as rendering, preventing clipping and cache-key
divergence.

The shaders continue receiving ordinary resolved values. No new shader uniforms, retained layers,
or rendering passes are required. A size change naturally changes coordinates and render-effect
keys; the geometry formula is reevaluated during draw preparation. Changing `sizing` invalidates
both drawing and layer bounds.

Logical response is independent of display density and `HazeInputScale`. The effect should respond
to a component's layout size, not to internal retained-layer downscaling or an external draw-time
transform.

## Native Reference Matrix

Extend the Swift reference app with controlled capture pages. Every geometry is captured in light
and dark appearances over both the existing grid and uniform backgrounds.

### Absolute Size Sweep

Keep aspect ratio at `3:2` and radius at one quarter of the shortest side. Use these sizes in
points:

| Size | Role |
| --- | --- |
| 66 x 44 | Training |
| 96 x 64 | Holdout |
| 132 x 88 | Training |
| 168 x 112 | Holdout |
| 216 x 144 | Training |
| 264 x 176 | Holdout |
| 330 x 220 | Training |

Width, height, and radius scale together, isolating absolute logical size while aspect ratio and
normalized roundness remain fixed.

### Aspect Ratio Sweep

Keep the shortest side at 80 points and normalized radius at one half. Use aspect ratios `1`,
`1.5`, `2`, `3`, and `4`. Ratios `1`, `2`, and `4` are training cases; `1.5` and `3` are holdouts.

### Roundness Sweep

Keep dimensions at `240 x 96` points. Use radii `0`, `12`, `24`, `36`, and `48` points. Radii `0`,
`24`, and `48` are training cases; `12` and `36` are holdouts.

The existing capsule, card, and panel captures remain natural-geometry regression validation. They
are not strict holdouts because they influenced the current calibration. Capture pages must leave
enough space around each surface for the material to sample only the intended background.

The manifest records each surface's logical dimensions, pixel bounds, corner radii, sweep axis,
and training or holdout role. Existing producer metadata remains pinned to Xcode, simulator runtime,
device, scale, and color space.

The calibrated input domain is therefore shortest side `44..220` points, aspect ratio `1..4`, and
symmetric roundness `0..1`. Haze treats iOS points and Compose dp as equivalent logical units for
model input.

## Calibration Process

Reuse the existing paired grid/uniform observables:

- Directional displacement in pixels
- High-frequency blur attenuation
- Interior luminance shift

Fit coefficients only against training cases. An internal calibration report presents normalized
residuals for the training matrix while coefficients are selected. The runtime contains only the
resulting formula and constants; it does not contain the fixture dataset or an optimizer.

Start with independent bounded terms for shortest side, aspect ratio, and symmetric roundness. Add
an interaction term only when training residuals demonstrate that independent terms cannot explain
the captures. Holdout results must not influence term selection or coefficients.

All native fixture metrics are committed before Haze coefficients are tuned. Metric bands are
derived from those immutable fixtures using the existing tolerances: displacement uses
`max(10%, 1px)` and normalized optical metrics use `max(10%, 1/255)`.

## Error Handling And Extrapolation

The model clamps logical geometry to the captured domain before evaluating the response. This
makes behavior outside the calibrated range stable and bounded rather than claiming unsupported
native fidelity.

Non-finite or non-positive layout geometry follows existing lifecycle behavior. Drawing is skipped
and unavailable retained resources are released. Layer-bound calculation falls back to identity
geometry response so it remains finite and does not throw.

All response outputs are checked as finite and clamped before use. Public endpoint semantics remain
intact: zero blur stays sharp, zero refraction scale stays undisplaced, and zero refraction strength
removes coupled geometry response.

## Verification

### Pure Model Tests

- `Fixed` returns identity for every geometry.
- Adaptive outputs are finite and within declared bounds.
- Width/height rotation and layout-direction corner permutation preserve response.
- Equivalent dp geometry at different densities and input scales produces the same response.
- Zero blur and refraction baselines remain zero after resolution.
- Closely spaced sizes produce bounded output deltas with no breakpoint-like discontinuity.
- Inputs below and above the calibration domain clamp to stable endpoint responses.

### Bounds And Render Parameters

- Layer padding uses the same adapted blur and displacement as render parameters.
- Size and sizing-policy changes update the relevant render-effect keys.
- Changing `GlassVisualEffect.shape` updates adaptive response, masking, and layer bounds together.
- Adaptive progressive blur changes only its maximum radius, not its mask.
- Invalid geometry produces finite fallback bounds and no retained-layer leak.

### Native Fixtures

- Every committed PNG reproduces its manifest metrics exactly.
- The capture script self-test validates page layout, metadata, and all declared surface bounds.
- Training and holdout roles are fixed in the manifest before renderer calibration.

### Cross-Platform Holdouts

Android API 33+ and Skiko render the reserved holdout geometries. They also keep the existing
capsule, card, and panel cases as regression validation. Displacement, blur attenuation, and
luminance must fall within their predeclared iOS-derived bands on both platforms.

Representative compact, middle, and expansive screenshots remain as visual regression coverage.
A resize test steps through closely spaced dimensions and asserts bounded metric deltas so a hidden
profile switch cannot pass only by matching static endpoints.

## Performance

The runtime cost is a small amount of scalar common-Kotlin math during draw preparation. The design
adds no shader work, texture sampling, render pass, retained layer, or allocation requirement.
Resizing already changes layer sizes and render-effect keys, so adaptive sizing does not introduce a
new class of cache churn.

## Documentation

Update the Glass effect documentation to state:

- `Adaptive` is the default for iOS Regular fidelity.
- Explicit optical values are adaptive baselines.
- `Fixed` requests literal optical values.
- Fidelity is calibrated within the documented geometry range and extrapolation is bounded.
- `GlassVisualEffect.shape` defines material geometry; `Modifier.clip()` is not inferred.

The API reference for `blurRadius`, `refractionHeight`, and `refractionScale` must describe their
baseline semantics when adaptive sizing is active.

## Non-Goals

- Interaction-driven deformation or morphing
- Appearance-dependent adaptation
- Clear glass
- Adaptive specular, rim, edge softness, or chromatic aberration
- Public custom sizing curves or calibration coefficients
- Semantic compact, regular, or expansive size classes
- Inferring arbitrary modifier clips or supporting arbitrary `Shape` implementations
- Pixel-identical rendering with native iOS
- Changes to fallback rendering fidelity
