# ADR-0007: Export Regular and Clear as built-in Glass styles

Haze will expose `GlassStyle.regular` and `GlassStyle.clear` as shared built-in styles rather than
adding a public modifier variant parameter or extending the public `GlassOptics` API with semantic
cases. The parallel sample-only preset catalogue will be removed rather than retained or exported.
Regular remains the default size-responsive material. Clear initially used size-responsive blur and depth
with an authored refraction response, and remains recognisably distinct when a renderer
simplifies advanced optics. Both styles own the complete optical, edge, lighting, chromatic, tone,
and content-normal response while preserving independently composed shape, background colour, tint,
alpha, light position, and interaction appearance. These are Haze styles informed by iOS's semantic
distinction and measured direction, not promises of pixel parity or Apple-internal constants.
Identity remains outside this decision because disabling attachment and rendering is
modifier/runtime behaviour rather than a Glass appearance.

## Consequences

Callers select a built-in response through the existing replayable Style seam and customize it with
`then`, without learning another public type or precedence rule. Switching between Regular and Clear
resets every built-in-style-owned channel but does not erase theme or caller presentation. Samples
use Regular and Clear directly; sample-only Adaptive, Clear, Frosted, Deep, and Prism presets are
removed. A sample that teaches custom authoring declares that customization locally instead of
adding another reusable preset catalogue.

ADR-0008 revises the representation of their optics: Regular and Clear remain built-in Glass styles,
but their optical behavior is expressible through the same public configuration available
to callers.

## Diffusion calibration, 2026-09-16

Clear now uses constant shallow diffusion: `OpticalSizeValue.Fixed(1f)` for depth and
`OpticalSizeValue.Fixed(1.25.dp)` for blur radius. This supersedes the size-responsive Clear response
above. Native UIKit captures on iOS 27.0 (24A434), built with Xcode 27.0 (27A266a) and SDK 27.0,
use the same 64/176/220pt geometry and 8pt grid as Haze's size fixture. The simulator's Liquid Glass
preference is Default (Tint Amount 50%); Reduce Transparency and Increase Contrast are disabled.
Clear retains approximately 23/27/28% of the unfiltered grid's adjacent-pixel linear-luminance
contrast. Light and dark Clear captures agree. This replaces the initial iOS 26 calibration, whose
more diffuse Clear reference led to a provisional 2dp radius.

Regular retains approximately 24% on the compact capsule and effectively none on the larger native
surfaces. An independent launch reproduces that response. Haze keeps its compact diffusion and
increases the 176/220dp depth anchors to 0.98/1, reducing the sharp-source contribution on cards
and removing it on panels. Its 64dp depth remains 0.85 and its blur radii remain 4/10/15dp.

These are empirical diffusion targets, not Apple-specified radii or a pixel-parity contract. The
metric includes tone and refraction as well as blur, and native Regular's adaptive appearance and
edge treatment differ. The calibration prioritizes stronger diffusion on larger Regular surfaces.
Both styles continue to use the existing public optics configuration; no renderer-specific identity
branch or additional API is needed.


## Clear refraction calibration, 2026-09-16

A separate four-phase native capture isolates apparent sampling displacement from the grid's
repeating lines. Clear now selects `RefractionProfile.Edge(28.dp)`, with strength 0.85 and authored
displacement 56dp. The fitted inward decay and optical corner radius (1.5 times the visible radius,
capped at half the shortest side) reproduce the accepted corner loops. Displacement is capped at
half the shortest side. The production normal is analytic rather than four additional distance
samples per pixel. Lighting retains Circle, with height fraction 0.35; white point is 0.17.

`RefractionProfile.Surface` remains the default for caller-authored optics. This
explicit profile preserves existing surface semantics; Edge width controls refraction independently
of the surface height used for lighting. Both base and interaction detail use the same sampling map.
Zero width disables Edge sampling and its detail pass.

These settings are an empirical visual match for the accepted fixture, not Apple's implementation
or a promise of parity across content.

## Regular refraction and wide diffusion calibration, 2026-09-16

Further grid and photographic comparisons supersede the provisional Regular settings above.
Regular now uses `Edge(20.dp)` with authored
48dp displacement at 0.7 strength, lighting height fraction 0.15, and no secondary detail or fold.
Depth is 0.65/1/1 and blur is 20/24/25dp at 64/176/220dp. Compact controls retain refracted grid
loops; cards and panels remove both fine grid lines and medium-scale photographic detail.
The light-appearance tone uses white point 0.55 and chroma multiplier 1.5, with ambient response
0.15 and edge softness 1dp. Clear's accepted settings remain unchanged.

This exposed a source-space blur cap of 38.5 physical pixels: larger authored radii previously
produced the same result at dense resolutions. Non-progressive blur now supports up to 256px.
Both backends use native Gaussian filtering for uniform blur above four input pixels so input
bounds include all required samples, including lower-density Performance rendering. The custom
runtime-filter paths exposed sharp backdrop strips near output boundaries. Android retains one fused output graph; the retained Skiko path caches the native
filter and its blurred layer. Small-radius and progressive blur keep their semantic kernels,
and progressive blur retains its previous radius bound. Layer budgets and lifecycle follow
the selected backend path. This wide-blur correctness exception to ADR-0003 has host pixel
coverage; its physical-device performance has not been measured.

Native light and dark appearances differ substantially; the fixed Haze style targets the light
reference and does not implement native appearance or content adaptation. The comparisons establish
visual direction, not physical GPU performance.
