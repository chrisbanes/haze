# Architecture

Haze keeps effect configuration (a Style) separate from the per-modifier state that renders it.

## Core model

Every typed effect has three parts:

```kotlin
interface HazeEffectFactory<Style> {
    fun createRenderer(): HazeEffectRenderer<Style>
}

interface HazeEffectRenderer<Style> {
    fun HazeEffectDrawScope.draw(style: Style)
    fun HazeEffectLayoutScope.calculateLayerBounds(style: Style): Rect = modifierBounds
    fun onTrimMemory(level: TrimMemoryLevel) = Unit
    fun dispose() = Unit
}
```

- A Style describes the effect configuration. Built-in Blur and Glass Style blocks can read snapshot
  state, so their resolved values can change without replacing the Style.
- A factory is a stateless descriptor that many modifiers can share.
- A renderer owns the mutable state and resources for exactly one modifier.

Changing the Style, input, sampling, or layer expansion keeps the current renderer. Replacing the
factory, or removing the modifier, disposes it.

## Inputs and policies

Structural choices are explicit modifier arguments rather than part of the Style:

```kotlin
Modifier.hazeEffect(
    factory = customEffect,
    input = HazeInput.Sources(hazeState),
    style = style,
    sampling = HazeSampling.Adaptive,
    expandLayerBounds = true,
)
```

- `HazeInput.Backdrop(state)` is the normal input for built-in Blur and Glass. It consumes pixels
  drawn earlier in the current window, and its `fallback` configures the source capture used
  when the native backdrop isn't available. It can't select individual sources, include later
  drawing, or cross a window boundary.
- `HazeInput.Sources` consumes content captured by `hazeSource`, with explicit selection and
  retention.
- `HazeInput.Content` consumes the modifier's own content.

See [Core concepts](core-concepts.md#explicit-inputs) for how to choose.

## Renderer scopes

The draw scope is a `DrawScope` that adds the modifier bounds, the selected sampling policy,
composition-local access, and a single `drawInput()` call that works for every input.

The layout scope gives density, modifier bounds, and composition-local access, so a renderer can
request larger layer bounds.

Reads are tracked in the phase where they happen:

- state or composition-local reads while drawing redraw the effect when they change;
- reads while calculating layer bounds recalculate the bounds, then redraw;
- a new Style updates both without replacing the renderer.

Source geometry, windows, and captured layers aren't exposed to renderers; Haze handles source
selection, ordering, and cross-window alignment for you.

## Built-in Blur

`hazeBlur` takes a `HazeBlurStyle`, resolved from `HazeBlurDefaults.style`, `LocalHazeBlurStyle`, and
the explicit Style, in that order. Snapshot state read inside a Style block is observed: when it
changes, Blur resolves the Style again and redraws without recomposition. Styles and the Blur
factory can be shared; each modifier owns its resolved values and renderer.

## Built-in Glass

`hazeGlass` takes a `GlassStyle`, resolved from defaults, `LocalGlassStyle`, and the explicit Style.
Snapshot state read inside a Style block is observed: when it changes, Glass resolves the Style
again and redraws without recomposition. Glass renderers are never shared between modifiers.

## Modules

- **haze**: core state, source capture, and the custom-effect API
- **haze-blur**: Blur effect
- **haze-blur-materials**: ready-made Blur Styles, such as `HazeMaterials.thin()`
- **haze-blur-material3**: optional Compose Material 3 Blur Style factory
- **haze-glass**: Glass effect
- **haze-glass-material3**: optional Compose Material 3 Glass Style factory
- **haze-utils**: shared internal utilities, included transitively; you don't add it yourself

## Extension points

Use `hazeBlur` for Blur, `hazeGlass` for Glass, and `HazeEffectFactory` with `hazeEffect` for
custom effects. The older lambda-based effect API, `VisualEffect`, and the `glassEffect` DSL have
been removed. See [Custom effects](custom-effects.md).

## Design decisions

The reasoning behind Haze's larger design choices is recorded in its
[architecture decision records](https://github.com/chrisbanes/haze/tree/main/docs/adr).
