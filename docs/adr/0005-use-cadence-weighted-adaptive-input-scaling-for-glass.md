# ADR-0005: Use cadence-weighted adaptive input scaling for Glass

## Status

Accepted workload fallback. The host-feedback amendment is approved for implementation under
CB-10 plan r2, with a reversible Skiko probe experiment approved under r3. Default-release
validation is open.

## Date

2026-08-03; amended 2026-09-23

## Context

Glass retains platform-specific rendering layers. Their cost depends on both the pixels in the
active layer graph and how often that graph consumes new input. Raw material area alone cannot
distinguish a reused result from a frequently updated one. The original deterministic workload
policy addressed this without a platform timing source, but it cannot probe full resolution when
a fast host has headroom or respond to measured sustained frame pressure shared by several nodes.

Frame evidence has different meanings across targets. Android `FrameMetrics` reports whole-window
rendering duration; Skiko frame-clock intervals report callback cadence, not GPU completion. Neither
source isolates the cost of Glass from other work in that host. Missing or stale evidence must not
be interpreted as spare capacity.

## Decision

`HazeSampling.Default` points to `HazeSampling.Adaptive`. Active Adaptive Glass nodes registered
to the same verified rendering host share one demand-driven controller and one timing observer.
The controller starts at linear input scale `sqrt(0.5)`, can step down to `0.5` after sustained
misses, and can probe upward to `1.0` after a longer healthy interval when its timing source has
an independent refresh budget. Skiko may make one bounded relative probe during sustained,
stable source demand. It compares callback cadence before and after the tier change, rolls back
and backs off if cadence regresses, and discards the comparison when demand, retained workload,
visibility, or timing continuity changes. A tier change is consumed
on each node's next natural draw; it does not wake an idle node. Demand expires after 350 ms
without a natural update. Host stop, idle, stale samples, missing reports, and refresh changes
reset timing evidence. A failed upward probe backs off before another attempt.

The implementation's initial policy uses at least 30 samples and 500 ms to warm up, at least
30 samples over 1 s with a 20% miss ratio to step down, and at least 90 samples over 3 s with a
5% or lower miss ratio to probe up. A new tier settles for 500 ms; probes require at least
20 samples over 500 ms to validate. The initial retry delay is 2 s and grows after failed
probes. These are **uncalibrated starting values**, retained until valid paired measurements
justify a change.

Android uses `FrameMetrics.TOTAL_DURATION` only when an attached view can be matched to its
actual Activity or Compose Dialog `Window` on API 24+. API 31+ uses the reported deadline;
API 24–30 estimates the budget from display refresh. First-draw and dropped reports are invalid.
An unmatched, detached, or unsupported window gets no timing tier. Desktop uses `LocalAwtWindow`
and iOS uses `LocalUIView` when available. Web and native macOS use a composition-root
`GlassAdaptiveHost` token because no automatic host identity was established there. Skiko targets
use demand-driven callback cadence with lower confidence. When the display refresh target is
unknown, a stable cadence can initiate the bounded relative probe after the normal three-second
healthy window. The pre-probe cadence must stay within 125% of its shortest interval. At least
20 callbacks over 500 ms after promotion establish the comparison; a mean interval over 120%
of baseline or the existing miss threshold rolls back and starts exponential retry backoff.
Accepted probes remain under comparison while demand continues. A stable 16.7 ms stream can
represent healthy 60 Hz or an unrelated half-rate 120 Hz stream; a learned 60 Hz cap is not
independent refresh evidence, and unchanged cadence does not prove GPU headroom. Cadence slower
than 60 Hz is treated conservatively. A missing identity, lifecycle, timing
source, or recent valid sample selects the deterministic workload fallback; hosts are never
merged under a global unknown key.

The fallback builds the actual retained-layer plan at `sqrt(0.5)`, including expanded samples
and active blur, depth, refraction, rim, interaction, and group-composite layers. Distinct input
updates no more than 100 ms apart multiply retained pixels by up to three. A score of at least
1,500,000 selects `0.5`; it stays there until the score falls below 1,312,500. Otherwise it uses
`sqrt(0.5)`. This fallback has no full-resolution tier.

`FullResolution` remains exactly `1.0`. `Fixed(qualityFraction)` remains authoritative at
`sqrt(0.25 + 0.75 * qualityFraction)`, so public fixed `Balanced` is `sqrt(0.625)`, distinct from
Adaptive's middle tier. Fixed modes do not register timing demand. Retained-layer resource guards,
rendering topology, and Blur policy are unchanged.

## Validation and release gate

The earlier Pixel 6 eight-iteration fixed-scale comparison measured a stable 280dp by 180dp
surface at linear `0.75` and `0.5`: CPU frame P50 / P90 was 5.9 / 8.2 ms and
5.4 / 7.9 ms respectively. In its animated Glass Playground workload, CPU frame P50 / P90
was 22.9 / 30.1 ms at `0.75` and 13.8 / 19.5 ms at `0.5`; median peak GPU memory fell from
133,346 to 92,468 KiB. This supports the cost of lower input resolution, not the new
host-feedback thresholds or cross-platform behavior. Deterministic controller, host, render,
and Android-window tests pass. Apple simulator and macOS tests compile and pass, but they do not
exercise real window lifecycle and timing.

The CB-10 physical and visible-browser attempts, including benchmark workload and tier
behaviour, are recorded in
[the calibration report](../../internal/benchmark/CB10_ADAPTIVE_VALIDATION.md). They do not
justify threshold changes. The r3 Skiko experiment has deterministic cadence and lifecycle
tests, visible fast and throttled browser traces, and selected tier-backed Full-versus-Quality
corner pairs at DPR 1/2. Those pairs support a narrow visual comparison; source-phase shifts and
one selected pair per DPR limit the claim. Callback and completed-draw markers do not establish
GPU completion. The paired physical Android comparison on the final revision, allocation churn,
GPU memory parity, and real desktop/iOS lifecycle checks remain open. **Do not release
host-feedback Adaptive as the default based on this evidence.**

## Consequences

- A host can coordinate active Glass nodes while preserving separate decisions across windows.
- Verified Android timing can respond to whole-window pressure. Skiko cadence is a weaker signal;
  its relative probe is reversible, and an unchanged callback interval is not proof of GPU
  headroom or of the display's true refresh target.
- A missing or expired timing source returns to the bounded workload policy without changing
  Fixed-mode output.
- Threshold changes need a valid active Glass workload, paired physical measurements, frame and
  memory traces, and representative corner captures. An unsafe host binding or harmful churn
  requires revisiting plan r2 before a material design change.

## References

- [Issue #1178: Make Glass adaptive sampling workload-aware](https://github.com/chrisbanes/haze/issues/1178)
- [ADR-0002: Use a shared retained-stage graph for Glass](0002-use-a-shared-retained-stage-graph-for-glass.md)
- [ADR-0003: Use one Android fused Glass renderer](0003-use-one-android-fused-glass-renderer.md)
