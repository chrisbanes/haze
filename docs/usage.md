Haze is implemented through two Compose Modifiers: [Modifier.haze](../api/haze/dev.chrisbanes.haze/haze.html) and [Modifier.hazeChild](../api/haze/dev.chrisbanes.haze/haze-child.html).

The most basic usage would be something like:

``` kotlin hl_lines="1 7-8 17-19"
val hazeState = remember { HazeState() }

Box {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      // Pass it the HazeState we stored above
      .haze(state = hazeState)
  ) {
    // todo
  }

  LargeTopAppBar(
    // Need to make app bar transparent to see the content behind
    colors = TopAppBarDefaults.largeTopAppBarColors(Color.Transparent),
    modifier = Modifier
      // We use hazeChild on anything where we want the background
      // blurred.
      .hazeChild(state = hazeState)
      .fillMaxWidth(),
  )
}
```

## Styling

Haze has support for customizing the resulting effect, which is performed via the [HazeStyle](../api/haze/dev.chrisbanes.haze/-haze-style/) class, or the lambda block provided to `hazeChild`.

Styles can be provided in a number of different ways:

- [LocalHazeStyle](../api/haze/dev.chrisbanes.haze/-local-haze-style.html) composition local.
- The style parameter on [Modifier.hazeChild](../api/haze/dev.chrisbanes.haze/haze-child.html).
- By setting the relevant property in the optional [HazeChildScope](../api/haze/dev.chrisbanes.haze/-haze-child-scope/index.html) lambda `block`, passed into [Modifier.hazeChild](../api/haze/dev.chrisbanes.haze/haze-child.html).

### HazeChildScope

We now have a parameter on `Modifier.hazeChild` which allow you to provide a lambda block, for controlling all of Haze's styling parameters. It is similar to concept to `Modifier.graphicsLayer { ... }`.

It's useful for when you need to update styling parameters, using values derived from other state. Here's an example which fades the effect as the user scrolls:

```kotlin
FooAppBar(
  ...
  modifier = Modifier
    .hazeChild(state = hazeState) {
      alpha = if (listState.firstVisibleItemIndex == 0) {
        listState.layoutInfo.visibleItemsInfo.first().let {
          (it.offset / it.size.height.toFloat()).absoluteValue
        }
      } else {
        alpha = 1f
      }
    },
)
```

### Styling resolution

As we a few different ways to set styling properties, it's important to know how the final values are resolved.

Each styling property (such as `blurRadius`) is resolved seperately, and the order of precedence for each property is as follows, in order:

- Value set in [HazeChildScope](../api/haze/dev.chrisbanes.haze/-haze-child-scope/index.html), if specified.
- Value set in style provided to hazeChild (or HazeChildScope.style), if specified.
- Value set in the [LocalHazeStyle](../api/haze/dev.chrisbanes.haze/-local-haze-style.html) composition local.

### Styling properties

#### Blur Radius

The blur radius controls how strong the blur effect is. This defaults to `20.dp` but can be customized as needed. Larger values might be needed to keep foreground control (such as text) legible and accessible.

#### Tint

A tint effect is applied, primarily to maintain contrast and legibility. By default we use the provided background color at 70% opacity. You may wish to use a different color or opacity. You provide multiple tints, which will be applied in sequence.

#### Noise

Some visual noise is applied, to provide some tactility. This is completely optional, and defaults to a value of `0.15f` (15% strength). You can disable this by providing `0f`.

## Progressive (aka gradient) blurs

Progressive blurs allow you to provide a visual effect where the blur radius is varied over a dimension. You may have seen this effect used on iOS.

![type:video](./media/progressive.mp4)

Progressive blurs can be enabled by setting the `progressive` property on [HazeChildScope](../api/haze/dev.chrisbanes.haze/-haze-child-scope/index.html). The API is very similar to the Brush gradient APIs, so it should feel familiar.

```kotlin
LargeTopAppBar(
  // ...
  modifier = Modifier.hazeChild(hazeState) {
    progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
  }
)
```

!!! warning "Performance of Progressive"

    Please be aware that using progressive blurring does come with a performance cost. Please see the [Performance](performance.md) page for up-to-date benchmarks.

    As a quick summary: on Android SDK 33+ and other platforms, the cost is about 25% more than non-progressive. On Android SDK 32 it is about 2x. If performance is critical, you may wish to look at the masking functionality below.

## Masking

You can provide any `Brush`, which will be used as a mask when the final effect is drawn.

```kotlin
LargeTopAppBar(
  // ...
  modifier = Modifier.hazeChild(hazeState) {
    mask = Brush.verticalGradient(...)
  }
)
```

!!! info "Mask vs Progressive"

    When you provide a gradient brush as a mask, the effect is visually similar to a gradient blur. The difference is that the effect is faded through opacity only, and may not feel as refined. However, it is much faster than progressive blurring, having a negligible cost.

## Input Scale

You can provide an input scale value which determines how much the content is scaled in both the x and y dimensions, allowing the blur effect to be potentially applied over scaled-down content (and thus less pixels), before being scaled back up and drawn at the original size.

![](./media/inputscale.png)

```kotlin
LargeTopAppBar(
  // ...
  modifier = Modifier.hazeChild(hazeState) {
    inputScale = HazeInputScale.Fixed(0.5f)
  }
)
```

Using a value less than 1.0 **may** improve performance, at the sacrifice of quality and crispness. As always, run your own benchmarks as to whether this compromise is worth it.

If you're looking for a good value to experiment with, `0.8` results in a reduction in total resolution of ~35%, while being visually imperceptible to most people (probably).

The minimum value I would realistically use is somewhere in the region of `0.5`, which results in the total pixel count of only 25% of the original content. This is likely to be visually different to no scaling, but depending on the styling parameters, it will be visually pleasing to the user.
