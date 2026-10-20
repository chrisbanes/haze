# Blur usage

`Modifier.hazeBlur` takes an input, which decides what gets blurred, and a Style, which decides how
it looks.

## Choosing the input

| Requirement | Input | What it blurs |
| --- | --- | --- |
| Blur whatever is behind a surface | `HazeInput.Backdrop(hazeState)` | Content drawn earlier in the same window, using your `hazeSource` content where native backdrop rendering isn't used. |
| Control exactly which sources are blurred | `HazeInput.Sources(hazeState)` | Content marked with selected `hazeSource` modifiers. |
| Blur the modifier's own content | `HazeInput.Content` | The composable's own content. |

Use `Backdrop` for most cases.

## Source-backed Blur

```kotlin
val hazeState = rememberHazeState()
val style = HazeMaterials.thin()

Box {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .hazeSource(hazeState),
  ) {
    // Content
  }

  TopAppBar(
    modifier = Modifier.hazeBlur(
      input = HazeInput.Sources(hazeState),
      style = style,
    ),
  )
}
```

`HazeInput.Sources` also controls which sources are used and what is shown while they are
unavailable. The default, `KeepLastFrame`, avoids an empty flash during source transitions, but the
retained output is not guaranteed to be an exact copy of the last frame (see
[Core concepts](../core-concepts.md)). Use `ClearWhenUnavailable` for privacy-sensitive content:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Sources(
    state = hazeState,
    retention = HazeSourceRetention.ClearWhenUnavailable,
  ),
)
```

## Own-content Blur

Use `HazeInput.Content` when the modifier's own content is the input:

```kotlin
Image(
  modifier = Modifier.hazeBlur(
    input = HazeInput.Content,
    style = HazeMaterials.thin(),
  ),
)
```

## Android window-backdrop Blur

On Android 37.2 or later, `HazeInput.Backdrop` can blur the window backdrop natively. This is
experimental and off by default. Enable it before the Blur modifiers you want to affect are
attached:

```kotlin
HazeFeatureFlags.isPlatformBackdropEnabled = true

Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
  style = HazeMaterials.thin(),
)
```

Enabling the flag makes native rendering possible, not guaranteed. On other platforms, older
Android releases, a software canvas, or if native rendering fails, Haze uses your `hazeSource`
content instead; switching can take one frame. Changing the flag only affects modifiers attached
afterwards.

Native rendering blurs content drawn earlier in the same window. It can't see content drawn later,
or content in another dialog, popup, or window, and `HazePerformanceMode` doesn't reduce its
resolution. Use `HazeInput.Sources` when you need exact source selection, content from another
window, or a retention policy.

Set `HazeLogger.enabled = true` to log which path is chosen. A later release may enable native
rendering by default.

## Enabling Blur

Blur is enabled by default on Android 12 and newer and on all other platforms. On Android 11 and
below, Haze draws a translucent scrim instead unless you enable Blur (see
[Platforms](platforms.md#android-11-and-below)). To override the default, set `blurEnabled` in a
Style:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
  style = HazeBlurStyle {
    blurEnabled(true)
  },
)
```

When Blur is disabled, Haze draws `fallbackColorEffect` instead.

## Replayable Styles

A `HazeBlurStyle` is a list of property writes:

```kotlin
val style = HazeBlurStyle {
  blurEnabled(true)
  blurRadius(20.dp)
  noiseFactor(0.15f)
  backgroundColor(Color.Black)
  colorEffects(
    listOf(
      HazeColorEffect.tint(Color.White.copy(alpha = 0.12f)),
    ),
  )
  fallbackColorEffect(HazeColorEffect.tint(Color.Black.copy(alpha = 0.7f)))
  alpha(1f)
  mask(null)
  progressive(null)
  blurredEdgeTreatment(BlurredEdgeTreatment.Rectangle)
}
```

Haze applies Styles in this order:

1. `HazeBlurDefaults.style`
2. `LocalHazeBlurStyle`
3. The explicit `hazeBlur` Style

The last write to a property wins, both across tiers and within a Style chain:

```kotlin
val compact = HazeMaterials.thin().then {
  blurRadius(12.dp)
  noiseFactor(0f)
}
```

Properties a Style doesn't write keep their inherited value. The block runs each time a node
resolves its Style, under snapshot observation: a change to snapshot state the block reads, such as
an animated value, updates the node on the next frame without recomposition.

```kotlin
val radius by animateDpAsState(if (expanded) 24.dp else 0.dp)
val style = remember { HazeBlurStyle { blurRadius(radius) } }
```

Only snapshot state schedules an update. A plain captured value, or a `MutableList` you mutate, does
not. A remembered block keeps what it captured, so give `remember` keys or use
`rememberUpdatedState` for non-state inputs that change. The built-in presets are memoized by their
inputs, so an unchanged preset keeps the same instance across recompositions.

`colorEffects` copies the list you pass each time the block runs, so mutating your list afterwards
does not schedule an update. An empty list clears inherited color effects:

```kotlin
val noColorEffects = HazeBlurStyle {
  colorEffects(emptyList())
}
```

## Progressive Blur and masks

Progressive Blur varies intensity across the surface:

```kotlin
val progressiveStyle = HazeBlurStyle {
  progressive(
    HazeProgressive.verticalGradient(
      startIntensity = 1f,
      endIntensity = 0f,
    ),
  )
}
```

A mask fades the effect's opacity instead, and is usually cheaper:

```kotlin
val maskedStyle = HazeBlurStyle {
  mask(
    Brush.verticalGradient(
      colors = listOf(Color.Black, Color.Transparent),
    ),
  )
}
```

The two can be combined. Progressive Blur varies the intensity first, then the mask fades the
complete result, so fully transparent mask regions show no effect:

```kotlin
val maskedProgressiveStyle = HazeBlurStyle {
  progressive(HazeProgressive.horizontalGradient())
  mask(
    Brush.verticalGradient(
      colors = listOf(Color.Black, Color.Transparent),
    ),
  )
}
```

On Android 12L and below, progressive Blur is approximated (see [Platforms](platforms.md)).

<a id="performance-mode"></a>

## Performance mode and layer expansion

Performance mode and layer expansion are `hazeBlur` parameters, not Style properties:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Sources(hazeState),
  style = style,
  performanceMode = HazePerformanceMode.Default,
  expandLayerBounds = true,
)
```

See the [performance guide](../performance.md#performance-mode) for choosing a mode, and
[Blur guidance](../performance.md#blur) for when to change `expandLayerBounds`. Measured results
are in the [benchmark results](../benchmark-results.md#performance-mode-calibration).
