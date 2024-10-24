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

