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
- **Optical complexity:** Progressive blur and Full chromatic aberration can add work relative to
  the default material. Measure them with the surfaces and content your application uses.
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

## Reference measurements

The following Haze reference run is a reproducible comparison point, not a performance target or
promise. It used the `benchmarkRelease` build at commit `334557df` on 2026-08-04: Pixel 6,
Android 17/API 37, 1080×2400, locked 60 Hz render rate, locked CPU frequency, and eight
Macrobenchmark iterations per scenario. The metric is P90 CPU frame duration in milliseconds.

| Scenario | Workload | P90 CPU frame duration |
| --- | --- | ---: |
| `productPager` | Gallery paging journey | 7.5 ms |
| `playgroundTimeline` | Gallery animated timeline journey | 11.2 ms |
| `steadyFull` | Controlled steady state, 1 Glass effect | 5.6 ms |
| `steadyFull3` | Controlled steady state, 3 Glass effects | 5.2 ms |
| `steadyFull9` | Controlled steady state, 9 Glass effects | 8.1 ms |

The Gallery journeys are closest to the sample's visible user work. The `steadyFull*` controls
characterize the baseline at different effect counts. Cold-initialization `effectAttach*` results
are deliberately omitted: they attach new effects at the measurement boundary and diagnose
delegate/shader creation rather than representative interaction performance. They remain covered
by the internal [Glass benchmark runbook][benchmark-runbook]. Compare the interactions, content,
and device classes that your application supports.

The adaptive-sampling rationale and supporting measurements are recorded in
[ADR-0005][sampling-adr].

[benchmark-runbook]: https://github.com/chrisbanes/haze/blob/main/internal/benchmark/README.md
[sampling-adr]: ../adr/0005-use-cadence-weighted-adaptive-input-scaling-for-glass.md
