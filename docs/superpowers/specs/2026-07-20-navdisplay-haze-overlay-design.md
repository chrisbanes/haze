# NavDisplay Haze Sibling Layout Design

## Goal

Allow persistent adaptive navigation chrome to sample the fully animated `NavDisplay` composite
without recursively sampling a `hazeSource` ancestor.

The solution must work across the existing Compose Multiplatform targets, preserve adaptive bar and
rail selection, and avoid a new public Haze API or a Navigation3 integration artifact.

## Root cause

`HazeSourceNode` records its descendant content into a graphics layer. A descendant
`HazeEffectNode` using the same `HazeState` cannot safely sample that layer while it is being
recorded, so Haze rejects the recursive draw.

Moving `hazeSource` into each decorated scene avoids the recursive ancestor relationship, but each
scene is recorded before `NavDisplay` applies its outer `AnimatedContent` alpha and transform.
During a transition the effect therefore replays independent scene layers instead of sampling the
visible animated composite.

An attempted shared-transition overlay did not create a safe boundary. Although the navigation
chrome was composited in the overlay, its `HazeEffectNode` was still visited while the ancestor
`NavDisplay` source was recording and Haze correctly rejected the recursive draw.

The required tree and draw boundary is therefore:

1. Record the complete `NavDisplay` animated composite as one Haze source.
2. Keep persistent effect-backed navigation chrome outside the `NavDisplay` subtree.
3. Draw the chrome as a sibling over the source so it can sample the completed layer.

## Design

Use Material 3 Adaptive's `NavigationSuiteScaffoldLayout` as a navigation-only overlay layer:

- Put a full-size `NavDisplay` and a full-size `NavigationSuiteScaffoldLayout` in the same parent
  `Box`.
- Apply one `hazeSource` to `NavDisplay`.
- Leave the scaffold layout's `content` empty; application content remains owned by `NavDisplay`.
- Render `NavigationSuite` in the scaffold layout's `navigationSuite` slot and apply `hazeEffect`
  to it.
- Pass the same `NavigationSuiteType` to both APIs so the scaffold positions the selected bar or
  rail correctly.
- Make the Material navigation container transparent so the Haze result remains visible.

The intended shape is:

```kotlin
val hazeState = rememberHazeState()
val navigationSuiteType =
  NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())

Box(Modifier.fillMaxSize()) {
  NavDisplay(
    modifier = Modifier
      .fillMaxSize()
      .hazeSource(hazeState),
    // back stack, entries, transitions, and scene decorators that affect scene content
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

## Layout and behavior

Using `NavigationSuiteScaffoldLayout` around `NavDisplay` would measure the content beside or above
the navigation component. That is useful for opaque navigation, but leaves no scene pixels behind a
translucent bar or rail to sample. The overlay arrangement keeps `NavDisplay` edge-to-edge while
reusing Material's adaptive positioning for the navigation component.

The overlay layer does not itself consume pointer input outside the navigation component. The
navigation component remains interactive, while the underlying `NavDisplay` owns the rest of the
screen.

Persistent navigation chrome is no longer authored by a scene decorator. Decorators may still
alter scene content and layout, but an effect that samples the complete `NavDisplay` transition must
remain outside that subtree. Applications should apply any content padding needed to keep controls
clear of the overlaid bar or rail while allowing decorative scene content to draw edge-to-edge.

This solution uses the existing Compose Multiplatform artifact
`org.jetbrains.compose.material3:material3-adaptive-navigation-suite` and requires no Haze runtime
change. The regression test uses version `1.9.0`, matching this repository's Material 3 dependency.

## Regression coverage

Replace the ignored issue #999 comparison with a shared fixture and two target-appropriate tests:

- Render one `NavDisplay` with one Haze source and two effect-backed sibling bars over a scene whose
  horizontal pattern repeats in each half.
- Position the control directly in its half and the subject through a sibling
  `NavigationSuiteScaffoldLayout` with `content = {}` in the other half.
- On JVM, pause a slide-plus-fade transition at 500 ms and compare the two bar interiors from one
  capture.
- On Android host, use the module's native-graphics Roborazzi harness to compare the same sibling
  layout over a static scene. Compose's direct node capture cannot force a redraw against the frozen
  Android host test clock, so the deterministic transition assertion remains on JVM.
- Require identical dimensions and a small mean-absolute-difference bound: `0.02` for the frozen JVM
  transition midpoint and `0.01` for the static Android native-renderer smoke. The original broken
  architecture measured about `0.077`, well outside both thresholds.

Using one source and one capture per assertion is essential. Two independent `NavDisplay` instances
may reach slightly different transition progress even when they share the same test clock, making a
side-by-side source comparison an invalid oracle. Exact cross-position pixels are also not expected
because blur sampling depends on absolute layer edges.

This proves the documented adaptive layout preserves the safe sibling draw boundary and samples the
same completed transition composite. The previous scene-decorator arrangement remains unsupported
and is removed from the active fixture.

Run the focused test on the JVM and Android host targets, then the complete screenshot-test and
formatting tasks:

- `./gradlew :haze-screenshot-tests:jvmTest`
- `./gradlew :haze-screenshot-tests:testAndroidHostTest`
- `./gradlew :haze-screenshot-tests:test :haze-screenshot-tests:spotlessCheck`

## Documentation and release notes

Add a Navigation3 section to `docs/recipes.md` that explains:

- why descendant effects cannot sample an in-place ancestor source;
- why a source on each scene misses the outer `AnimatedContent` transition;
- the `NavDisplay` source and sibling adaptive navigation overlay;
- why the scaffold layout's content slot is intentionally empty;
- transparent Material navigation container colors; and
- content-padding and edge-to-edge considerations.

Add an Unreleased `Added` changelog entry referencing issue #999. This is a supported integration
recipe and regression fixture, not a weakening of Haze's recursive-draw protection.

## Acceptance criteria

- The issue #999 midpoint regression passes on JVM and the shared layout smoke test passes on
  Android host.
- Adaptive sibling chrome stays within the platform-specific pixel bounds relative to the direct
  sibling control.
- The navigation component remains outside the `NavDisplay` source subtree.
- Navigation bar and rail positioning can still be selected through `NavigationSuiteType`.
- Existing Haze recursive ancestor protection is unchanged.
- The recipe and changelog describe the supported sibling layout and its edge-to-edge constraints.
