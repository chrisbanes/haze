// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class InteractiveVisualEffectPixelTest {

  @Test
  fun materialAndContentTransform_scalesFinalGroupWithoutChangingBounds() = runComposeUiTest {
    val effect = RecordingInteractiveVisualEffect().apply {
      transform = VisualEffectTransform(0.5f, 0.5f, Offset(50f, 50f))
    }
    setContent {
      Box(Modifier.size(100.dp).background(Color.Black)) {
        Box(
          Modifier
            .fillMaxSize()
            .testTag("glass")
            .hazeEffect {
              drawContentBehind = true
              visualEffect = effect
            }
            .background(Color.Red),
        )
      }
    }

    val pixels = onNodeWithTag("glass").captureToImage().toPixelMap()
    assertThat(pixels[50, 50]).isEqualTo(Color.Red)
    assertThat(pixels[5, 5]).isEqualTo(Color.Black)
    onNodeWithTag("glass").assertWidthIsEqualTo(100.dp).assertHeightIsEqualTo(100.dp)
  }
}
