# Architecture

Haze separates shareable effect configuration from modifier-node rendering state.

## Core model

The supported custom-effect path has three parts:

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
modifier's own content. Experimental `HazeInput.Backdrop` lets supported built-in effects request
the combined earlier pixels in the current Android window while carrying a mandatory Sources
fallback. It is not a source-selection mode and cannot cross a window boundary. Source selection,
retained-output privacy, sampling, and layer expansion remain core policies rather than
effect-specific mutable configuration.

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

For Backdrop input, core also owns the attachment-scoped native/fallback decision, full Android
minor-SDK and hardware-canvas gate, backdrop geometry and clipping, draw ordering, and dormant
fallback capture demand. Blur and Glass own their platform root-effect graphs; no backend or
platform effect is public. A native failure becomes sticky source fallback after at most one
transition frame. [ADR-0009](adr/0009-use-opt-in-android-window-backdrops.md) records this exception
to the ordinary source-capture path.

Those details are intentionally absent from the public typed renderer scopes.

## Built-in Blur

Blur uses the same typed node lifecycle plus narrow `@InternalHazeApi` capabilities for lifecycle,
retained output, and semantic input drawing. Source geometry, captured layers, modifier nodes,
delegates, and caches remain owned by their implementation modules.

`HazeBlurStyle` and the shared Blur factory are stateless. Each modifier node creates one
`BlurVisualEffect` runtime that owns its resolved snapshot, invalidation state, adaptive-performance
history, delegate, retained output, render-effect cache, and platform resources. Style replacement
replays defaults, the current composition-local Style, and the explicit Style into a fresh snapshot
on that same runtime.

## Built-in Glass

Glass exposes only the typed `hazeGlass` modifier, replayable `GlassStyle`, and structural modifier
arguments. The shared factory is stateless. Each modifier creates one internal node-owned runtime
which replays the immutable writes recorded by defaults, `LocalGlassStyle`, and the explicit Style
into a fresh snapshot and owns its interaction controller, delegate, caches, retained layers, and
platform resources. A Style builder executes only during Style construction; changing a captured
input requires replacing the Style through recomposition.

The old public effect, renderer, cache, grouped sentinel values, and `glassEffect` DSL are removed.
No renderer or lifecycle object can be shared between Glass nodes.

On supported Backdrop input, Glass reuses the Android fused effect graph with compositor input and
keeps only independent rim and interaction-lighting foreground layers. It does not replace the
source-backed renderer established by
[ADR-0003](adr/0003-use-one-android-fused-glass-renderer.md).

## Modules

- **haze** — core state, source capture, and typed custom-effect orchestration
- **haze-blur** — blur effect implementation
- **haze-blur-materials** — reusable blur presets
- **haze-blur-material3** — optional Compose Material 3 Blur Style factory
- **haze-glass** — Glass effect implementation
- **haze-glass-material3** — optional Compose Material 3 Glass Style factory
- **haze-utils** — shared platform rendering utilities

Effect modules can keep platform-specific renderer internals behind their own `expect`/`actual`
boundaries.

## Extension boundary

The lambda-based effect API, mutable `VisualEffect` runtime, direct modifier nodes, source areas,
coordinates, captured layers, and position strategies are internal or removed. New Blur code uses
`hazeBlur`; new Glass code uses `hazeGlass`; custom effects use `HazeEffectFactory`.

See [Custom effects](custom-effects.md) for the preferred API.
