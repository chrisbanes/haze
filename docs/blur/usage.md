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
devices you support. `Default` is `Adaptive`, and `Quality` replaces the previous built-in
full-resolution choice. Remeasure any previous built-in fixed input-pixel fraction before choosing
an explicit `Fixed(qualityFraction)` value.

### Controlled calibration reference

The following Android Macrobenchmark reference is a comparison point, not a performance target or
promise. It was captured on 2026-08-09 with the `benchmarkRelease` variant on a Pixel 6 (Android
17/API 37, 1080×2400), with the display locked to 60 Hz and Android fixed-performance mode enabled.
Each row contains 16 fixed-duration iterations of the controlled Blur sample. Values are **P90 CPU
frame duration / P90 frame overrun**, in milliseconds; a negative overrun is margin below the
60 Hz frame budget.

| Workload | Adaptive | Quality | Balanced | Performance |
| --- | ---: | ---: | ---: | ---: |
| Stable source | 10.0 / -3.2 | 10.2 / -3.0 | 10.1 / -3.1 | 10.0 / -2.7 |
| Continuously changing source | 10.0 / -2.9 | 10.2 / -2.7 | 10.1 / -1.8 | 10.1 / -3.0 |

The controlled sample holds all other style choices fixed. It is useful for comparing the named
profiles, but measure the layout, content, and interaction patterns of your application before
choosing an override.
