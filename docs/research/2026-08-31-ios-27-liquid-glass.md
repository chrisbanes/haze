# iOS 27 Liquid Glass: what changed and what Haze should take from it

_Researched 2026-08-31 against Apple’s WWDC26 transcripts and current Apple
Developer documentation. iOS 27 is in beta; the observations below describe
Apple’s published 2027-release behavior, not a guarantee for every beta build._

## Answer

iOS 27 is a refinement of the iOS 26 Liquid Glass model, not a new public
optical-material API. Apple says the platform renderer now diffuses complex
background content more effectively, adds a darkened edge for depth and
separation, and uses brighter specular highlights. It also introduces a user
setting that ranges from ultra-clear to fully tinted. Existing Liquid Glass
adopters receive these rendering improvements on the 2027 releases without
code changes. [Platforms State of the Union (WWDC26)](https://developer.apple.com/videos/play/wwdc2026/112/)

SwiftUI’s public `Glass` configuration remains small: `regular`, `clear`, and
`identity`, plus `tint(_:)` and `interactive(_:)`. The new system slider is a
user preference, not an app-facing blur, edge, specular, or tint-alpha
parameter. [Glass](https://developer.apple.com/documentation/swiftui/glass)
[What’s new in SwiftUI (WWDC26)](https://developer.apple.com/videos/play/wwdc2026/269/)

## Verified iOS 27 behavior

### Material appearance

- **More diffusion:** Apple says Liquid Glass was tuned to diffuse complex
  content more effectively, specifically to maintain readability.
- **Dark edge and brighter highlight:** Apple explicitly identifies a darkened
  edge and brighter specular highlights as new depth/separation refinements.
  This is the strongest official evidence so far for making Haze’s edge stage
  include a dark component as well as a light/specular component.
- **User-controlled clarity/tint:** Settings can move Liquid Glass from ultra
  clear to fully tinted. The SwiftUI transcript says the material responds to
  this slider automatically; it does not document a public API for reading the
  slider or reproducing its mapping.
- **No custom optical knobs:** The published `Glass` API still exposes no
  numeric blur radius, refraction strength, displacement field, edge width,
  specular intensity, or depth control. [Glass](https://developer.apple.com/documentation/swiftui/glass)

### Layout, controls, and adaptation

- Apple’s SwiftUI guidance continues to reserve Liquid Glass for controls and
  navigation, not the content layer. Use standard components where possible;
  they receive platform behavior automatically. Custom glass should be used
  sparingly. [Materials HIG](https://developer.apple.com/design/human-interface-guidelines/materials)
  [Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass)
- For buttons, Apple’s WWDC26 SwiftUI Group Lab recommends the glass button
  style (`glass`/`glassProminent`) rather than putting a raw `glassEffect` on a
  button. A raw effect produces a button sitting on glass, rather than the
  system button treatment. [SwiftUI Group Lab (WWDC26)](https://developer.apple.com/videos/play/wwdc2026/8120/)
- iOS apps are now expected to work across a dynamic range of sizes, including
  resizable iPad and iPhone Mirroring contexts. SwiftUI’s adaptive layout and
  size-class guidance is therefore relevant to any size-dependent material
  tuning. [Platforms State of the Union (WWDC26)](https://developer.apple.com/videos/play/wwdc2026/112/)
- System and custom effects adapt to accessibility settings such as Reduced
  Transparency and Increased Contrast. Apple’s environment API exposes
  `accessibilityShowBorders`; the detailed documentation specifically calls
  out the dedicated Show Borders setting on macOS 27 and using clearly visible
  edges for custom interactive controls. [accessibilityShowBorders](https://developer.apple.com/documentation/swiftui/environmentvalues/accessibilityshowborders)

## What Haze should change

1. **Add a dark, shape-following edge to the Glass runtime, paired with the
   existing specular highlight.** Treat this as one adaptive edge treatment,
   not as a new independent render pass. The iOS 27 evidence supports making
   it part of an iOS-27-aligned style or a future default, but existing Haze
   defaults should not change silently without screenshot and visual review.

2. **Revisit regular-material diffusion for busy backgrounds.** Tune the
   existing blur/scattering path against calibrated high-frequency content and
   large surfaces. Do not infer Apple’s kernel, radius, or “complexity”
   threshold from the transcript; those remain empirical Haze design choices.

3. **Keep tint, edge, and highlight controls explicit in Haze.** Apple’s system
   preference is not an invitation to invent a public “iOS slider” API. A
   platform adapter may respond to accessibility or platform preferences where
   they are actually exposed, while Haze’s cross-platform style remains
   deterministic and testable.

4. **Test at multiple sizes and settings.** Add visual coverage for small and
   large surfaces, busy and simple backgrounds, light and dark content, and
   Reduced Transparency/Increased Contrast/Reduced Motion configurations. For
   interactive controls, verify that a dark edge or explicit border remains
   visible when contrast settings require it.

5. **Align samples and guidance with Apple’s functional-layer rule.** Keep
   glass examples around important controls and navigation. For buttons,
   demonstrate the control treatment rather than layering a generic glass
   backdrop under a normal button. Keep clear media-oriented glass distinct
   from regular text-heavy glass.

These changes improve platform fidelity while preserving Haze’s reason to
exist: a cross-platform renderer with deliberate, inspectable optical
controls rather than a copy of SwiftUI’s system-owned material.

## What Apple does not establish

Apple’s public sources do **not** establish the numeric dark-edge width,
opacity, falloff, highlight intensity/color, diffusion radius/kernel, tint
curve, or the mapping from the Settings slider to rendered pixels. They also
do not state that every Liquid Glass element uses the same edge/highlight
parameters, when the renderer decides that content is “complex,” or whether
the visual changes are identical across iPhone, iPad, and other Apple
platforms. Those questions require controlled captures on named OS builds and
devices; they should not be presented as Apple constants.

The sources also do not provide an app API to query the user’s Liquid Glass
slider value. `accessibilityShowBorders` is documented as an accessibility
environment value, but the current page’s platform-specific discussion is
about macOS 27; the WWDC26 overview says iOS already supports the setting
without documenting a separate iOS-only customization contract. Treat this as
an integration point to verify against the target SDK, not as a renderer
parameter.
