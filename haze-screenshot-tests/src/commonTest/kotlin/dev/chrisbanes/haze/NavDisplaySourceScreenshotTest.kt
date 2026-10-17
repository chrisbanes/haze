// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

abstract class NavDisplaySourceScreenshotTest : ScreenshotTest() {
  protected open fun ScreenshotUiTest.refreshCapture() = Unit

  @Test
  fun blur_siblingNavigation_tracksCompletedForwardAndBackTransitions() =
    assertNavigationCapture(SourcePlacement.AnimatedComposite)

  @Test
  fun blur_missingSource_doesNotMatchCompletedTransition() =
    assertNavigationCapture(SourcePlacement.None)

  private fun assertNavigationCapture(placement: SourcePlacement) = runScreenshotTest(size = Size(880f, 660f)) {
    lateinit var backStack: SnapshotStateList<String>
    var showReference by mutableStateOf(false)

    setContent {
      backStack = remember { mutableStateListOf(FIRST_SCENE) }
      ScreenshotTheme {
        NavigationSample(backStack, placement) { showReference }
      }
    }

    fun captureBar(): PixelSnapshot {
      refreshCapture()
      val pixels = captureRootPixels().snapshot()
      val bounds = onNodeWithTag(SOURCE_TAG).fetchSemanticsNode().boundsInRoot.roundToIntRect()
      // Compare the interior, away from the independently bounded blur inputs' edges.
      return pixels.crop(IntRect(bounds.left + 72, bounds.top + 72, bounds.right - 72, bounds.bottom - 72))
    }

    fun capturePair(): Pair<PixelSnapshot, PixelSnapshot> {
      waitForIdle()
      val subject = captureBar()
      // Toggle only drawing: recomposition would require advancing the frozen transition clock.
      showReference = true
      return try {
        waitForIdle()
        subject to captureBar()
      } finally {
        showReference = false
        waitForIdle()
      }
    }

    fun captureComposite(label: String): PixelSnapshot {
      val (subject, control) = capturePair()
      val difference = subject.meanAbsoluteDifference(control)
      assertThat(difference, label).isLessThan(1f / 255f)
      return subject
    }

    val initial = if (placement == SourcePlacement.None) capturePair().first else captureComposite("initial")
    mainClock.autoAdvance = false
    try {
      backStack += SECOND_SCENE
      waitForIdle()
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeBy(500)

      if (placement == SourcePlacement.None) {
        val (subject, control) = capturePair()
        // The oracle must detect the absent effect, rather than matching the raw scene.
        assertThat(subject.meanAbsoluteDifference(control)).isGreaterThan(0.01f)
        return@runScreenshotTest
      }

      val midpoint = captureComposite("forward midpoint")
      assertThat(initial.changedPixelRatio(midpoint)).isGreaterThan(0.05f)
      mainClock.advanceTimeBy(500)
      val destination = captureComposite("destination")
      assertThat(midpoint.changedPixelRatio(destination)).isGreaterThan(0.05f)

      backStack.removeAt(backStack.lastIndex)
      waitForIdle()
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeBy(500)
      val returning = captureComposite("back midpoint")
      assertThat(destination.changedPixelRatio(returning)).isGreaterThan(0.05f)
      mainClock.advanceTimeBy(500)
      assertThat(initial.meanAbsoluteDifference(captureComposite("returned initial"))).isLessThan(1f / 255f)
    } finally {
      mainClock.autoAdvance = true
    }
  }
}

private enum class SourcePlacement {
  AnimatedComposite,
  None,
}

@Composable
private fun NavigationSample(
  backStack: List<String>,
  placement: SourcePlacement,
  showReference: () -> Boolean,
) {
  val hazeState = remember { HazeState() }
  val completedScene = rememberGraphicsLayer()
  val style = remember {
    HazeBlurStyle {
      blurRadius(12.dp)
      noiseFactor(0f)
      colorEffects(listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.18f))))
    }
  }

  Scaffold(
    containerColor = Color.Black,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      Box(Modifier.fillMaxWidth().height(96.dp)) {
        Box(
          Modifier.fillMaxSize().testTag(SOURCE_TAG)
            .hazeBlur(input = HazeInput.Sources(hazeState), style = style),
        )
        Box(
          Modifier.fillMaxSize()
            .drawWithContent { if (showReference()) drawContent() }
            .drawBehind {
              // Match the source effect's underlay during translucent transitions.
              drawRect(Color.Black)
              translate(top = size.height - completedScene.size.height) {
                drawLayer(completedScene)
              }
            }
            .hazeBlur(input = HazeInput.Content, style = style)
            .drawBehind {
              translate(top = size.height - completedScene.size.height) {
                drawLayer(completedScene)
              }
            },
        )
      }
    },
  ) { _ ->
    // Keep the viewport full-size; a real scene applies Scaffold's contentPadding internally.
    NavDisplay(
      backStack = backStack,
      modifier = Modifier.fillMaxSize()
        .drawWithContent {
          completedScene.record { this@drawWithContent.drawContent() }
          drawLayer(completedScene)
        }
        .then(if (placement == SourcePlacement.AnimatedComposite) Modifier.hazeSource(hazeState) else Modifier),
      onBack = {},
      transitionSpec = {
        (slideInHorizontally(tween(1000)) + fadeIn(tween(1000))) togetherWith
          (slideOutHorizontally(tween(1000)) + fadeOut(tween(1000)))
      },
      entryProvider = { sceneKey -> NavEntry(sceneKey) { TestScene(sceneKey) } },
    )
  }
}

@Composable
private fun TestScene(sceneKey: String) {
  Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize()) {
      val colors = if (sceneKey == FIRST_SCENE) FIRST_SCENE_COLORS else SECOND_SCENE_COLORS
      colors.forEach { color ->
        Box(Modifier.weight(1f).fillMaxHeight().background(color))
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

private const val FIRST_SCENE = "First"
private const val SECOND_SCENE = "Second"
private const val SOURCE_TAG = "source"
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
