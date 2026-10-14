## What's the difference between this and Modifier.blur?

[Modifier.blur](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier#(androidx.compose.ui.Modifier).blur(androidx.compose.ui.unit.Dp,androidx.compose.ui.unit.Dp,androidx.compose.ui.draw.BlurredEdgeTreatment)) blurs a composable's own content. Haze's main use is blurring the content _behind_ a surface, which is what glass-like effects need. Haze can also blur a composable's own content with `HazeInput.Content`.

Haze also adds tints, noise, progressive Blur, masks, and more. See the [Usage](usage.md) page.

## Why does my Blur get clipped inside a graphics layer?

An enclosing `graphicsLayer` that uses `alpha` below one, an offscreen compositing strategy, a
color filter, or similar options clips its content to its bounds. Blur that spreads past those
bounds is cut off.

Use Compose 1.12's [LayerOutsets][layer-outsets] on that enclosing layer, on the edges that need
the extra space. For Blur that can spread equally on every edge, match each outset to the Blur
radius:

```kotlin
val blurRadius = 32.dp

Box(
  Modifier.graphicsLayer(
    alpha = 0.5f,
    outsets = LayerOutsets(blurRadius, blurRadius, blurRadius, blurRadius),
  ),
) {
  Box(
    Modifier.hazeBlur(
      input = HazeInput.Backdrop(hazeState),
      style = HazeBlurStyle { blurRadius(blurRadius) },
    ),
  )
}
```

Outsets only enlarge the area the layer can draw into; they don't change what Haze blurs. An
explicit clip still applies.

## Does Blur look the same on every platform?

Yes, with the same API on every platform. The exceptions are on older Android versions: Android 12
and 12L approximate some progressive Blur, and Android 11 and below draw a scrim unless you enable
Blur. See [Platforms](platforms.md).

## What versions of Android does Haze work on?

See the [Platforms](platforms.md) page.

## When should I use Backdrop instead of Sources?

Use `HazeInput.Backdrop(hazeState)` for most Blur: it blurs whatever was drawn earlier in the same
window. Use `HazeInput.Sources(hazeState)` when you need to choose exactly which sources are
blurred, blur content from another window, or control what is shown while sources are
unavailable. See [Blur usage](usage.md) for details, including the experimental native Android
backdrop.

 [layer-outsets]: https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/LayerOutsets
