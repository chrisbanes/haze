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

    override fun update(context: VisualEffectContext, scope: HazeEffectScope) {
        // Called when styling properties change
        // Update internal state based on scope properties
    }

    override fun detach() {
        // Called when the effect is removed
        // Clean up any resources allocated in attach()
    }

    override fun draw(canvas: Canvas, context: VisualEffectContext) {
        // Called to render the effect
        // Draw the effect to the provided canvas
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
    myShader = createShader(context.size, context.density)
}
```

#### update(context: VisualEffectContext, scope: HazeEffectScope)

Called whenever styling parameters change (when `HazeEffectScope` properties are updated). Use this to:
- Update effect parameters from `HazeEffectScope`
- Recalculate values based on new properties
- Update platform-specific rendering parameters

```kotlin
override fun update(context: VisualEffectContext, scope: HazeEffectScope) {
    // Update effect from scope properties
    myIntensity = scope.alpha
    myInputScale = scope.inputScale
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

#### draw(canvas: Canvas, context: VisualEffectContext)

Called to render the effect. This is where the actual effect rendering happens.

```kotlin
override fun draw(canvas: Canvas, context: VisualEffectContext) {
    // Draw the effect to canvas
    canvas.drawRect(0f, 0f, context.size.width.toFloat(),
                   context.size.height.toFloat(), myPaint)
}
```

## VisualEffectContext

The context provided to effect methods gives you access to:

```kotlin
interface VisualEffectContext {
    val size: IntSize                    // Size of the effect area
    val density: Float                  // Screen pixel density
    val layoutDirection: LayoutDirection // Text layout direction
    val coroutineScope: CoroutineScope  // For async operations
}
```

## HazeEffectScope

The effect receives the `HazeEffectScope` which provides common properties:

```kotlin
interface HazeEffectScope {
    var alpha: Float                    // Overall effect opacity
    var inputScale: HazeInputScale      // Performance optimization
    var drawContentBehind: Boolean      // Whether to draw source content
    var canDrawArea: ((DrawArea) -> Boolean)? // Layer filtering
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
expect fun createCustomShader(size: IntSize): Shader

class CustomVisualEffect : VisualEffect {
    private lateinit var shader: Shader

    override fun attach(context: VisualEffectContext) {
        shader = createCustomShader(context.size)
    }
}
```

**androidMain:**
```kotlin
actual fun createCustomShader(size: IntSize): Shader {
    // Android-specific shader creation
}
```

**desktopMain:**
```kotlin
actual fun createCustomShader(size: IntSize): Shader {
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

    private val paint = Paint().apply {
        isAntiAlias = true
    }

    override fun attach(context: VisualEffectContext) {
        // No resources to allocate for a simple tint
    }

    override fun update(context: VisualEffectContext, scope: HazeEffectScope) {
        paint.color = color.copy(alpha = scope.alpha * alpha).toArgb()
    }

    override fun detach() {
        // Nothing to clean up
    }

    override fun draw(canvas: Canvas, context: VisualEffectContext) {
        canvas.drawRect(
            0f, 0f,
            context.size.width.toFloat(),
            context.size.height.toFloat(),
            paint
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
