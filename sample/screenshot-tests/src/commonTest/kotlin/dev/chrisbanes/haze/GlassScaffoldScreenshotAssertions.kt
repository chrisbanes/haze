// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.navigation.compose.rememberNavController
import assertk.assertThat
import assertk.assertions.isLessThan
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import dev.chrisbanes.haze.sample.SampleEffect
import dev.chrisbanes.haze.sample.SamplesTheme
import dev.chrisbanes.haze.sample.ScaffoldSample
import dev.chrisbanes.haze.sample.ScaffoldSampleMode
import dev.chrisbanes.haze.test.ScreenshotUiTest
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalCoilApi::class)
internal fun ScreenshotUiTest.assertProgressiveScaffoldKeepsRegularChrome() {
  var mode by mutableStateOf(ScaffoldSampleMode.Default)
  val preview = AsyncImagePreviewHandler { ColorImage(0xff34506f.toInt()) }
  setContent {
    CompositionLocalProvider(
      LocalInspectionMode provides true,
      LocalAsyncImagePreviewHandler provides preview,
    ) {
      SamplesTheme(useDarkColors = true) {
        key(mode) {
          ScaffoldSample(rememberNavController(), SampleEffect.Glass, mode = mode)
        }
      }
    }
  }
  waitForIdle()
  val regular = captureRootPixels()
  val bounds = onNodeWithTag("glass_scaffold_back").fetchSemanticsNode().boundsInRoot
  mode = ScaffoldSampleMode.Progressive
  waitForIdle()
  val progressive = captureRootPixels()
  // The top half of this floating control has only a flat dark backdrop. Changing the blur
  // gradient must not change its material response or introduce the old sharp-detail ring.
  var difference = 0f
  var maximumAt = ""
  for (y in bounds.top.roundToInt() until bounds.center.y.roundToInt()) {
    for (x in bounds.left.roundToInt() until bounds.right.roundToInt()) {
      val before = regular[x, y]
      val after = progressive[x, y]
      val delta = maxOf(abs(after.red - before.red), abs(after.green - before.green), abs(after.blue - before.blue))
      if (delta > difference) {
        difference = delta
        maximumAt = "$x,$y in $bounds: $before -> $after"
      }
    }
  }
  assertThat(difference, "Progressive flat-backdrop chrome versus Regular, $maximumAt").isLessThan(2f / 255f)
  captureRoot()
}
