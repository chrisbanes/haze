// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformPivot
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.hazeGlassContainer
import dev.chrisbanes.haze.test.ScreenshotUiTest
import kotlin.math.abs

internal enum class GlassContainerScene { Merged, Separate, MixedColours, MixedKeys, Overflow, Nested, InteractiveRest, InteractivePressed, Overlap }

/** [fallback] asserts the fallback renderer's hard union, which draws no bridge. */
internal fun ScreenshotUiTest.assertGlassContainerScene(scene: GlassContainerScene, fallback: Boolean = false) {
  val interactionSource = MutableInteractionSource()
  setContent { GlassContainerScreenshotScene(scene, interactionSource = interactionSource) }
  waitForIdle()
  val rest = if (scene == GlassContainerScene.InteractivePressed) captureRootPixels() else null
  if (rest != null) {
    interactionSource.tryEmit(PressInteraction.Press(Offset(50f, 40f)))
    waitForIdle()
  }
  val pixels = captureRootPixels()
  when (scene) {
    GlassContainerScene.Merged, GlassContainerScene.MixedColours -> {
      val bridge = pixels[149, 110]
      if (fallback) {
        assertThat(abs(bridge.red - bridge.blue)).isLessThan(2f / 255f)
      } else {
        assertThat(bridge.red).isGreaterThan(0.3f)
        assertThat(bridge.blue).isGreaterThan(0.3f)
        assertThat(bridge.green).isLessThan(0.8f)
      }
      assertThat(pixels[80, 110].red).isGreaterThan(pixels[80, 110].blue)
      assertThat(pixels[220, 110].blue).isGreaterThan(pixels[220, 110].red)
    }
    GlassContainerScene.Separate, GlassContainerScene.MixedKeys -> assertThat(pixels[149, 110]).isEqualTo(sceneBackground(149, 110))
    GlassContainerScene.Overflow -> {
      assertThat(pixels[52, 110].green).isLessThan(0.5f)
      assertThat(pixels[154, 110]).isEqualTo(sceneBackground(154, 110))
    }
    GlassContainerScene.Nested -> {
      assertThat(pixels[145, 110]).isEqualTo(sceneBackground(145, 110))
      assertThat(pixels[200, 110].green).isLessThan(0.5f)
    }
    GlassContainerScene.Overlap -> {
      // Both pixels sit on the same checker colour; the overlap shows the first member's tint once.
      val overlap = pixels[120, 110]
      val firstOnly = pixels[72, 110]
      for ((actual, expected) in listOf(overlap.red to firstOnly.red, overlap.green to firstOnly.green, overlap.blue to firstOnly.blue)) {
        assertThat(abs(actual - expected)).isLessThan(2f / 255f)
      }
      assertThat(pixels[190, 150].blue).isGreaterThan(pixels[190, 150].red)
      assertThat(pixels[150, 70]).isEqualTo(sceneBackground(150, 70))
    }
    GlassContainerScene.InteractiveRest, GlassContainerScene.InteractivePressed -> {
      val bridge = pixels[144, 110]
      assertThat(bridge.red).isGreaterThan(0.3f)
      assertThat(bridge.blue).isGreaterThan(0.3f)
      if (rest != null) {
        assertThat(pixels[90, 100].luminance()).isGreaterThan(rest[90, 100].luminance())
        for ((x, y) in listOf(200 to 100, 248 to 110, 200 to 62)) assertThat(pixels[x, y], "B at ($x, $y)").isEqualTo(rest[x, y])
        // Inside A's layout rect but outside its 0.95 scaled rect, A's tint gives way to the backdrop.
        assertThat(pixels[41, 110]).isEqualTo(sceneBackground(41, 110))
      }
    }
  }
  // Opaque label strokes are replayed above the entire material phase.
  assertThat(pixels[if (scene == GlassContainerScene.Overflow) 28 else 48, 68]).isEqualTo(Color.White)
  captureRoot()
}

internal fun ScreenshotUiTest.assertGlassContainerRemovalRestoresOutsidePixels() {
  val enabled = mutableStateOf(false)
  setContent { GlassContainerScreenshotScene(GlassContainerScene.Merged, enabled.value) }
  waitForIdle()
  val outside = captureRootPixels().colours()
  assertThat(captureRootPixels()[149, 110]).isEqualTo(sceneBackground(149, 110))
  enabled.value = true
  waitForIdle()
  assertThat(captureRootPixels()[149, 110].green).isLessThan(0.8f)
  enabled.value = false
  waitForIdle()
  assertThat(captureRootPixels().colours()).isEqualTo(outside)
}

/** Grows B out of A ([grow]) or retracts it into A, capturing the first, middle and settled frames. */
internal fun ScreenshotUiTest.assertGlassContainerTransitionSequence(grow: Boolean) {
  val present = mutableStateOf(!grow)
  setContent { GlassContainerScreenshotScene(GlassContainerScene.Merged, secondPresent = present.value, transitionSpec = tween(300, easing = LinearEasing)) }
  waitForIdle()
  mainClock.autoAdvance = false
  present.value = grow
  mainClock.advanceTimeByFrame()
  waitForIdle()
  var pixels = captureRootPixels()
  if (grow) {
    assertThat(pixels[220, 110].blue - pixels[220, 110].red).isLessThan(0.05f)
    assertThat(pixels[80, 110].red).isGreaterThan(pixels[80, 110].blue)
  } else {
    assertMerged(pixels)
  }
  captureRoot("frame0")
  // Whole 16ms frames: 144ms of the 300ms transition.
  mainClock.advanceTimeBy(150)
  waitForIdle()
  pixels = captureRootPixels()
  // B's tint is mid-interpolation from A's red, so it is mixed rather than blue-dominant.
  assertThat(pixels[190, 110].blue).isGreaterThan(0.3f)
  assertThat(pixels[190, 110]).isNotEqualTo(sceneBackground(190, 110))
  if (grow) {
    assertThat(pixels[149, 110]).isNotEqualTo(sceneBackground(149, 110))
  } else {
    assertThat(pixels[240, 110]).isEqualTo(sceneBackground(240, 110))
  }
  captureRoot("frame150")
  mainClock.advanceTimeBy(320)
  waitForIdle()
  pixels = captureRootPixels()
  if (grow) {
    assertMerged(pixels)
  } else {
    assertThat(pixels[220, 110]).isEqualTo(sceneBackground(220, 110))
    assertThat(pixels[80, 110].red).isGreaterThan(pixels[80, 110].blue)
  }
  captureRoot("settled")
}

private fun assertMerged(pixels: PixelMap) {
  val bridge = pixels[149, 110]
  assertThat(bridge.red).isGreaterThan(0.3f)
  assertThat(bridge.blue).isGreaterThan(0.3f)
  assertThat(bridge.green).isLessThan(0.8f)
  assertThat(pixels[80, 110].red).isGreaterThan(pixels[80, 110].blue)
  assertThat(pixels[220, 110].blue).isGreaterThan(pixels[220, 110].red)
}

private fun PixelMap.colours(): List<Color> = List(width * height) { this[it % width, it / width] }

private fun sceneBackground(x: Int, y: Int): Color =
  if ((x / 12 + y / 12) % 2 == 0) Color.White else Color(0xFFDDDDDD)

private fun sceneStyle(tint: Color, background: Color = Color.Transparent, clear: Boolean = false): GlassStyle =
  (if (clear) GlassStyle.clear else GlassStyle.regular).then {
    optics(GlassOptics(refractionStrength = 0f, refractionDisplacement = 0.dp, refractionDetailIntensity = 0f, depth = OpticalSizeValue.Fixed(0f), blurRadius = OpticalSizeValue.Fixed(0.dp)))
    tint(tint)
    backgroundColor(background)
    shape(RoundedCornerShape(12.dp))
    edgeSoftness(0.dp)
    edgeShadow(Color.Transparent)
    specularIntensity(0f)
    ambientResponse(0f)
    chromaticAberrationStrength(0f)
    contrast(0f)
    whitePoint(0f)
    chromaMultiplier(1f)
  }

/** Separate regression scene: shared platform geometry, no matrix catalog enrollment. */
@Composable
private fun GlassContainerScreenshotScene(
  scene: GlassContainerScene,
  enabled: Boolean = true,
  interactionSource: MutableInteractionSource? = null,
  secondPresent: Boolean = true,
  transitionSpec: FiniteAnimationSpec<Float> = spring(),
) {
  CompositionLocalProvider(LocalDensity provides Density(1f)) {
    val state = remember { HazeState() }
    Box(Modifier.size(360.dp, 240.dp).background(Color.White)) {
      Canvas(Modifier.size(360.dp, 240.dp).hazeSource(state)) {
        for (y in 0 until 240 step 12) for (x in 0 until 360 step 12) {
          val colour = sceneBackground(x, y).let { if (scene == GlassContainerScene.MixedColours) it.copy(alpha = 0.25f) else it }
          drawRect(colour, Offset(x.toFloat(), y.toFloat()), Size(12f, 12f))
        }
      }
      Box(Modifier.size(360.dp, 240.dp).then(if (enabled) Modifier.hazeGlassContainer(40.dp, transitionSpec) else Modifier)) {
        when (scene) {
          GlassContainerScene.Overflow -> repeat(6) { index ->
            GlassContainerSceneMember(state, 20 + index * 34, 60, 30, 80, sceneStyle(if (index % 2 == 0) Color.Red else Color.Blue))
          }
          GlassContainerScene.Nested -> {
            GlassContainerSceneMember(state, 40, 60, 100, 80, sceneStyle(Color.Red))
            Box(Modifier.offset(150.dp, 60.dp).size(100.dp, 80.dp).hazeGlassContainer(40.dp)) {
              GlassContainerSceneMember(state, 0, 0, 45, 80, sceneStyle(Color.Blue))
              GlassContainerSceneMember(state, 55, 0, 45, 80, sceneStyle(Color.Red))
            }
          }
          GlassContainerScene.InteractiveRest, GlassContainerScene.InteractivePressed -> {
            // A 10px gap stays under the 20px bridge threshold while A is pressed to 0.95.
            val pressed = GlassStyle {
              pressed {
                scale(0.95f)
                lightingIntensity(1f)
              }
            }
            GlassContainerSceneMember(state, 40, 60, 100, 80, sceneStyle(Color.Red).then(pressed), interactionSource)
            GlassContainerSceneMember(state, 150, 60, 100, 80, sceneStyle(Color.Blue).then(pressed))
          }
          GlassContainerScene.Overlap -> {
            GlassContainerSceneMember(state, 40, 60, 100, 80, sceneStyle(Color.Red.copy(alpha = 0.5f)))
            GlassContainerSceneMember(state, 110, 80, 100, 80, sceneStyle(Color.Blue.copy(alpha = 0.5f)))
          }
          else -> {
            val mixed = scene == GlassContainerScene.MixedColours
            GlassContainerSceneMember(state, 40, 60, 100, 80, sceneStyle(if (mixed) Color.Red.copy(alpha = 0.65f) else Color.Red, if (mixed) Color(0xFFFFCC99) else Color.Transparent))
            if (secondPresent) GlassContainerSceneMember(state, if (scene == GlassContainerScene.Separate) 160 else 159, 60, 100, 80, sceneStyle(if (mixed) Color.Blue.copy(alpha = 0.65f) else Color.Blue, if (mixed) Color(0xFF99CCFF) else Color.Transparent, scene == GlassContainerScene.MixedKeys))
          }
        }
      }
    }
  }
}

@Composable
private fun GlassContainerSceneMember(
  state: HazeState,
  x: Int,
  y: Int,
  width: Int,
  height: Int,
  style: GlassStyle,
  interactionSource: MutableInteractionSource? = null,
) {
  Box(
    Modifier.offset(x.dp, y.dp).size(width.dp, height.dp).hazeGlass(
      input = HazeInput.Sources(state),
      style = style,
      performanceMode = HazePerformanceMode.Quality,
      interactionSource = interactionSource,
      interactionTransformPivot = GlassTransformPivot.Center,
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
    ),
  ) {
    // Small opaque label marks leave the interior and inter-member bridge observable.
    Box(Modifier.offset(6.dp, 6.dp).size(12.dp, 4.dp).background(Color.White))
    Box(Modifier.offset(6.dp, 12.dp).size(18.dp, 4.dp).background(Color.White))
  }
}
