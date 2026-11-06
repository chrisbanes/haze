// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class GlassDrawOutsideBoundsTest {
  @Test
  fun glassScale_singleSurfaceGrowsOutsideLayoutRect_runtime() = assertPressGrowsOutsideLayoutRect(fallback = false)

  @Test
  fun glassScale_singleSurfaceGrowsOutsideLayoutRect_fallback() = assertPressGrowsOutsideLayoutRect(fallback = true)

  @Test
  fun glassScale_validationUnchangedAtDefaultLimit() {
    assertFailure { GlassInteractionScopeImpl().scale(1.2f) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage("scaleX must be $POSITIVE_AT_MOST_ONE_DOMAIN")
  }

  @Test
  fun drawOutset_inflatesClipAndGroupComposite_runtime() = assertDrawOutsetInflatesComposite(fallback = false)

  @Test
  fun drawOutset_inflatesClipAndGroupComposite_fallback() = assertDrawOutsetInflatesComposite(fallback = true)

  private fun assertDrawOutsetInflatesComposite(fallback: Boolean) {
    lateinit var baseline: PixelMap
    runComposeUiTest { baseline = surface(GlassStyle { alpha(0.5f) }, fallback).capture() }
    runComposeUiTest {
      val fixture = surface(GlassStyle { drawOutset(6.dp); alpha(0.5f) }, fallback)

      assertThat(fixture.effect.nodeClipOutsetPx()).isEqualTo(6f)
      if (fallback) {
        val delegate = fixture.effect.lastDrawDelegateForTest as FallbackGlassDelegate
        assertThat(delegate.groupAlphaSizeForTest).isEqualTo(IntSize(112, 92))
      } else {
        assertThat(checkNotNull(fixture.effect.preparedRender).groupCompositeSize).isEqualTo(IntSize(112, 92))
      }
      // Nothing draws in the outset yet, so a misplaced composite would shift the material.
      assertThat(regionDifference(fixture.capture(), baseline, 90, 70, 210, 170)).isLessThan(1f / 255f)
    }
  }

  @Test
  fun zeroExtent_clipAndCompositeMatchToday() {
    runComposeUiTest {
      val runtime = surface(GlassStyle { alpha(0.5f) }, fallback = false)
      assertThat(runtime.effect.nodeClipOutsetPx()).isEqualTo(0f)
      assertThat(checkNotNull(runtime.effect.preparedRender).groupCompositeSize).isEqualTo(IntSize(100, 80))
    }
    runComposeUiTest {
      val fallback = surface(GlassStyle { alpha(0.5f) }, fallback = true)
      assertThat(fallback.effect.nodeClipOutsetPx()).isEqualTo(0f)
      val delegate = fallback.effect.lastDrawDelegateForTest as FallbackGlassDelegate
      assertThat(delegate.groupAlphaSizeForTest).isEqualTo(IntSize(100, 80))
    }
  }

  @Test
  fun pressAnimation_singleSurfaceLayerBoundsStable() = withScaleLimit(1.2f) {
    runComposeUiTest {
      val linear = tween<Float>(300, easing = LinearEasing)
      val fixture = surface(GlassStyle { pressed { animate(linear, linear) { scale(1.2f) } } }, fallback = false)
      mainClock.autoAdvance = false
      runBlocking { fixture.source.emit(PressInteraction.Press(Offset(50f, 40f))) }
      mainClock.advanceTimeByFrame()
      val resolves = fixture.effect.layerPaddingResolveCountForTest
      val sampleSize = checkNotNull(fixture.effect.preparedRender).params.coordinates.sampleSize
      val scales = mutableSetOf<Float>()
      repeat(10) {
        mainClock.advanceTimeByFrame()
        fixture.capture()
        scales += fixture.effect.currentInteractionState.scaleX
        assertThat(fixture.effect.layerPaddingResolveCountForTest).isEqualTo(resolves)
        assertThat(checkNotNull(fixture.effect.preparedRender).params.coordinates.sampleSize).isEqualTo(sampleSize)
      }
      assertThat(scales.size, "press animated").isGreaterThan(1)
      mainClock.autoAdvance = true
      waitForIdle()
    }
  }

  private fun assertPressGrowsOutsideLayoutRect(fallback: Boolean) = withScaleLimit(1.2f) {
    runComposeUiTest {
      val fixture = surface(GlassStyle { pressed { animate(snap(), snap()) { scale(1.2f) } } }, fallback)
      val idle = fixture.capture()
      runBlocking { fixture.source.emit(PressInteraction.Press(Offset(50f, 40f))) }
      waitForIdle()
      val pressed = fixture.capture()

      // Layout rect is (100, 80)-(200, 160); the 1.2x Center-pivot rect is (90, 72)-(210, 168).
      for ((x, y) in listOf(96 to 120, 203 to 120, 150 to 76, 150 to 163)) {
        assertThat(pixelDifference(pressed, idle, x, y), "grown probe ($x, $y)").isGreaterThan(0.01f)
      }
      for ((x, y) in listOf(85 to 120, 214 to 120, 150 to 67, 150 to 172)) {
        assertThat(pixelDifference(pressed, idle, x, y), "outside probe ($x, $y)").isLessThan(1f / 255f)
      }
    }
  }

  internal class SurfaceFixture(
    val test: ComposeUiTest,
    val effect: GlassRuntimeEffect,
    val source: MutableInteractionSource,
  ) {
    fun capture() = test.onNodeWithTag("root").captureToImage().toPixelMap()
  }

  internal companion object {
    fun <T> withScaleLimit(limit: Float, block: () -> T): T {
      val previous = glassInteractionScaleLimitForTest
      glassInteractionScaleLimitForTest = limit
      try {
        return block()
      } finally {
        glassInteractionScaleLimitForTest = previous
      }
    }

    fun ComposeUiTest.surface(style: GlassStyle, fallback: Boolean): SurfaceFixture {
      val effect = GlassRuntimeEffect()
      if (fallback) {
        effect.runtimeEffectFactory = GlassRuntimeEffectFactory {
          throw RuntimeShaderRenderEffectException(IllegalStateException("test unavailable runtime"))
        }
      }
      val source = MutableInteractionSource()
      val state = HazeState()
      setContent {
        Box(Modifier.size(300.dp, 240.dp).testTag("root").background(Color.White)) {
          Box(Modifier.size(300.dp, 240.dp).hazeSource(state).background(Color.White)) {
            repeat(30) { index ->
              Box(
                Modifier.offset((index * 10).dp, 0.dp).size(10.dp, 240.dp)
                  .background(if (index % 2 == 0) Color.Black else Color.White),
              )
            }
          }
          Box(
            Modifier.offset(100.dp, 80.dp).size(100.dp, 80.dp).hazeGlass(
              HazeEffectFactory { effect }, HazeInput.Sources(state),
              // A rounded shape makes Glass clip to its node bounds.
              containerTestStyle(Color.Red).then(GlassStyle { shape(RoundedCornerShape(8.dp)) }).then(style),
              HazePerformanceMode.Quality, false, source, GlassTransformTarget.MaterialOnly,
              GlassTransformPivot.Center, GlassReducedMotionPolicy.Full,
            ),
          )
        }
      }
      waitForIdle()
      return SurfaceFixture(this, effect, source)
    }

    fun regionDifference(first: PixelMap, second: PixelMap, left: Int, top: Int, right: Int, bottom: Int): Float {
      var difference = 0f
      var count = 0
      for (y in top until bottom) for (x in left until right) {
        difference += pixelDifference(first, second, x, y)
        count++
      }
      return difference / count
    }

    fun pixelDifference(first: PixelMap, second: PixelMap, x: Int, y: Int): Float {
      val a = first[x, y]
      val b = second[x, y]
      return (abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)) / 3f
    }
  }
}
