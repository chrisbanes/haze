# ADR-0007: Export Regular and Clear as built-in Glass styles

Haze will expose `GlassStyle.regular` and `GlassStyle.clear` as shared built-in styles rather than
adding a modifier variant parameter or extending `GlassOptics` with semantic cases. The parallel
sample-only preset catalogue will be removed rather than retained or exported. Regular remains the
default geometry-adaptive material response; Clear uses fixed authored optics and remains
recognisably distinct when a renderer simplifies advanced optics. Both styles own the complete
optical, edge, lighting, chromatic, tone, and content-normal response while preserving independently
composed shape, background colour, tint, alpha, light position, and interaction appearance. These
are Haze styles informed by iOS's semantic distinction and measured direction, not promises of pixel
parity or Apple-internal constants. Identity remains outside this decision because disabling
attachment and rendering is modifier/runtime behaviour rather than a Glass appearance.

## Consequences

Callers select a built-in response through the existing replayable Style seam and customize it with
`then`, without learning another public type or precedence rule. Switching between Regular and Clear
resets every built-in-style-owned channel but does not erase theme or caller presentation. Samples
use Regular and Clear directly; sample-only Adaptive, Clear, Frosted, Deep, and Prism presets are
removed. A sample that teaches custom authoring declares that customization locally instead of
adding another reusable preset catalogue.
