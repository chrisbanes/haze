// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.math.abs
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

/** Exercises the actual optical, coverage, and sharp-detail stages used by glass containers. */
class GlassContainerOpticalPipelineTest {
  @Test
  fun approachingMember_preservesRenderedOutputAcrossFusedAndMultipassPipelines() {
    val original = renderBackdrop().toPixelMap()
    for (fused in listOf(false, true)) {
      val standalone = render(fused = fused, gap = 40.1f).toPixelMap()
      val merged = render(fused = fused, gap = 39.9f).toPixelMap()
      val differencesFromBackdrop = probePoints.maxOf { (x, y) ->
        standalone[x, y].maxRgbDistance(original[x, y])
      }
      assertThat(differencesFromBackdrop, "Refraction should visibly change the patterned backdrop, fused=$fused")
        .isGreaterThan(3f / 255f)

      for ((x, y) in probePoints) {
        assertThat(
          standalone[x, y].maxRgbDistance(merged[x, y]),
          "First-member output across the merge boundary at $x,$y, fused=$fused",
        ).isLessThan(4f / 255f)
      }
    }
  }

  private fun renderBackdrop(): ImageBitmap = ImageBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT).also { image ->
    with(Canvas(image)) { drawPattern(Rect(Offset.Zero, sampleSize)) }
  }

  private fun render(fused: Boolean, gap: Float): ImageBitmap {
    val merged = gap < 40f
    val layoutSize = Size(120f, 40f)
    val materialSize = if (merged) Size(layoutSize.width * 2f + gap, layoutSize.height) else layoutSize
    val field = if (merged) {
      GlassMergedField(
        members = listOf(
          mergedMember(Rect(0f, 0f, 120f, 40f), layoutSize),
          mergedMember(Rect(120f + gap, 0f, 240f + gap, 40f), layoutSize),
        ),
        mergeDistancePx = 40f,
        opticalSize = sharedOpticalSize,
      )
    } else {
      null
    }
    val effect = GlassRuntimeEffect().apply {
      opticalSizeOverride = sharedOpticalSize
      mergedField = field
      style = GlassStyle {
        shape(RoundedCornerShape(8.dp))
        optics(
          GlassOptics(
            refractionStrength = 1f,
            refractionHeightFraction = 0.5f,
            refractionDisplacement = 56.dp,
            depth = OpticalSizeValue.Fixed(0f),
            blurRadius = OpticalSizeValue.Fixed(0.dp),
          ),
        )
        ambientResponse(1f)
        tint(Color.Transparent)
      }
    }
    val params = buildGlassRenderParams(
      resolveGlassStyle(effect, materialSize, Density(1f), LayoutDirection.Ltr),
      GlassCoordinates(sampleSize, materialOrigin, materialSize, 1f),
    )
    val detail = checkNotNull(params.activeRefractionDetailEffectKey(params.refractionDetailIntensity)) {
      "The pipeline scenario must exercise the refraction detail stage"
    }
    val mainShader = if (fused) {
      GlassShaders.buildFused(sharpDetail = true, merged = merged)
    } else {
      GlassShaders.buildOptical(merged = merged)
    }
    val filter = createRuntimeShaderRenderEffect(
      RuntimeEffect.makeForShader(mainShader),
      arrayOf("content"),
      arrayOf(null),
    ) {
      setOpticalUniforms(params.opticalEffectKey())
      if (fused) setRefractionDetailUniforms(detail)
    }

    return ImageBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT).also { image ->
      with(Canvas(image)) {
        val bounds = Rect(Offset.Zero, sampleSize)
        saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = filter })
        drawPattern(bounds)
        restore()
        if (!fused) {
          val coverage = createRuntimeShaderRenderEffect(
            RuntimeEffect.makeForShader(GlassShaders.buildRefractionDetail(coverageOnly = true, merged = merged)),
            arrayOf("content"),
            arrayOf(null),
          ) { setRefractionDetailUniforms(detail) }
          saveLayer(
            bounds,
            Paint().apply {
              skiaPaint.imageFilter = coverage
              blendMode = BlendMode.DstOut
            },
          )
          drawPattern(bounds)
          restore()
        }
        val sharp = createRuntimeShaderRenderEffect(
          RuntimeEffect.makeForShader(GlassShaders.buildRefractionDetail(merged = merged)),
          arrayOf("content"),
          arrayOf(null),
        ) { setRefractionDetailUniforms(detail) }
        saveLayer(
          bounds,
          Paint().apply {
            skiaPaint.imageFilter = sharp
            blendMode = BlendMode.Plus
          },
        )
        drawPattern(bounds)
        restore()
      }
    }
  }

  private fun mergedMember(rect: Rect, layoutSize: Size) = GlassMergedMember(
    rect = rect,
    cornerRadii = CornerRadii(8f, 8f, 8f, 8f),
    tint = Color.Transparent,
    backgroundColor = Color.Transparent,
    layoutSize = layoutSize,
  )

  private fun Canvas.drawPattern(bounds: Rect) {
    val tileSize = 8
    for (y in 0 until bounds.height.toInt() step tileSize) {
      for (x in 0 until bounds.width.toInt() step tileSize) {
        val color = if ((x / tileSize + y / tileSize) % 2 == 0) {
          Color(0xFFEF315A)
        } else {
          Color(0xFF22A8D8)
        }
        drawRect(
          Rect(
            bounds.left + x,
            bounds.top + y,
            minOf(bounds.left + x + tileSize, bounds.right),
            minOf(bounds.top + y + tileSize, bounds.bottom),
          ),
          Paint().apply { this.color = color },
        )
      }
    }
  }

  private fun Color.maxRgbDistance(other: Color): Float = maxOf(
    abs(red - other.red),
    abs(green - other.green),
    abs(blue - other.blue),
  )

  private companion object {
    const val SAMPLE_WIDTH = 384
    const val SAMPLE_HEIGHT = 96
    val sampleSize = Size(SAMPLE_WIDTH.toFloat(), SAMPLE_HEIGHT.toFloat())
    val materialOrigin = Offset(16f, 16f)
    val sharedOpticalSize = Size(40f, 40f)
    val probePoints = listOf(28 to 28, 36 to 32, 48 to 36, 64 to 28, 88 to 40, 112 to 32)
  }
}
