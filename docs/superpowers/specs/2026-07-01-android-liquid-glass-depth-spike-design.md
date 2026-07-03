# Android Liquid Glass Depth Spike Design

## Context

Issue #1009 tracks a Liquid Glass rendering limitation on Android 13+. Skiko can
build a runtime shader with two inputs: original content and separately blurred
content. The shader can then use `depth` to mix between those inputs.

Android's `RenderEffect.createRuntimeShaderEffect` path exposes only one content
input to AGSL. The current Android implementation feeds blurred content into the
single-input shader, so the shader's `base` sample is already blurred. That
keeps the effect working but prevents Android `depth` from matching the Skiko
dual-input semantics.

The shader generator already has an `OverlayWithExternalUnderlay` mode. The
spike should test whether Android can use that mode with a draw-level
composition strategy: draw a blurred underlay first, then draw a Liquid Glass
overlay from the original content layer.

## Goal

Prove whether an Android two-pass draw can provide acceptable Liquid Glass
`depth` behavior without changing public APIs.

The specific question is whether Compose on Android reliably honors two
different `GraphicsLayer.renderEffect` values when the same retained content
layer is drawn twice in a single draw pass.

## Non-Goals

- Do not redesign the `VisualEffect` lifecycle.
- Do not change public Liquid Glass APIs.
- Do not productionize fallback behavior for every Android version in the first
  pass.
- Do not tune the final visual values beyond what is needed to judge the
  composition strategy.
- Do not change the Skiko runtime shader path.

## Recommended Spike

Add an Android-specific two-pass path to the existing runtime-shader delegate
shape.

The draw sequence should use the retained content layer that
`RuntimeShaderLiquidGlassDelegate` already records:

1. Set the layer's render effect to a blurred underlay effect.
2. Draw the layer.
3. Set the layer's render effect to a Liquid Glass overlay effect.
4. Draw the same layer again.

The blurred underlay effect should apply the current blur or progressive blur
configuration and the same output shape mask used today. The overlay effect
should use `LiquidGlassShaders.ContentMode.OverlayWithExternalUnderlay`, sampling
the original `content` input instead of a pre-blurred input.

The implementation can be temporary and narrowly scoped. It is acceptable for
the spike to keep the two-pass behavior behind Android-only factory or renderer
helpers rather than fully polishing the common delegate abstraction.

## Implementation Shape

Keep Skiko unchanged:

- `LiquidGlassRenderEffectFactory.skiko.kt` continues to use the dual-input
  shader with `content` and `blurredContent`.

Change Android only:

- Replace the current single Android `SingleBlurredInput` effect with two
  reusable effects:
  - a masked blurred underlay effect;
  - an overlay Liquid Glass runtime shader effect using
    `OverlayWithExternalUnderlay`.
- Cache both effects from the same `RenderParams` used by the current delegate.
- In the Android draw path, mutate `layer.renderEffect` between two
  `drawLayer(layer)` calls.
- Keep `layer.alpha` handling explicit. The initial spike should use
  `effect.alpha` for the overlay and apply underlay visibility according to
  `depth`, so `depth = 0f` visually favors the original-content overlay and
  `depth = 1f` visibly favors the blurred underlay.

If the common delegate becomes awkward, split only the draw/effect application
boundary by platform. Avoid duplicating source capture, retained-output, and
parameter resolution unless the spike proves a platform-specific delegate is the
cleaner production shape.

## Verification

Add Android screenshot coverage that makes the depth behavior obvious:

- `depth = 0f`
- a mid-depth value such as `0.5f`
- `depth = 1f`

Use a high-contrast background so blur contribution is easy to see. Existing
Liquid Glass screenshot helpers are sufficient; this should be a visual contract
test, not a semantic Compose test.

Run the Android screenshot test on API 33+ because the runtime-shader render
effect path is only available there. Compare the spike output against current
Android behavior and Skiko behavior by visual inspection.

## Pass Criteria

The spike is promising if:

- Android renders both passes in the same draw operation.
- Changing `GraphicsLayer.renderEffect` between the two `drawLayer(layer)` calls
  is reliable in screenshots.
- The `depth = 0f`, mid-depth, and `depth = 1f` screenshots show a meaningful
  progression from original-content overlay toward blurred underlay.
- The extra draw pass does not show obvious visual artifacts such as incorrect
  clipping, stale layer content, double alpha outside the shape, or broken edge
  softness.

## Fail Criteria

Keep the current Android limitation documented if:

- Android ignores one of the render-effect changes within the draw pass.
- The two draws produce stale or order-dependent output.
- The alpha/composition model cannot approximate Skiko `depth` well enough.
- The extra pass is obviously too expensive for common Liquid Glass use.

## Follow-Up If Successful

Productionize the approach by tightening the platform boundary:

- Extract a small renderer/effect bundle abstraction rather than growing
  conditional Android behavior in the shared delegate.
- Add focused tests for depth screenshots on Android API 33+.
- Document that Android uses a draw-level composition strategy while Skiko uses a
  native dual-input shader.
- Revisit alpha math and blending after comparing against Skiko outputs.
