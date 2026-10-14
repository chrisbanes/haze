# Core concepts

Every Haze effect declares what it consumes (its input), how it looks (its Style), and a few
structural policies such as performance mode and layer expansion.

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

Refine either selection with `where`. The predicate receives each source's `key` and `zIndex`.
Repeated refinements combine with logical AND:

```kotlin
val selection = HazeSourceSelection.Behind
  .where { source -> source.zIndex >= 1f }
  .where { source -> source.key != "sensitive" }
```

## Explicit inputs

Typed effects always declare what they consume:

| Intent | Input | Behavior |
| --- | --- | --- |
| Whatever is already behind a built-in effect | `HazeInput.Backdrop(hazeState)` | Pixels drawn earlier in the current window, with source capture as the portable fallback. |
| Specific captured Haze sources | `HazeInput.Sources(hazeState)` | Explicit source selection and retention. |
| The modifier's own content | `HazeInput.Content` | Foreground or own-content effects. |

Use `HazeInput.Backdrop(hazeState)` for ordinary built-in Blur and Glass surfaces. Its `fallback`
configures only the source fallback; it doesn't select or filter native window pixels:

```kotlin
Modifier.hazeGlass(
  input = HazeInput.Backdrop(
    HazeInput.Sources(
      state = hazeState,
      retention = HazeSourceRetention.ClearWhenUnavailable,
    ),
  ),
)
```

`HazeInput.Sources` also takes a `retention` policy:

- `KeepLastFrame` (the default) keeps drawing the last output through temporary source gaps. It
  isn't a frozen copy: on Android, retained Glass output can redraw from live or changed source
  content after its source is lost.
- `ClearWhenUnavailable` clears the output as soon as no selected source is drawable. Use it when
  stale or deselected content must not stay visible.

### Android window backdrops

`HazeInput.Backdrop` works on every platform. On Android it can also use a native window
backdrop, which is eligible only when the experimental flag is enabled, the built-in effect
supports it, and the modifier is attached to a hardware-accelerated window on Android 37.2 or
later. The native path filters the combined pixels already drawn earlier in the same window. It
can't select individual Haze sources, include content drawn later, or cross a dialog, popup, or
window boundary.

```kotlin
// Set this before the effect is attached.
HazeFeatureFlags.isPlatformBackdropEnabled = true

Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
)
```

- The flag defaults to `false`, is read when the effect attaches, and makes the native path
  eligible rather than guaranteed.
- Native sampling uses compositor resolution; the effect's performance mode doesn't downscale it.
- If native rendering is unavailable or fails, the source fallback is used for the rest of the
  attachment. The switch can take one frame.
- `HazeInput.Backdrop()` without a state has no fallback: if native rendering is unavailable, the
  content draws unchanged.
- Once native rendering has started, a frame the effect can't express natively, such as Glass
  beyond its layer budget, draws its content unchanged instead of switching to the fallback.
- While native rendering works, fallback sources aren't recorded, so they add no capture cost.

Set `HazeLogger.enabled = true` to log backdrop selection and fallback. The flag is
experimental, and a later release may enable it by default.

Use `HazeInput.Sources` directly when you need exact source selection, cross-window alignment, or
a retention policy that applies to the actual input rather than only the fallback.

## Typed Blur

Blur has an ordinary typed modifier:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
  style = HazeMaterials.thin(),
  performanceMode = HazePerformanceMode.Default,
  expandLayerBounds = true,
)
```

Use `HazeInput.Content` for own-content Blur. Input, performance mode, and layer expansion are
modifier arguments, not part of `HazeBlurStyle`.

A `HazeBlurStyle` is a block of settings, and `then` appends more:

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

Haze applies `HazeBlurDefaults.style`, then `LocalHazeBlurStyle`, then the explicit Style, and the
last write wins. Styles hold no rendering state, so you can share them between modifiers. Two
Styles with the same writes in the same order are equal, so recreating an identical Style during
recomposition doesn't update the effect; pass a new Style when a value changes.

## Typed custom effects

Custom effects use a shareable factory that creates one renderer per modifier:

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

A new Style updates the existing renderer. Replacing the factory, or removing the modifier,
calls `dispose` once. See [Custom effects](custom-effects.md) for the full API.

## Built-in performance mode

`HazePerformanceMode` lets you choose the quality and performance trade-off for built-in Blur and
Glass:

- `Default` selects the fixed `Balanced` profile. `Adaptive` is deprecated and behaves the same as `Default`.
- `Quality` prioritises detail, `Performance` prioritises lower rendering cost, and `Balanced`
  offers a middle ground.
- `Fixed(qualityFraction)` lets you choose a level from `0f` (lowest supported quality) to `1f`
  (highest supported quality).

Pass `performanceMode` to one effect, or provide `LocalHazePerformanceMode` to set it for a
subtree; an explicit argument wins.

Start with the default. If the effect looks too pixelated, try a higher fixed quality level and
check that scrolling and animations remain smooth on the devices you support. See the
[performance guide](performance.md) for more guidance and the [migration guide](migrating-2.0.md)
for replacing older sampling settings.

## Generic sampling

Custom effects use `HazeSampling` instead of `HazePerformanceMode`. Its `Default` (currently
`Adaptive`), `Adaptive`, `FullResolution`, and `Fixed(pixelFraction)` policies control the
fraction of full-resolution input pixels the effect receives.

## Layer bounds

`expandLayerBounds` lets an effect capture beyond its own bounds. Blur expands by its blur radius
to avoid artifacts at the edges. Disable it only when surrounding pixels must not be captured.

## Background and foreground effects

Background effects usually use `HazeInput.Backdrop`. This example uses `HazeInput.Sources` to
blur exactly the captured list:

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
      input = HazeInput.Backdrop(LocalHazeState.current),
    ),
  )
}
```

## Overlapping effects

One composable can both consume lower sources and be a source for a higher effect. Use
`HazeInput.Sources` here, because source ordering matters, and give each source an explicit
`zIndex`:

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

Effects in a dialog can consume sources from the window behind it. Use `HazeInput.Sources`,
because the native backdrop can't cross windows; Haze aligns the two automatically:

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

## Screenshot testing

On Android, run Robolectric screenshot tests against SDK 35 or newer. Earlier Robolectric SDK
levels render effect edges differently:

```kotlin
@Config(sdk = [35])
class MyScreenshotTest {
  // Tests
}
```

This only affects Robolectric, not real devices.
