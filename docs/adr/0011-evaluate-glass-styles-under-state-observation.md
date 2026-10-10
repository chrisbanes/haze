---
status: accepted
---

# Evaluate Glass styles under state observation

A `GlassStyle` block currently runs exactly once, when the style is built. Its values are validated
and recorded at that point, and the recorded writes are replayed into each node later. To animate a
Glass shape, for example a button morphing into a menu, a caller has to build a new style on every
frame, which recomposes the `hazeGlass` call site each time
([#1408](https://github.com/chrisbanes/haze/issues/1408)).

Glass styles will instead follow the evaluation model of Compose Foundation Styles
(`androidx.compose.foundation.style`). The node evaluates the style block itself and observes the
snapshot state the block reads. When that state changes, the node resolves the style again and
invalidates only the affected work, without recomposition. Built-in styles,
`LocalGlassStyle`, `then` and the order in which later writes win stay the same.

## Considered options

- **Implement Compose's `CustomStyle<GlassStyleScope>` directly.** Rejected. Haze's public styling
  API would then inherit `@ExperimentalFoundationStyleApi` and the stability of foundation's types.
  Haze copies the semantics using its own `GlassStyle` and `GlassStyleScope` types instead.
- **Add per-frame lambdas for a few named properties, or a separate modifier-level block.** Rejected.
  Either would give Glass two ways to set the same appearance value, each with its own precedence
  rules, and would diverge from the Compose model that callers already know.
- **Move Glass interaction responses to `pressed {}`, `hovered {}` and `animate {}` blocks.**
  Deferred. The interaction API is unchanged by this decision.

## Consequences

- **The block's contract changes.** The documentation that says the block "executes exactly once"
  no longer holds. A block may run on any frame, so it must not have side effects.
- **Validation moves to evaluation time, with coercion for animated values.** Alpha and tint
  alpha are clamped to `0..1` and negative corner radii to zero, so animation overshoot can't throw
  during draw. Non-finite corner radii keep using the safe-default fallback. Other invalid values
  still throw `IllegalArgumentException`, now when the block runs.
- **No property has a guaranteed-cheap invalidation path.** Every property can be driven by
  state, and a change invalidates the stages its dirty fields map to, which for shape, alpha and tint
  still includes layer bounds and delegate selection. The original decision promised that those three
  would skip that work. A physical-device measurement
  ([#1408](https://github.com/chrisbanes/haze/issues/1408#issuecomment-6096008219)) found the skipped
  work would cost about 0.004 ms per effect per frame on a shape-and-size morph, where the size change
  pays it anyway. The narrowing ([#1420](https://github.com/chrisbanes/haze/issues/1420)) was dropped.
  Revisit it only if a measurement, such as a radius-only animation at constant size, shows a
  meaningful cost.
- **Some changes always pay the broader cost.** These can change the retained-layer plan:
  - Corner radii crossing zero change the clip decision.
  - Alpha entering or leaving the fractional range `0 < alpha < 1` adds or removes the full-size
    group-composite layer. That layer counts against the render budget, so the change can select a
    different renderer.
  - Alpha reaching zero skips drawing altogether.
