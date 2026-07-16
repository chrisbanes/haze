// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(
  ExperimentalHazeApi::class,
  ExperimentalSharedTransitionApi::class,
  ExperimentalTestApi::class,
)

package dev.chrisbanes.haze

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.ui.NavDisplay
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.test.ScreenshotTheme
import kotlin.test.Ignore
import kotlin.test.Test

class NavDisplaySourceScreenshotTest {

  @Test
  @Ignore("https://github.com/chrisbanes/haze/issues/999")
  fun blur_sceneDecorator_midTransition_matchesAnimatedCompositeControl() = runComposeUiTest {
    lateinit var backStack: SnapshotStateList<String>

    setContent {
      backStack = remember { mutableStateListOf(FIRST_SCENE) }
      ScreenshotTheme {
        Row(Modifier.fillMaxSize()) {
          NavigationSample(
            backStack = backStack,
            sourcePlacement = SourcePlacement.AnimatedComposite,
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .testTag(ANIMATED_COMPOSITE_TAG),
          )
          NavigationSample(
            backStack = backStack,
            sourcePlacement = SourcePlacement.SceneDecorator,
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .testTag(SCENE_DECORATOR_TAG),
          )
        }
      }
    }

    mainClock.autoAdvance = false
    runOnIdle { backStack += SECOND_SCENE }
    waitForIdle()
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeBy(500)
    waitForIdle()

    val control = onNodeWithTag(ANIMATED_COMPOSITE_TAG).captureToImage().toPixelMap()
    val subject = onNodeWithTag(SCENE_DECORATOR_TAG).captureToImage().toPixelMap()
    assertThat(control.width).isEqualTo(subject.width)
    assertThat(control.height).isEqualTo(subject.height)
    assertThat(control.snapshot().meanAbsoluteDifference(subject.snapshot())).isEqualTo(0f)
  }
}

private enum class SourcePlacement {
  AnimatedComposite,
  SceneDecorator,
}

@Composable
private fun NavigationSample(
  backStack: List<String>,
  sourcePlacement: SourcePlacement,
  modifier: Modifier = Modifier,
) {
  val hazeState = remember { HazeState() }

  SharedTransitionLayout(modifier) {
    val sharedTransitionScope = this
    Box(Modifier.fillMaxSize()) {
      NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize().then(
          if (sourcePlacement == SourcePlacement.AnimatedComposite) {
            Modifier.hazeSource(hazeState)
          } else {
            Modifier
          },
        ),
        onBack = {},
        sceneDecoratorStrategies = if (sourcePlacement == SourcePlacement.SceneDecorator) {
          listOf(rememberHazeSourceSceneDecoratorStrategy(hazeState))
        } else {
          emptyList()
        },
        sharedTransitionScope = sharedTransitionScope,
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
      TestNavigationBar(
        hazeState = hazeState,
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }
}

@Composable
private fun rememberHazeSourceSceneDecoratorStrategy(
  hazeState: HazeState,
): SceneDecoratorStrategy<String> = remember(hazeState) {
  SceneDecoratorStrategy { scene -> HazeSourceScene(scene, hazeState) }
}

private data class HazeSourceScene<T : Any>(
  private val scene: Scene<T>,
  private val hazeState: HazeState,
) : Scene<T> by scene {
  override val key: Any = scene::class to scene.key

  override val content: @Composable () -> Unit = {
    Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
      scene.content()
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
private const val ANIMATED_COMPOSITE_TAG = "animated-composite"
private const val SCENE_DECORATOR_TAG = "scene-decorator"

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
