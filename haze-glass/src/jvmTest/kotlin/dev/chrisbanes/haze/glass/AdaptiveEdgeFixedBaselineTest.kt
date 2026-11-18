// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class, androidx.compose.ui.test.ExperimentalTestApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test

class AdaptiveEdgeFixedBaselineTest : ContextTest() {
  @Suppress("DEPRECATION")
  @Test
  fun fixedSplit_repeatedCaptureAndShaderDigests() = runSkikoComposeUiTest(size = Size(128f, 96f), density = Density(1f)) {
    val state = HazeState()
    val effect = GlassRuntimeEffect()
    val style = GlassStyle.regular.then {
      shape(RoundedCornerShape(8.dp))
      backgroundColor(Color.Transparent)
    }
    setContent {
      CompositionLocalProvider(LocalSystemTheme provides SystemTheme.Light) {
        Box(Modifier.size(128.dp, 96.dp).testTag("root").background(Color.Black)) {
          Box(
            Modifier.size(128.dp, 96.dp).hazeSource(state).drawBehind {
              drawRect(Color(0.1f, 0.1f, 0.1f))
              drawRect(Color(0.9f, 0.9f, 0.9f), topLeft = Offset(64f, 0f))
            },
          )
          Box(Modifier.offset(16.dp, 16.dp).size(96.dp, 64.dp).hazeGlass(HazeEffectFactory { effect }, HazeInput.Sources(state), style, HazePerformanceMode.Quality, true, null))
        }
      }
    }
    waitForIdle()
    fun capture(): ByteArray {
      val p = onNodeWithTag("root").captureToImage().toPixelMap()
      return ByteArray(p.width * p.height * 4).also { bytes ->
        for (y in 0 until p.height) for (x in 0 until p.width) {
          val argb = p[x, y].toArgb()
          for (i in 0..3) bytes[(y * p.width + x) * 4 + i] = (argb ushr (i * 8)).toByte()
        }
      }
    }
    val first = capture()
    val second = capture()
    assertThat(first.contentEquals(second)).isTrue()
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val results = buildString {
      appendLine("fixed_capture_sha256=${hash(first)}")
      appendLine("repeat_capture_sha256=${hash(second)}")
      appendLine("repeat_max_channel_difference=0/255")
      for (merged in listOf(false, true)) for (continuous in listOf(false, true)) {
        appendLine("rim_merged=${merged}_continuous=${continuous}_sha256=${hash(GlassShaders.buildRim(merged, continuous).encodeToByteArray())}")
      }
    }
    System.getenv("HAZE_T2_ARTIFACT_DIR")?.let { output ->
      File(output).mkdirs()
      File(output, "t1-fixed-baseline.txt").writeText(results)
    }
  }
}
