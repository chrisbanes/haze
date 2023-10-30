Haze is implemented through a Compose Modifier: [Modifier.haze](../api/haze/dev.chrisbanes.haze/haze.html).

The most basic usage would be something like:

``` kotlin hl_lines="5-11"
Box {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .haze(
        // rectangle of the column to blur. In this instance we would
        // pass in the bounds of the top app bar content below
        Rect(...),
        // Need to provide background color of the content
        backgroundColor = MaterialTheme.colorScheme.surface,
      ),
  ) {
    // todo
  }

  LargeTopAppBar(
    // Need to make app bar transparent to see the content behind
    colors = TopAppBarDefaults.largeTopAppBarColors(Color.Transparent),
    modifier = Modifier.fillMaxWidth(),
  )
}
```

The important thing to note is that you are using `Modifier.haze` on the background content, but passing in the bounds of any
content drawn on top. How you calculate the bounds of the overlapping content to be blurred is up to you, and usually dependent on the situation which you're are in. 


## Scaffold

Make the content behind app bars is a common use case, so how can we use Haze with `Scaffold`? The sample in this repository uses `Scaffold` with `BoxWithConstraints` to blur any content behind the top and bottom app bars:

!!! tip "Multiple Rects"
    Note: how we are passing in 2 `Rect`s in this example. You can actually pass in an abitrary number of rectangles into `Modifier.haze`.

``` kotlin
Scaffold(
  topBar = { /* todo */ },
  bottomBar = { /* todo */ },
) { contentPadding ->
  // The contentPadding contains the areas which are overlapped by 
  // any top and bottom bars. We can use that to make any content
  // behind those bars 'hazy'.

  BoxWithConstraints {
    // Calculate the top and bottom bar bounds by combining the incoming
    // constraints + contentPadding
    val topBarBounds = with(LocalDensity.current) {
      Rect(
        Offset(0f, 0f),
        Offset(maxWidth.toPx(), contentPadding.calculateTopPadding().toPx())
      )
    }
    val bottomBarBounds = with(LocalDensity.current) {
      val bottomPaddingPx = contentPadding.calculateBottomPadding().toPx()
      Rect(
        Offset(0f, maxHeight.toPx() - bottomPaddingPx),
        Offset(maxWidth.toPx(), maxHeight.toPx())
      )
    }

    LazyVerticalGrid(
      modifier = Modifier
        .haze(
          topBarBounds,
          bottomBarBounds,
          backgroundColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        // todo
    }
}
```

!!! note "Scaffold support"
    As mentioned, using `Modifier.haze` with `Scaffold` is a common use case so we may add better support for this in the future. In the mean time, the Tivi app has a [custom scaffold implementation](https://github.com/chrisbanes/tivi/blob/main/common/ui/compose/src/commonMain/kotlin/app/tivi/common/compose/TiviScaffold.kt) which might be useful.