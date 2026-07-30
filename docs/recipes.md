# Recipes

## Scaffold chrome

Place `hazeSource` on the scrolling content and use the same state for each app bar:

```kotlin
val hazeState = rememberHazeState()
val style = HazeMaterials.thin()

Scaffold(
  topBar = {
    TopAppBar(
      colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
      modifier = Modifier.hazeBlur(
        input = HazeInput.Sources(hazeState),
        style = style,
      ),
    )
  },
  bottomBar = {
    NavigationBar(
      containerColor = Color.Transparent,
      modifier = Modifier.hazeBlur(
        input = HazeInput.Sources(hazeState),
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

Pass scaffold padding to the scrollable content rather than applying it outside the source. That
keeps interactive content clear of chrome while preserving source pixels behind translucent bars.

## Sticky headers

Avoid making a `LazyColumn` both a source and a descendant effect. Mark non-header items as sources:

```kotlin
val hazeState = rememberHazeState()
val style = HazeMaterials.thin()

LazyColumn {
  stickyHeader {
    Header(
      modifier = Modifier.hazeBlur(
        input = HazeInput.Sources(hazeState),
        style = style,
        sampling = HazeSampling.Adaptive,
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

Clear retained Blur output when no source is available:

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
  modifier = Modifier.hazeBlur(HazeInput.Sources(hazeState), quiet),
)
BottomAppBar(
  modifier = Modifier.hazeBlur(HazeInput.Sources(hazeState), quiet),
)
```

Both modifiers evaluate the shared Style into independent node-owned runtimes.
