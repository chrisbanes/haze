// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class GlassDefaultCandidateStyleTest {
  private val seed = GlassStyle { shape(ContinuousRoundedCornerShape(16.dp, smoothing = 1f)) }

  private fun candidate(clear: Boolean, c2: Boolean, dark: Boolean): GlassStyle =
    (if (clear) GlassStyle.clear else GlassStyle.regular).then {
      edgeOutline(Color.Black.copy(alpha = if (c2) 0.25f else 0.4f), 0.7.dp)
      rimHighlight(
        if (clear) {
          if (c2) 0.8f else 1f
        } else {
          if (c2) 0.45f else 0.6f
        },
        1.dp,
        0f,
      )
      if (clear) {
        specularIntensity(0.55f)
        edgeShadow(Color.Black.copy(alpha = 0.1f))
        ambientResponse(0.42f)
        contrast(0.08f)
        whitePoint(0.17f)
        chromaMultiplier(1.05f)
      } else if (dark) {
        whitePoint(if (c2) 0f else -0.10f)
        contrast(if (c2) -0.04f else 0.02f)
      }
    }

  @Test
  fun candidateShapeSeedPreservesAuthoredShapesAndOrder() {
    val rounded = RoundedCornerShape(topStart = 4.dp, topEnd = 9.dp, bottomEnd = 13.dp, bottomStart = 17.dp)
    val continuous = ContinuousRoundedCornerShape(CornerSize(4.dp), CornerSize(9.dp), CornerSize(13.dp), CornerSize(17.dp), smoothing = 0.6f)
    val expectedSeed = ContinuousRoundedCornerShape(16.dp, smoothing = 1f)
    assertThat(resolveGlassStyleValues(GlassStyle, GlassStyle.regular).shape).isEqualTo(GlassDefaults.shape)
    for (clear in listOf(false, true)) for (c2 in listOf(false, true)) {
      val builtIn = candidate(clear, c2, dark = true)
      assertThat(resolveGlassStyleValues(seed, builtIn).shape).isEqualTo(expectedSeed)
      assertThat(resolveGlassStyleValues(seed, GlassStyle { tint(Color.Red) }).shape).isEqualTo(expectedSeed)
      for (shapeStyle in listOf(GlassStyle { shape(rounded) }, GlassStyle { shape(continuous) })) {
        val expected = resolveGlassStyleValues(GlassStyle, shapeStyle).shape
        assertThat(resolveGlassStyleValues(seed.then(shapeStyle), builtIn).shape).isSameInstanceAs(expected)
        assertThat(resolveGlassStyleValues(seed, builtIn.then(shapeStyle)).shape).isSameInstanceAs(expected)
        assertThat(resolveGlassStyleValues(seed, shapeStyle.then(builtIn)).shape).isSameInstanceAs(expected)
        assertThat(resolveGlassStyleValues(seed.then { shape(RoundedCornerShape(3.dp)) }, builtIn.then(shapeStyle)).shape).isSameInstanceAs(expected)
      }
    }
  }

  @Test
  fun candidatePreservesIndependentAndOrderedCallerWrites() {
    for (clear in listOf(false, true)) for (c2 in listOf(false, true)) {
      val material = candidate(clear, c2, dark = true)
      val authored = GlassStyle {
        tint(Color.Red)
        backgroundColor(Color.Transparent)
        fallbackColor(Color.Blue)
        alpha(0.7f)
        lightPosition(Alignment.BottomEnd)
        hovered { lightingIntensity(0.3f) }
      }
      val values = resolveGlassStyleValues(seed.then(authored), material, GlassSystemAppearance.Dark)
      assertThat(values.tint).isEqualTo(Color.Red)
      assertThat(values.backgroundColor).isEqualTo(Color.Transparent)
      assertThat(values.fallbackColor).isEqualTo(Color.Blue)
      assertThat(values.alpha).isEqualTo(0.7f)
      assertThat(values.lightPosition).isEqualTo(Alignment.BottomEnd)
      assertThat(values.hoveredInteraction?.lightingIntensity?.value).isEqualTo(0.3f)
      val overridden = resolveGlassStyleValues(
        seed.then(authored),
        material.then {
          tint(Color.Transparent)
          fallbackColor(Color.Unspecified)
          contrast(0.3f)
        },
        GlassSystemAppearance.Dark,
      )
      assertThat(overridden.tint).isEqualTo(Color.Transparent)
      assertThat(overridden.fallbackColor).isEqualTo(Color.Unspecified)
      assertThat(resolveGlassStyleValues(seed, material.then { fallbackColor(Color.Transparent) }).fallbackColor).isEqualTo(Color.Transparent)
      assertThat(overridden.contrast).isEqualTo(0.3f)
    }
  }

  @Test
  fun candidatesRespectAccessibilityPrecedence() {
    for (appearance in GlassSystemAppearance.entries) for (clear in listOf(false, true)) for (c2 in listOf(false, true)) {
      val effect = GlassRuntimeEffect().apply {
        systemAppearance = appearance
        style = candidate(clear, c2, appearance == GlassSystemAppearance.Dark).then { alpha(0.7f) }
        accessibilitySettings = GlassAccessibilitySettings(reduceTransparency = true, increaseContrast = true, showBorders = true)
      }
      fun resolve() = resolveGlassStyle(effect, Size(280f, 176f), Density(1f), LayoutDirection.Ltr)
      val resolved = resolve()
      assertThat(resolved.fallbackBaseColor).isEqualTo(if (appearance == GlassSystemAppearance.Dark) Color.Black else Color.White)
      assertThat(resolved.alpha).isEqualTo(0.7f)
      assertThat(resolved.resolvedOptics.depth).isEqualTo(1f)
      assertThat(resolved.resolvedOptics.blurRadiusPx).isGreaterThanOrEqualTo(20f)
      assertThat(resolved.contrast).isGreaterThanOrEqualTo(0.16f)
      assertThat(resolved.specularIntensity).isGreaterThanOrEqualTo(0.58f)
      effect.accessibilitySettings = GlassAccessibilitySettings(reduceTransparency = true, reducedTransparencyFallbackColor = Color.Blue)
      assertThat(resolve().fallbackBaseColor).isEqualTo(Color.Blue)
    }
  }

  @Test
  fun clearPinsMatchLightResponseInBothAppearances() {
    val expected = resolveGlassStyleValues(GlassStyle, GlassStyle.clearLight)
    for (appearance in GlassSystemAppearance.entries) for (c2 in listOf(false, true)) {
      val actual = resolveGlassStyleValues(seed, candidate(clear = true, c2 = c2, dark = appearance == GlassSystemAppearance.Dark), appearance)
      assertThat(actual.specularIntensity).isEqualTo(expected.specularIntensity)
      assertThat(actual.edgeShadow).isEqualTo(expected.edgeShadow)
      assertThat(actual.ambientResponse).isEqualTo(expected.ambientResponse)
      assertThat(actual.contrast).isEqualTo(expected.contrast)
      assertThat(actual.whitePoint).isEqualTo(expected.whitePoint)
      assertThat(actual.chromaMultiplier).isEqualTo(expected.chromaMultiplier)
    }
  }
}
