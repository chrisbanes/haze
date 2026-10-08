// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class HazeInputPresentationOutsetTest {
  @Test fun nestedBoundary_preservesOutsetsUnderScaleAndTranslation() = runComposeUiTest {
    val inputs = HazeState()
    val outputs = HazeState()
    val observing = mutableStateOf(true)
    val transformed = mutableStateOf(false)
    val renderer = Renderer()
    val factory = HazeEffectFactory { renderer }
    setContent {
      Box(Modifier.size(100.dp)) {
        Box(Modifier.fillMaxSize().hazeSource(inputs).background(Color.Red))
        Box(
          Modifier.align(Alignment.Center).size(40.dp).graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = if (transformed.value) .8f else 1f
            scaleY = if (transformed.value) .8f else 1f
            translationX = if (transformed.value) 10f else 0f
          }.hazeSource(outputs).hazeEffect(factory, HazeInput.Sources(inputs), observing.value),
        )
      }
    }
    fun pixels() = run {
      repeat(3) {
        mainClock.advanceTimeByFrame()
        onRoot().captureToImage()
        waitForIdle()
      }
      onRoot().captureToImage().toPixelMap()
    }
    assertThat(pixels()[20, 50]).isEqualTo(Color.Blue)
    runOnIdle { transformed.value = true }
    val scaled = pixels()
    assertThat(scaled[20, 50]).isEqualTo(Color.Red)
    assertThat(scaled[32, 50]).isEqualTo(Color.Blue)
    runOnIdle { observing.value = false }
    assertThat(pixels()[32, 50]).isEqualTo(Color.Blue)
    runOnIdle { observing.value = true }
    assertThat(pixels()[32, 50]).isEqualTo(Color.Blue)
  }

  private class Renderer :
    HazeEffectRenderer<Boolean>,
    HazeEffectRendererDrawHooks<Boolean>,
    HazeEffectRendererLifecycle<Boolean> {
    override var observesSourceSnapshotChanges = false
      private set
    override fun update(scope: HazeEffectLifecycleScope, style: Boolean, sampling: HazeSampling) {
      observesSourceSnapshotChanges = style
    }
    override fun HazeEffectLayoutScope.calculateLayerBounds(style: Boolean): Rect = modifierBounds.inflate(12f)
    override fun HazeEffectDrawScope.draw(style: Boolean) {
      drawRect(Color.Blue, topLeft = Offset(-12f, -12f), size = Size(size.width + 24f, size.height + 24f))
    }
  }
}
