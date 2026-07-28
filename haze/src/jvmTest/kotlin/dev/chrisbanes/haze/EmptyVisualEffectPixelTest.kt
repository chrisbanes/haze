// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class EmptyVisualEffectPixelTest {

  @Test
  fun foregroundDefaultEffectPreservesContent() = runComposeUiTest {
    setContent {
      Box(Modifier.size(100.dp).background(Color.Black)) {
        Box(
          Modifier
            .fillMaxSize()
            .testTag("subject")
            .hazeEffect()
            .background(Color.Red),
        )
      }
    }

    val pixels = onNodeWithTag("subject").captureToImage().toPixelMap()
    assertThat(pixels[50, 50]).isEqualTo(Color.Red)
  }

  @Test
  fun replacingForegroundEffectWithEmptyReleasesContentLayer() = runComposeUiTest {
    val recordingEffect = ContentLayerRecordingVisualEffect()
    val effect = mutableStateOf<VisualEffect>(recordingEffect)

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .hazeEffect {
            visualEffect = effect.value
          }
          .background(Color.Red),
      )
    }
    waitForIdle()

    assertThat(recordingEffect.contentLayer).isNotNull()
    val contentLayer = checkNotNull(recordingEffect.contentLayer)

    effect.value = VisualEffect.Empty
    waitForIdle()

    assertThat(contentLayer.isReleased).isTrue()
  }
}

private class ContentLayerRecordingVisualEffect : VisualEffect {
  var contentLayer: GraphicsLayer? = null

  override fun DrawScope.draw(context: VisualEffectContext) {
    contentLayer = context.areas.single().contentLayer
  }
}
