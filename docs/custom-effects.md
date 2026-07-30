# Custom effects

Build custom effects with a shareable `HazeEffectFactory<Style>` and a renderer owned by each
`hazeEffect` modifier node.

## Define a complete Style

Keep effect configuration immutable and free of renderer or platform resources:

```kotlin
@Immutable
data class SparkStyle(
    val color: Color,
    val alpha: Float,
)
```

Haze passes the complete current Style to every draw and bounds evaluation. Replacing the Style
updates the existing renderer, so values from an earlier evaluation cannot stick accidentally.

## Create a shareable factory

A factory is a stateless descriptor. The same instance can be used by concurrent modifiers, but it
must create an independent renderer for each one:

```kotlin
data object SparkEffect : HazeEffectFactory<SparkStyle> {
    override fun createRenderer(): HazeEffectRenderer<SparkStyle> = SparkRenderer()
}

private class SparkRenderer : HazeEffectRenderer<SparkStyle> {
    override fun HazeEffectDrawScope.draw(style: SparkStyle) {
        drawInput()
        drawRect(style.color.copy(alpha = style.alpha))
    }

    override fun HazeEffectLayoutScope.calculateLayerBounds(style: SparkStyle): Rect {
        val extra = 12.dp.toPx()
        return modifierBounds.inflate(extra)
    }

    override fun onTrimMemory(level: TrimMemoryLevel) {
        // Release caches that can be rebuilt on the next draw.
    }

    override fun dispose() {
        // Release all renderer-owned resources.
    }
}
```

Mutable caches, shaders, and other disposable resources belong in the renderer. Haze creates one
renderer per modifier node, reuses it for Style and input-policy updates, and disposes it when the
factory is replaced or the node detaches.

## Draw source-backed input

Use an explicit `HazeInput.Sources` when the effect consumes content captured by `hazeSource`:

```kotlin
val hazeState = rememberHazeState()

Box {
    Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
    )

    Box(
        Modifier.hazeEffect(
            factory = SparkEffect,
            input = HazeInput.Sources(hazeState),
            style = SparkStyle(color = Color.Blue, alpha = 0.3f),
            sampling = HazeSampling.FullResolution,
        ),
    )
}
```

Source selection and retention remain explicit:

```kotlin
input = HazeInput.Sources(
    state = hazeState,
    selection = HazeSourceSelection.All.where { it.key == "hero" },
    retention = HazeSourceRetention.ClearWhenUnavailable,
)
```

`drawInput()` draws the already-selected source content. It does not expose source areas,
coordinates, captured layers, windows, or renderer ownership internals.

## Draw the modifier's own content

Use `HazeInput.Content` when the effect consumes the composable carrying the modifier:

```kotlin
Box(
    Modifier
        .hazeEffect(
            factory = SparkEffect,
            input = HazeInput.Content,
            style = SparkStyle(color = Color.Blue, alpha = 0.3f),
        )
        .background(Color.White),
)
```

The renderer uses the same `drawInput()` operation for both input modes.

## Semantic scopes

`HazeEffectDrawScope` is a `DrawScope` with:

- `modifierBounds`, expressed in the current effect layer
- the structural `HazeSampling` value
- `drawInput()` for the selected input
- tracked composition-local access through `currentValueOf`

`HazeEffectLayoutScope` is a `Density` with the modifier bounds and tracked composition-local
access. Return the bounds required by the effect in the same coordinate space. Setting
`expandLayerBounds = false` on the modifier skips renderer-requested expansion.

Snapshot state and composition locals read while drawing invalidate drawing. Values read while
calculating bounds recalculate the bounds and then redraw.

## Ownership and lifecycle

- Share the immutable Style and factory freely.
- Never return the same mutable renderer from more than one `createRenderer()` call.
- Release rebuildable caches in `onTrimMemory`.
- Release all renderer-owned resources in `dispose`.
- Keep pointer interaction and retained-output behavior inside your effect module until those
  capabilities have dedicated public contracts.

On Android and iOS, Haze forwards the platform's native memory-pressure notifications to attached
effects. On Desktop and Web, an attached effect receives `TrimMemoryLevel.MODERATE` whenever its
composition lifecycle reaches `ON_STOP`. Discard only resources that can be rebuilt: the next draw
that needs them must recreate valid output.

Direct `HazeEffectNode` construction is deprecated and does not receive automatic Desktop or Web
lifecycle trimming. Custom modifier extensions should migrate to the typed `Modifier.hazeEffect`
overload with a `HazeEffectFactory`; the node type will become internal in
[#1132](https://github.com/chrisbanes/haze/issues/1132).

Platform-specific renderer internals can use `expect`/`actual` declarations in the effect module.
The public renderer contract intentionally does not expose platform delegates or captured layers.

## Temporary legacy path

`VisualEffect`, `VisualEffectContext`, `HazeEffectScope`, and the lambda-based `hazeEffect`
overloads remain available while built-in and existing third-party effects migrate. They are a
temporary compatibility path, not the recommended extension seam for new effects.

See
[`CustomVisualEffectSample.kt`](https://github.com/chrisbanes/haze/blob/main/sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/CustomVisualEffectSample.kt)
for a complete typed example.
