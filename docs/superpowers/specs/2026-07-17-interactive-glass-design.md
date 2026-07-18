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

Every interaction state is independently opt-in and is configured directly on the retained
`GlassVisualEffect`. The smallest opt-in installs only the default pressed response:

```kotlin
Modifier.hazeEffect(state) {
  glassEffect {
    pressed()
    shape = shape
  }
}
```

The no-argument state methods install conservative presets, and `interactable()` is shorthand for
installing all three:

```kotlin
glassEffect {
  hovered()
  focused()
  pressed()
}

// Equivalent to the three calls above:
glassEffect {
  interactable()
}
```

Custom state blocks replace that state's preset and start from identity:

```kotlin
glassEffect {
  interactionSource = interactionSource
  interactionLightRadiusFraction = 0.7f
  interactionTransformTarget = GlassTransformTarget.MaterialAndContent
  interactionTransformPivot = GlassTransformPivot.Pointer
  interactionPositionAnimationSpec = GlassDefaults.positionAnimationSpec
  interactionReducedMotionPolicy = GlassReducedMotionPolicy.System

  hovered()

  pressed {
    animate(
      toSpec = GlassDefaults.pressAnimationSpec,
      fromSpec = GlassDefaults.releaseAnimationSpec,
    ) {
      scale(0.98f)
    }
  }
}
```

The custom pressed response above contains only scale. It does not inherit the preset pressed
lighting or optics. If hover is active during the press, its lighting and optics remain visible
because the pressed response does not declare replacements for those properties.

The public API has these semantics:

- `GlassVisualEffect` exposes no standalone `GlassInteraction` value, interaction parameter,
  enabled flag, or nested interaction builder.
- `hovered()`, `focused()`, and `pressed()` each replace that state's response slot with its preset.
- `hovered {}`, `focused {}`, and `pressed {}` each replace that state's response slot with a custom
  response containing only the properties declared in its block.
- `interactable()` replaces all three slots with their presets. A later state call replaces only
  that state, so `interactable(); pressed { ... }` retains preset hover and focus responses and uses
  the custom pressed response.
- `clearHovered()`, `clearFocused()`, and `clearPressed()` remove individual slots.
  `clearInteractions()` removes all three. These explicit clears match the retained configuration
  semantics of existing `GlassVisualEffect` properties.
- With no response slots configured, the effect is non-interactive and installs no interaction
  controller, pointer observer, or animation resources.
- A public `GlassInteractionScope` is only the receiver for custom response blocks. It exposes
  property functions for lighting intensity, refraction multiplier, white-point delta, and
  independent X/Y scale. It does not expose state methods, so state blocks cannot be nested.
- Multiple declarations of the same property within one response use last-write-wins semantics.
- `animate(toSpec, fromSpec) {}` associates transition specs with the properties in its block.
  `toSpec` applies when the response becomes the owner of a property; `fromSpec` applies when it
  stops owning that property and reveals a lower-priority response or identity. Properties declared
  outside `animate` update immediately.
- Active responses resolve per property by applying focus, then hover, then press. A higher-priority
  response overrides only properties it declares; undeclared properties fall through to the next
  active response or their identity values.
- `interactionSource` remains a nullable `GlassVisualEffect` property because it supplies live
  interaction state rather than visual configuration.
- Focus responses activate only from a configured `interactionSource`. These APIs do not add focus
  semantics or make the component focusable.
- Common interaction settings are direct `GlassVisualEffect` properties:
  `interactionLightRadiusFraction`, `interactionTransformTarget`, `interactionTransformPivot`,
  `interactionPositionAnimationSpec`, and `interactionReducedMotionPolicy`.
- The copy constructor copies all response slots, the interaction source, and common interaction
  settings.
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
- Lighting intensities are constrained to `0f..1f`, and
  `interactionLightRadiusFraction` is constrained to `0f..2f` as a fraction of the material's
  shortest side.
- Refraction multipliers are constrained to `0f..2f`, and white-point deltas are constrained to
  `-1f..1f`. The resolved results are clamped to the base properties' supported ranges.
- Every numeric value must be finite. State property functions validate while compiling the
  response; an invalid declaration fails before it reaches the controller or renderer.

The state presets use these response values:

- hover and focus: lighting intensity `0.35f`, refraction multiplier `1.02f`, white-point delta
  `0.01f`, and identity scale;
- press: lighting intensity `1f`, refraction multiplier `1.08f`, white-point delta `0.04f`, and
  `0.98f` scale on both axes.

The default light-radius fraction is `0.7f`. The default transform target is `MaterialOnly`, with a
pointer-relative pivot. Material-plus-content remains available for control-like elements. Each
optics delta uses the same radial falloff as interaction lighting so the response remains localized
around the pointer.

The focus preset uses the hover response values shown above. The preset motion specs are:

- hover or focus entry: `spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)`;
- press entry: `spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)`;
- state exit: `spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)`; and
- pointer position: `spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)`.

Animated response properties use `FiniteAnimationSpec<Float>`, and pointer position uses
`FiniteAnimationSpec<Offset>`. The defaults live in `GlassDefaults` so callers can reuse or replace
them without copying constants.

Interaction stays separate from `GlassStyle`. It controls input, animation, and lifecycle behavior
rather than an inheritable static appearance.

The API deliberately borrows state blocks, scoped property declarations, last-write-wins behavior,
per-property fallback, and animated enter/exit declarations from Compose Foundation's Style API.
It does not copy the Style ownership model: there is no reusable Style-like value, `then` operator,
declaration-order precedence, or nested combined-state block. Reuse uses the existing
`GlassVisualEffect.() -> Unit` configuration pattern. Haze does not implement or expose Compose
`Style`, `CustomStyle`, `StyleScope`, `StyleState`, or `Modifier.styleable`; glass properties and
custom rendering remain owned by Haze.

`interactable()` installs visual-response presets only. It does not add semantics or make the
component clickable, focusable, selectable, or otherwise behaviorally interactive.

## Core Interaction Capability

Core `haze` gains a narrow, generic `InteractiveVisualEffect` capability. It is a public interface
annotated with `@InternalHazeApi` so the separately compiled `haze-glass` module can implement it
without making core Haze aware of glass types. It extends `VisualEffect` and has this semantic
surface:

```kotlin
@InternalHazeApi
public interface InteractiveVisualEffect : VisualEffect {
  public val observesPointerEvents: Boolean

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

- whether raw pointer observation is currently required;
- delivery of non-consuming pointer events in node-local coordinates;
- pointer-stream cancellation;
- access to the existing `VisualEffectContext`; and
- the current optional final-draw transform when an effect requests a material-plus-content
  transform.

`HazeEffectNode` becomes a `DelegatingNode` and dynamically installs a private
`PointerInputModifierNode` only while its active effect requests interaction. Disabling interaction
or replacing the effect removes that delegate. A non-interactive haze node therefore does not enter
pointer hit testing and does not run interaction coroutines.

`GlassVisualEffect.observesPointerEvents` is true when a hover or press response slot exists. A
focus-only response driven by `InteractionSource` does not install a pointer delegate.

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
controller begins with identity response values and applies each configured active slot per
property in fixed focus, hover, then press order. A later slot overrides only properties it
declares. This gives press-over-hover behavior while allowing, for example, a custom scale-only
press response to retain active hover lighting.

A specified pointer position takes precedence over a centered source-only interaction. Focus and
keyboard or D-pad activation use the center of the current node. A source press with a specified
position may use that position when no raw pointer position is active.

The first down pointer becomes the primary press pointer. Additional pointers are ignored until the
primary pointer ends or is cancelled. Hover continues to update the latest in-bounds position. The
last valid position is retained through the release animation so the response fades from the point
of contact rather than jumping to the center.

Raw input and `InteractionSource` signals are merged independently rather than counted. This makes
sharing the same source with a clickable component safe: duplicate observations of one physical
press cannot apply a response slot twice, and either signal can end without prematurely clearing the
other.

Clearing the final response slot cancels input collection and animation, clears the primary pointer,
and returns to the identity transform on the next draw. Replacing the interaction source cancels
the old collector before starting the new one. Detachment and effect replacement perform the same
cleanup.

## Motion And Reduced Motion

Each animated declaration supplies entering and leaving specs. The resolver produces a target and
owning response slot for every property. When a response becomes the property's owner, its `toSpec`
animates from the currently rendered value. When it stops owning the property, its `fromSpec`
animates toward the newly revealed lower-priority response or identity. State changes hidden by a
higher-priority active response do not restart that property's animation.

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
- Replacing a response slot while active reevaluates targets against the current states. A new or
  replaced target uses its new owning response's `toSpec`; a removed target uses its previous
  owner's `fromSpec` when another slot remains configured.
- Clearing the final response slot performs immediate lifecycle cleanup and identity reset rather
  than keeping input machinery alive for a configuration-removal animation.

## Testing

### Core Haze

- Pointer delegation is installed only when `observesPointerEvents` is true.
- A focus-only response does not install a pointer delegate.
- Events are observed but never consumed.
- Pointer cancellation reaches the effect.
- Clearing hover and press responses, replacing the effect, or detaching removes the pointer
  delegate.
- Material-plus-content transforms wrap the correct draw group in foreground and background modes
  without changing measurement or hit bounds.

### Interaction Controller

- Idle, hover, focus, press, release, and cancellation transitions.
- Independent opt-in and clearing for hover, focus, and press slots.
- No-argument state presets and `interactable()` equivalence.
- A custom state response replacing, rather than inheriting, its preset.
- Fixed focus, hover, and press precedence with per-property identity fallback.
- Last-write-wins declarations within a custom response.
- Per-property `toSpec` and `fromSpec` selection when responses enter, leave, or are obscured.
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

Add a sample showing `pressed()`, `interactable()`, and a customized scale-only `pressed { ... }`
response using an `InteractionSource`, material-plus-content compression, custom motion, and a
reduced-motion policy.

## Success Criteria

The feature is complete when callers can independently opt a glass effect into default or custom
touch, hover, focus, and press feedback; enable all presets with `interactable()`; customize or
disable every channel; use either transform target; and observe smooth motion without input
interference or base-pipeline churn. Non-interactive glass must retain its current behavior and
incur no pointer-input or animation overhead. Fallback platforms must retain lighting and transform
feedback with the optics limitation documented.
