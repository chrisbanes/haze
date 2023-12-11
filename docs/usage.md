Haze is implemented through two Compose Modifiers: [Modifier.haze](../api/haze/dev.chrisbanes.haze/haze.html) and [Modifier.hazeChild](../api/haze/dev.chrisbanes.haze/haze-child.html).

The most basic usage would be something like:

``` kotlin hl_lines="1 7-12 21-23"
val hazeState = remember { HazeState() } 

Box {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .haze(
        // Pass it the HazeState we stored above
        state = hazeState,
        // Need to provide background color of the content
        backgroundColor = MaterialTheme.colorScheme.surface,
      ),
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

## Shapes

Haze has some support for blurring of `Shape`s. Each platform has varying support:

- Android: full support, through `clipPath`
- iOS and Desktop: limited support. Only `RoundedCornerShape` currently works.

To use a shape, you can just pass it in to `hazeChild`:

``` kotlin hl_lines="10"
val hazeState = remember { HazeState() } 

Box {
  // rest of sample from above

  LargeTopAppBar(
    modifier = Modifier
      .hazeChild(
        state = hazeState,
        shape = RoundedCornerShape(16.dp),
      ),
  )
}
```


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
        backgroundColor = MaterialTheme.colorScheme.surface,
      ),
  ) {
    // todo
  }
}
```
