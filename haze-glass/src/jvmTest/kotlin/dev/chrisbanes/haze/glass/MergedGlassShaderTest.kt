// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

/** Prototype (#1438): two rounded members merged into one smooth-minimum Glass field. */
class MergedGlassShaderTest {
  @Test
  fun mergePrototype_changesRenderParamsAndEffectKeys() {
    val merge = members(gapPx = 12f)
    val merged = params(merge)
    assertThat(merged).isNotEqualTo(params(merge = null))

    val moved = params(merge.withRectBLeft(merge.rectB.left + 1.dp))
    assertThat(moved.opticalEffectKey()).isNotEqualTo(merged.opticalEffectKey())
    assertThat(moved.refractionDetailEffectKey()).isNotEqualTo(merged.refractionDetailEffectKey())
    assertThat(moved.rimEffectKey()).isNotEqualTo(merged.rimEffectKey())
  }

  @Test
  fun gapBelowSpacing_bridgesNeck() {
    val pixels = render(members(gapPx = 12f)).toPixelMap()
    assertThat(pixels[100, 40].alpha, "neck midpoint alpha").isGreaterThan(0f)
  }

  @Test
  fun gapAboveSpacing_leavesTwoSeparateMembers() {
    val pixels = render(members(gapPx = 40f)).toPixelMap()
    assertThat(pixels[100, 40].alpha, "gap midpoint alpha").isEqualTo(0f)
    assertThat(pixels[50, 40].alpha, "member A centre alpha").isGreaterThan(0f)
    assertThat(pixels[150, 40].alpha, "member B centre alpha").isGreaterThan(0f)
  }

  @Test
  fun mergedFusedSource_compilesForEveryVariant() {
    for (interactionOptics in listOf(false, true)) {
      for (sharpDetail in listOf(false, true)) {
        val source = GlassShaders.buildFused(
          interactionOptics = interactionOptics,
          sharpDetail = sharpDetail,
          merged = true,
        )
        assertThat(RuntimeEffect.makeForShader(source), "interaction=$interactionOptics, detail=$sharpDetail")
          .isNotNull()
      }
    }
  }

  /** Draws the merged optical stage, then the merged rim, over an opaque uniform source. */
  private fun render(merge: GlassMergePrototype): ImageBitmap {
    val params = params(merge)
    val optical = createRuntimeShaderRenderEffect(
      RuntimeEffect.makeForShader(GlassShaders.buildOptical(merged = true)),
      arrayOf("content"),
      arrayOf(null),
    ) { setOpticalUniforms(params.opticalEffectKey()) }
    val rim = createRuntimeShaderRenderEffect(
      RuntimeEffect.makeForShader(GlassShaders.buildRim(merged = true)),
      arrayOf("content"),
      arrayOf(null),
    ) { setRimUniforms(params.rimEffectKey()) }
    return ImageBitmap(MATERIAL.width.toInt(), MATERIAL.height.toInt()).also { image ->
      with(Canvas(image)) {
        val bounds = Rect(Offset.Zero, MATERIAL)
        for (filter in listOf(optical, rim)) {
          saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = filter })
          drawRect(bounds, Paint().apply { color = Color(0xFF3366CC) })
          restore()
        }
      }
    }
  }

  private fun GlassMergePrototype.withRectBLeft(left: Dp) = GlassMergePrototype(
    rectA = rectA,
    shapeA = shapeA,
    rectB = DpRect(left, rectB.top, left + rectB.width, rectB.bottom),
    shapeB = shapeB,
    spacing = spacing,
  )

  /** Two 60x40 px members with 20 px radii, centred in a 200x80 px material, [gapPx] apart. */
  private fun members(gapPx: Float, spacingPx: Float = 24f): GlassMergePrototype {
    val leftA = (MATERIAL.width - 120f - gapPx) / 2f
    val leftB = leftA + 60f + gapPx
    return GlassMergePrototype(
      rectA = DpRect(leftA.dp, 20.dp, (leftA + 60f).dp, 60.dp),
      shapeA = RoundedCornerShape(20.dp),
      rectB = DpRect(leftB.dp, 20.dp, (leftB + 60f).dp, 60.dp),
      shapeB = RoundedCornerShape(20.dp),
      spacing = spacingPx.dp,
    )
  }

  private fun params(merge: GlassMergePrototype?): GlassRenderParams {
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle.regular.then { mergePrototype(merge) }
    }
    return buildGlassRenderParams(
      resolveGlassStyle(effect, MATERIAL, Density(1f), LayoutDirection.Ltr),
      GlassCoordinates(MATERIAL, Offset.Zero, MATERIAL, 1f),
    )
  }

  private companion object {
    val MATERIAL = Size(200f, 80f)
  }
}
