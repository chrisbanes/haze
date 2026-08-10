# Core concepts

Haze separates captured input, shareable configuration, and node-owned rendering resources.

## Sources

`HazeState` connects one or more `hazeSource` modifiers to source-backed effects:

```kotlin
val hazeState = rememberHazeState()

LazyColumn(
  modifier = Modifier.hazeSource(hazeState),
) {
  // Content
}
```

Sources can have a `zIndex` and metadata `key`. `HazeSourceSelection.Behind` is the default. With
a nearest ancestor `hazeSource` using the same state, it selects lower-z sources; without one, it
selects every source. `HazeSourceSelection.All` bypasses the ancestor relationship.

Refine either selection with `where`. Predicates receive only immutable `key` and `zIndex`
metadata, not captured pixels or renderer resources. Repeated refinements combine with logical
AND:

```kotlin
val selection = HazeSourceSelection.Behind
  .where { source -> source.zIndex >= 1f }
  .where { source -> source.key != "sensitive" }
```

## Explicit inputs

Typed effects always declare what they consume:

- `HazeInput.Sources(hazeState)` consumes captured source content.
- `HazeInput.Content` consumes the modifier's own content.

Source-backed input also declares retention:

```kotlin
HazeInput.Sources(
  state = hazeState,
  retention = HazeSourceRetention.ClearWhenUnavailable,
)
```

`KeepLastFrame` smooths temporary source gaps. `ClearWhenUnavailable` clears retained output as
soon as no selected source is drawable.

## Typed Blur

Blur has an ordinary typed modifier:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Sources(hazeState),
  style = HazeMaterials.thin(),
  performanceMode = HazePerformanceMode.Adaptive,
  expandLayerBounds = true,
)
```

Use `HazeInput.Content` for own-content Blur. The structural input, retention, performance-mode,
and layer-expansion policies do not live in `HazeBlurStyle`.

`HazeBlurStyle` is an opaque replayable program:

```kotlin
val style = HazeBlurStyle {
  blurRadius(20.dp)
  colorEffects(
    listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.12f))),
  )
}.then {
  noiseFactor(0f)
}
```

Resolution replays `HazeBlurDefaults.style`, `LocalHazeBlurStyle`, and the explicit Style in order.
The last write wins, and every evaluation starts fresh. Styles contain no mutable renderer or
platform state and can be shared by concurrent modifiers. Style programs with identical ordered
writes and equal values are structurally equal, so recreating the same program during recomposition
does not update the modifier node; provide a replacement Style when a configured value changes.

## Typed custom effects

Custom effects use a stateless factory and one renderer per modifier node:

```kotlin
val factory = HazeEffectFactory<MyStyle> {
  object : HazeEffectRenderer<MyStyle> {
    override fun HazeEffectDrawScope.draw(style: MyStyle) {
      drawInput()
      // Draw the effect.
    }
  }
}

Modifier.hazeEffect(
  factory = factory,
  input = HazeInput.Content,
  style = MyStyle(...),
)
```

The renderer can own mutable resources and releases them in `dispose`. Style replacement updates
the existing renderer. Factory replacement and detachment dispose it exactly once.

## Built-in performance mode

- `HazePerformanceMode.Default` is `Adaptive` and lets built-in Blur and Glass balance quality and
  cost automatically.
- `HazePerformanceMode.Quality`, `Balanced`, and `Performance` select effect-owned named profiles.
- `HazePerformanceMode.Fixed(qualityFraction)` selects a normalized, deterministic profile for a
  built-in effect.

Start with the default. Choose a fixed profile only when visual comparison shows that you need a
stable quality and performance trade-off.

`Quality` replaces the previous built-in full-resolution choice. Previous built-in fixed
input-pixel fractions have no direct equivalent: remeasure an explicit `Fixed(qualityFraction)`
choice on the effect and layout you support.

## Generic sampling

`HazeSampling` remains the generic input-sampling contract for custom effects. Built-in Blur and
Glass use `HazePerformanceMode` instead. `HazeSampling`'s `Default`, `Adaptive`, `FullResolution`, and
`Fixed(pixelFraction)` policies control the fraction of full-resolution input pixels custom
effects receive.

## Layer bounds

`expandLayerBounds` lets an effect request a larger capture layer. Blur normally expands by its
resolved radius to avoid edge artifacts. Disable it only when the surrounding pixels must not be
captured.

## Background and foreground effects

Source-backed effects render captured content from elsewhere in the hierarchy:

```kotlin
Box {
  LazyColumn(
    modifier = Modifier.hazeSource(hazeState),
  ) {
    // Content
  }

  TopAppBar(
    modifier = Modifier.hazeBlur(
      input = HazeInput.Sources(hazeState),
    ),
  )
}
```

Own-content effects capture and transform the modifier's content:

```kotlin
Box(
  modifier = Modifier.hazeBlur(input = HazeInput.Content),
) {
  // This content is blurred.
}
```

## Deep UI hierarchies

When `HazeState` would otherwise pass through many composables, provide it through a composition
local:

```kotlin
val LocalHazeState = compositionLocalOf { HazeState() }

@Composable
fun HazeExample() {
  val hazeState = rememberHazeState()

  CompositionLocalProvider(LocalHazeState provides hazeState) {
    Box {
      Background()
      Foreground()
    }
  }
}

@Composable
fun Foreground() {
  Text(
    modifier = Modifier.hazeBlur(
      input = HazeInput.Sources(LocalHazeState.current),
    ),
  )
}
```

## Overlapping effects

One composable can both consume lower sources and become a source for a higher effect. Give each
source an explicit `zIndex`:

```kotlin
Box {
  Background(
    modifier = Modifier.hazeSource(hazeState, zIndex = 0f),
  )

  Card(
    modifier = Modifier
      .hazeSource(hazeState, zIndex = 1f)
      .hazeBlur(input = HazeInput.Sources(hazeState)),
  )

  TopAppBar(
    modifier = Modifier
      .hazeSource(hazeState, zIndex = 2f)
      .hazeBlur(input = HazeInput.Sources(hazeState)),
  )
}
```

The Card consumes the Background, while the TopAppBar consumes both lower sources.

## Dialogs

Mark the source before showing a dialog. Haze can then align a source and effect that live in
different windows:

```kotlin
Box {
  LazyColumn(
    modifier = Modifier.hazeSource(hazeState),
  ) {
    // Background content
  }

  if (showDialog) {
    Dialog(onDismissRequest = { showDialog = false }) {
      Surface(
        modifier = Modifier.hazeBlur(
          input = HazeInput.Sources(hazeState),
        ),
      ) {
        // Dialog content
      }
    }
  }
}
```

Haze handles alignment between the dialog and its source automatically.

## Screenshot testing

On Android, run Robolectric screenshot tests against SDK 35 or newer. Earlier Robolectric SDK
levels do not fully reproduce the blur tile modes used at effect edges:

```kotlin
@Config(sdk = [35])
class MyScreenshotTest {
  // Tests
}
```

This limitation affects the test environment, not the equivalent effect on a physical device.
