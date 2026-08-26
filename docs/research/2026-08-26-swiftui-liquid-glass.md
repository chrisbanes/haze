# SwiftUI Liquid Glass: model and configuration boundary

_Researched 2026-08-26 against Apple documentation and WWDC25 sessions only._

## Answer

SwiftUI models Liquid Glass as a system-owned adaptive material, not a family of
authored optical presets. A custom view receives a `Glass` value through
`glassEffect(_:in:)`. The public values are `regular` (the default), `clear`,
and `identity` (no effect), while the published `Glass` configuration API has
only `tint(_:)` and `interactive(_:)`. It exposes no blur-radius, refraction
displacement/strength, depth, shadow, or chromatic-aberration controls.
[Glass](https://developer.apple.com/documentation/swiftui/glass)
[glassEffect(_:in:)](https://developer.apple.com/documentation/swiftui/view/glasseffect%28_%3Ain%3A%29)

`regular` is the adaptive baseline. Apple describes it as adapting tint,
shadows, and dynamic range for underlying content and as changing optical
characteristics with size: larger forms get deeper shadows and stronger
lensing/refraction. These remain system decisions rather than SwiftUI knobs.
[Meet Liquid Glass — Adaptivity](https://developer.apple.com/videos/play/wwdc2025/219/)

`clear` is a deliberately different, highly translucent variant, not a
stronger `regular`. Apple recommends it only over visually rich media, with a
dimming treatment where needed for legibility. `regular` instead blurs and
adjusts background luminosity for legibility and is used by most system
components. [Human Interface Guidelines: Materials](https://developer.apple.com/design/human-interface-guidelines/materials)
[Glass.clear](https://developer.apple.com/documentation/swiftui/glass/clear)

## What is publicly knowable about the implementation

### Verified public facts

The local read-only SDK audit used Xcode 26.3 (17C529) and the
`iPhoneSimulator26.2.sdk` arm64 Swift interfaces. In
`SwiftUICore.framework/.../SwiftUICore.swiftmodule/arm64-apple-ios-simulator.swiftinterface`:

- lines 2600–2604 declare only
  `View.glassEffect(_ glass: Glass = .regular, in: some Shape = DefaultGlassEffectShape())`;
- lines 5780–5797 declare `Glass.regular`, `Glass.clear`, `Glass.identity`,
  `tint(_:)`, and `interactive(_:)`; and
- lines 9168–9177 declare `GlassEffectContainer(spacing: CGFloat?, ...)`.

The matching public SwiftUI interface adds configurable `GlassButtonStyle` and
`GlassProminentButtonStyle`, but no material-optics controls. Therefore, in the
installed public iOS 26.2 Swift interfaces, there are **no public numeric
constants or parameters for Liquid Glass blur, refraction, displacement, or
depth**. `spacing` is a container geometry/blending threshold, not an optical
parameter. The SDK also has general-purpose `blur(radius:)` and
`distortionEffect(_:maxSampleOffset:)` APIs, but they are separate SwiftUI
effects, not configuration for `Glass`.

This is a statement about the published interface, not a claim that the
renderer has no internal constants. Swift interfaces intentionally hide the
implementation of the `Glass` values and their rendering pipeline.

Apple describes the system behavior, rather than numeric tuning. Its design
session says that Regular continuously adapts its layers to what lies behind it;
its tint and dynamic range change for legibility, and larger morphing forms get
deeper shadows plus more pronounced lensing and refraction. Clear is
permanently more transparent and has no adaptive behaviors. Apple also states
that system accessibility settings such as Reduced Transparency, Increased
Contrast, and Reduced Motion modify Liquid Glass automatically.
[Meet Liquid Glass, transcript 201–208 and 245–266](https://developer.apple.com/videos/play/wwdc2025/219/)

### Local empirical evidence — Regular only

The dedicated **Haze Liquid Glass Reference** simulator probe provides one
qualitative rendering observation, deliberately kept separate from the public
API evidence above. It used Xcode 26.3 and an iOS 26.3 simulator, with
`dev.chrisbanes.haze.liquidglassreferencecapture` installed and launched using
`--capture-scene grid-dark`. Its
`/Users/chris/Library/Developer/CoreSimulator/Devices/7C1DB337-D67D-4668-981E-4FEB015EE544/data/Containers/Data/Application/B4612521-024C-454C-A9DF-E0FFE68559D9/Documents/capture-ready.json`
metadata records a 1206×2622 framebuffer at scale 3 and these surface bounds:

| Surface | Bounds in pixels |
| --- | ---: |
| Capsule | 720×192 |
| Card | 840×528 |
| Panel | 960×660 |

A read-only audit of the probe binary found `SwiftUI.Glass.regular` and no
`Glass.clear` reference. That establishes only that this app/capture uses
**Regular**; it says nothing about whether SwiftUI itself provides Clear. On
its dark-grid background, the grid remains discernible through the capsule but
is heavily suppressed/blurred across the card and panel.

This is a qualitative observation from one simulator, OS build, scene, and
background. It is neither an Apple rendering contract nor a numeric blur or
refraction estimate. It cannot by itself support a Regular-versus-Clear
comparison; the completed matched comparison appears below.

### Matched comparison reporting rubric

This rubric is predeclared for the planned matched `Glass.identity`,
`Glass.regular`, and `Glass.clear` captures. The results below apply it only to
the available harness artifacts.

**Capture controls.** Compare only images with the same framebuffer, scale,
color space, calibrated source grid, view content, surface geometry, and
captured frame. Decode screenshots to linear light, first register them using
only pixels outside material regions, then preserve the three source PNGs plus
metadata, masks, and measurement script/version. Disable or wait out
animations and interaction; record the OS, simulator/device, accessibility
settings, and whether the capture is SDR or HDR. Repeat each settled capture at
least three times. Analyse the same predeclared interior, edge-annulus, and
nearby-background regions for each surface; do not pool them. Use tiles/lines
as repeated observations rather than treating adjacent pixels as independent
samples.

| Metric | How to calculate it | Claim it can support | Claim it cannot support |
| --- | --- | --- | --- |
| Grid-frequency contrast | Measure horizontal and vertical grid luminance modulation separately (for example Michelson contrast, line-profile peak-to-trough, or Fourier amplitude), normalized to the matching Identity crop. | “The grid’s effective contrast is lower/higher under this material in this capture.” | A blur radius, opacity, or a specific internal compositing operation. |
| High-frequency retention | Compare gradient/Laplacian energy or spectral power in the grid band, with the same masks and normalization. | “This capture retains/suppresses more fine grid detail.” It is reasonable shorthand to call that **effective blur** if stated as an image result. | The kernel type, kernel radius, sampling scale, or a proof that blur rather than tint/tonemapping caused the loss. |
| Local grid displacement | Cross-correlate small grid tiles against Identity, reporting the best x/y offset in screenshot pixels and the correlation-peak confidence; report interior and edge regions separately. | “A measurable apparent grid shift/warp is present here,” including its size in this screenshot. | Apple’s refractive index, a stable refraction-strength constant, or the material’s full displacement field. |
| Luminance separation | Report mean luminance, grid light/dark contrast, and local standard deviation for each matching region. | “The material changes apparent brightness/contrast and legibility against this background.” | An intrinsic tint alpha, dynamic-range curve, or a cross-background guarantee. |
| Edge transition width | On an isolated, registered grid edge, measure the 10–90% intensity-transition width using the same sampling rule. | “The edge is visually softer/sharper in this capture.” | A physical thickness or a unique blur/refraction decomposition. |

For each reported ratio or displacement, show the absolute value, the Identity
baseline, the region, units, and a tile/line spread (for example median and
range). If the cross-correlation peak is broad or ambiguous, report the shift as
unresolved rather than selecting a displacement.

The resulting figures characterize these screenshots only. Apple documents
that Liquid Glass adapts to content, size, and system settings, so a matched
simulator experiment can support an *effective rendered-pixel* comparison, not
Apple-internal constants or behavior on another background, device, OS build,
or accessibility configuration. [Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/)

### Matched comparison result — Xcode 26.3 / iOS 26.3 simulator

The completed probe holds the 360×720 pt calibrated dark-grid scene and
surface geometry constant, changing only the `Glass` variant among Identity,
Regular, and Clear. The unobscured surfaces are capsule 240×64 pt, card
280×176 pt, and panel 320×220 pt. The artifacts are
`/private/tmp/haze-liquid-glass-variant-probe/{identity,regular,clear}.png`,
the `identity-regular-clear.png` contact sheet, and `measurements.json` in the
same directory. Metrics use interior masks to exclude visible edge treatment:
48 px/16 pt for the capsule and 72 px/24 pt for the card and panel.

| Surface | Regular: stddev / gradient retention / correlation vs Identity | Clear: stddev / gradient retention / correlation vs Identity | p05–p95 contrast (Identity → Regular → Clear) |
| --- | --- | --- | --- |
| Capsule | 0.959 / 0.959 / 0.9999 | 1.000 / 1.000 / 1.000 | 0.968 → 0.968 → 0.968 |
| Card | 0.488 / 0.457 / 0.551 | 0.604 / 0.553 / 0.731 | 0.968 → 0.635 → 0.938 |
| Panel | 0.259 / 0.287 / 0.289 | 0.376 / 0.343 / 0.622 | 0.968 → 0.664 → 0.932 |

The exact uncontaminated bottom-strip control has RMSE 0 for both Regular and
Clear versus Identity. Do not substitute the broad outside-surface mask: its
left strip is 0.005874 RMSE for Regular and 0 for Clear, consistent with a
Regular outset/shadow rather than a changed source background.

**Conservative reading.** In this one matched scene, larger Regular surfaces
suppress calibrated grid structure more than Clear: the card and panel retain
less luminance variation, less gradient detail, and less correlation with the
Identity grid. Clear retains more structure on both surfaces. The capsule
interior is effectively unchanged under both variants at the stated interior
margin, while the contact sheet still visibly shows edge lensing. This supports
a rendered-pixel observation of size-dependent, variant-dependent effective
structure suppression; it does not identify a blur kernel, a refraction vector,
or any Apple-internal constant.

These are single-capture descriptive measurements, not repeatability results.
They characterize Xcode 26.3 / iOS 26.3 simulator rendering of this dark-grid
scene only, and are neither an Apple API contract nor a general claim about
other content, devices, OS builds, or accessibility settings.

### What requires empirical rendering

Public sources do not reveal the numeric mapping from content, size, state, or
system settings to pixels. The following would need controlled rendering and
measurement on a chosen OS/device configuration rather than further public API
inspection:

- effective blur radius or kernel;
- refraction/lensing displacement field, including its edge falloff;
- shadow opacity, spread, and color response;
- tint, dynamic-range, and scattering transforms; and
- how any of those vary with size, background, interaction, accessibility
  settings, device, or OS revision.

A useful empirical study would render fixed calibration imagery beneath known
shapes and sizes, capture Regular and Clear under explicitly recorded system
settings, then measure the resulting pixels. It could characterize a particular
build and environment, but would not turn the measurements into an Apple API
contract.

## SwiftUI configuration surface

| Intent | SwiftUI mechanism | Apple-controlled behavior |
| --- | --- | --- |
| Baseline material | `.glassEffect()` or `.glassEffect(.regular)` | The default is `regular` in a capsule, anchored to the view bounds. [API](https://developer.apple.com/documentation/swiftui/view/glasseffect%28_%3Ain%3A%29) |
| Alternate clarity | `.glassEffect(.clear)` | Highly translucent; the caller owns the legibility decision. [HIG](https://developer.apple.com/design/human-interface-guidelines/materials) |
| Shape | `.glassEffect(..., in: shape)` | The caller supplies a `Shape`; the default shape is a capsule. [Applying Liquid Glass](https://developer.apple.com/documentation/SwiftUI/Applying-Liquid-Glass-to-custom-views) |
| Semantic prominence | `.regular.tint(color)` | Tint should communicate meaning/prominence, not decoration. [Applying Liquid Glass](https://developer.apple.com/documentation/SwiftUI/Applying-Liquid-Glass-to-custom-views) [WWDC25 SwiftUI design](https://developer.apple.com/videos/play/wwdc2025/323/) |
| Touch/pointer response | `.regular.interactive()` | Activates the responsive fluid behavior used by standard glass buttons; Apple describes scale, bounce, and shimmer on iOS. [Applying Liquid Glass](https://developer.apple.com/documentation/SwiftUI/Applying-Liquid-Glass-to-custom-views) [WWDC25 SwiftUI design](https://developer.apple.com/videos/play/wwdc2025/323/) |
| Important button action | `.buttonStyle(.glassProminent)` | A button-style distinction, separate from `Glass` configuration. [glassProminent](https://developer.apple.com/documentation/swiftui/primitivebuttonstyle/glassprominent) |
| Nearby glass | `GlassEffectContainer(spacing:)` | Renders child effects together; spacing controls when shapes start to blend and morph. [GlassEffectContainer](https://developer.apple.com/documentation/swiftui/glasseffectcontainer) |
| Morph transition | `glassEffectID(_:in:)` and `glassEffectTransition(_:)` in a container | IDs let SwiftUI animate glass shapes to and from one another. [glassEffectID](https://developer.apple.com/documentation/swiftui/view/glasseffectid%28_%3Ain%3A%29) [glassEffectTransition](https://developer.apple.com/documentation/swiftui/view/glasseffecttransition%28_%3A%29) |

The container is more than grouping: Apple explains that glass samples a larger
surrounding area and cannot sample other glass. A shared
`GlassEffectContainer` gives nearby elements a consistent sampling region and
their blend/morph behavior. [WWDC25 SwiftUI design](https://developer.apple.com/videos/play/wwdc2025/323/)

## Implication for Haze samples

The closest Apple-shaped model is one adaptive material for normal interactive
UI, optionally paired with a clear, media-oriented alternate. It does **not**
map to fixed `Deep`, `Frosted`, or `Prism` optical materials, and it has no
public displacement parameter. A Playground demonstrating platform-like glass
should use the built-in Regular and Clear styles. A sample that explicitly
teaches custom authoring may declare an unnamed local style; it should not add
another shared preset catalogue.

This is a design comparison, not a proposal to duplicate Apple’s API: Haze’s
cross-platform renderer and explicit optical controls serve a different library
contract.
