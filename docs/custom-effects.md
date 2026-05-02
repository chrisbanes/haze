# Custom Effects

This guide shows how to build and ship custom `VisualEffect` implementations for Haze.

If you want a complete production implementation, see the [haze-blur](https://github.com/chrisbanes/haze/tree/main/haze-blur) module.

## Overview

Haze is extensible by design. `Modifier.hazeEffect { ... }` is driven by a `VisualEffect` instance, and you can provide your own implementation.

For most real-world usage, start with **background mode** (`hazeSource` + `hazeEffect(state = hazeState)`), since that is where Haze shines: applying effects to transformed background graphics layers.

Typical workflow:

1. Implement `VisualEffect`
2. Provide a `HazeEffectScope` builder extension for ergonomic configuration
3. Use your builder from `Modifier.hazeEffect { ... }`

## Background source layers (primary pattern)

Use one `HazeState` shared by transformed source layers and your effect target:

```kotlin
val hazeState = rememberHazeState()

Box(Modifier.fillMaxSize()) {
    AsyncImage(
        model = rememberRandomSampleImageUrl(),
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1.06f
                translationX = 24f
            }
            .hazeSource(state = hazeState),
    )

    Box(
        modifier = Modifier
            .size(260.dp, 180.dp)
            .graphicsLayer { rotationZ = 10f }
            .hazeSource(state = hazeState, zIndex = 1f),
    )

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .hazeEffect(state = hazeState) {
                sparkEffect { }
            },
    )
}
```

The key point is that Haze samples the transformed `hazeSource` layers behind the target node. Your custom `VisualEffect` then controls how that sampled content is rendered.

## Implementing VisualEffect

Create a class implementing `VisualEffect`:

```kotlin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext

@OptIn(ExperimentalHazeApi::class)
class SparkVisualEffect : VisualEffect {
    var color: Color = Color.Black
    var alpha: Float = 0.2f

    override fun attach(context: VisualEffectContext) {
        // Allocate expensive resources if needed.
    }

    override fun update(context: VisualEffectContext) {
        // Read composition locals or snapshot state.
        // Call context.invalidateDraw() if output changes.
    }

    override fun detach(context: VisualEffectContext) {
        // Release resources from attach().
    }

    override fun DrawScope.draw(context: VisualEffectContext) {
        drawRect(
            color = color.copy(alpha = alpha),
            size = context.size,
        )
    }
}
```

### Lifecycle methods

#### `attach(context: VisualEffectContext)`

Called when the effect is attached to a node.

- Allocate long-lived resources here (shaders, caches, delegates)
- Geometry may not be resolved yet, so treat `position`, `size`, `layerSize`, and `layerOffset` as potentially unspecified/zero during attach

#### `update(context: VisualEffectContext)`

Called when the effect should refresh state from composition locals or snapshot-backed data.

- Snapshot reads are tracked
- When read state changes, `update` is invoked again
- Call `context.invalidateDraw()` when visual output should be re-drawn

```kotlin
override fun update(context: VisualEffectContext) {
    val newColor = context.currentValueOf(LocalSparkColor)
    if (newColor != color) {
        color = newColor
        context.invalidateDraw()
    }
}
```

#### `detach(context: VisualEffectContext)`

Called when the effect is detached.

- Release resources acquired in `attach`
- Cancel work tied to effect internals

```kotlin
override fun detach(context: VisualEffectContext) {
    shader?.release()
    shader = null
}
```

#### `onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel)`

Called on memory pressure/background transitions.

- Free heavy caches and temporary buffers
- Keep this fast and safe to call repeatedly
- Optionally call `context.invalidateDraw()` after release

#### `draw(context: VisualEffectContext)`

Called during rendering.

- Keep this path hot and allocation-light
- Use `context.size`/`context.layerSize` to align your output to the requested bounds

## VisualEffectContext

`VisualEffectContext` provides geometry, environment, and lifecycle helpers:

```kotlin
interface VisualEffectContext {
    val position: Offset
    val size: Size
    val layerSize: Size
    val layerOffset: Offset
    val rootBounds: Rect

    val inputScale: HazeInputScale
    val windowId: Any?
    val areas: List<HazeArea>
    val state: HazeState?

    val coroutineScope: CoroutineScope

    fun requireDensity(): Density
    fun <T> currentValueOf(local: CompositionLocal<T>): T
    fun requireGraphicsContext(): GraphicsContext
    fun invalidateDraw()
}
```

Mode semantics:

- `state != null`: background mode (`hazeEffect(state = hazeState)`)
- `state == null`: foreground/content mode (`hazeEffect { ... }`)

## HazeEffectScope

The `Modifier.hazeEffect { ... }` lambda uses `HazeEffectScope`:

```kotlin
interface HazeEffectScope {
    var visualEffect: VisualEffect
    var inputScale: HazeInputScale
    var drawContentBehind: Boolean
    var clipToAreasBounds: Boolean?
    var expandLayerBounds: Boolean?
    var forceInvalidateOnPreDraw: Boolean
    var canDrawArea: ((HazeArea) -> Boolean)?
}
```

## Builder extension pattern

Expose your effect through a `HazeEffectScope` extension:

```kotlin
@OptIn(ExperimentalHazeApi::class)
fun HazeEffectScope.sparkEffect(
    block: SparkVisualEffect.() -> Unit,
) {
    val effect = visualEffect as? SparkVisualEffect ?: SparkVisualEffect()
    visualEffect = effect
    effect.block()
}
```

Usage:

```kotlin
Modifier.hazeEffect(state = hazeState) {
    sparkEffect {
        color = Color.Blue
        alpha = 0.3f
    }
}
```

## Ownership model

`VisualEffect` instances are single-owner.

- One effect instance can only be attached to one active `hazeEffect` node at a time
- Do not share the same effect instance across multiple active nodes
- Prefer creating/reusing instances per node via your builder pattern

## Layer bounds behavior

Override `calculateLayerBounds(rect, density)` if your effect needs extra sampling space:

```kotlin
override fun calculateLayerBounds(rect: Rect, density: Density): Rect {
    val extra = with(density) { 24.dp.toPx() }
    return rect.inflate(extra)
}
```

Coordinate-space contract:

- Return bounds in the same coordinate space as `rect`
- In background mode (`context.state != null`), Haze passes a root/screen-aligned rect
- In foreground mode (`context.state == null`), Haze passes a local node rect

## Platform-specific implementations

Use `expect`/`actual` for platform-specific rendering internals:

```kotlin
// commonMain
expect fun createPlatformShader(size: Size): Shader

@OptIn(ExperimentalHazeApi::class)
class ShaderEffect : VisualEffect {
    private lateinit var shader: Shader

    override fun attach(context: VisualEffectContext) {
        shader = createPlatformShader(context.size)
    }
}
```

```kotlin
// androidMain
actual fun createPlatformShader(size: Size): Shader {
    // Android implementation
}
```

```kotlin
// desktopMain / skikoMain
actual fun createPlatformShader(size: Size): Shader {
    // Desktop implementation
}
```

## Sample

See the dedicated custom effect sample:

- [CustomVisualEffectSample.kt](https://github.com/chrisbanes/haze/blob/main/sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/CustomVisualEffectSample.kt)

## Best practices

1. Allocate in `attach`, release in `detach`
2. Keep `draw` allocation-free where possible
3. Use `update` for tracked state reads and controlled invalidation
4. Respect `inputScale` and bounds contracts for performance and correctness
5. Validate behavior with screenshot tests for visual regressions

## Next steps

- Read [Architecture](architecture.md) for internals
- Review [haze-blur](https://github.com/chrisbanes/haze/tree/main/haze-blur) for a full production effect
- Browse the [sample app](https://github.com/chrisbanes/haze/tree/main/sample)
