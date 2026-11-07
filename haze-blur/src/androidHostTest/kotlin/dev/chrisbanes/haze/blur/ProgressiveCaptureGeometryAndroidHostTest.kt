// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31, 32, 35], qualifiers = "+w160dp-h320dp")
class ProgressiveCaptureGeometryAndroidHostTest : ScreenshotTest() {
  @Test
  fun qualityVertical_userMaskTracksCaptureGeometry() = assertGeometry(HazePerformanceMode.Quality, false)

  @Test
  fun performanceVertical_userMaskTracksCaptureGeometry() = assertGeometry(HazePerformanceMode.Performance, false)

  @Test
  fun qualityHorizontal_userMaskTracksCaptureGeometry() = assertGeometry(HazePerformanceMode.Quality, true)

  @Test
  fun performanceHorizontal_userMaskTracksCaptureGeometry() = assertGeometry(HazePerformanceMode.Performance, true)

  private fun assertGeometry(mode: HazePerformanceMode, horizontal: Boolean) = runScreenshotTest {
    val logicalSize = Size(21f, 19f)
    val geometries = listOf(
      Size(21f, 19f) to Offset.Zero,
      Size(21.2f, 19.2f) to Offset.Zero,
      Size(21f, 21f) to Offset.Zero,
      Size(25f, 23f) to Offset(2f, 4f),
      Size(25.2f, 23.4f) to Offset(2.25f, 4.5f),
      Size(21f, 19f) to Offset.Zero,
    )
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val mask = if (horizontal) {
      Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = 6f, endX = 12f)
    } else {
      Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = 6f, endY = 12f)
    }
    val progressive = if (horizontal) {
      HazeProgressive.horizontalGradient(startX = 0f, endX = 21f, startIntensity = 1f, endIntensity = 1f)
    } else {
      HazeProgressive.verticalGradient(startY = 0f, endY = 19f, startIntensity = 1f, endIntensity = 1f)
    }
    val effect = BlurVisualEffect()
    effect.update(
      HostLifecycleScope(context),
      BlurConfiguration(
        HazeBlurStyle {
          blurRadius(0.dp)
          noiseFactor(0f)
          backgroundColor(Color.Transparent)
          colorEffects(emptyList())
          mask(mask)
          progressive(progressive)
        },
        mode,
      ),
      HazeSampling.FullResolution,
    )
    val delegate = RenderEffectBlurVisualEffectDelegate(effect)
    var geometry by mutableStateOf(geometries.first())
    var hardwareDraws = 0
    try {
      setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
          val graphics = LocalGraphicsContext.current
          Box(
            Modifier.size(21.dp, 19.dp).background(Color.Black).testTag("progressiveGeometry").drawBehind {
              val drawScope = this
              val input = object : HazeEffectRuntimeDrawScope, DrawScope by drawScope {
                override val modifierSize = logicalSize
                override val modifierBounds = Rect(Offset.Zero, logicalSize)
                override val layerSize = geometry.first
                override val layerOffset = geometry.second
                override val sampling = HazeSampling.FullResolution
                override val hasDrawableInput = true
                override val inputSnapshot: HazeEffectInputSnapshot? = null
                override val coroutineScope = CoroutineScope(EmptyCoroutineContext)
                override fun requirePlatformContext(): PlatformContext = context
                override fun requireGraphicsContext(): GraphicsContext = graphics
                override fun requireDensity(): Density = Density(1f)
                override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unused by the delegate")
                override fun invalidateDraw() = Unit
                override fun drawInput() = Unit
                override fun DrawScope.drawInput() = drawRect(Color.White, topLeft = Offset(-100f, -100f), size = Size(250f, 250f))
              }
              if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) hardwareDraws++
              with(delegate) { draw(input) }
            },
          )
        }
      }
      for ((layer, offset) in geometries) {
        composeTestRule.runOnIdle { geometry = layer to offset }
        waitForIdle()
        val bounds = onNodeWithTag("progressiveGeometry").fetchSemanticsNode().boundsInRoot
        val pixels = captureRootPixels()
        for (coordinate in listOf(7, 8, 9, 10)) {
          val x = if (horizontal) coordinate else 10
          val y = if (horizontal) 10 else coordinate
          val actual = pixels[bounds.left.roundToInt() + x, bounds.top.roundToInt() + y].red
          val expected = (12f - coordinate - 0.5f) / 6f
          assertThat(actual, "$mode horizontal=$horizontal layer=$layer offset=$offset coordinate=$coordinate")
            .isCloseTo(expected, 0.025f)
        }
      }
      assertThat(hardwareDraws > 0, "hardware draw path").isTrue()
    } finally {
      delegate.detach()
    }
  }
}
