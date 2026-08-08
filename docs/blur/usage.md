# Blur usage

Blur works with either source-backed content or the modifier's own content. Both modes use the
typed `hazeBlur` modifier and the same replayable Style.

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

`HazeInput.Sources` also owns source selection and retained-output behavior. The default
`KeepLastFrame` policy avoids an empty flash during source transitions. Use
`ClearWhenUnavailable` for privacy-sensitive content:

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

## Enabling Blur

Blur is enabled by default only where Haze considers the platform implementation reliable. To
override that decision, write `blurEnabled` in a Style:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Sources(hazeState),
  style = HazeBlurStyle {
    blurEnabled(true)
  },
)
```

When Blur is disabled, Haze draws the configured fallback scrim instead.

## Replayable Styles

`HazeBlurStyle` is an opaque program of Blur-specific writes:

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

Style resolution always replays these tiers in order:

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

If a replacement Style omits `blurRadius`, the local or default value becomes visible again. Styles
are immutable and safe to share; create a replacement Style when the appearance needs to change.

Caller-owned color-effect lists are snapshotted when the Style is created. An explicit empty list
clears inherited color effects:

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

A mask fades the effect's opacity and is usually cheaper:

```kotlin
val maskedStyle = HazeBlurStyle {
  mask(
    Brush.verticalGradient(
      colors = listOf(Color.Black, Color.Transparent),
    ),
  )
}
```

## Performance mode

### Performance mode and layer expansion

Performance mode and layer expansion are structural modifier policies, not Style properties:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Sources(hazeState),
  style = style,
  performanceMode = HazePerformanceMode.Adaptive,
  expandLayerBounds = true,
)
```

- `HazePerformanceMode.Default` and `Adaptive` let Blur balance quality and cost automatically.
- `HazePerformanceMode.Quality`, `Balanced`, and `Performance` select named fixed fidelity
  profiles.
- `HazePerformanceMode.Fixed(qualityFraction)` selects a deterministic fidelity profile for a
  normalized quality fraction from `0f` through `1f`.

Start with the default. Override it only after comparing visual quality and performance on the
devices you support.
