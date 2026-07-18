# Interactive Glass Design

## Context

`GlassVisualEffect` currently renders a static glass material. Callers can move its light source or
change optical properties, but the effect does not observe touch, pointer, focus, or press
interactions. Apple's interactive Liquid Glass uses an explicit opt-in and responds to touch and
pointer input with localized illumination, changing optics, and physical motion.

Haze should offer a similar optional interaction model without consuming application gestures,
changing layout, or forcing every glass effect to participate in pointer hit testing.

The existing glass renderer caches source, blur, depth, optical, detail, and rim stages. Several
properties that would change during interaction are currently part of immutable render-effect keys.
Updating those properties every frame would rebuild cached effects and invalidate more of the glass
pipeline than the interaction requires.

## Goal

Add optional interactive behavior to `GlassVisualEffect` with:

- automatic, non-consuming touch and pointer observation;
- mouse and stylus hover tracking;
- optional `InteractionSource` support for focus and keyboard or D-pad activation;
- independently configurable lighting, optics, and transform channels;
- configurable hover, press, release, and position animation specs;
- material-only and material-plus-content transform targets;
- reduced-motion behavior that follows the system by default and can be overridden; and
- a dynamic rendering path that does not recompute the base glass pipeline on every interaction
  frame.

## Non-Goals

- Do not merge, morph, or propagate interaction between nearby glass elements.
- Do not add a `GlassEffectContainer` equivalent.
- Do not consume, synthesize, or own click, drag, scroll, or long-press gestures.
- Do not change layout size, placement, semantics bounds, or pointer hit targets.
- Do not promise pixel-identical behavior to Apple's private renderer.
- Do not add a fully custom per-frame render-parameter resolver in the first version.
- Do not add refraction to the existing non-runtime-shader fallback delegate.

## Public API

Interactivity is disabled by default. The common opt-in is configured with the existing glass DSL:

```kotlin
Modifier.hazeEffect(state) {
  glassEffect(
    interaction = GlassInteraction.Default,
  ) {
    shape = shape
  }
}
```

The `interaction` parameter defaults to `null`; a non-null value is the explicit opt-in. The
`GlassInteraction.Default` companion value enables all three response channels with conservative
values. Callers customize it by layering a scope-based interaction over it:

```kotlin
glassEffect(
  interaction = GlassInteraction.Default then {
    lightRadiusFraction(0.7f)
    transformTarget(GlassTransformTarget.MaterialOnly)
    transformPivot(GlassTransformPivot.Pointer)
    pointerPositionAnimationSpec(GlassInteractionDefaults.positionAnimationSpec)
    reducedMotion(GlassReducedMotionPolicy.System)

    hovered {
      animate(
        toSpec = GlassInteractionDefaults.hoverAnimationSpec,
        fromSpec = GlassInteractionDefaults.releaseAnimationSpec,
      ) {
        lightingIntensity(0.35f)
        refractionMultiplier(1.02f)
        whitePointDelta(0.01f)
      }
    }

    pressed {
      animate(
        toSpec = GlassInteractionDefaults.pressAnimationSpec,
        fromSpec = GlassInteractionDefaults.releaseAnimationSpec,
      ) {
        lightingIntensity(1f)
        refractionMultiplier(1.08f)
        whitePointDelta(0.04f)
        scale(0.98f)
      }
    }
  },
  interactionSource = interactionSource,
)
```

The public API uses the following concrete shape:

- `glassEffect` gains `interaction: GlassInteraction? = null` and
  `interactionSource: InteractionSource? = null` parameters. The retained `GlassVisualEffect`
  exposes and copies both properties.
- `GlassInteraction` is an opaque `fun interface`, not a data class:

  ```kotlin
  public fun interface GlassInteraction {
    public fun GlassInteractionScope.applyInteraction()

    public companion object {
      public val Default: GlassInteraction
    }
  }
  ```

- `GlassInteraction { ... }` is the DSL. There is no separate builder, response object hierarchy,
  public constructor state, or `copy` API.
- A standalone `GlassInteraction { ... }` contains only its declarations; unspecified response
  properties resolve to their identity values. `GlassInteraction.Default then { ... }` is the
  convenience form for overriding the complete preset.
- `GlassInteractionScope` exposes property functions for lighting intensity, light radius,
  refraction multiplier, white-point delta, scale, transform target, transform pivot, pointer
  position animation, and reduced-motion policy.
- `hovered {}`, `focused {}`, and `pressed {}` conditionally apply property declarations. State
  blocks may be nested to target combined states.
- `animate(toSpec, fromSpec) {}` associates transition specs with the properties in its block.
  `toSpec` applies when the declaration becomes active and `fromSpec` applies when it stops being
  active and reveals an underlying value.
- `GlassInteraction.then` combines two interactions by applying the left side followed by the right
  side. The last declaration wins independently for each property, including declarations from
  active state blocks. A trailing lambda is converted to `GlassInteraction`, allowing
  `GlassInteraction.Default then { ... }`.
- `GlassInteraction.Default` is an ordinary reusable interaction value. `null`, rather than a
  disabled interaction instance or flag, is the only off state.
- `interactionSource` remains separate because it supplies live interaction state rather than
  visual configuration.
- Each channel has an identity/disabled value so it can be switched off independently.
- Lighting is disabled with zero intensity.
- Optics are disabled with a `1f` refraction multiplier and zero white-point delta.
- Transform is disabled with identity scale.
- Lighting exposes intensity plus the localized glow radius; state blocks provide state-specific
  intensity targets.
- Optics exposes refraction multiplier and white-point delta. State-specific targets are applied
  after normal style and adaptive-optics resolution; they do not mutate `GlassStyle`.
- Transform exposes independent X/Y scales, a target, and a pivot.
- `GlassTransformTarget` supports `MaterialOnly` and `MaterialAndContent`.
- `GlassTransformPivot` supports `Pointer` and `Center`.
- Transform scales are finite and constrained to `0f < scale <= 1f`, keeping the feature a visual
  compression that cannot require expanded layer bounds.
- Lighting intensities are constrained to `0f..1f`, and `radiusFraction` is constrained to
  `0f..2f` as a fraction of the material's shortest side.
- Refraction multipliers are constrained to `0f..2f`, and white-point deltas are constrained to
  `-1f..1f`. The resolved results are clamped to the base properties' supported ranges.
- Every numeric value must be finite. Property functions validate when the interaction is applied;
  an invalid declaration fails before it reaches the controller or renderer.

The default transform target is `MaterialOnly`, with a pointer-relative pivot. The default
material-plus-content behavior remains available for control-like elements. The initial defaults
are the numeric values shown above. Each optics delta uses the same radial falloff as the interaction
lighting so the response remains localized around the pointer.

`GlassInteraction.Default` uses the hover response values shown above for focus as well. It declares
its hover and focus responses before its pressed response, so the pressed values win when those
states overlap. Its default motion specs are:

- hover or focus entry: `spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)`;
- press entry: `spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)`;
- state exit: `spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)`; and
- pointer position: `spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)`.

Animated response properties use `FiniteAnimationSpec<Float>`, and pointer position uses
`FiniteAnimationSpec<Offset>`. The defaults live in `GlassInteractionDefaults` so callers can reuse
or replace them without copying constants.

Interaction stays separate from `GlassStyle`. It controls input, animation, and lifecycle behavior
rather than an inheritable static appearance.

The API deliberately borrows the concepts and vocabulary of Compose Foundation's Style API—opaque
scope-based values, state blocks, animated declarations, and right-biased layering—but does not
implement or expose Compose `Style`, `CustomStyle`, `StyleScope`, `StyleState`, or
`Modifier.styleable`. Glass properties and custom rendering remain owned by Haze, and callers do
not need to opt in to an experimental Compose API.

## Core Interaction Capability

Core `haze` gains a narrow, generic `InteractiveVisualEffect` capability. It is a public interface
annotated with `@InternalHazeApi` so the separately compiled `haze-glass` module can implement it
without making core Haze aware of glass types. It extends `VisualEffect` and has this semantic
surface:

```kotlin
@InternalHazeApi
public interface InteractiveVisualEffect : VisualEffect {
  public val isInteractionEnabled: Boolean

  public fun onPointerEvent(
    event: PointerEvent,
    context: VisualEffectContext,
  )

  public fun onCancelPointerInput(context: VisualEffectContext)

  public fun currentContentTransform(
    context: VisualEffectContext,
  ): VisualEffectTransform = VisualEffectTransform.Identity
}
```

`VisualEffectTransform` is an `@InternalHazeApi` value containing finite `scaleX`, `scaleY`, and a
node-local `pivot`, plus an `Identity` value. `GlassVisualEffect` returns a non-identity transform
only for `MaterialAndContent`; material-only transforms remain inside its renderer.

The capability provides:

- whether pointer observation is currently enabled;
- delivery of non-consuming pointer events in node-local coordinates;
- pointer-stream cancellation;
- access to the existing `VisualEffectContext`; and
- the current optional final-draw transform when an effect requests a material-plus-content
  transform.

`HazeEffectNode` becomes a `DelegatingNode` and dynamically installs a private
`PointerInputModifierNode` only while its active effect requests interaction. Disabling interaction
or replacing the effect removes that delegate. A non-interactive haze node therefore does not enter
pointer hit testing and does not run interaction coroutines.

The delegate observes the final pointer pass and never consumes changes. If another gesture such as
scrolling consumes the active pointer's movement, the raw visual press is cancelled. The component
that owns the actual gesture remains the sole authority for click and drag semantics.

## Interaction Controller

`GlassVisualEffect` owns an internal controller tied to the existing effect-node coroutine scope.
The controller keeps frame-rate state out of composition and separates input reduction from
interaction declaration resolution, animation, and render-parameter resolution.

It merges the following signals:

- in-bounds mouse and stylus hover;
- raw pointer down, move, up, exit, and cancellation;
- `PressInteraction` events from an optional `InteractionSource`;
- hover events from an optional `InteractionSource`; and
- focus events from an optional `InteractionSource`.

Raw input and the optional source produce independent hovered, focused, and pressed booleans. The
controller evaluates the combined `GlassInteraction` against those states in declaration order.
Base declarations always apply; active state blocks apply where they occur, nested blocks require
all enclosing states, and later declarations override earlier values per property. This gives the
default interaction press-over-hover behavior without hard-coding that precedence for custom
interactions.

A specified pointer position takes precedence over a centered source-only interaction. Focus and
keyboard or D-pad activation use the center of the current node. A source press with a specified
position may use that position when no raw pointer position is active.

The first down pointer becomes the primary press pointer. Additional pointers are ignored until the
primary pointer ends or is cancelled. Hover continues to update the latest in-bounds position. The
last valid position is retained through the release animation so the response fades from the point
of contact rather than jumping to the center.

Raw input and `InteractionSource` signals are merged independently rather than counted. This makes
sharing the same source with a clickable component safe: duplicate observations of one physical
press cannot apply a state block twice, and either signal can end without prematurely clearing the
other.

Disabling interaction cancels input collection and animation, clears the primary pointer, and
returns to the identity transform on the next draw. Replacing the interaction source cancels the old
collector before starting the new one. Detachment and effect replacement perform the same cleanup.

## Motion And Reduced Motion

Each animated declaration supplies entering and leaving specs. The resolver produces a target and
the owning declaration for every response property. When a declaration becomes active, its
`toSpec` animates from the currently rendered value. When it becomes inactive, its `fromSpec`
animates toward the newly revealed declaration or base identity. State changes hidden by a later
active declaration do not restart that property's animation.

Pointer-position tracking has its own animation spec because its target changes continuously rather
than through a state block. All property and position animations run in the effect-node coroutine
scope and invalidate drawing without causing composition.

`GlassReducedMotionPolicy` has three semantic modes:

- `System`: follow the platform motion-duration setting;
- `Reduced`: always use reduced behavior; and
- `Full`: use configured motion even when the platform requests reduced motion.

`System` is the default. When motion is reduced, lighting and optical values update immediately,
position tracking does not spring, and transform/compression remains at identity. This preserves
clear input feedback without spatial motion. Nonzero platform duration scaling continues to scale
configured animations normally.

## Rendering Pipeline

Interaction must not mutate the resolved base `GlassStyle` values or add frame-rate values to the
existing blur, depth, optical, detail, or rim cache keys.

The runtime-shader delegate adds interaction variants of only the stages whose uniforms change:

1. Record or reuse the existing source, blur, depth, optical, and refraction-detail stages exactly as
   today.
2. When the resolved interaction optics are non-identity, record an interaction optical layer from
   the already-retained depth input using the resolved base parameters plus localized interaction
   deltas.
3. When a refraction change affects the detail stage, record an interaction detail layer from the
   already-retained source input with the same resolved strength. Otherwise reuse the base detail
   layer.
4. Draw the interaction optical/detail output in place of the base optical/detail output.
5. Apply localized lighting using pointer position and animated strength, then draw the foreground
   rim using the same position and strength.

Lighting-only interaction reuses the base optical/detail output, and transform-only interaction adds
no offscreen layer. When all animated response properties reach their base identity values, the
effect bypasses all interaction render stages and draws the existing retained output directly.
Enabling interactivity therefore adds no steady-state offscreen pass while idle.

The compiled interaction shaders and static geometry inputs are cached. Android retains each
`RuntimeShader` and its `RenderEffect` and updates only dynamic uniforms. Skiko caches each compiled
`RuntimeEffect` and creates a new `RuntimeShaderBuilder` and final `ImageFilter` when dynamic
uniforms change. Neither platform re-records source content, reruns blur/depth, or rebuilds the base
optical/detail effects solely because an interaction value changes.

Material-only transforms wrap the glass output and its foreground rim inside
`GlassVisualEffect`. Material-plus-content transforms are exposed through the generic capability so
`HazeEffectNode` can wrap its final effect, content, and foreground draw group. Source capture and
geometry resolution happen before this final visual transform. Both targets keep layout and hit
bounds unchanged.

The fallback delegate supports interaction lighting with its existing Compose radial-gradient
drawing and supports both transform targets. The optics channel is a documented no-op on that
delegate because it cannot refract content. This matches the fallback's existing optical
limitations rather than presenting a misleading approximation.

## Invalid Geometry And Degradation

- A zero-sized or unspecified node ignores positional input until valid geometry is available.
- Non-finite pointer coordinates are discarded; the controller uses the last valid position or the
  node center.
- An active pressed pointer that moves outside the node retains the press and clamps its visual
  position to the node bounds until up, cancellation, detachment, or gesture consumption.
- Hover ends when the pointer exits. A pressed pointer ends on up, cancellation, detachment, or
  consumption by another gesture.
- Unsupported runtime shaders select the existing fallback delegate; they do not disable input or
  transform feedback.
- Changing configuration while active reevaluates declarations against the current states. A new or
  replaced target uses its new owning declaration's `toSpec`; a removed target uses its previous
  owner's `fromSpec`. Setting `interaction` to `null` performs immediate lifecycle cleanup and
  identity reset.

## Testing

### Core Haze

- Pointer delegation is installed only for an effect with a non-null interaction.
- Events are observed but never consumed.
- Pointer cancellation reaches the effect.
- Disabling, replacing, or detaching the effect removes the pointer delegate.
- Material-plus-content transforms wrap the correct draw group in foreground and background modes
  without changing measurement or hit bounds.

### Interaction Controller

- Idle, hover, focus, press, release, and cancellation transitions.
- Default press precedence over hover and focus.
- Base, active, nested-state, declaration-order, and right-biased `then` resolution.
- Per-property `toSpec` and `fromSpec` selection when declarations enter, leave, or are obscured.
- Primary-pointer selection and ignored secondary pointers.
- Last-position retention during release.
- Raw input and `InteractionSource` merging without duplicate strength.
- Consumed movement cancelling the raw press.
- Source replacement and lifecycle cancellation.
- System, forced-reduced, and forced-full motion policies.
- Invalid geometry and non-finite input handling.

### Rendering

- Deterministic screenshots for idle, hover, press, and release at multiple positions.
- Independent lighting, optics, and transform channel screenshots.
- Material-only and material-plus-content transform screenshots.
- Center and pointer pivot screenshots.
- Runtime-shader and fallback delegate coverage.
- Reduced-motion output with identity transform and immediate lighting/optics.
- Stage-invalidation tests proving interaction changes do not invalidate source, blur, depth, or
  base optical stages.
- An idle-path test proving the interaction layer is bypassed at zero progress.

### Integration And Performance

- A click still fires through an interactive glass effect.
- A scroll gesture cancels raw press feedback and remains unblocked.
- Mouse or stylus hover tracks position.
- Keyboard or D-pad focus and activation use the center position.
- Frame-rate interaction updates do not cause composition.
- Interaction does not recreate the base glass render effects or re-record captured content.

Add a sample showing `GlassInteraction.Default` and a customized
`GlassInteraction.Default then { ... }` control using an `InteractionSource`,
material-plus-content compression, custom motion, and reduced-motion policy.

## Success Criteria

The feature is complete when callers can opt a glass effect into responsive touch, hover, focus,
and press feedback with one configuration value; customize or disable every channel; use either
transform target; and observe smooth motion without input interference or base-pipeline churn.
Non-interactive glass must retain its current behavior and incur no pointer-input or animation
overhead. Fallback platforms must retain lighting and transform feedback with the optics limitation
documented.
