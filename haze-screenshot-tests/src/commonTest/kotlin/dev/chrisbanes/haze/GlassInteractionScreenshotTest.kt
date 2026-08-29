// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassTransformPivot
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test

class GlassInteractionScreenshotTest : ScreenshotTest() {

  @Test
  fun glassInteraction_idleHoverPressAndRelease() = runScreenshotTest {
    val effect = combinedEffect()
    setContent { ScreenshotTheme { GlassInteractionScene("combined", "HOVER / PRESS", effect) } }
    captureRoot("idle")

    onNodeWithTag("combined").performMouseInput { enter(Offset(24f, 32f)) }
    waitForIdle()
    captureRoot("hover_top_left")

    onNodeWithTag("combined").performTouchInput { down(lowerRightPosition()) }
    waitForIdle()
    captureRoot("press_bottom_right")

    onNodeWithTag("combined").performTouchInput { up() }
    waitForIdle()
    captureRoot("release")
  }

  @Test
  fun glassInteraction_lightingChannel() = runScreenshotTest {
    capturePressed("lighting", "LIGHTING", lightingEffect())
  }

  @Test
  fun glassInteraction_opticsChannel() = runScreenshotTest {
    capturePressed("optics", "OPTICS", opticsEffect())
  }

  @Test
  fun glassInteraction_materialOnlyTransformTarget() = runScreenshotTest {
    capturePressed("material_only", "MATERIAL ONLY", scaleEffect(GlassTransformTarget.MaterialOnly))
  }

  @Test
  fun glassInteraction_materialAndContentTransformTarget() = runScreenshotTest {
    capturePressed("material_and_content", "MATERIAL + CONTENT", scaleEffect(GlassTransformTarget.MaterialAndContent))
  }

  @Test
  fun glassInteraction_centerPivot() = runScreenshotTest {
    capturePressed("center_pivot", "CENTER PIVOT", scaleEffect(GlassTransformTarget.MaterialAndContent, GlassTransformPivot.Center))
  }

  @Test
  fun glassInteraction_reducedMotionKeepsTransformIdentity() = runScreenshotTest {
    capturePressed("reduced", "REDUCED: OPTICS, NO SCALE", reducedEffect())
  }

  @Test
  fun glassInteraction_localPatchPreservesPixelsOutsideInteractionRegion() = runScreenshotTest {
    val radiusFraction = 0.22f
    val effect = GlassTestConfiguration().apply {
      optics = GlassOptics(
        refractionStrength = 0.7f,
        refractionDisplacement = 28.dp,
        depth = OpticalSizeValue.Fixed(0.5f),
        blurRadius = OpticalSizeValue.Fixed(14.dp),
      )
      pressed {
        animate(toSpec = snap(), fromSpec = snap()) {
          refractionMultiplier(1.5f)
          whitePointDelta(0.15f)
        }
      }
      interactionLightRadiusFraction = radiusFraction
      interactionPositionAnimationSpec = snap()
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
      shape = RoundedCornerShape(20.dp)
    }
    setContent {
      ScreenshotTheme {
        GlassInteractionScene(
          tag = "local_patch",
          label = "LOCAL PATCH",
          effect = effect,
          performanceMode = HazePerformanceMode.Quality,
        )
      }
    }

    val node = onNodeWithTag("local_patch")
    val nodeBounds = node.fetchSemanticsNode().boundsInRoot
    val pressPosition = Offset(
      x = nodeBounds.width * 0.5f + 0.25f,
      y = nodeBounds.height * 0.5f + 0.75f,
    )
    val idle = captureRootPixels().snapshot()

    node.performTouchInput { down(pressPosition) }
    waitForIdle()
    val pressed = captureRootPixels().snapshot()

    val pointerInRoot = nodeBounds.topLeft + pressPosition
    val radiusPx = minOf(nodeBounds.width, nodeBounds.height) * radiusFraction
    val interactionBounds = IntRect(
      left = floor(pointerInRoot.x - radiusPx).toInt().coerceAtLeast(0),
      top = floor(pointerInRoot.y - radiusPx).toInt().coerceAtLeast(0),
      right = ceil(pointerInRoot.x + radiusPx).toInt().coerceAtMost(idle.width),
      bottom = ceil(pointerInRoot.y + radiusPx).toInt().coerceAtMost(idle.height),
    )

    assertThat(idle.changedPixelRatioOutside(pressed, interactionBounds)).isEqualTo(0f)
    assertThat(
      idle.changedPixelRatioOutsideCircle(
        other = pressed,
        center = pointerInRoot,
        radius = radiusPx + 1f,
      ),
    ).isEqualTo(0f)

    node.performTouchInput { up() }
    waitForIdle()
    assertThat(idle.changedPixelRatio(captureRootPixels().snapshot())).isEqualTo(0f)
  }
}

private fun ScreenshotUiTest.capturePressed(tag: String, label: String, effect: GlassTestConfiguration) {
  setContent { ScreenshotTheme { GlassInteractionScene(tag, label, effect) } }
  onNodeWithTag(tag).performTouchInput { down(lowerRightPosition()) }
  waitForIdle()
  captureRoot("pressed")
}

internal fun ScreenshotUiTest.assertGlassInteractionOpticsHasNoCircularPatchSeamInvariant() {
  val effect = GlassTestConfiguration().apply {
    pressed { refractionMultiplier(1.08f) }
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    shape = RoundedCornerShape(20.dp)
  }
  setContent { ScreenshotTheme { GlassInteractionScene("optics_seam", "HOVER / PRESS", effect) } }
  val node = onNodeWithTag("optics_seam")
  val idle = captureRootPixels().snapshot()
  node.performTouchInput { down(lowerRightPosition()) }
  waitForIdle()

  val bounds = node.fetchSemanticsNode().boundsInRoot
  val probeBounds = IntRect(
    left = floor(bounds.left + bounds.width * 0.35f).toInt(),
    top = floor(bounds.top + bounds.height * 0.55f).toInt(),
    right = ceil(bounds.left + bounds.width * 0.42f).toInt(),
    bottom = ceil(bounds.top + bounds.height * 0.66f).toInt(),
  )
  val pressed = captureRootPixels().snapshot()
  val maxDelta = idle.maxHorizontalRgbEffectDelta(pressed, probeBounds)
  assertThat(maxDelta, "pressed interaction-optics seam RGB effect delta").isLessThan(0.02f)
}

private fun PixelSnapshot.maxHorizontalRgbEffectDelta(
  other: PixelSnapshot,
  bounds: IntRect,
): Float {
  require(width == other.width && height == other.height)
  require(bounds.left >= 0 && bounds.right <= width)
  require(bounds.top >= 0 && bounds.bottom <= height)
  return (bounds.top until bounds.bottom).maxOf { y ->
    (bounds.left + 1 until bounds.right).maxOf { x ->
      val previousIdle = this[x - 1, y]
      val currentIdle = this[x, y]
      val previousEffect = other[x - 1, y]
      val currentEffect = other[x, y]
      kotlin.math.abs((currentEffect.red - currentIdle.red) - (previousEffect.red - previousIdle.red)) +
        kotlin.math.abs((currentEffect.green - currentIdle.green) - (previousEffect.green - previousIdle.green)) +
        kotlin.math.abs((currentEffect.blue - currentIdle.blue) - (previousEffect.blue - previousIdle.blue))
    }
  }
}

private fun androidx.compose.ui.test.TouchInjectionScope.lowerRightPosition(): Offset = Offset(
  x = center.x * 1.5f,
  y = center.y * 1.5f,
)

@Composable
internal fun GlassInteractionScene(
  tag: String,
  label: String,
  effect: GlassTestConfiguration,
  performanceMode: HazePerformanceMode = HazePerformanceMode.Default,
) {
  val hazeState = remember { HazeState() }
  Box(Modifier.size(320.dp, 320.dp)) {
    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
      drawRect(Color(0xFF10233E))
      rotate(-25f) {
        repeat(11) { index ->
          drawRect(
            color = if (index % 2 == 0) Color(0xFF2CE1C2) else Color(0xFFF15B8A),
            topLeft = Offset(index * 56f - 220f, -120f),
            size = size.copy(width = 22f),
            alpha = 0.72f,
          )
        }
      }
      drawCircle(Color(0xFFFFD166), radius = 76f, center = Offset(size.width * 0.77f, size.height * 0.24f))
    }
    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
      Box(
        Modifier
          .size(264.dp, 144.dp)
          .testTag(tag)
          .hazeGlass(
            input = HazeInput.Sources(hazeState),
            configuration = effect,
            performanceMode = performanceMode,
          ),
        contentAlignment = Alignment.Center,
      ) {
        Text(label, color = Color.White)
      }
    }
  }
}

private fun combinedEffect() = GlassTestConfiguration().apply {
  applyTestHoverAndPressResponses()
  interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  shape = RoundedCornerShape(20.dp)
}

private fun lightingEffect() = GlassTestConfiguration().apply {
  pressed { lightingIntensity(1f) }
  interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  shape = RoundedCornerShape(20.dp)
}

private fun opticsEffect() = GlassTestConfiguration().apply {
  pressed {
    refractionMultiplier(1.08f)
    whitePointDelta(0.04f)
  }
  interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  shape = RoundedCornerShape(20.dp)
}

private fun scaleEffect(
  target: GlassTransformTarget,
  pivot: GlassTransformPivot = GlassTransformPivot.Pointer,
) = GlassTestConfiguration().apply {
  pressed { scale(0.9f, 0.96f) }
  interactionTransformTarget = target
  interactionTransformPivot = pivot
  interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
  shape = RoundedCornerShape(20.dp)
}

private fun reducedEffect() = GlassTestConfiguration().apply {
  pressed {
    lightingIntensity(1f)
    refractionMultiplier(1.08f)
    whitePointDelta(0.04f)
    scale(0.9f, 0.96f)
  }
  interactionTransformTarget = GlassTransformTarget.MaterialAndContent
  interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  shape = RoundedCornerShape(20.dp)
}
