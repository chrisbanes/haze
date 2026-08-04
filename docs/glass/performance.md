# Glass performance

Read the [general performance guide](../performance.md) first for the shared workflow, sampling
choices, and measurement advice. This page focuses on the decisions that are specific to Glass.

For styling and API guidance, see the [Glass overview](../effects/glass.md).

## Start here

- Use `HazeSampling.Default` with `GlassOptics.Adaptive`.
- Build the complete screen before tuning; isolated effects do not represent a real workload.
- Profile the slowest devices you support with realistic content and interactions.
- Override one setting at a time and keep it only when the benefit is visible or measurable.

## Glass-specific cost drivers

- **Surface size:** Large Glass surfaces process more content and optical detail.
- **Number of surfaces:** Several independent Glass elements add work even when they share a Style.
- **Changing content:** Scrolling or animation behind Glass costs more than a stable background.
- **Optical complexity:** Progressive blur and Full chromatic aberration are more expensive than
  the default material.
- **Interaction:** Animated lighting, refraction, and transforms add a changing workload while the
  user hovers, focuses, or presses the element.

## When to change sampling

Keep adaptive sampling unless a target-device comparison gives you a reason to override it:

- Choose `FullResolution` when fine source detail is visibly important.
- Choose `Fixed(pixelFraction)` when you need a deliberate, stable trade-off for a known layout.
- Return to `Default` when the override does not produce a meaningful improvement.

## What to test

Exercise the states that represent the real screen:

- the largest number of Glass surfaces visible at once;
- scrolling or animation behind those surfaces;
- hover, focus, and press responses;
- resizing, orientation changes, and other layout transitions;
- the lowest-performance devices and highest display resolutions you support.

Repository contributors can use the complete [Glass benchmark runbook][benchmark-runbook]. The
adaptive-sampling rationale and supporting measurements are recorded in [ADR-0004][sampling-adr].

[benchmark-runbook]: https://github.com/chrisbanes/haze/blob/main/internal/benchmark/README.md
[sampling-adr]: ../adr/0004-use-cadence-weighted-adaptive-input-scaling-for-glass.md
