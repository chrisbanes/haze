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

Every evaluation starts from fresh defaults. If a replacement Style omits `blurRadius`, the local
or default value becomes visible immediately; the previous explicit value cannot stick. A Style
can be shared by concurrent modifiers because it contains no renderer, delegate, cache, retained
layer, or lifecycle state.

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

## Input scale

### Sampling and layer expansion

Sampling and layer expansion are structural modifier policies, not Style properties:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Sources(hazeState),
  style = style,
  sampling = HazeSampling.Adaptive,
  expandLayerBounds = true,
)
```

- `HazeSampling.Default` points to the library's current default policy, which is `Adaptive`.
- `HazeSampling.Adaptive` uses Blur's adaptive policy.
- `HazeSampling.FullResolution` disables input downscaling.
- `HazeSampling.Fixed(pixelFraction)` retains a fixed fraction of the full-resolution input pixels
  in `0 < pixelFraction <= 1`; `0.5` scales each dimension by approximately `0.707`.

Adaptive Blur considers the physical Blur radius, expanded capture-layer area, and recent distinct
input-update cadence, with hysteresis between its quality tiers.

## Temporary legacy boundary

`BlurVisualEffect`, `HazeEffectScope.blurEffect`, and the lambda-based `hazeEffect` overloads remain
temporarily available for staged migration. New code should use `hazeBlur` and replayable Styles.
