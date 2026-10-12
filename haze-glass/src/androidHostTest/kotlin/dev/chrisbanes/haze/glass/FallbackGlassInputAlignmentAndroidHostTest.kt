// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Fallback Glass draws in effect space, but `drawInput()` draws in expanded-layer space. A member
 * with a background must still show the backdrop at its true position, not shifted by the layer
 * padding.
 */
@Config(sdk = [31])
class FallbackGlassInputAlignmentAndroidHostTest : ScreenshotTest() {
  private val style = GlassStyle { backgroundColor(Color.White.copy(alpha = 0.35f)) }

  @Test
  fun fallbackMemberWithBackground_drawsBackdropAtTruePosition() = runScreenshotTest(size = Size(900f, 600f)) {
    val state = HazeState()
    val effect = GlassRuntimeEffect()
    setContent {
      Root(state) {
        Member(effect, state, x = 300)
      }
    }

    assertThat(effect.delegate).isInstanceOf<FallbackGlassDelegate>()
    assertSquareAtTruePosition(captureRootPixels())
  }

  @Test
  fun fallbackClusterMemberWithBackground_drawsBackdropAtTruePosition() = runScreenshotTest(size = Size(900f, 600f)) {
    val state = HazeState()
    val effects = List(2) { GlassRuntimeEffect() }
    setContent {
      Root(state) {
        Box(Modifier.pxSize(900, 600).hazeGlassContainer(40.dp)) {
          Member(effects[0], state, x = 300)
          Member(effects[1], state, x = 510)
        }
      }
    }

    val pixels = captureRootPixels()

    // Two members in one surface prove the merged-cluster path is under test.
    val owner = checkNotNull(effects.first().memberBindingForTest?.node?.container)
    assertThat(owner.renderer.runtimeSurfaces.size).isEqualTo(1)
    owner.renderer.runtimeSurfaces.forEach {
      assertThat(it.delegate).isInstanceOf<FallbackGlassDelegate>()
    }
    assertSquareAtTruePosition(pixels)
  }

  @Composable
  private fun Root(state: HazeState, content: @Composable () -> Unit) {
    Box(Modifier.pxSize(900, 600).testTag("root").background(Color.White)) {
      Box(Modifier.pxSize(900, 600).hazeSource(state).background(Color.White)) {
        Box(Modifier.offset { IntOffset(SQUARE_X, SQUARE_Y) }.pxSize(SQUARE_SIZE, SQUARE_SIZE).background(Color.Red))
      }
      content()
    }
  }

  @Composable
  private fun Member(effect: GlassRuntimeEffect, state: HazeState, x: Int) {
    Box(
      Modifier.offset { IntOffset(x, 250) }.pxSize(200, 160)
        .hazeGlass(HazeEffectFactory { effect }, HazeInput.Sources(state), style, null, true, null),
    )
  }

  @Composable
  private fun Modifier.pxSize(width: Int, height: Int): Modifier = with(LocalDensity.current) {
    size(width.toDp(), height.toDp())
  }

  /** Counts red-dominant pixels inside the first member, and checks their count and centroid. */
  private fun assertSquareAtTruePosition(pixels: PixelMap) {
    var count = 0
    var sumX = 0L
    var sumY = 0L
    for (y in 250 until 410) {
      for (x in 300 until 500) {
        val pixel = pixels[x, y]
        if (pixel.red - pixel.green > 0.3f) {
          count++
          sumX += x
          sumY += y
        }
      }
    }
    assertThat(count, "red pixels").isEqualTo(SQUARE_SIZE * SQUARE_SIZE)
    // Integer centroid of pixels [380, 420) x [310, 350) is (399, 329).
    assertThat((sumX / count).toInt(), "centroid x").isBetween(398, 400)
    assertThat((sumY / count).toInt(), "centroid y").isBetween(328, 330)
  }

  private companion object {
    const val SQUARE_X = 380
    const val SQUARE_Y = 310
    const val SQUARE_SIZE = 40
  }
}
