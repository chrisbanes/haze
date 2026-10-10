// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.math.abs
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
  fun mergePrototype_resolvesOpticsFromSmallerMemberNotContainer() {
    val merged = params(members(gapPx = 12f))
    // A lone 60x40 member, resolved by the normal single-shape path.
    val member = buildGlassRenderParams(
      resolveGlassStyle(
        GlassRuntimeEffect().apply { style = GlassStyle.regular },
        Size(60f, 40f),
        Density(1f),
        LayoutDirection.Ltr,
      ),
      GlassCoordinates(Size(60f, 40f), Offset.Zero, Size(60f, 40f), 1f),
    )
    val container = params(merge = null)
    assertThat(container.depth, "container depth differs, so this test is sensitive")
      .isNotEqualTo(member.depth)

    assertThat(merged.depth).isEqualTo(member.depth)
    assertThat(merged.blurRadiusPx).isEqualTo(member.blurRadiusPx)
    assertThat(merged.refractionHeightPx).isEqualTo(member.refractionHeightPx)
    assertThat(merged.edgeRefractionWidthPx).isEqualTo(member.edgeRefractionWidthPx)
  }

  @Test
  fun fullyOverlappingMembers_drawTheirUnionWithoutGrowth() {
    // Both members on the same 60x40 rect at x 70..130, y 20..60.
    val pixels = render(members(gapPx = -60f)).toPixelMap()
    assertThat(pixels[100, 40].alpha, "member centre alpha").isGreaterThan(0f)
    // The unscaled smooth minimum grows the union by spacing / 2 (12 px) on every side.
    assertThat(pixels[134, 40].alpha, "4 px right of the member").isEqualTo(0f)
    assertThat(pixels[100, 64].alpha, "4 px below the member").isEqualTo(0f)
  }

  @Test
  fun mergeSmoothing_keepsSpacingWhileApartAndFadesToZeroAtFullOverlap() {
    fun smoothing(gapPx: Float) = members(gapPx).resolve(Density(1f), LayoutDirection.Ltr).smoothingPx
    assertThat(smoothing(gapPx = 12f)).isEqualTo(24f)
    assertThat(smoothing(gapPx = 0f)).isEqualTo(24f)
    assertThat(smoothing(gapPx = -60f)).isEqualTo(0f)
    val partial = smoothing(gapPx = -20f)
    assertThat(partial).isGreaterThan(0f)
    assertThat(24f - partial).isGreaterThan(0f)
  }

  @Test
  fun thinBridge_doesNotRefractContentAcrossItsMidline() {
    // At a 16 px gap with 24 px spacing the bridge is about 30 px tall, thinner than twice the
    // 20 px refraction width, so both edges would otherwise shift the ramp by up to ~0.07.
    val merge = members(gapPx = 16f)
    val refracted = render(merge, source = VerticalRamp).toPixelMap()
    val unrefracted = render(merge, source = VerticalRamp, refraction = false).toPixelMap()
    val neckColumn = 100
    for (y in 28..52) {
      val difference = abs(refracted[neckColumn, y].red - unrefracted[neckColumn, y].red)
      assertThat(difference, "red difference at neck y=$y").isLessThan(0.02f)
    }
  }

  @Test
  fun mergedChromaCornerWeight_usesNearestMemberNotContainer() {
    for (source in listOf(GlassShaders.buildFused(merged = true), GlassShaders.buildOptical(merged = true))) {
      assertThat(source).contains("memberCornerWeight(localCoord)")
      assertThat(RuntimeEffect.makeForShader(source)).isNotNull()
    }
    for (source in listOf(GlassShaders.buildFused(), GlassShaders.buildOptical())) {
      assertThat(source).doesNotContain("memberCornerWeight")
    }
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

  @Test
  fun mergedRefractionDetailSource_compilesWithAndWithoutCoverage() {
    for (coverageOnly in listOf(false, true)) {
      val source = GlassShaders.buildRefractionDetail(coverageOnly = coverageOnly, merged = true)
      assertThat(RuntimeEffect.makeForShader(source), "coverageOnly=$coverageOnly").isNotNull()
    }
  }

  /** Draws the merged optical stage, then the merged rim, over an opaque [source]. */
  private fun render(
    merge: GlassMergePrototype,
    source: Brush = SolidColor(Color(0xFF3366CC)),
    refraction: Boolean = true,
  ): ImageBitmap {
    val params = params(merge, refraction)
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
          drawRect(bounds, Paint().also { source.applyTo(MATERIAL, it, 1f) })
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

  private fun params(merge: GlassMergePrototype?, refraction: Boolean = true): GlassRenderParams {
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle.regular.then {
        mergePrototype(merge)
        if (!refraction) optics(GlassDefaults.optics.copy(refractionStrength = 0f))
      }
    }
    return buildGlassRenderParams(
      resolveGlassStyle(effect, MATERIAL, Density(1f), LayoutDirection.Ltr),
      GlassCoordinates(MATERIAL, Offset.Zero, MATERIAL, 1f),
    )
  }

  private companion object {
    val MATERIAL = Size(200f, 80f)
    val VerticalRamp = Brush.verticalGradient(listOf(Color.Black, Color.Red), startY = 0f, endY = 80f)
  }
}
