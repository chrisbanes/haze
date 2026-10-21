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
import androidx.compose.ui.graphics.PixelMap
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
import kotlin.math.floor
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

  @Test
  fun pressedMember_samplesContentLikeItsStandaloneSurface() {
    for (scenario in pressedScenarios) {
      val r = scenario.inputScale
      val unpressed = renderStages(
        pressedParams(scenario, field = null, materialSize = pressedLayout.size),
        fused = scenario.fused,
        merged = false,
        inputScale = r,
      )
      val standalone = unpressed.scaledAround(scenario, r).toPixelMap()
      val merged = renderPressedMerged(scenario).toPixelMap()
      val probes = scenario.flatProbes(unpressed.toPixelMap())
      assertThat(probes.size, "Comparable probes for $scenario").isGreaterThan(8)
      for ((x, y) in probes) {
        assertThat(
          standalone[x, y].maxRgbDistance(merged[x, y]),
          "Pressed member sampled content at $x,$y for $scenario",
        ).isLessThan(6f / 255f)
      }
    }
  }

  /** Scales a completed unpressed surface around the press pivot, as a standalone surface draws. */
  private fun ImageBitmap.scaledAround(scenario: PressedScenario, r: Float): ImageBitmap {
    val output = this
    return ImageBitmap(output.width, output.height).also { image ->
      with(Canvas(image)) {
        val pivot = (materialOrigin + scenario.pivot) * r
        translate(pivot.x, pivot.y)
        scale(PRESS_SCALE, PRESS_SCALE)
        translate(-pivot.x, -pivot.y)
        drawImage(output, Offset.Zero, Paint())
      }
    }
  }

  private fun renderPressedMerged(scenario: PressedScenario): ImageBitmap {
    val second = Rect(Offset(pressedLayout.right + scenario.gap, 0f), pressedLayout.size)
    val field = GlassMergedField(
      members = listOf(
        GlassMergedMember(
          rect = scenario.pressedRect(),
          cornerRadii = CornerRadii(8f * PRESS_SCALE, 8f * PRESS_SCALE, 8f * PRESS_SCALE, 8f * PRESS_SCALE),
          tint = Color.Transparent,
          backgroundColor = Color.Transparent,
          layoutRect = pressedLayout,
        ),
        mergedMember(second),
      ),
      mergeDistancePx = 40f,
      opticalSize = sharedOpticalSize,
    )
    val params = pressedParams(scenario, field, materialSize = Size(second.right, pressedLayout.height))
    return renderStages(params, fused = scenario.fused, merged = true, inputScale = scenario.inputScale)
  }

  private fun pressedParams(
    scenario: PressedScenario,
    field: GlassMergedField?,
    materialSize: Size,
  ): GlassRenderParams {
    val effect = GlassRuntimeEffect().apply {
      opticalSizeOverride = sharedOpticalSize
      mergedField = field
      style = GlassStyle {
        shape(RoundedCornerShape(8.dp))
        optics(
          GlassOptics(
            refractionStrength = scenario.refractionStrength,
            refractionHeightFraction = 0.5f,
            refractionDisplacement = 56.dp,
            depth = OpticalSizeValue.Fixed(0f),
            blurRadius = OpticalSizeValue.Fixed(0.dp),
          ),
        )
        surfaceProfile(SurfaceProfile.Circle)
        chromaticAberrationStrength(scenario.chromaticAberration)
        ambientResponse(0f)
        // Linear grading keeps resampling the output equivalent to resampling the content.
        contrast(0f)
        whitePoint(0f)
        chromaMultiplier(1f)
        tint(Color.Transparent)
      }
    }
    val r = scenario.inputScale
    return buildGlassRenderParams(
      resolveGlassStyle(effect, materialSize, Density(1f), LayoutDirection.Ltr),
      GlassCoordinates(sampleSize * r, materialOrigin * r, materialSize * r, r),
    )
  }

  private data class PressedScenario(
    val pivot: Offset,
    val fused: Boolean,
    val refractionStrength: Float,
    val chromaticAberration: Float,
    val inputScale: Float,
    val gap: Float = 39.9f,
  ) {
    fun pressedRect(): Rect = Rect(
      pivot + (pressedLayout.topLeft - pivot) * PRESS_SCALE,
      pressedLayout.size * PRESS_SCALE,
    )

    /**
     * Pixels inside the pressed member's drawn rect whose unpressed source pixels are locally flat.
     * Filtering the completed output and sampling the content then agree, so only the sampled
     * position is compared.
     */
    fun flatProbes(unpressed: PixelMap): List<Pair<Int, Int>> {
      val pivotPx = (materialOrigin + pivot) * inputScale
      val drawn = pressedRect().translate(materialOrigin)
      return buildList {
        for (y in (drawn.top * inputScale).toInt() + 4 until (drawn.bottom * inputScale).toInt() - 4) {
          for (x in (drawn.left * inputScale).toInt() + 4 until (drawn.right * inputScale).toInt() - 4) {
            val source = pivotPx + (Offset(x + 0.5f, y + 0.5f) - pivotPx) / PRESS_SCALE - Offset(0.5f, 0.5f)
            val footprint = listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1).map { (dx, dy) ->
              unpressed[floor(source.x).toInt() + dx, floor(source.y).toInt() + dy]
            }
            if (footprint.all { it.maxRgbDistance(footprint.first()) < 1f / 255f }) add(x to y)
          }
        }
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
          mergedMember(Rect(0f, 0f, 120f, 40f)),
          mergedMember(Rect(120f + gap, 0f, 240f + gap, 40f)),
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
    checkNotNull(params.activeRefractionDetailEffectKey(params.refractionDetailIntensity)) {
      "The pipeline scenario must exercise the refraction detail stage"
    }
    return renderStages(params, fused = fused, merged = merged, inputScale = 1f)
  }

  /** Draws the production optical, coverage and sharp-detail stages over the pattern. */
  private fun renderStages(params: GlassRenderParams, fused: Boolean, merged: Boolean, inputScale: Float): ImageBitmap {
    val detail = params.activeRefractionDetailEffectKey(params.refractionDetailIntensity)
    val mainShader = if (fused) {
      GlassShaders.buildFused(sharpDetail = detail != null, merged = merged)
    } else {
      GlassShaders.buildOptical(merged = merged)
    }
    val filter = createRuntimeShaderRenderEffect(
      RuntimeEffect.makeForShader(mainShader),
      arrayOf("content"),
      arrayOf(null),
    ) {
      setOpticalUniforms(params.opticalEffectKey())
      if (fused && detail != null) setRefractionDetailUniforms(detail)
    }

    val width = (SAMPLE_WIDTH * inputScale).toInt()
    val height = (SAMPLE_HEIGHT * inputScale).toInt()
    return ImageBitmap(width, height).also { image ->
      with(Canvas(image)) {
        val bounds = Rect(0f, 0f, width.toFloat(), height.toFloat())
        fun drawLayer(paint: Paint) {
          saveLayer(bounds, paint)
          save()
          scale(inputScale, inputScale)
          drawPattern(Rect(Offset.Zero, sampleSize))
          restore()
          restore()
        }
        drawLayer(Paint().apply { skiaPaint.imageFilter = filter })
        if (detail == null) return@also
        if (!fused) {
          val coverage = createRuntimeShaderRenderEffect(
            RuntimeEffect.makeForShader(GlassShaders.buildRefractionDetail(coverageOnly = true, merged = merged)),
            arrayOf("content"),
            arrayOf(null),
          ) { setRefractionDetailUniforms(detail) }
          drawLayer(
            Paint().apply {
              skiaPaint.imageFilter = coverage
              blendMode = BlendMode.DstOut
            },
          )
        }
        val sharp = createRuntimeShaderRenderEffect(
          RuntimeEffect.makeForShader(GlassShaders.buildRefractionDetail(merged = merged)),
          arrayOf("content"),
          arrayOf(null),
        ) { setRefractionDetailUniforms(detail) }
        drawLayer(
          Paint().apply {
            skiaPaint.imageFilter = sharp
            blendMode = BlendMode.Plus
          },
        )
      }
    }
  }

  private fun mergedMember(rect: Rect) = GlassMergedMember(
    rect = rect,
    cornerRadii = CornerRadii(8f, 8f, 8f, 8f),
    tint = Color.Transparent,
    backgroundColor = Color.Transparent,
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

  private companion object {
    const val SAMPLE_WIDTH = 384
    const val SAMPLE_HEIGHT = 96
    val sampleSize = Size(SAMPLE_WIDTH.toFloat(), SAMPLE_HEIGHT.toFloat())
    val materialOrigin = Offset(16f, 16f)
    val sharedOpticalSize = Size(40f, 40f)
    val probePoints = listOf(28 to 28, 36 to 32, 48 to 36, 64 to 28, 88 to 40, 112 to 32)

    const val PRESS_SCALE = 0.8f
    val pressedLayout = Rect(0f, 0f, 120f, 40f)
    val pressedScenarios = buildList {
      for (pivot in listOf(pressedLayout.center, Offset(30f, 10f))) {
        for (fused in listOf(false, true)) {
          for (inputScale in listOf(1f, 0.5f)) {
            for (refraction in listOf(0f, 1f)) {
              for (chromaticAberration in listOf(0f, 0.5f)) {
                add(PressedScenario(pivot, fused, refraction, chromaticAberration, inputScale))
              }
            }
            // Blending toward the bridge and fully bridged, where only the sample position differs.
            for (gap in listOf(30f, 10f)) add(PressedScenario(pivot, fused, 0f, 0f, inputScale, gap))
          }
        }
      }
    }
  }
}

private fun Color.maxRgbDistance(other: Color): Float = maxOf(
  abs(red - other.red),
  abs(green - other.green),
  abs(blue - other.blue),
)
