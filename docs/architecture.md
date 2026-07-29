# Architecture

Haze separates shareable effect configuration from modifier-node rendering state.

## Core model

The preferred custom-effect path has three parts:

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

- A Style is the complete immutable effect configuration.
- A factory is a stateless descriptor that can be shared by concurrent modifiers.
- A renderer owns mutable rendering state and resources for exactly one modifier node.

Factory identity controls renderer ownership. Style, input, sampling, and layer-expansion updates
reuse the current renderer. Replacing the factory or detaching the node disposes that renderer.

## Explicit input and policies

The typed modifier makes structural behavior explicit:

```kotlin
Modifier.hazeEffect(
    factory = customEffect,
    input = HazeInput.Sources(hazeState),
    style = style,
    sampling = HazeSampling.Adaptive,
    expandLayerBounds = true,
)
```

`HazeInput.Sources` selects content captured by `hazeSource`; `HazeInput.Content` selects the
modifier's own content. Source selection, retained-output privacy, sampling, and layer expansion
remain core policies rather than effect-specific mutable configuration.

## Semantic renderer scopes

The draw scope exposes ordinary `DrawScope` operations, the modifier bounds in the effect layer,
the selected sampling policy, tracked composition-local access, and one `drawInput()` operation for
both input modes.

The layout scope exposes density, modifier bounds, and tracked composition-local access. A renderer
can request larger bounds without seeing the modifier node, source geometry, coordinates, windows,
or captured layers.

Reads are observed in the phase where they occur:

- snapshot or composition-local reads in draw invalidate drawing;
- reads while calculating layer bounds recalculate bounds and then redraw;
- Style replacement invalidates both phases without replacing the renderer.

## Internal node responsibilities

`HazeEffectNode` remains responsible for:

- source selection and z-ordering;
- own-content capture;
- coordinate-space and cross-window handling;
- graphics-layer allocation and release;
- trim-memory forwarding;
- renderer replacement and disposal;
- draw and bounds invalidation.

Those details are intentionally absent from the public typed renderer scopes.

## Modules

- **haze** — core state, source capture, typed custom-effect orchestration, and the temporary legacy
  path
- **haze-blur** — blur effect implementation
- **haze-blur-materials** — reusable blur presets
- **haze-glass** — Glass effect implementation
- **haze-utils** — shared platform rendering utilities

Effect modules can keep platform-specific renderer internals behind their own `expect`/`actual`
boundaries.

## Legacy compatibility

`VisualEffect`, `VisualEffectContext`, `HazeEffectScope`, and the lambda-based modifier overloads
remain temporarily available for Blur, Glass, and third-party migration. They expose more lifecycle
and rendering internals and are no longer the recommended contract for new custom effects.

See [Custom effects](custom-effects.md) for the preferred API.
