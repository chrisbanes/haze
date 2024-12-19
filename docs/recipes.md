
## Scaffold

Blurring the content behind app bars is a common use case, so how can we use Haze with `Scaffold`? It's pretty much the same as above:

!!! tip "Multiple hazeEffects"
    Note: We are using multiple `hazeEffect`s in this example. You can actually use an abitrary number of `hazeEffect`s.

``` kotlin
val hazeState = remember { HazeState() }

Scaffold(
  topBar = {
    TopAppBar(
      // Need to make app bar transparent to see the content behind
      colors = TopAppBarDefaults.largeTopAppBarColors(Color.Transparent),
      modifier = Modifier
        .hazeEffect(state = hazeState)
        .fillMaxWidth(),
    ) {
      /* todo */
    }
  },
  bottomBar = {
    NavigationBar(
      containerColor = Color.Transparent,
      modifier = Modifier
        .hazeEffect(state = hazeState)
        .fillMaxWidth(),
    ) {
      /* todo */
    }
  },
) {
  LazyVerticalGrid(
    modifier = Modifier
      .hazeSource(
        state = hazeState,
        style = HazeDefaults.style(backgroundColor = MaterialTheme.colorScheme.surface),
      ),
  ) {
    // todo
  }
}
```

## Sticky Headers

The `stickyHeader` functionality on `LazyColumn` and friends is very useful, but unfortunately the limitations of Haze means that blurring the list contents for the header background is tricky.

Since we can not use `Modifier.hazeSource` on the `LazyColumn` and `Modifier.hazeEffect` on items, as we would get into recursive drawing, we need to get a bit more creative.

Since we can have multiple nodes using `Modifier.hazeSource`, we can use the modifier on all non-header items, and then use `hazeEffect` as normal on the `stickyHeader`:

```kotlin
val hazeState = remember { HazeState() }

LazyColumn(...) {
  stickyHeader {
    Header(
      modifier = Modifier
        .hazeEffect(state = hazeState),
    )
  }

  items(list) { item ->
    Foo(
      modifier = Modifier
        .hazeSource(hazeState),
    )
  }
}
```

A more complete example can be found here: [ListWithStickyHeaders](https://github.com/chrisbanes/haze/blob/main/sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/ListWithStickyHeaders.kt).

![type:video](./media/sticky.mp4)
