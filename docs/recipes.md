# Recipes

## Scaffold chrome

Place `hazeSource` on the scrolling content and pass the same state to each bar's effect:

```kotlin
val hazeState = rememberHazeState()
val style = HazeMaterials.thin()

Scaffold(
  topBar = {
    TopAppBar(
      colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
      modifier = Modifier.hazeBlur(
        input = HazeInput.Backdrop(hazeState),
        style = style,
      ),
    )
  },
  bottomBar = {
    NavigationBar(
      containerColor = Color.Transparent,
      modifier = Modifier.hazeBlur(
        input = HazeInput.Backdrop(hazeState),
        style = style,
      ),
    ) {
      // Items
    }
  },
) { contentPadding ->
  LazyVerticalGrid(
    contentPadding = contentPadding,
    modifier = Modifier
      .fillMaxSize()
      .hazeSource(hazeState),
  ) {
    // Items
  }
}
```

Pass the scaffold padding to the scrolling content as `contentPadding` rather than padding the
source. Content then scrolls behind the translucent bars while staying reachable.

## Sticky headers

Don't mark the whole `LazyColumn` as a source when the header inside it applies the effect. Mark
the non-header items instead:

```kotlin
val hazeState = rememberHazeState()
val style = HazeMaterials.thin()

LazyColumn {
  stickyHeader {
    Header(
      modifier = Modifier.hazeBlur(
        input = HazeInput.Backdrop(hazeState),
        style = style,
        performanceMode = HazePerformanceMode.Default,
      ),
    )
  }

  items(items) { item ->
    Item(
      item,
      modifier = Modifier.hazeSource(hazeState),
    )
  }
}
```

## Privacy-sensitive source transitions

By default, an effect can keep showing its last output after its source disappears. To clear it
instead, for example when the source showed sensitive content:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Sources(
    state = hazeState,
    retention = HazeSourceRetention.ClearWhenUnavailable,
  ),
  style = style,
)
```

## Reusing and customizing a Style

```kotlin
val base = HazeMaterials.regular()
val quiet = base.then {
  noiseFactor(0f)
  colorEffects(emptyList())
}

TopAppBar(
  modifier = Modifier.hazeBlur(HazeInput.Backdrop(hazeState), quiet),
)
BottomAppBar(
  modifier = Modifier.hazeBlur(HazeInput.Backdrop(hazeState), quiet),
)
```

Styles are immutable, so one Style can be shared by several modifiers.
