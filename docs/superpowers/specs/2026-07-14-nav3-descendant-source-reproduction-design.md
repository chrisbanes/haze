# Nav3 Descendant Haze Source Reproduction Design

## Context

Issue [#999](https://github.com/chrisbanes/haze/issues/999) reports that a navigation bar
composed by a Navigation3 scene decorator does not render a Haze effect when `NavDisplay` is the
matching `hazeSource`. The latest report identifies the navigation bar's `hazeEffect` as a
descendant of that source and uses an external overlay as a workaround.

The report is distinct from #983. That issue covered retaining the last effect output while source
areas are temporarily absent. In #999, the effect's nearest same-state source is its ancestor.
`HazeEffectNode` deliberately filters that source unless lower-z areas exist, and drawing an area
while its source layer is being recorded is rejected to prevent recursive drawing. The immediate
next step is to capture this behavior in deterministic tests before choosing a fix or declaring the
hierarchy unsupported.

## Goal

Produce screenshot evidence that answers two questions in order:

1. Does a same-state `hazeEffect` below a `hazeSource` reproduce the transparent output without
   Navigation3?
2. Does a real `NavDisplay` transition with a scene-decorator navigation bar fail in the same way,
   or does shared-transition overlay behavior introduce a second failure?

The reproduction phase ends with passing controls and intentionally failing subject screenshots.

## Non-Goals

- Change Haze source capture or area filtering.
- Add a Navigation3 integration API to a published Haze module.
- Copy the complete responsive-navigation recipe or add Material Adaptive/window-size behavior.
- Cover Liquid Glass or every Android SDK. The suspected behavior is common source selection, and
  one Android blur backend is sufficient for the first proof.
- Post a resolution to issue #999 before the tests identify the failing boundary.

## Test Platform

Add Android host screenshot tests on SDK 35 in `haze-screenshot-tests`. Use the existing Roborazzi
configuration, Pixel 5 qualifiers, comparison thresholds, `ScreenshotTest` rule, and
`BlurVisualEffect`. Keep reproduction fixtures in `androidHostTest` so Navigation3 does not enter
common source sets or published artifacts.

Add test-only version-catalog aliases for:

- `androidx.navigation3:navigation3-runtime:1.2.0-alpha02`
- `androidx.navigation3:navigation3-ui:1.2.0-alpha02`

No screenshot-harness API change is expected. Android host tests can use the existing
`composeTestRule.mainClock` directly.

## Static Control Pair

Create an Android screenshot test with a deterministic high-contrast background and bottom effect
surface. Both variants use the same dimensions, colors, blur radius, tint, and `HazeState`.

### Control

Compose the source and bottom effect surface as siblings. The control must render a clearly visible
blur and pass against a newly recorded golden.

```kotlin
Box {
  Background(Modifier.hazeSource(state))
  BottomBar(Modifier.hazeEffect(state))
}
```

### Subject

Move the same bottom effect surface below the matching source in the modifier-node hierarchy. Do
not set `canDrawArea`; exercise the default ancestor filtering. The desired image is the control
image, so seed the subject golden by copying the inspected control golden to the subject output
name.

```kotlin
Box(Modifier.hazeSource(state)) {
  Background()
  BottomBar(Modifier.hazeEffect(state))
}
```

The subject must fail comparison on current `main`. This establishes whether the core hierarchy is
sufficient to reproduce the visible symptom independently of Navigation3.

## Navigation3 Control Pair

Create a separate Android host screenshot test using actual `NavDisplay` and
`SharedTransitionLayout`. Define only the minimal test-local types needed:

- Two stable navigation keys.
- Two full-size, deterministic, high-contrast scenes.
- A bottom-bar `SceneDecoratorStrategy` that composes scene content and one movable/shared bottom
  bar inside each decorated scene, preserving the reported ancestor relationship without copying
  the recipe's responsive geometry.
- A 1,000 ms tween destination transition.

The bottom bar overlays scene content so its blur has unambiguous source pixels. The decorator uses
the same shared-transition behavior in both variants; only source placement changes.

### Control

Mark each scene's full-size content as a source for the shared `HazeState`. During the transition,
the incoming and outgoing scenes therefore provide multiple source areas while the decorator's
bottom-bar effect remains their sibling.

Record and inspect this control before creating the subject golden. If the control does not render
the intended blur, stop: source transition or overlay behavior is independently broken and must be
isolated before testing the ancestor topology.

### Subject

Remove the per-scene sources and place the source on `NavDisplay`, making the decorator's bottom-bar
effect a descendant of the matching source. Keep all visual content and animation state identical
to the control. Seed the subject golden from the inspected control image.

The subject must fail comparison on current `main` if the ancestor topology is the relevant Nav3
failure.

## Deterministic Transition Capture

Use this sequence for both Nav3 variants:

1. Compose the initial scene with automatic clock advancement enabled.
2. Wait for idle so source layers and retained output are initialized.
3. Disable automatic clock advancement.
4. Mutate the back stack to start navigation to the second scene.
5. Advance the main clock by 500 ms to the midpoint of the 1,000 ms transition.
6. Wait for pending work without advancing the animation to completion.
7. Capture the root once.

The captured frame must contain both incoming and outgoing scenes. Do not use gestures, wall-clock
delays, remote content, or an animation that is allowed to settle before capture.

## Golden Workflow

For each pair:

1. Implement and run the control only.
2. Record its golden with the repository's Roborazzi recording task.
3. Inspect the image and confirm that the bottom surface visibly samples the high-contrast source.
4. Verify the control in comparison mode.
5. Copy the control golden to the subject test's expected output path.
6. Run the subject in comparison mode and retain the generated actual/diff artifacts.

Never record the subject directly. Doing so would bless the transparent output and turn the
reproduction into a characterization of broken behavior rather than a red visual contract.

## Interpretation

| Static subject | Nav3 control | Nav3 subject | Conclusion |
| --- | --- | --- | --- |
| Fails | Passes | Fails similarly | Ancestor source/effect topology is sufficient to explain #999. |
| Fails | Passes | Fails differently | Ancestor topology exists, with an additional Nav3 overlay/position/invalidation factor. |
| Fails | Fails | Not seeded | Isolate multiple-source or shared-transition behavior before continuing. |
| Passes | Any | Any | Reject the ancestor-filtering hypothesis and inspect the reporter's missing setup details. |

After collecting evidence, choose a separate resolution design:

- Document and test a supported source/effect topology for Navigation3.
- Design a new capture model capable of safely handling descendant effects.
- Investigate a second shared-transition overlay defect if the control exposes one.

## Expected Files

- `gradle/libs.versions.toml`
- `haze-screenshot-tests/build.gradle.kts`
- `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/AncestorSourceAndroidScreenshotTest.kt`
- `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/NavDisplaySourceAndroidScreenshotTest.kt`
- Two static control/subject Android goldens.
- Two Navigation3 control/subject Android goldens, created only after the Nav3 control passes.

## Verification

Run Spotless after adding the tests. Verify the controls and unaffected Android host screenshot
tests normally. Run each subject independently and confirm that it fails for the expected visual
diff rather than compilation, setup, timeout, or unrelated rendering errors. Save the Roborazzi
actual and diff paths in the implementation notes so the next resolution discussion is based on
the observed pixels.

The reproduction phase is complete when both controls pass and at least one subject produces the
expected red screenshot diff. A production fix is explicitly deferred to the next design cycle.
