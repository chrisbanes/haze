// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.roundToInt
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class HazeScreenTransformAndroidTest : ScreenshotTest() {

  @Test
  fun screenStrategy_scaledSource_samplesInScreenCoordinates() = runScreenshotTest {
    val hazeState = HazeState().apply {
      positionStrategy = HazePositionStrategy.Screen
    }
    setContent {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
          modifier = Modifier
            .graphicsLayer {
              transformOrigin = TransformOrigin.Center
              scaleX = 0.5f
              scaleY = 0.5f
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
        }

        Box(
          modifier = Modifier
            .offset(y = 37.5.dp)
            .size(width = 100.dp, height = 25.dp)
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

    waitForIdle()
    val pixels = captureRootPixels()
    val effectBounds = onNodeWithTag(EFFECT_TAG).fetchSemanticsNode().boundsInRoot
    val sampledColor = pixels[
      effectBounds.center.x.roundToInt(),
      effectBounds.center.y.roundToInt(),
    ]

    assertThat(sampledColor.blue).isGreaterThan(0.8f)
    assertThat(sampledColor.red).isLessThan(0.2f)
  }
}

private const val EFFECT_TAG = "screen_transform_effect"

private val PASSTHROUGH_EFFECT = HazeEffectFactory<Unit> {
  object : HazeEffectRenderer<Unit> {
    override fun HazeEffectDrawScope.draw(style: Unit) {
      drawRect(Color.Black)
      drawInput()
    }
  }
}
