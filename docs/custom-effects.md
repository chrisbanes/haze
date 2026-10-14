# Custom effects

A custom effect is a shareable `HazeEffectFactory<Style>` that creates one renderer for each
`hazeEffect` modifier.

## Define a complete Style

Keep effect configuration immutable and free of renderer or platform resources:

```kotlin
@Immutable
data class SparkStyle(
    val color: Color,
    val alpha: Float,
)
```

Haze passes the complete current Style to every draw and bounds calculation, so a renderer never
sees stale values. Passing a new Style updates the existing renderer.

## Create a shareable factory

A factory is stateless and can be shared by any number of modifiers, but it must create a new
renderer each time:

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

Mutable caches, shaders, and other disposable resources belong in the renderer. Haze keeps the
renderer across Style, input, and sampling changes, and disposes it when the factory is replaced
or the modifier is removed.

## Draw source-backed input

Use `HazeInput.Sources` when the effect consumes content captured by `hazeSource`. Custom effects
don't get the native Android window backdrop, so `HazeInput.Backdrop` would only ever draw its
source fallback; `Sources` makes that explicit:

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

Selection and retention are set on the input:

```kotlin
input = HazeInput.Sources(
    state = hazeState,
    selection = HazeSourceSelection.All.where { it.key == "hero" },
    retention = HazeSourceRetention.ClearWhenUnavailable,
)
```

`drawInput()` draws the selected source content, already positioned for the effect.

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

## Renderer scopes

`HazeEffectDrawScope` is a `DrawScope` with:

- `modifierBounds`, expressed in the current effect layer
- `sampling`, the `HazeSampling` policy passed to the modifier
- `drawInput()` for the selected input
- `currentValueOf` for composition locals

`HazeEffectLayoutScope` is a `Density` with `modifierBounds` and `currentValueOf`. Return the
bounds the effect needs in the same coordinate space. Setting `expandLayerBounds = false` on the
modifier ignores any expansion the renderer requests.

State and composition locals read while drawing trigger a redraw when they change. Values read
while calculating bounds recalculate the bounds, then redraw.

## Ownership and lifecycle

- Share the immutable Style and factory freely.
- Never return the same mutable renderer from more than one `createRenderer()` call.
- Release rebuildable caches in `onTrimMemory`.
- Release all renderer-owned resources in `dispose`.
- The public API doesn't cover pointer interaction or retained output; implement those inside
  your effect if you need them.

`onTrimMemory` receives memory pressure:

- On Android and iOS, Haze forwards the platform's memory-pressure notifications. Android 14 and
  later only send `TRIM_MEMORY_UI_HIDDEN` and `TRIM_MEMORY_BACKGROUND`, so Haze reports
  `TRIM_MEMORY_BACKGROUND` as `TrimMemoryLevel.MODERATE` there.
- On Desktop, macOS, and Web, an effect receives `TrimMemoryLevel.MODERATE` when its lifecycle reaches
  `ON_STOP`.

Discard only resources you can rebuild; the next draw that needs them must recreate valid output.

`Modifier.hazeEffect` with a factory is the only supported way to add a custom effect.

See
[`CustomVisualEffectSample.kt`](https://github.com/chrisbanes/haze/blob/main/sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/CustomVisualEffectSample.kt)
for a complete typed example.
