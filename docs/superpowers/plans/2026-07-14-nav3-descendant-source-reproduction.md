# Nav3 Descendant Haze Source Reproduction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add deterministic red screenshot tests that prove whether a descendant `hazeEffect` can sample a matching ancestor `hazeSource`, first in a static layout and then during a Navigation3 scene transition.

**Architecture:** Add two Android host screenshot test files. Each file contains a supported control and an otherwise-identical ancestor-source subject; record only the control and copy its inspected golden to the subject path so current broken output fails comparison. Keep Navigation3 dependencies and fixtures entirely in `androidHostTest`, with no production or screenshot-harness changes.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose, Haze Blur, Navigation3 1.2.0-alpha02, Robolectric SDK 35, Roborazzi.

---

The approved design is in
`docs/superpowers/specs/2026-07-14-nav3-descendant-source-reproduction-design.md`.

Commit steps below are approval-gated. Run them only if the user explicitly authorizes commits for
the execution session.

## File Structure

- `gradle/libs.versions.toml`: declare the test-only Navigation3 version and runtime/UI aliases.
- `haze-screenshot-tests/build.gradle.kts`: expose Navigation3 only to `androidHostTest`.
- `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/AncestorSourceAndroidScreenshotTest.kt`: static control and ancestor-source subject.
- `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/NavDisplaySourceAndroidScreenshotTest.kt`: real `NavDisplay`, minimal shared bottom-bar scene decorator, transition control, and ancestor-source subject.
- `haze-screenshot-tests/screenshots/android/AncestorSourceAndroidScreenshotTest.*.png`: static control and subject goldens.
- `haze-screenshot-tests/screenshots/android/NavDisplaySourceAndroidScreenshotTest.*.png`: Navigation3 control and subject goldens.

### Task 1: Add And Record The Static Control

**Files:**
- Create: `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/AncestorSourceAndroidScreenshotTest.kt`
- Create: `haze-screenshot-tests/screenshots/android/AncestorSourceAndroidScreenshotTest.blur_siblingEffect_rendersSource.png`

- [ ] **Step 1: Create the static control test and reusable fixture**

Create `AncestorSourceAndroidScreenshotTest.kt` with this content:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class AncestorSourceAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun blur_siblingEffect_rendersSource() = captureSourceLayout(sourceContainsEffect = false)

  private fun captureSourceLayout(sourceContainsEffect: Boolean) = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        AncestorSourceSample(sourceContainsEffect = sourceContainsEffect)
      }
    }

    waitForIdle()
    captureRoot()
  }
}

@Composable
private fun AncestorSourceSample(sourceContainsEffect: Boolean) {
  val hazeState = rememberHazeState()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black),
  ) {
    if (sourceContainsEffect) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .hazeSource(hazeState),
      ) {
        SourcePattern(Modifier.fillMaxSize())
        TestBottomBar(
          hazeState = hazeState,
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    } else {
      SourcePattern(
        modifier = Modifier
          .fillMaxSize()
          .hazeSource(hazeState),
      )
      TestBottomBar(
        hazeState = hazeState,
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }
}

@Composable
private fun SourcePattern(modifier: Modifier = Modifier) {
  Row(modifier = modifier) {
    SourceColors.forEach { color ->
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .weight(1f)
          .background(color),
      )
    }
  }
}

@Composable
private fun TestBottomBar(
  hazeState: HazeState,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(220.dp)
      .hazeEffect(hazeState) {
        blurEffect {
          blurRadius = 32.dp
          colorEffects = listOf(
            HazeColorEffect.tint(Color.White.copy(alpha = 0.18f)),
          )
        }
      },
  ) {
    Text(
      text = "Navigation",
      color = Color.White,
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.align(Alignment.Center),
    )
  }
}

private val SourceColors = listOf(
  Color(0xFFE53935),
  Color(0xFF1E88E5),
  Color(0xFFFDD835),
  Color(0xFF43A047),
  Color(0xFF8E24AA),
  Color(0xFFFF7043),
)
```

- [ ] **Step 2: Record only the control golden**

Run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest \
  --tests 'dev.chrisbanes.haze.AncestorSourceAndroidScreenshotTest.blur_siblingEffect_rendersSource' \
  -Proborazzi.test.record=true
```

Expected: PASS and create
`haze-screenshot-tests/screenshots/android/AncestorSourceAndroidScreenshotTest.blur_siblingEffect_rendersSource.png`.

- [ ] **Step 3: Inspect the control image**

Open the generated PNG and verify that the lower 220 dp visibly softens the hard vertical color
boundaries while the upper source remains sharp. If the lower boundaries are not visibly blurred,
stop and report that the control is invalid; do not create a subject golden from it.

- [ ] **Step 4: Verify the control in comparison mode**

Run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest \
  --tests 'dev.chrisbanes.haze.AncestorSourceAndroidScreenshotTest.blur_siblingEffect_rendersSource' \
  -Proborazzi.test.verify=true
```

Expected: PASS with no Roborazzi image diff.

- [ ] **Step 5: Commit the control if commits were explicitly authorized**

```bash
git status --short
git diff
git log --oneline -10
git add \
  docs/superpowers/specs/2026-07-14-nav3-descendant-source-reproduction-design.md \
  docs/superpowers/plans/2026-07-14-nav3-descendant-source-reproduction.md \
  haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/AncestorSourceAndroidScreenshotTest.kt \
  haze-screenshot-tests/screenshots/android/AncestorSourceAndroidScreenshotTest.blur_siblingEffect_rendersSource.png
git commit -m "Add ancestor haze source screenshot control"
```

### Task 2: Add The Static Red Subject

**Files:**
- Modify: `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/AncestorSourceAndroidScreenshotTest.kt`
- Create: `haze-screenshot-tests/screenshots/android/AncestorSourceAndroidScreenshotTest.blur_descendantEffect_matchesSibling.png`

- [ ] **Step 1: Add the descendant subject test**

Insert this method immediately after `blur_siblingEffect_rendersSource`:

```kotlin
  @Test
  fun blur_descendantEffect_matchesSibling() = captureSourceLayout(sourceContainsEffect = true)
```

- [ ] **Step 2: Seed the subject golden from the inspected control**

Run:

```bash
cp \
  haze-screenshot-tests/screenshots/android/AncestorSourceAndroidScreenshotTest.blur_siblingEffect_rendersSource.png \
  haze-screenshot-tests/screenshots/android/AncestorSourceAndroidScreenshotTest.blur_descendantEffect_matchesSibling.png
cmp \
  haze-screenshot-tests/screenshots/android/AncestorSourceAndroidScreenshotTest.blur_siblingEffect_rendersSource.png \
  haze-screenshot-tests/screenshots/android/AncestorSourceAndroidScreenshotTest.blur_descendantEffect_matchesSibling.png
```

Expected: the two baseline PNG files are byte-for-byte identical.

- [ ] **Step 3: Run the subject to verify the reproduction is red**

Run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest \
  --tests 'dev.chrisbanes.haze.AncestorSourceAndroidScreenshotTest.blur_descendantEffect_matchesSibling' \
  -Proborazzi.test.verify=true
```

Expected: FAIL with a Roborazzi comparison error above the repository's 0.8% unmatched-pixel
threshold. The actual image should show sharp/unblurred source boundaries behind the bottom bar,
not a compilation, setup, timeout, or unrelated rendering failure.

- [ ] **Step 4: Save the static diff path**

Record the actual and comparison image paths emitted by the failing test and Roborazzi report for
the final handoff. Do not run a Roborazzi record task for this subject.

- [ ] **Step 5: Commit the red subject if commits were explicitly authorized**

```bash
git status --short
git diff
git log --oneline -10
git add \
  haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/AncestorSourceAndroidScreenshotTest.kt \
  haze-screenshot-tests/screenshots/android/AncestorSourceAndroidScreenshotTest.blur_descendantEffect_matchesSibling.png
git commit -m "Reproduce descendant haze source rendering"
```

### Task 3: Add And Record The Navigation3 Control

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `haze-screenshot-tests/build.gradle.kts`
- Create: `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/NavDisplaySourceAndroidScreenshotTest.kt`
- Create: `haze-screenshot-tests/screenshots/android/NavDisplaySourceAndroidScreenshotTest.blur_sceneSources_midTransition.png`

- [ ] **Step 1: Add Navigation3 test dependency aliases**

Add this version after `navigation-compose` in `[versions]`:

```toml
navigation3 = "1.2.0-alpha02"
```

Add these libraries after `androidx-navigation-compose` in `[libraries]`:

```toml
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
```

- [ ] **Step 2: Expose Navigation3 only to Android host tests**

Add this source-set block after `commonTest` and before `jvmTest` in
`haze-screenshot-tests/build.gradle.kts`:

```kotlin
    androidHostTest {
      dependencies {
        implementation(libs.androidx.navigation3.runtime)
        implementation(libs.androidx.navigation3.ui)
      }
    }
```

- [ ] **Step 3: Create the deterministic Navigation3 control**

Create `NavDisplaySourceAndroidScreenshotTest.kt` with this content:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, ExperimentalSharedTransitionApi::class)

package dev.chrisbanes.haze

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneDecoratorStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class NavDisplaySourceAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun blur_sceneSources_midTransition() = captureMidTransition(
    sourcePlacement = NavigationSourcePlacement.SceneContent,
  )

  private fun captureMidTransition(sourcePlacement: NavigationSourcePlacement) =
    runScreenshotTest {
      lateinit var backStack: SnapshotStateList<String>

      setContent {
        backStack = remember { mutableStateListOf(FIRST_SCENE) }
        ScreenshotTheme {
          NavDisplaySourceSample(
            backStack = backStack,
            sourcePlacement = sourcePlacement,
          )
        }
      }

      waitForIdle()
      composeTestRule.mainClock.autoAdvance = false
      composeTestRule.runOnIdle { backStack += SECOND_SCENE }
      composeTestRule.mainClock.advanceTimeBy(500L)
      composeTestRule.waitForIdle()
      captureRoot()
    }
}

private enum class NavigationSourcePlacement {
  SceneContent,
  NavDisplay,
}

@Composable
private fun NavDisplaySourceSample(
  backStack: SnapshotStateList<String>,
  sourcePlacement: NavigationSourcePlacement,
) {
  val hazeState = rememberHazeState()

  SharedTransitionLayout {
    val decorator = rememberNavigationBarSceneDecoratorStrategy<String>(
      navigationBar = { TestNavigationBar(hazeState) },
      sharedTransitionScope = this,
    )

    NavDisplay(
      backStack = backStack,
      modifier = Modifier
        .fillMaxSize()
        .then(
          if (sourcePlacement == NavigationSourcePlacement.NavDisplay) {
            Modifier.hazeSource(hazeState)
          } else {
            Modifier
          },
        ),
      onBack = { backStack.removeLastOrNull() },
      sceneDecoratorStrategies = listOf(decorator),
      sharedTransitionScope = this,
      transitionSpec = {
        (
          slideInHorizontally(animationSpec = tween(1_000)) { it } +
            fadeIn(animationSpec = tween(1_000))
          ) togetherWith (
          slideOutHorizontally(animationSpec = tween(1_000)) { -it } +
            fadeOut(animationSpec = tween(1_000))
          )
      },
      entryProvider = { key ->
        NavEntry(key) { entryKey ->
          TestScene(
            key = entryKey,
            modifier = Modifier
              .fillMaxSize()
              .then(
                if (sourcePlacement == NavigationSourcePlacement.SceneContent) {
                  Modifier.hazeSource(hazeState)
                } else {
                  Modifier
                },
              ),
          )
        }
      },
    )
  }
}

@Composable
private fun TestScene(
  key: String,
  modifier: Modifier = Modifier,
) {
  val colors = if (key == FIRST_SCENE) FirstSceneColors else SecondSceneColors

  Box(modifier = modifier) {
    Row(Modifier.fillMaxSize()) {
      colors.forEach { color ->
        Box(
          modifier = Modifier
            .fillMaxHeight()
            .weight(1f)
            .background(color),
        )
      }
    }
    Text(
      text = key,
      color = Color.White,
      style = MaterialTheme.typography.displayMedium,
      modifier = Modifier.align(Alignment.Center),
    )
  }
}

@Composable
private fun TestNavigationBar(
  hazeState: HazeState,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(220.dp)
      .hazeEffect(hazeState) {
        blurEffect {
          blurRadius = 32.dp
          colorEffects = listOf(
            HazeColorEffect.tint(Color.White.copy(alpha = 0.18f)),
          )
        }
      },
  ) {
    Text(
      text = "Navigation",
      color = Color.White,
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.align(Alignment.Center),
    )
  }
}

private class NavigationBarScene<T : Any>(
  private val scene: Scene<T>,
  private val sharedTransitionScope: SharedTransitionScope,
  private val navigationBarContent: @Composable () -> Unit,
) : Scene<T> by scene {
  override val key: Any = scene::class to scene.key

  override val content: @Composable () -> Unit = {
    val animatedContentScope = LocalNavAnimatedContentScope.current
    val isMovableContentCaller =
      animatedContentScope.transition.targetState == EnterExitState.Visible

    with(sharedTransitionScope) {
      Box(Modifier.fillMaxSize()) {
        scene.content()
        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .sharedElement(
              rememberSharedContentState(NAVIGATION_BAR_SHARED_KEY),
              animatedContentScope,
            ),
        ) {
          if (isMovableContentCaller) {
            navigationBarContent()
          }
        }
      }
    }
  }
}

private class NavigationBarSceneDecoratorStrategy<T : Any>(
  private val sharedTransitionScope: SharedTransitionScope,
  private val navigationBarContent: @Composable () -> Unit,
) : SceneDecoratorStrategy<T> {
  override fun SceneDecoratorStrategyScope<T>.decorateScene(scene: Scene<T>): Scene<T> {
    return NavigationBarScene(
      scene = scene,
      sharedTransitionScope = sharedTransitionScope,
      navigationBarContent = navigationBarContent,
    )
  }
}

@Composable
private fun <T : Any> rememberNavigationBarSceneDecoratorStrategy(
  navigationBar: @Composable () -> Unit,
  sharedTransitionScope: SharedTransitionScope,
): SceneDecoratorStrategy<T> {
  val currentNavigationBar by rememberUpdatedState(navigationBar)
  val movableNavigationBar = remember { movableContentOf { currentNavigationBar() } }

  return remember(sharedTransitionScope) {
    NavigationBarSceneDecoratorStrategy(
      sharedTransitionScope = sharedTransitionScope,
      navigationBarContent = movableNavigationBar,
    )
  }
}

private const val FIRST_SCENE = "First"
private const val SECOND_SCENE = "Second"
private const val NAVIGATION_BAR_SHARED_KEY = "navigation-bar"

private val FirstSceneColors = listOf(
  Color(0xFFE53935),
  Color(0xFF1E88E5),
  Color(0xFFFDD835),
  Color(0xFF43A047),
  Color(0xFF8E24AA),
  Color(0xFFFF7043),
)

private val SecondSceneColors = listOf(
  Color(0xFF00ACC1),
  Color(0xFFD81B60),
  Color(0xFF3949AB),
  Color(0xFF7CB342),
  Color(0xFFFFB300),
  Color(0xFF546E7A),
)
```

- [ ] **Step 4: Run Spotless before compiling the Navigation3 fixture**

Run:

```bash
./gradlew spotlessApply
```

Expected: PASS; import ordering and multiline transition formatting may change, but behavior should
not.

- [ ] **Step 5: Record only the Navigation3 control**

Run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest \
  --tests 'dev.chrisbanes.haze.NavDisplaySourceAndroidScreenshotTest.blur_sceneSources_midTransition' \
  -Proborazzi.test.record=true
```

Expected: PASS and create
`haze-screenshot-tests/screenshots/android/NavDisplaySourceAndroidScreenshotTest.blur_sceneSources_midTransition.png`.

- [ ] **Step 6: Inspect and verify the Navigation3 control**

Open the generated PNG. Confirm that both scenes are visible at the transition midpoint and that
the navigation bar visibly softens the source boundaries. Then run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest \
  --tests 'dev.chrisbanes.haze.NavDisplaySourceAndroidScreenshotTest.blur_sceneSources_midTransition' \
  -Proborazzi.test.verify=true
```

Expected: PASS with no Roborazzi image diff.

If the control is transparent, stuck on one scene, or otherwise fails, stop this plan before Task 4.
That is separate evidence about multiple sources or shared-transition overlays; do not seed a
subject golden from it.

- [ ] **Step 7: Commit the Navigation3 control if commits were explicitly authorized**

```bash
git status --short
git diff
git log --oneline -10
git add \
  gradle/libs.versions.toml \
  haze-screenshot-tests/build.gradle.kts \
  haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/NavDisplaySourceAndroidScreenshotTest.kt \
  haze-screenshot-tests/screenshots/android/NavDisplaySourceAndroidScreenshotTest.blur_sceneSources_midTransition.png
git commit -m "Add NavDisplay haze source screenshot control"
```

### Task 4: Add The Navigation3 Red Subject

**Files:**
- Modify: `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/NavDisplaySourceAndroidScreenshotTest.kt`
- Create: `haze-screenshot-tests/screenshots/android/NavDisplaySourceAndroidScreenshotTest.blur_navDisplaySource_midTransition_matchesSceneSources.png`

- [ ] **Step 1: Add the `NavDisplay` ancestor-source subject**

Insert this method immediately after `blur_sceneSources_midTransition`:

```kotlin
  @Test
  fun blur_navDisplaySource_midTransition_matchesSceneSources() = captureMidTransition(
    sourcePlacement = NavigationSourcePlacement.NavDisplay,
  )
```

- [ ] **Step 2: Seed the subject golden from the inspected Navigation3 control**

Run:

```bash
cp \
  haze-screenshot-tests/screenshots/android/NavDisplaySourceAndroidScreenshotTest.blur_sceneSources_midTransition.png \
  haze-screenshot-tests/screenshots/android/NavDisplaySourceAndroidScreenshotTest.blur_navDisplaySource_midTransition_matchesSceneSources.png
cmp \
  haze-screenshot-tests/screenshots/android/NavDisplaySourceAndroidScreenshotTest.blur_sceneSources_midTransition.png \
  haze-screenshot-tests/screenshots/android/NavDisplaySourceAndroidScreenshotTest.blur_navDisplaySource_midTransition_matchesSceneSources.png
```

Expected: the two Navigation3 baseline PNG files are byte-for-byte identical.

- [ ] **Step 3: Run the subject to verify the Navigation3 reproduction is red**

Run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest \
  --tests 'dev.chrisbanes.haze.NavDisplaySourceAndroidScreenshotTest.blur_navDisplaySource_midTransition_matchesSceneSources' \
  -Proborazzi.test.verify=true
```

Expected: FAIL with a Roborazzi comparison error above the 0.8% unmatched-pixel threshold. The
actual image should retain the same midpoint scene geometry while showing a transparent/unblurred
navigation bar.

If the diff is materially different from the static subject, preserve both artifacts and classify
the discrepancy as a second Navigation3 overlay, coordinate, or invalidation factor.

- [ ] **Step 4: Save the Navigation3 diff path**

Record the actual and comparison image paths emitted by the failing test and Roborazzi report for
the final handoff. Do not run a Roborazzi record task for this subject.

- [ ] **Step 5: Commit the red subject if commits were explicitly authorized**

```bash
git status --short
git diff
git log --oneline -10
git add \
  haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/NavDisplaySourceAndroidScreenshotTest.kt \
  haze-screenshot-tests/screenshots/android/NavDisplaySourceAndroidScreenshotTest.blur_navDisplaySource_midTransition_matchesSceneSources.png
git commit -m "Reproduce NavDisplay descendant haze effect"
```

### Task 5: Verify Controls, Existing Coverage, And Expected Failures

**Files:**
- Verify only; no production files should change.

- [ ] **Step 1: Apply formatting and inspect the final scope**

Run:

```bash
./gradlew spotlessApply
git status --short
git diff --check
```

Expected: Spotless passes; no production files are changed; any uncommitted paths are limited to
the design/plan, version catalog, screenshot-test build file, two test files, and four PNGs.
`git diff --check` reports no whitespace errors.

- [ ] **Step 2: Re-run both controls**

Run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest \
  --tests 'dev.chrisbanes.haze.AncestorSourceAndroidScreenshotTest.blur_siblingEffect_rendersSource' \
  --tests 'dev.chrisbanes.haze.NavDisplaySourceAndroidScreenshotTest.blur_sceneSources_midTransition' \
  -Proborazzi.test.verify=true
```

Expected: both tests PASS.

- [ ] **Step 3: Verify existing source-transition coverage remains green**

Run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest \
  --tests 'dev.chrisbanes.haze.SourceTransitionAndroidScreenshotTest' \
  -Proborazzi.test.verify=true
```

Expected: PASS on SDK 32 and 35. This confirms the #983 retained-output behavior remains separate
from #999.

- [ ] **Step 4: Re-run each subject independently**

Run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest \
  --tests 'dev.chrisbanes.haze.AncestorSourceAndroidScreenshotTest.blur_descendantEffect_matchesSibling' \
  -Proborazzi.test.verify=true
```

Expected: FAIL for the static visual mismatch.

Run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest \
  --tests 'dev.chrisbanes.haze.NavDisplaySourceAndroidScreenshotTest.blur_navDisplaySource_midTransition_matchesSceneSources' \
  -Proborazzi.test.verify=true
```

Expected: FAIL for the Navigation3 visual mismatch.

- [ ] **Step 5: Report evidence without proposing a fix**

The handoff must include:

- Control commands and PASS results.
- Each subject's unmatched-pixel percentage.
- Paths to each Roborazzi actual/comparison artifact.
- Whether the static and Navigation3 diffs show the same transparent/unblurred bar failure.
- A note that the full screenshot suite is intentionally red until a resolution is selected.

Do not change `HazeSourceNode`, `HazeEffectNode`, retained-output behavior, or issue #999 during this
plan. The evidence determines the next design cycle.
