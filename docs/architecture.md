# Architecture

Haze v2 is built around a flexible architecture that supports multiple visual effects beyond just blur. This document explains the core concepts and how to extend Haze with custom effects.

## Visual Effects Framework

At the heart of Haze is the `VisualEffect` interface, which defines a common contract for all visual effects. This allows Haze to be extensible and support different types of effects on different platforms.

### VisualEffect Interface

The `VisualEffect` interface is the base contract for all visual effects. Implementations provide platform-specific rendering logic for creating the desired visual effect.

```kotlin
interface VisualEffect {
    fun attach(context: VisualEffectContext)
    fun update(context: VisualEffectContext, scope: HazeEffectScope)
    fun detach()
    fun draw(canvas: Canvas, context: VisualEffectContext)
}
```

- **attach**: Called when the effect is first attached to a composable. Implementations can initialize platform-specific resources.
- **update**: Called whenever styling parameters change (from `HazeEffectScope`).
- **detach**: Called when the effect is removed. Implementations should clean up resources.
- **draw**: Renders the effect to the provided canvas.

### HazeEffectScope

The `HazeEffectScope` is a receiver scope passed to the lambda block of `Modifier.hazeEffect`. It provides common properties that apply across all effects:

```kotlin
modifier = Modifier.hazeEffect(state) {
    inputScale = HazeInputScale.Auto
    drawContentBehind = true
    // Effect-specific configuration
}
```

Common properties include:
- **inputScale**: Controls the resolution at which the effect is rendered (performance optimization)
- **drawContentBehind**: Whether to draw the source content before applying the effect
- **canDrawArea**: Optional filter to control which layers are included in the effect

## Modular Effect Architecture

Each effect is provided in a separate module, allowing you to include only the effects you need:

- **haze** - Core infrastructure (`VisualEffect`, `HazeState`, modifiers)
- **haze-blur** - Blur effect implementation
- **haze-materials** - Pre-built blur styles (Material, Cupertino, Fluent)
- **haze-utils** - Shared utilities for platform-specific rendering

### Module Dependencies

```
haze (core)
├── haze-blur (blur effect)
│   └── haze-materials (blur styles)
└── haze-utils (platform utilities)
```

Future effects will follow the same pattern:
- **haze-liquidglass** - Refraction-based liquid glass effect with rounded-shape SDF and optional chromatic aberration
- Custom third-party effect modules

## Effect Registration Pattern

Effect implementations provide builder extension functions on `HazeEffectScope` for convenient configuration:

```kotlin
// Blur effect example
modifier = Modifier.hazeEffect(state) {
    blurEffect {
        style = HazeMaterials.thin()
        progressive = HazeProgressive.verticalGradient(...)
    }
}
```

Future effects will follow the same pattern:
```kotlin
modifier = Modifier.hazeEffect(state) {
    liquidglassEffect {
        // liquidglass-specific properties
    }
}
```

## Platform Support

Haze provides a `VisualEffectContext` that gives effects access to platform-specific capabilities:

```kotlin
interface VisualEffectContext {
    val size: IntSize
    val density: Float
    val layoutDirection: LayoutDirection
    val coroutineScope: CoroutineScope
    // Platform-specific rendering utilities
}
```

Effects can detect platform capabilities and provide the best implementation available. For example:

- **Android 13+**: Uses `RenderEffect.createBlurEffect()`
- **Android 12**: Uses `RenderNode` with shader-based blur
- **Android 11 and below**: Uses Renderscript blur
- **Desktop/iOS**: Uses Skia shaders
- **Web**: Uses canvas filters
- **WASM**: Uses custom shader implementations

## Implementing Custom Effects

To create a custom effect, implement the `VisualEffect` interface and provide a builder extension:

```kotlin
class CustomVisualEffect : VisualEffect {
    override fun attach(context: VisualEffectContext) {
        // Initialize resources
    }

    override fun update(context: VisualEffectContext, scope: HazeEffectScope) {
        // Update with new styling parameters
    }

    override fun detach() {
        // Clean up resources
    }

    override fun draw(canvas: Canvas, context: VisualEffectContext) {
        // Render the effect
    }
}

// Provide a builder extension
fun HazeEffectScope.customEffect(block: CustomVisualEffect.() -> Unit = {}) {
    visualEffect = CustomVisualEffect().apply(block)
}
```

See the [haze-blur](https://github.com/chrisbanes/haze/tree/main/haze-blur) module for a complete reference implementation.

## Styling Resolution

When multiple sources of styling are provided, Haze resolves values using the following precedence:

1. Value set directly in the effect builder (e.g., `blurEffect { }`)
2. Value set via the `style` property
3. Value set via `LocalHazeStyle` composition local
4. Default value

This allows flexible composition of styles from different sources while maintaining predictable behavior.
