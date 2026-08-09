# ADR-0006: Reconcile built-in performance-mode terminology

## Status

Accepted

## Date

2026-08-09

## Context

ADR-0004 and ADR-0005 record the validation and adaptive-policy decisions for Blur and Glass.
They predate the unified built-in `HazePerformanceMode` API, so they describe built-in effects with
the former `HazeSampling` vocabulary. That vocabulary is still correct for generic custom effects,
but it no longer describes the public modifiers for built-in Blur and Glass.

The historical measurements remain useful evidence for the separate Blur and Glass resolver
policies. Rewriting their tables would silently alter a record of the conditions, API, and metrics
that were accepted at the time.

## Decision

Built-in Blur and Glass use `HazePerformanceMode`; custom effects retain `HazeSampling` as their
generic input-sampling contract.

- `HazePerformanceMode.Default` is `Adaptive`.
- `Quality` replaces the former built-in full-resolution choice.
- `Quality`, `Balanced`, and `Performance` are effect-owned named profiles. Their internal
  resolution mappings are not a cross-effect public contract.
- `Fixed(qualityFraction)` is a normalized, effect-owned override. A previous built-in fixed
  input-pixel fraction has no direct equivalent and must be remeasured for the effect and layout in
  use.
- The adaptive resolver thresholds, cadence handling, and hysteresis recorded in ADR-0004 and
  ADR-0005 remain unchanged. Changing them requires fresh paired physical-device evidence and
  representative visual review.

The controlled Android Macrobenchmark suite runs every named built-in performance mode for both
effects under stable and rapidly changing input workloads. It records frame-duration CPU and
frame-overrun metrics; changing Glass workloads also record peak memory. Release documentation
summarizes the device, build, refresh rate, iteration count, scenarios, and accepted metrics rather
than committing raw traces or JSON reports.

## Validation

The compatibility and terminology change is covered by public API compilation, effect-specific
resolver tests, deterministic controlled benchmark navigation, and representative screenshot
coverage. The current physical-device reference measurements are recorded in the Blur and Glass
performance guidance; raw Macrobenchmark artifacts remain local build outputs.

## Consequences

- Existing callers can choose a stable named profile without knowing a renderer's input-pixel
  calculation.
- Existing custom effects keep their explicit `HazeSampling` behavior.
- Documentation and migration guidance distinguish the two contracts and require remeasurement
  instead of presenting a speculative fixed-fraction conversion.
- ADR-0004 and ADR-0005 retain their historical evidence and implementation-policy decisions, but
  their built-in public-API terminology is superseded by this ADR.

## References

- [Issue #1206: Calibrate performance modes](https://github.com/chrisbanes/haze/issues/1206)
- [ADR-0004: Use quality-gated adaptive input scaling for blur](0004-use-quality-gated-adaptive-input-scaling-for-blur.md)
- [ADR-0005: Use cadence-weighted adaptive input scaling for Glass](0005-use-cadence-weighted-adaptive-input-scaling-for-glass.md)
