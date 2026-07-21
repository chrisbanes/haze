// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(
  ExperimentalHazeApi::class,
  ExperimentalTestApi::class,
)

package dev.chrisbanes.haze

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.test.ContextTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import kotlin.math.roundToInt

abstract class NavDisplaySourceScreenshotTestBase : ContextTest() {

  protected fun runNavigationSuiteSiblingMidTransitionTest() = runComposeUiTest {
    lateinit var backStack: SnapshotStateList<String>

    setContent {
      backStack = remember { mutableStateListOf(FIRST_SCENE) }
      ScreenshotTheme {
        NavigationSample(
          backStack = backStack,
          modifier = Modifier
            .fillMaxSize()
            .testTag(NAVIGATION_SAMPLE_TAG),
        )
      }
    }

    mainClock.autoAdvance = false
    runOnIdle { backStack += SECOND_SCENE }
    waitForIdle()
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeBy(500)
    waitForIdle()

    val root = onNodeWithTag(NAVIGATION_SAMPLE_TAG).captureToImage().toPixelMap().snapshot()
    val controlBounds = onNodeWithTag(DIRECT_NAVIGATION_TAG).fetchSemanticsNode().boundsInRoot
    val subjectBounds = onNodeWithTag(ADAPTIVE_NAVIGATION_TAG).fetchSemanticsNode().boundsInRoot
    assertNavigationSuiteSiblingCapture(root, controlBounds, subjectBounds)
  }
}

@Composable
internal fun NavigationSample(
  backStack: List<String>,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()

  Box(modifier.fillMaxSize()) {
    NavDisplay(
      backStack = backStack,
      modifier = Modifier
        .fillMaxSize()
        .hazeSource(hazeState),
      onBack = {},
      transitionSpec = {
        (slideInHorizontally(tween(1000)) + fadeIn(tween(1000))) togetherWith
          (slideOutHorizontally(tween(1000)) + fadeOut(tween(1000)))
      },
      entryProvider = { sceneKey ->
        NavEntry(sceneKey) {
          TestScene(sceneKey)
        }
      },
    )
    Row(Modifier.fillMaxSize()) {
      Box(Modifier.weight(1f).fillMaxHeight()) {
        TestNavigationSuite(
          hazeState = hazeState,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .testTag(DIRECT_NAVIGATION_TAG),
        )
      }
      Box(Modifier.weight(1f).fillMaxHeight()) {
        NavigationSuiteScaffoldLayout(
          navigationSuiteType = NavigationSuiteType.NavigationBar,
          navigationSuite = {
            TestNavigationSuite(
              hazeState = hazeState,
              modifier = Modifier.testTag(ADAPTIVE_NAVIGATION_TAG),
            )
          },
          content = {},
        )
      }
    }
  }
}

@Composable
private fun TestScene(sceneKey: String) {
  Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize()) {
      val colors = if (sceneKey == FIRST_SCENE) FIRST_SCENE_COLORS else SECOND_SCENE_COLORS
      colors.forEach { color ->
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(color),
        )
      }
    }
    Text(
      text = sceneKey,
      color = Color.White,
      style = MaterialTheme.typography.displayMedium,
      modifier = Modifier.align(Alignment.Center),
    )
  }
}

@Composable
private fun TestNavigationSuite(
  hazeState: HazeState,
  modifier: Modifier = Modifier,
) {
  NavigationSuite(
    navigationSuiteType = NavigationSuiteType.NavigationBar,
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
    modifier = modifier
      .fillMaxWidth()
      .height(220.dp)
      .hazeEffect(hazeState) {
        blurEffect {
          blurRadius = 32.dp
          colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.18f)))
        }
      },
  ) {
    Text(
      text = "Navigation",
      color = Color.White,
      style = MaterialTheme.typography.headlineMedium,
    )
  }
}

internal fun assertNavigationSuiteSiblingCapture(
  root: PixelSnapshot,
  controlBounds: Rect,
  subjectBounds: Rect,
  pixelTolerance: Float = MIDPOINT_PIXEL_TOLERANCE,
) {
  assertThat(controlBounds.width.roundToInt()).isEqualTo(subjectBounds.width.roundToInt())
  assertThat(controlBounds.height.roundToInt()).isEqualTo(subjectBounds.height.roundToInt())
  val control = root.crop(controlBounds.roundToIntRect())
  val subject = root.crop(subjectBounds.roundToIntRect())
  // Exclude the two effects' opposite outer source edges from the repeated interior comparison.
  val edgeInset = control.width / 4
  val comparisonBounds = IntRect(edgeInset, 0, control.width - edgeInset, control.height)
  assertThat(
    control.crop(comparisonBounds).meanAbsoluteDifference(subject.crop(comparisonBounds)),
  ).isLessThanOrEqualTo(pixelTolerance)
}

internal const val FIRST_SCENE = "First"
private const val SECOND_SCENE = "Second"
internal const val NAVIGATION_SAMPLE_TAG = "navigation-sample"
internal const val DIRECT_NAVIGATION_TAG = "direct-navigation"
internal const val ADAPTIVE_NAVIGATION_TAG = "adaptive-navigation"
private const val MIDPOINT_PIXEL_TOLERANCE = 0.02f

private val FIRST_SCENE_COLORS = listOf(
  Color(0xFFE53935),
  Color(0xFF1E88E5),
  Color(0xFFFDD835),
  Color(0xFFE53935),
  Color(0xFF1E88E5),
  Color(0xFFFDD835),
)

private val SECOND_SCENE_COLORS = listOf(
  Color(0xFF00ACC1),
  Color(0xFFD81B60),
  Color(0xFF3949AB),
  Color(0xFF00ACC1),
  Color(0xFFD81B60),
  Color(0xFF3949AB),
)
