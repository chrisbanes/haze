
## Scaffold

Blurring the content behind app bars is a common use case, so how can we use Haze with `Scaffold`? It's pretty much the same as above:

!!! tip "Multiple hazeEffects"
    Note: We are using multiple `hazeEffect`s in this example. You can actually use an abitrary number of `hazeEffect`s.

``` kotlin
val hazeState = rememberHazeState()
val style = HazeMaterials.thin()

Scaffold(
  topBar = {
    TopAppBar(
      // Need to make app bar transparent to see the content behind
      colors = TopAppBarDefaults.largeTopAppBarColors(Color.Transparent),
      modifier = Modifier
        .hazeEffect(state = hazeState) {
          blurEffect {
            this.style = style
          }
        }
        .fillMaxWidth(),
    ) {
      /* todo */
    }
  },
  bottomBar = {
    NavigationBar(
      containerColor = Color.Transparent,
      modifier = Modifier
        .hazeEffect(state = hazeState) {
          blurEffect {
            this.style = style
          }
        }
        .fillMaxWidth(),
    ) {
      /* todo */
    }
  },
) { contentPadding ->
  LazyVerticalGrid(
    contentPadding = contentPadding,
    modifier = Modifier
      .fillMaxSize()
      .hazeSource(
        state = hazeState,
      ),
  ) {
    // todo
  }
}
```

The same guidance applies to other scaffold composables that provide content padding: pass that padding to the scrollable content rather than applying it as an outer modifier. This keeps interactive items clear of the chrome while preserving a full-size Haze source with pixels behind translucent bars. Scaffold-like layouts that measure content beside or above their chrome require an application-level overlay or custom layout to achieve an edge-to-edge translucent effect.

## Sticky Headers

The `stickyHeader` functionality on `LazyColumn` and friends is very useful, but unfortunately the limitations of Haze means that blurring the list contents for the header background is tricky.

Since we can not use `Modifier.hazeSource` on the `LazyColumn` and `Modifier.hazeEffect` on items, as we would get into recursive drawing, we need to get a bit more creative.

Since we can have multiple nodes using `Modifier.hazeSource`, we can use the modifier on all non-header items, and then use `hazeEffect` as normal on the `stickyHeader`:

```kotlin
val hazeState = rememberHazeState()
val style = HazeMaterials.thin()

LazyColumn(...) {
  stickyHeader {
    Header(
      modifier = Modifier
        .hazeEffect(state = hazeState) {
          blurEffect {
            this.style = style
          }
        },
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
