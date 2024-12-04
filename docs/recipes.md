
## Scaffold

Blurring the content behind app bars is a common use case, so how can we use Haze with `Scaffold`? It's pretty much the same as above:

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
