# NavDisplay Haze Overlay Design

## Goal

Allow effect-backed navigation chrome authored inside a Navigation3 scene decorator to sample the
fully animated `NavDisplay` composite without recursively sampling a `hazeSource` ancestor.

The solution must preserve scene-decorator layout, work across the existing Compose Multiplatform
targets, and avoid a new public Haze API or Navigation3 integration artifact.

## Root cause

`HazeSourceNode` records its descendant content into a graphics layer. A descendant
`HazeEffectNode` using the same `HazeState` cannot safely sample that layer while it is being
recorded, so Haze excludes the nearest ancestor source by default. The effect therefore has no
eligible source and appears transparent.

The reverted Navigation3 integration moved `hazeSource` to each decorated scene. That avoided the
recursive ancestor relationship, but each scene was recorded before `NavDisplay` applied its outer
`AnimatedContent` alpha and transform. During a transition the effect replayed independent scene
layers instead of sampling the visible animated composite.

The required draw boundary is therefore:

1. Record the completed `NavDisplay` scene composite.
2. Draw effect-backed navigation chrome after that recording finishes.
3. Let the chrome sample only the completed `NavDisplay` source.

## Design

Use Compose's existing `SharedTransitionScope.renderInSharedTransitionScopeOverlay` API together
with Haze's existing keyed source filtering.

- Apply one `hazeSource` to `NavDisplay` and give it a private stable key.
- Keep the navigation bar or rail inside its `SceneDecoratorStrategy` so the decorator continues to
  own responsive layout and scene identity.
- Promote the effect-backed chrome into the surrounding `SharedTransitionLayout` overlay with
  `renderInOverlay = { true }`.
- Apply `hazeEffect` after the overlay modifier and set `canDrawArea` to accept only the keyed
  `NavDisplay` source.

The overlay must remain enabled continuously. If the chrome returned to in-place drawing outside a
transition, it would again be drawn inside the source recording and recreate the recursive sampling
relationship.

The intended modifier shape is:

```kotlin
val hazeState = rememberHazeState()
val navDisplaySourceKey = remember { Any() }

SharedTransitionLayout {
  val sharedTransitionScope = this
  val navigationChromeModifier = with(sharedTransitionScope) {
    Modifier
      .renderInSharedTransitionScopeOverlay(renderInOverlay = { true })
      .hazeEffect(hazeState) {
        canDrawArea = { area -> area.key == navDisplaySourceKey }
        blurEffect {
          blurRadius = 20.dp
        }
      }
  }

  val navigationDecorator = rememberResponsiveNavigationSceneDecoratorStrategy(
    navBar = {
      NavigationBar(modifier = navigationChromeModifier)
    },
    navRail = { NavigationRail(modifier = navigationChromeModifier) },
    sharedTransitionScope = sharedTransitionScope,
  )

  NavDisplay(
    modifier = Modifier.hazeSource(
      state = hazeState,
      key = navDisplaySourceKey,
    ),
    sceneDecoratorStrategies = listOf(navigationDecorator),
  )
}
```

The responsive decorator retains the bar or rail in its scene layout. The example elides unrelated
Navigation3 entry and scene configuration to focus on the shared key, modifier ordering, and draw
ownership.

## Draw flow and safety

`NavDisplay` records its animated scenes into the keyed Haze source. The always-on overlay modifier
defers navigation chrome drawing to the `SharedTransitionLayout` overlay, after the source recording
has completed. The effect's `canDrawArea` filter deliberately opts into the otherwise-excluded
ancestor source at this safe draw point.

The filter matches the private key rather than returning `true`, so unrelated areas in the same
`HazeState` cannot contribute pixels. Existing Haze ancestor filtering remains unchanged for every
other effect.

Overlay promotion intentionally removes the chrome from parent clipping and parent layer transforms
such as alpha and scale. A decorator that requires clipping or enter/exit animation must apply those
explicitly to the promoted content. This is the desired behavior for persistent navigation chrome:
the scene transition affects the sampled content, not the chrome that samples it.

The recipe requires a surrounding `SharedTransitionLayout` and opt-in to the experimental Compose
shared-transition and Haze area-filtering APIs. A missing or mismatched source key produces no
eligible background rather than recursive drawing.

## Regression coverage

Replace the ignored issue #999 common test with an active deterministic pixel comparison:

- The control renders a keyed `hazeSource` on `NavDisplay` and equivalent navigation chrome as an
  external sibling.
- The subject keeps the chrome inside a scene decorator, promotes it into the always-on shared
  transition overlay, and selects the keyed source with `canDrawArea`.
- Both cases use the same scene colors, dimensions, blur configuration, and slide-plus-fade
  transition.
- Pause the controlled animation at its midpoint and require the subject to match the control
  exactly. This fails if Haze samples independent per-scene layers or if the decorated effect cannot
  sample the completed composite.

Run the common test on the JVM and Android host targets, then run the complete focused screenshot
test and formatting tasks:

- `./gradlew :haze-screenshot-tests:jvmTest`
- `./gradlew :haze-screenshot-tests:testAndroidHostTest`
- `./gradlew :haze-screenshot-tests:test :haze-screenshot-tests:spotlessCheck`

If the red test shows that overlay promotion does not provide the required safe draw order, stop and
return to design. Do not weaken Haze's recursive-draw protection or introduce a core renderer change
without a new approved design.

## Documentation and release notes

Add a Navigation3 section to `docs/recipes.md` that explains:

- why a source on each scene misses the outer `AnimatedContent` transition;
- why descendant effects cannot sample an in-place ancestor source;
- the keyed `NavDisplay` source;
- always-on overlay promotion and required modifier order;
- keyed `canDrawArea` filtering; and
- the loss of implicit parent clipping and alpha/scale transforms in the overlay.

Add an Unreleased `Fixed` changelog entry referencing issue #999. No public API file, module,
dependency, or publication configuration changes are expected.

## Acceptance criteria

- The issue #999 regression test is enabled and passes on JVM and Android host tests.
- At the transition midpoint, decorated overlay chrome matches the external-sibling control exactly.
- Navigation chrome remains composed and laid out by the scene decorator.
- Only the keyed `NavDisplay` source contributes to the effect.
- Existing Haze recursive ancestor protection is unchanged.
- The recipe and changelog describe the supported solution and its overlay constraints.
