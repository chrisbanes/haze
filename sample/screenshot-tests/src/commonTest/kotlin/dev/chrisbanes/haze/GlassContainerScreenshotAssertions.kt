// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.sample.GlassContainerSampleContent
import dev.chrisbanes.haze.sample.SamplesTheme
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.ScreenshotUiTest

internal fun ScreenshotUiTest.captureGlassContainer() {
  var dragOffsets by mutableStateOf(listOf(Offset.Zero, Offset.Zero))
  var extraMemberVisible by mutableStateOf(false)

  setContent {
    SamplesTheme(useDarkColors = true) {
      ScreenshotTheme {
        GlassContainerSampleContent(
          mergeDistance = 24.dp,
          dragOffsets = dragOffsets,
          extraMemberVisible = extraMemberVisible,
          onMergeDistanceChange = {},
          onDrag = { _, _ -> },
          onResetDrag = {},
          onExtraMemberVisibleChange = {},
          onBack = {},
        )
      }
    }
  }

  // Layered runtime shaders amplify Skia's platform-specific pixel variance.
  val unmatchedPixelThreshold = 0.013f
  waitForIdle()
  captureRoot("merged", unmatchedPixelThreshold)

  // Move the second circle far outside the merge distance, so the circles draw as separate
  // surfaces.
  dragOffsets = listOf(Offset.Zero, Offset(400f, 0f))
  waitForIdle()
  captureRoot("separated", unmatchedPixelThreshold)

  // The extra pill grows out of the base pill once the container has drawn its first frame.
  extraMemberVisible = true
  waitForIdle()
  captureRoot("memberShown", unmatchedPixelThreshold)
}
