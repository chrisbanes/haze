
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
) {
  LazyVerticalGrid(
    modifier = Modifier
      .hazeSource(
        state = hazeState,
      ),
  ) {
    // todo
  }
}
```

## Navigation3 with adaptive navigation

`NavDisplay` applies its outer `AnimatedContent` transition after each scene is composed. A
`hazeSource` on every scene therefore misses that transform and alpha while a transition is in
progress. Conversely, an effect in a `NavDisplay` descendant cannot sample an active ancestor
source: it would recursively sample the layer while that source is being recorded.

Keep the complete `NavDisplay` composite in one source, then use Material 3 Adaptive's
`NavigationSuiteScaffoldLayout` as a sibling navigation-only overlay. The layout still positions the
appropriate bar or rail, but its `content` must remain empty so that `NavDisplay` stays full-size and
edge-to-edge behind the translucent navigation.

```kotlin
val hazeState = rememberHazeState()
val navigationSuiteType =
  NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())

Box(Modifier.fillMaxSize()) {
  NavDisplay(
    modifier = Modifier
      .fillMaxSize()
      .hazeSource(hazeState),
    // back stack, entries, transitions, and scene decorators
  )

  NavigationSuiteScaffoldLayout(
    navigationSuiteType = navigationSuiteType,
    navigationSuite = {
      NavigationSuite(
        navigationSuiteType = navigationSuiteType,
        colors = NavigationSuiteDefaults.colors(
          shortNavigationBarContainerColor = Color.Transparent,
          wideNavigationRailColors = WideNavigationRailDefaults.colors(
            containerColor = Color.Transparent,
            modalContainerColor = Color.Transparent,
          ),
          navigationBarContainerColor = Color.Transparent,
          navigationRailContainerColor = Color.Transparent,
          navigationDrawerContainerColor = Color.Transparent,
        ),
        modifier = Modifier.hazeEffect(hazeState) {
          blurEffect {
            blurRadius = 20.dp
          }
        },
      ) {
        // navigation items
      }
    },
    content = {},
  )
}
```

Use the same `NavigationSuiteType` for the layout and `NavigationSuite`, and make every relevant
Material navigation container transparent so the Haze result remains visible. Keep decorative scene
content edge-to-edge; apply any padding needed to keep interactive content clear of the overlaid bar
or rail.

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
