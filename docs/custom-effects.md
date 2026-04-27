# Custom Effects

This guide explains how to implement custom visual effects that work with Haze's architecture.

## Overview

Haze is designed to be extensible. While Haze ships with a blur effect, you can implement custom effects by implementing the `VisualEffect` interface and providing a builder extension function.

For a complete reference implementation, see the [haze-blur](https://github.com/chrisbanes/haze/tree/main/haze-blur) module.

## Implementing a VisualEffect

Create a class that implements the `VisualEffect` interface:

```kotlin
import android.graphics.Canvas
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext

class CustomVisualEffect : VisualEffect {
    // Your effect's configuration properties
    var intensity: Float = 1f
    var color: Color = Color.Black

    override fun attach(context: VisualEffectContext) {
        // Called when the effect is first attached
        // Initialize any platform-specific resources here
    }

    override fun update(context: VisualEffectContext) {
        // Called when the effect should update its state from composition locals
        // or other sources. You can safely read snapshot state here.
    }

    override fun detach() {
        // Called when the effect is removed
        // Clean up any resources allocated in attach()
    }

    override fun DrawScope.draw(context: VisualEffectContext) {
        // Called to render the effect
        // Draw the effect using the DrawScope receiver
    }
}
```

### Interface Methods

#### attach(context: VisualEffectContext)

Called once when the effect is first attached to a composable. Use this to:
- Allocate platform-specific resources (Shaders, RenderEffects, etc.)
- Initialize expensive objects
- Query platform capabilities

```kotlin
override fun attach(context: VisualEffectContext) {
    // Example: Initialize a platform-specific shader
    myShader = createShader(context.size, context.requireDensity())
}
```

#### update(context: VisualEffectContext)

Called whenever the effect should update its state from composition locals or other sources. You can safely read snapshot state in this function. When any snapshot state read in this function is mutated, this function will be re-invoked.

```kotlin
override fun update(context: VisualEffectContext) {
    // Example: read a composition local and trigger invalidation if needed
    val someLocal = context.currentValueOf(MyCompositionLocal)
    if (someLocal != cachedLocal) {
        cachedLocal = someLocal
        context.invalidateDraw()
    }
}
```

#### detach()

Called when the effect is removed. Use this to:
- Release native resources
- Cancel coroutines
- Clean up allocations from `attach()`

```kotlin
override fun detach() {
    myShader?.release()
    myCoroutineScope.cancel()
}
```

#### draw(context: VisualEffectContext)

Called to render the effect. This is where the actual effect rendering happens.

```kotlin
override fun DrawScope.draw(context: VisualEffectContext) {
    // Draw the effect using the DrawScope receiver
    drawRect(
        color = Color.Black.copy(alpha = 0.5f),
        size = context.size,
    )
}
```

## VisualEffectContext

The context provided to effect methods gives you access to:

```kotlin
interface VisualEffectContext {
    val position: Offset               // Position of the effect node
    val size: Size                    // Size of the effect area
    val layerSize: Size              // Size of the graphics layer (may differ from size)
    val layerOffset: Offset          // Graphics layer offset relative to node position
    val rootBounds: Rect             // Bounds of the root layout coordinates on screen
    val inputScale: HazeInputScale   // Input scale factor configuration
    val windowId: Any?               // Identifier for the containing window
    val areas: List<HazeArea>        // Source areas this effect should process
    val state: HazeState?            // Associated HazeState (null for foreground blur)
    val coroutineScope: CoroutineScope // CoroutineScope tied to the node lifecycle

    fun requireDensity(): Density
    fun <T> currentValueOf(local: CompositionLocal<T>): T
    fun requireGraphicsContext(): GraphicsContext
    fun invalidateDraw()
}
```

## HazeEffectScope

The effect receives the `HazeEffectScope` which provides common properties applicable to all effects:

```kotlin
interface HazeEffectScope {
    var visualEffect: VisualEffect        // The current visual effect implementation
    var inputScale: HazeInputScale        // Performance optimization via resolution scaling
    var drawContentBehind: Boolean        // Whether to draw source content behind the effect
    var clipToAreasBounds: Boolean?      // Whether to clip to total area bounds
    var expandLayerBounds: Boolean?       // Whether to expand layer bounds for edge consistency
    var forceInvalidateOnPreDraw: Boolean // Force invalidation from pre-draw events
    var canDrawArea: ((HazeArea) -> Boolean)? // Optional filter to control which areas are drawn
}
```

## Providing a Builder Extension

To make your effect convenient to use, provide a builder extension function:

```kotlin
fun HazeEffectScope.customEffect(
    block: CustomVisualEffect.() -> Unit = {}
): CustomVisualEffect {
    val effect = CustomVisualEffect()
    effect.apply(block)
    visualEffect = effect
    return effect
}
```

Users can then configure your effect like this:

```kotlin
modifier = Modifier.hazeEffect(state = hazeState) {
    customEffect {
        intensity = 0.8f
        color = Color.Blue
    }
}
```

## Platform-Specific Implementations

Effects often need platform-specific code. Use `expect`/`actual` to provide platform implementations:

**commonMain:**
```kotlin
expect fun createCustomShader(size: Size): Shader

class CustomVisualEffect : VisualEffect {
    private lateinit var shader: Shader

    override fun attach(context: VisualEffectContext) {
        shader = createCustomShader(context.size)
    }
}
```

**androidMain:**
```kotlin
actual fun createCustomShader(size: Size): Shader {
    // Android-specific shader creation
}
```

**desktopMain:**
```kotlin
actual fun createCustomShader(size: Size): Shader {
    // Skiko-based shader creation
}
```

See the [haze-blur module](https://github.com/chrisbanes/haze/tree/main/haze-blur) for complete platform-specific examples.

## Publishing Your Effect Module

To share your custom effect, publish it as a library:

1. Create a new Gradle module (e.g., `haze-myeffect`)
2. Add `haze` as a dependency:
   ```gradle
   dependencies {
       api("dev.chrisbanes.haze:haze:2.0.0")
   }
   ```
3. Publish to Maven Central or your preferred repository

Users can then add it like any effect:
```gradle
dependencies {
    implementation("dev.chrisbanes.haze:haze-myeffect:1.0.0")
}
```

## Example: Simple Tint Effect

Here's a minimal example of a custom effect that applies a color tint:

```kotlin
class TintVisualEffect : VisualEffect {
    var color: Color = Color.Black
    var alpha: Float = 0.2f

    override fun attach(context: VisualEffectContext) {
        // No resources to allocate for a simple tint
    }

    override fun update(context: VisualEffectContext) {
        // Nothing to update from composition locals
    }

    override fun detach() {
        // Nothing to clean up
    }

    override fun DrawScope.draw(context: VisualEffectContext) {
        drawRect(
            color = color.copy(alpha = alpha),
            size = context.size,
        )
    }
}

fun HazeEffectScope.tintEffect(
    block: TintVisualEffect.() -> Unit = {}
) {
    val effect = TintVisualEffect()
    effect.apply(block)
    visualEffect = effect
}
```

Usage:
```kotlin
modifier = Modifier.hazeEffect(state = hazeState) {
    tintEffect {
        color = Color.Blue
        alpha = 0.3f
    }
}
```

## Best Practices

1. **Resource Management** - Always allocate resources in `attach()` and clean up in `detach()`
2. **Thread Safety** - Effects may be accessed from multiple threads; use appropriate synchronization
3. **Performance** - Remember `draw()` is called frequently; optimize for performance
4. **Input Scale** - Respect `inputScale` from `HazeEffectScope` to support performance optimizations
5. **Documentation** - Document your effect's configuration properties clearly
6. **Testing** - Use screenshot tests to verify your effect works correctly

## Next Steps

- See [Architecture](architecture.md) for an overview of Haze's design
- Review the [haze-blur](https://github.com/chrisbanes/haze/tree/main/haze-blur) module for a production implementation
- Check out the [sample code](https://github.com/chrisbanes/haze/tree/main/sample) for usage examples
