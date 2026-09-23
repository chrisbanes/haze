// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import haze_root.haze_screenshot_tests.generated.resources.Res
import haze_root.haze_screenshot_tests.generated.resources.photo
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import org.jetbrains.compose.resources.painterResource

class GlassBuiltInStyleScreenshotTest : ScreenshotTest() {
  @Test
  fun regular_suppressesMediumScaleDetail() = checkMediumScaleDiffusion(HazePerformanceMode.Quality)

  @Test
  fun regular_minimumQualitySuppressesMediumScaleDetail() = checkMediumScaleDiffusion(HazePerformanceMode.Performance)

  @Test
  fun regular_suppressesMediumScaleDetailAtLowDensity() = checkMediumScaleDiffusion(HazePerformanceMode.Quality, 1f)

  @Test
  fun regular_minimumQualitySuppressesMediumScaleDetailAtLowDensity() = checkMediumScaleDiffusion(HazePerformanceMode.Performance, 1f)

  private fun checkMediumScaleDiffusion(mode: HazePerformanceMode, densityScale: Float = 3f) = runScreenshotTest(size = Size(1206f, 1600f)) {
    var blurEnabled by mutableStateOf(false)
    setContent {
      androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(densityScale),
      ) {
        val hazeState = remember { HazeState() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
            drawRect(Color(0xFF333333))
            val period = 32.dp.toPx()
            var x = 0f
            while (x < size.width) {
              drawRect(Color(0xFFCCCCCC), Offset(x, 0f), Size(period / 2f, size.height))
              x += period
            }
          }
          Box(
            Modifier.size(280.dp, 176.dp).testTag("medium-detail").hazeGlass(
              input = HazeInput.Sources(hazeState),
              style = GlassStyle.regular.then {
                optics(
                  GlassDefaults.optics.copy(
                    refractionStrength = 0f,
                    blurRadius = if (blurEnabled) GlassDefaults.optics.blurRadius else OpticalSizeValue.Fixed(0.dp),
                  ),
                )
              },
              performanceMode = mode,
            ),
          )
        }
      }
    }
    waitForIdle()
    val center = insetBounds("medium-detail", 40.dp, 176.dp)
    val material = onNodeWithTag("medium-detail").fetchSemanticsNode().boundsInRoot
    val density = material.height / 176f
    val edge = IntRect(center.left, (material.top + 8f * density).toInt(), center.right, (material.top + 24f * density).toInt())
    val regions = mapOf("center" to center, "edge" to edge)
    val sharp = captureRootPixels().snapshot()
    blurEnabled = true
    waitForIdle()
    if (isRuntimeShaderRenderEffectSupported()) {
      val blurred = captureRootPixels().snapshot()
      // The previous 38.5px source-space cap retained about a third of this 32dp pattern.
      regions.forEach { (name, bounds) ->
        val sharpEnergy = sharp.highFrequencyEnergy(bounds)
        assertThat(sharpEnergy, "$name positive control").isGreaterThan(0.0001f)
        assertThat(blurred.highFrequencyEnergy(bounds) / sharpEnergy, "Regular $name medium-scale detail retention")
          .isLessThan(0.15f)
      }
    }
  }

  @Test
  fun regular_matchesNativeReferenceGeometry() = captureNativeReference(GlassStyle.regular, HazePerformanceMode.Adaptive)

  @Test
  fun regular_referenceAtFullQuality() = captureNativeReference(GlassStyle.regular, HazePerformanceMode.Quality)

  @Test
  fun regular_referenceAtMinimumQuality() = captureNativeReference(GlassStyle.regular, HazePerformanceMode.Performance)

  @Test
  fun regular_diffusesPhotographicBackdrop() = captureNativeReference(GlassStyle.regular, HazePerformanceMode.Adaptive, photograph = true)

  @Test
  fun clear_matchesNativeReferenceGeometry() = captureNativeReference(GlassStyle.clear, HazePerformanceMode.Adaptive)

  @Test
  fun clear_referenceAtFullQuality() = captureNativeReference(GlassStyle.clear, HazePerformanceMode.Quality)

  @Test
  fun clear_referenceAtMinimumQuality() = captureNativeReference(GlassStyle.clear, HazePerformanceMode.Performance)

  @Test
  fun clear_preservesPhotographicBackdrop() = captureNativeReference(GlassStyle.clear, HazePerformanceMode.Adaptive, photograph = true)

  @Test
  fun regular_darkAppearanceOverStructuredBackdrop() = captureNativeReference(
    GlassStyle.regular,
    HazePerformanceMode.Adaptive,
    appearance = GlassTestSystemAppearance.Dark,
    size = Size(420f, 800f),
    densityScale = 1f,
  )

  @Test
  fun clear_darkAppearanceOverPhotographicBackdrop() = captureNativeReference(
    GlassStyle.clear,
    HazePerformanceMode.Adaptive,
    photograph = true,
    appearance = GlassTestSystemAppearance.Dark,
    size = Size(420f, 800f),
    densityScale = 1f,
  )

  @Test
  fun regular_attachedAppearanceSwitchUpdatesAndRestoresPixels() =
    checkAttachedAppearanceSwitch(GlassStyle.regular)

  @Test
  fun clear_attachedAppearanceSwitchUpdatesAndRestoresPixels() =
    checkAttachedAppearanceSwitch(GlassStyle.clear)

  private fun checkAttachedAppearanceSwitch(style: GlassStyle) = runScreenshotTest(size = Size(420f, 800f)) {
    var appearance by mutableStateOf(GlassTestSystemAppearance.Light)
    setContent {
      WithGlassTestSystemAppearance(appearance) {
        androidx.compose.runtime.CompositionLocalProvider(
          androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f),
        ) { GlassBuiltInStyleGeometrySample(style) }
      }
    }
    waitForIdle()
    val light = captureRootPixels().snapshot()
    appearance = GlassTestSystemAppearance.Dark
    waitForIdle()
    val dark = captureRootPixels().snapshot()
    assertThat(dark.changedPixelRatio(light)).isGreaterThan(0.01f)
    appearance = GlassTestSystemAppearance.Light
    waitForIdle()
    val restored = captureRootPixels().snapshot()
    assertThat(restored.meanAbsoluteDifference(light)).isLessThan(1f / 255f)
  }

  private fun captureNativeReference(
    style: GlassStyle,
    mode: HazePerformanceMode,
    photograph: Boolean = false,
    appearance: GlassTestSystemAppearance = GlassTestSystemAppearance.Light,
    size: Size = Size(1206f, 2622f),
    densityScale: Float = 3f,
  ) = runScreenshotTest(size = size) {
    setContent {
      WithGlassTestSystemAppearance(appearance) {
        androidx.compose.runtime.CompositionLocalProvider(
          androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(densityScale),
        ) { GlassBuiltInStyleGeometrySample(style, photograph, mode) }
      }
    }
    waitForIdle()
    captureRoot()
  }

  @Test
  fun builtInStyles_blurRespondsToMaterialSize() = runScreenshotTest(
    size = Size(1080f, 1600f),
  ) {
    var style by mutableStateOf<GlassStyle?>(null)
    setContent { GlassBuiltInStyleGeometrySample(style) }
    waitForIdle()

    val regions = geometryRegions()
    val identity = captureRootPixels().snapshot()

    style = GlassStyle.regular
    waitForIdle()
    val regular = captureRootPixels().snapshot()

    style = GlassStyle.clear
    waitForIdle()
    val clear = captureRootPixels().snapshot()

    if (isRuntimeShaderRenderEffectSupported()) {
      val regularRetention = regions.mapValues { (_, bounds) ->
        regular.highFrequencyEnergy(bounds) / identity.highFrequencyEnergy(bounds)
      }
      val clearRetention = regions.mapValues { (_, bounds) ->
        clear.highFrequencyEnergy(bounds) / identity.highFrequencyEnergy(bounds)
      }
      println("Regular high-frequency retention: $regularRetention")
      println("Clear high-frequency retention: $clearRetention")

      assertSizeProgression(regularRetention, "Regular")
      // Native Regular keeps refracted detail on compact controls while fully diffusing cards.
      // Medium-scale detail has a separate regression so the fine grid cannot hide a blur cap.
      assertThat(regularRetention.getValue("capsule"), "Regular capsule diffusion")
        .isLessThan(0.35f)
      assertThat(regularRetention.getValue("card"), "Regular card diffusion")
        .isLessThan(0.025f)
      assertThat(regularRetention.getValue("panel"), "Regular panel diffusion")
        .isLessThan(0.01f)
      regions.keys.forEach { region ->
        if (region != "capsule") {
          assertThat(regularRetention.getValue(region), "$region Regular retention")
            .isLessThan(clearRetention.getValue(region))
        }
        // Clear diffuses fine detail without becoming progressively frosted at larger sizes.
        assertThat(clearRetention.getValue(region), "$region Clear diffusion")
          .isLessThan(0.4f)
        assertThat(clearRetention.getValue(region), "$region Clear detail retention")
          .isGreaterThan(0.2f)
      }
      assertThat(clearRetention.values.max() - clearRetention.values.min(), "Clear size variation")
        .isLessThan(0.1f)
    }
    captureRoot("clear", unmatchedPixelThreshold = 0.01f)
    style = GlassStyle.regular
    waitForIdle()
    captureRoot("regular")
  }
}

private fun ScreenshotUiTest.geometryRegions(): Map<String, IntRect> = mapOf(
  "capsule" to insetBounds("capsule", inset = 16.dp, logicalHeight = 64.dp),
  "card" to insetBounds("card", inset = 24.dp, logicalHeight = 176.dp),
  "panel" to insetBounds("panel", inset = 24.dp, logicalHeight = 220.dp),
)

private fun ScreenshotUiTest.insetBounds(
  tag: String,
  inset: Dp,
  logicalHeight: Dp,
): IntRect {
  val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
  val density = bounds.height / logicalHeight.value
  return bounds.inflate(-inset.value * density).toIntRect()
}

private fun Rect.toIntRect(): IntRect = IntRect(
  left = floor(left).toInt(),
  top = floor(top).toInt(),
  right = ceil(right).toInt(),
  bottom = ceil(bottom).toInt(),
)

private fun assertSizeProgression(retention: Map<String, Float>, label: String) {
  assertThat(retention.getValue("capsule"), "$label capsule retention")
    .isGreaterThan(retention.getValue("card"))
  assertThat(retention.getValue("capsule"), "$label capsule versus panel retention")
    .isGreaterThan(retention.getValue("panel"))
}

@Composable
private fun GlassBuiltInStyleGeometrySample(
  style: GlassStyle?,
  photograph: Boolean = false,
  performanceMode: HazePerformanceMode = HazePerformanceMode.Quality,
) {
  val hazeState = remember { HazeState() }

  Box(Modifier.fillMaxSize()) {
    if (photograph) {
      Image(
        painter = painterResource(Res.drawable.photo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
      )
    } else {
      Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
        drawRect(Color(0xFF07141A))
        val spacing = 8.dp.toPx()
        val strokeWidth = 1.dp.toPx()
        var x = 0f
        while (x < size.width) {
          drawLine(
            color = Color.White.copy(alpha = 0.8f),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = strokeWidth,
          )
          x += spacing
        }
        var y = 0f
        while (y < size.height) {
          drawLine(
            color = Color.Cyan.copy(alpha = 0.65f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth,
          )
          y += spacing
        }
      }
    }

    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      GlassBuiltInStyleSurface(
        tag = "capsule",
        width = 240.dp,
        height = 64.dp,
        shape = RoundedCornerShape(32.dp),
        style = style,
        hazeState = hazeState,
        performanceMode = performanceMode,
      )
      GlassBuiltInStyleSurface(
        tag = "card",
        width = 280.dp,
        height = 176.dp,
        shape = RoundedCornerShape(28.dp),
        style = style,
        hazeState = hazeState,
        performanceMode = performanceMode,
      )
      GlassBuiltInStyleSurface(
        tag = "panel",
        width = 320.dp,
        height = 220.dp,
        shape = RoundedCornerShape(32.dp),
        style = style,
        hazeState = hazeState,
        performanceMode = performanceMode,
      )
    }
  }
}

@Composable
private fun GlassBuiltInStyleSurface(
  tag: String,
  width: Dp,
  height: Dp,
  shape: RoundedCornerShape,
  style: GlassStyle?,
  hazeState: HazeState,
  performanceMode: HazePerformanceMode,
) {
  val modifier = Modifier
    .size(width, height)
    .testTag(tag)
  Box(
    modifier = if (style != null) {
      modifier.hazeGlass(
        input = HazeInput.Sources(hazeState),
        style = style.then { shape(shape) },
        performanceMode = performanceMode,
      )
    } else {
      modifier
    },
  )
}
