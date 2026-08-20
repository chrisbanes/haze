// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.roundToInt
import kotlin.test.Test

class HazeGraphicsLayerTransformTest : ScreenshotTest() {

  @Test
  fun parentScale_samplesSourceInParentCoordinates() = runScreenshotTest(size = Size(600f, 600f)) {
    var parentScale by mutableStateOf(1f)
    setContent {
      ScreenshotTheme {
        TransformScene(parentScale = parentScale)
      }
    }

    waitForIdle()
    assertEffectSamplesBlue(label = "scale=1")

    parentScale = 0.5f
    waitForIdle()
    assertEffectSamplesBlue(label = "scale=0.5")
  }

  @Test
  fun parentRotation_samplesSourceInParentCoordinates() =
    runScreenshotTest(size = Size(800f, 800f)) {
      var parentRotation by mutableStateOf(0f)
      setContent {
        ScreenshotTheme {
          TransformScene(parentRotation = parentRotation)
        }
      }

      waitForIdle()
      assertEffectSamplesBlue(label = "rotation=0")

      parentRotation = 45f
      waitForIdle()
      assertEffectSamplesBlue(label = "rotation=45")
    }

  @Test
  fun effectScale_samplesSourceInEffectCoordinates() =
    runScreenshotTest(size = Size(600f, 600f)) {
      var effectScale by mutableStateOf(1f)
      setContent {
        ScreenshotTheme {
          TransformScene(effectScale = effectScale)
        }
      }

      waitForIdle()
      assertEffectSamplesBlue(label = "effectScale=1", horizontalFraction = 0.75f)

      effectScale = 0.5f
      waitForIdle()
      assertEffectSamplesBlue(label = "effectScale=0.5", horizontalFraction = 0.75f)
    }

  @Test
  fun sourceInnerScale_isNotAppliedTwice() = runScreenshotTest(size = Size(600f, 600f)) {
    var sourceScale by mutableStateOf(1f)
    setContent {
      ScreenshotTheme {
        InnerSourceTransformScene(sourceScale = sourceScale)
      }
    }

    waitForIdle()
    assertEffectSamplesBlue(label = "sourceScale=1", horizontalFraction = 0.375f, verticalFraction = 0.375f)

    sourceScale = 0.5f
    waitForIdle()
    assertEffectSamplesBlue(label = "sourceScale=0.5", horizontalFraction = 0.375f, verticalFraction = 0.375f)
  }

  private fun ScreenshotUiTest.assertEffectSamplesBlue(
    label: String,
    horizontalFraction: Float = 0.5f,
    verticalFraction: Float = 0.5f,
  ) {
    val pixels = captureRootPixels()
    val effectBounds = onNodeWithTag(EFFECT_TAG).fetchSemanticsNode().boundsInRoot
    val sampleX = effectBounds.left + effectBounds.width * horizontalFraction
    val sampleY = effectBounds.top + effectBounds.height * verticalFraction
    val sampledColor = pixels[sampleX.roundToInt(), sampleY.roundToInt()]

    assertThat(sampledColor.blue, "$label sampled blue channel").isGreaterThan(0.8f)
    assertThat(sampledColor.red, "$label sampled red channel").isLessThan(0.2f)
  }
}

@Composable
private fun InnerSourceTransformScene(sourceScale: Float) {
  val hazeState = rememberHazeState()
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Box(
      modifier = Modifier
        .size(200.dp)
        .hazeSource(hazeState)
        .graphicsLayer {
          transformOrigin = TransformOrigin(0f, 0f)
          scaleX = sourceScale
          scaleY = sourceScale
        }
        .background(Color.Blue),
    )

    Box(
      modifier = Modifier
        .size(200.dp)
        .testTag(EFFECT_TAG)
        .hazeEffect(
          factory = PASSTHROUGH_EFFECT,
          input = HazeInput.Sources(hazeState),
          style = Unit,
          sampling = HazeSampling.FullResolution,
          expandLayerBounds = false,
        ),
    )
  }
}

@Composable
private fun TransformScene(
  parentScale: Float = 1f,
  parentRotation: Float = 0f,
  effectScale: Float = 1f,
) {
  val hazeState = rememberHazeState()
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Box(
      modifier = Modifier
        .graphicsLayer {
          transformOrigin = TransformOrigin.Center
          scaleX = parentScale
          scaleY = parentScale
          rotationZ = parentRotation
        }
        .size(200.dp),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .hazeSource(hazeState),
      ) {
        Box(Modifier.fillMaxWidth().weight(3f).background(Color.Red))
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Blue))
      }

      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .fillMaxHeight(0.25f)
          .graphicsLayer {
            transformOrigin = TransformOrigin.Center
            scaleX = effectScale
            scaleY = effectScale
          }
          .testTag(EFFECT_TAG)
          .hazeEffect(
            factory = PASSTHROUGH_EFFECT,
            input = HazeInput.Sources(hazeState),
            style = Unit,
            sampling = HazeSampling.FullResolution,
            expandLayerBounds = false,
          ),
      )
    }
  }
}

private const val EFFECT_TAG = "transformed_haze_effect"

private val PASSTHROUGH_EFFECT = HazeEffectFactory<Unit> {
  object : HazeEffectRenderer<Unit> {
    override fun HazeEffectDrawScope.draw(style: Unit) {
      drawRect(Color.Black)
      drawInput()
    }
  }
}
