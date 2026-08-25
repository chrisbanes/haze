## What's the difference between this and Modifier.blur?

The [Modifier.blur](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier#(androidx.compose.ui.Modifier).blur(androidx.compose.ui.unit.Dp,androidx.compose.ui.unit.Dp,androidx.compose.ui.draw.BlurredEdgeTreatment)) modifier and Haze may sound similar, but what they provide is different.

Haze primarily provides background blurring, meaning that it will blur content behind, not the content itself. This is typically required to implement glass-like effects. `Modifier.blur` performs 'foreground blurring' (or content blurring) where the content in the node itself is blurred. Haze can work in this mode too, but it's not the primary goal.

Other than that, Haze provides a whole bunch of other features which you can read about on the [Usage](usage.md) page.

## Why does my Blur get clipped inside a graphics layer?

An enclosing caller-owned `graphicsLayer` can become a separate compositing boundary when it uses
`alpha` below one, an explicit offscreen strategy, a color filter, or similar options. Unbounded
Blur pixels outside that layer's ordinary bounds are then clipped by the caller layer, even though
Haze has expanded its own effect layer for the Blur radius.

Use Compose 1.12's [LayerOutsets][layer-outsets] on that enclosing layer, with only the edges that
need the extra raster space. For a Blur that may overflow equally on every edge, match each outset
to the configured Blur radius:

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
      input = HazeInput.Sources(hazeState),
      style = HazeBlurStyle { blurRadius(blurRadius) },
    ),
  )
}
```

This only expands the caller layer's visual raster bounds. It does not change Haze's capture or
sampling geometry. An explicit caller clip remains authoritative, so outsets cannot make Blur draw
outside geometry that you deliberately clip.

## Are the blur implementations the same across different platforms?

The short answer to this is yes. The majority of the implementation is the same across all platforms.

The only differences are in how the blur effect is implemented, especially on Android.

For most platforms we can use on [RenderEffects][rendereffect] to implement the blurring, but the implementations used are platform specific. All of the platforms implement them in a very similar, its just the platform classes being used which are different.

## What versions of Android does Haze work on?

See the [Platforms](platforms.md) page for a detailed run down of what is supported on various platforms.

 [rendereffect]: https://developer.android.com/reference/android/graphics/RenderEffect
 [layer-outsets]: https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/LayerOutsets
