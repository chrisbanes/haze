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

Haze has support for customizing the resulting effect, which is performed via the [HazeStyle](../api/haze/dev.chrisbanes.haze/-haze-style/) class. Styles can be provided to both [Modifier.haze](../api/haze/dev.chrisbanes.haze/haze.html) and [Modifier.hazeChild](../api/haze/dev.chrisbanes.haze/haze-child.html).

### Blur Radius

The blur radius controls how strong the blur effect is. This defaults to `20.dp` but can be customized as needed. Larger values might be needed to keep foreground control (such as text) legible and accessible.

### Tint

A tint effect is applied, primarily to maintain contrast and legibility. By default we use the provided background color at 70% opacity. You may wish to use a different color or opacity.

### Noise

Some visual noise is applied, to provide some tactility. This is completely optional, and defaults to a value of `0.15f` (15% strength). You can disable this by providing `0f`.

## Scaffold

Make the content behind app bars is a common use case, so how can we use Haze with `Scaffold`? It's pretty much the same as above:

!!! tip "Multiple hazeChilds"
    Note: We are using multiple `hazeChild`s in this example. You can actually use an abitrary number of `hazeChild`s.

``` kotlin
val hazeState = remember { HazeState() }

Scaffold(
  topBar = {
    TopAppBar(
      // Need to make app bar transparent to see the content behind
      colors = TopAppBarDefaults.largeTopAppBarColors(Color.Transparent),
      modifier = Modifier
        .hazeChild(state = hazeState)
        .fillMaxWidth(),
    ) {
      /* todo */
    }
  },
  bottomBar = {
    NavigationBar(
      containerColor = Color.Transparent,
      modifier = Modifier
        .hazeChild(state = hazeState)
        .fillMaxWidth(),
    ) {
      /* todo */
    }
  },
) {
  LazyVerticalGrid(
    modifier = Modifier
      .haze(
        state = hazeState,
        style = HazeDefaults.style(backgroundColor = MaterialTheme.colorScheme.surface),
      ),
  ) {
    // todo
  }
}
```
