// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, ExperimentalSharedTransitionApi::class)

package dev.chrisbanes.haze

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
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
  fun blur_sceneSources_midTransition() = captureMidTransition(NavigationSourcePlacement.SceneContent)

  @Test
  fun blur_navDisplaySource_midTransition_matchesSceneSources() = captureMidTransition(
    sourcePlacement = NavigationSourcePlacement.NavDisplay,
  )

  private fun captureMidTransition(sourcePlacement: NavigationSourcePlacement) = runScreenshotTest {
    lateinit var backStack: SnapshotStateList<String>

    setContent {
      backStack = remember { mutableStateListOf(FIRST_SCENE) }
      ScreenshotTheme {
        NavigationSample(backStack, sourcePlacement)
      }
    }

    waitForIdle()
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.runOnIdle { backStack += SECOND_SCENE }
    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeByFrame()
    composeTestRule.mainClock.advanceTimeBy(500)
    composeTestRule.waitForIdle()
    captureRoot()
  }
}

private enum class NavigationSourcePlacement {
  SceneContent,
  NavDisplay,
}

@Composable
private fun NavigationSample(
  backStack: List<String>,
  sourcePlacement: NavigationSourcePlacement,
) {
  val hazeState = remember { HazeState() }

  SharedTransitionLayout {
    val sceneDecoratorStrategy = rememberNavigationBarSceneDecoratorStrategy(
      hazeState = hazeState,
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
      onBack = {},
      sceneDecoratorStrategies = listOf(sceneDecoratorStrategy),
      sharedTransitionScope = this,
      transitionSpec = {
        (slideInHorizontally(tween(1000)) + fadeIn(tween(1000))) togetherWith
          (slideOutHorizontally(tween(1000)) + fadeOut(tween(1000)))
      },
      entryProvider = { sceneKey ->
        NavEntry(sceneKey) {
          TestScene(
            sceneKey = sceneKey,
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
private fun rememberNavigationBarSceneDecoratorStrategy(
  hazeState: HazeState,
  sharedTransitionScope: SharedTransitionScope,
): SceneDecoratorStrategy<String> {
  val navigationBar = remember(hazeState) {
    movableContentOf { modifier: Modifier ->
      TestNavigationBar(hazeState, modifier)
    }
  }

  return remember(sharedTransitionScope, navigationBar) {
    SceneDecoratorStrategy { scene ->
      NavigationBarScene(
        scene = scene,
        sharedTransitionScope = sharedTransitionScope,
        navigationBar = navigationBar,
      )
    }
  }
}

private data class NavigationBarScene<T : Any>(
  private val scene: Scene<T>,
  private val sharedTransitionScope: SharedTransitionScope,
  private val navigationBar: @Composable (Modifier) -> Unit,
) : Scene<T> by scene {
  override val key: Any = scene::class to scene.key

  override val content: @Composable () -> Unit = {
    val animatedContentScope = LocalNavAnimatedContentScope.current
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.BottomCenter,
    ) {
      scene.content()
      with(sharedTransitionScope) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .sharedElement(
              rememberSharedContentState(NAVIGATION_BAR_SHARED_KEY),
              animatedContentScope,
            ),
          contentAlignment = Alignment.BottomCenter,
        ) {
          if (animatedContentScope.transition.targetState == EnterExitState.Visible) {
            navigationBar(Modifier)
          }
        }
      }
    }
  }
}

@Composable
private fun TestScene(
  sceneKey: String,
  modifier: Modifier = Modifier,
) {
  Box(modifier) {
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
          colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.18f)))
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "Navigation",
      color = Color.White,
      style = MaterialTheme.typography.headlineMedium,
    )
  }
}

private const val FIRST_SCENE = "First"
private const val SECOND_SCENE = "Second"
private const val NAVIGATION_BAR_SHARED_KEY = "navigation-bar"

private val FIRST_SCENE_COLORS = listOf(
  Color(0xFFE53935),
  Color(0xFF1E88E5),
  Color(0xFFFDD835),
  Color(0xFF43A047),
  Color(0xFF8E24AA),
  Color(0xFFFF7043),
)

private val SECOND_SCENE_COLORS = listOf(
  Color(0xFF00ACC1),
  Color(0xFFD81B60),
  Color(0xFF3949AB),
  Color(0xFF7CB342),
  Color(0xFFFFB300),
  Color(0xFF546E7A),
)
