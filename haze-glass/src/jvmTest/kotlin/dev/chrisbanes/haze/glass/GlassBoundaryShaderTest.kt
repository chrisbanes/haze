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
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.math.abs
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

class GlassBoundaryShaderTest {
  @Test
  fun progressiveBlur_preservesFlatBackdrop() {
    val plain = render(false).toPixelMap()
    val blurred = render(false, progressiveBlur = true).toPixelMap()
    for (y in 0 until plain.height) {
      for (x in 0 until plain.width) {
        assertThat(abs(plain[x, y].red - blurred[x, y].red), "progressive flat red at $x,$y")
          .isLessThan(2f / 255f)
        assertThat(abs(plain[x, y].alpha - blurred[x, y].alpha), "progressive flat alpha at $x,$y")
          .isLessThan(2f / 255f)
      }
    }
  }

  @Test
  fun progressiveBlur_preservesTranslucentBackdropAlpha() {
    val plain = render(false, sourceAlpha = 0.4f).toPixelMap()
    val blurred = render(false, sourceAlpha = 0.4f, progressiveBlur = true).toPixelMap()
    for (y in 0 until plain.height) {
      for (x in 0 until plain.width) {
        assertThat(abs(plain[x, y].alpha - blurred[x, y].alpha), "progressive translucent alpha at $x,$y")
          .isLessThan(2f / 255f)
      }
    }
  }

  @Test
  fun roundedControl_hasFractionalBoundaryCoverage() {
    assertFractionalBoundaryCoverage(pill = false)
  }

  @Test
  fun pill_hasFractionalBoundaryCoverage() {
    assertFractionalBoundaryCoverage(pill = true)
  }

  @Test
  fun outputCoverageEffect_hasFractionalAsymmetricBoundaryCoverage() {
    val size = Size(168f, 136f)
    val key = GlassOutputCoverageEffectKey(
      materialSize = size,
      cornerRadii = CornerRadii(56f, 32f, 18f, 44f),
      sampleStepPx = 2f,
    )
    val effect = createRuntimeShaderRenderEffect(
      RuntimeEffect.makeForShader(GlassShaders.buildOutputCoverage()),
      arrayOf("content"),
      arrayOf(null),
    ) { setOutputCoverageUniforms(key) }
    val image = ImageBitmap(size.width.toInt(), size.height.toInt())
    with(Canvas(image)) {
      val bounds = Rect(Offset.Zero, size)
      saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = effect })
      drawRect(bounds, Paint().apply { color = Color.White })
      restore()
    }
    val pixels = image.toPixelMap()
    val fractional = (0 until pixels.height).sumOf { y ->
      (0 until pixels.width).count { x -> pixels[x, y].alpha in 0.01f..0.99f }
    }

    assertThat(fractional).isGreaterThan(80)
    assertThat(pixels[0, 0].alpha).isLessThan(1f / 255f)
  }

  private fun assertFractionalBoundaryCoverage(pill: Boolean) {
    for (fused in listOf(false, true)) {
      val pixels = render(fused, pill = pill).toPixelMap()
      val fractional = (0 until pixels.height).sumOf { y ->
        (0 until pixels.width).count { x -> pixels[x, y].alpha in 0.01f..0.99f }
      }
      assertThat(fractional, "fractional edge pixels, fused=$fused").isGreaterThan(100)
    }
  }

  @Test
  fun roundedControl_regularFlatBackdropHasNoInsetDarkRing() {
    for (fused in listOf(false, true)) {
      val pixels = render(fused).toPixelMap()
      val center = pixels[96, 96].red
      val darkest = (20..85).minOf { x -> pixels[x, 96].red }
      assertThat(center - darkest, "inset ring contrast, fused=$fused").isLessThan(0.01f)
    }
  }

  @Test
  fun sharpDetail_preservesFractionalBoundaryCoverage() {
    for (fused in listOf(false, true)) {
      val optical = render(fused).toPixelMap()
      val detailed = render(fused, customOptics = true).toPixelMap()
      for (y in 0 until optical.height) {
        for (x in 0 until optical.width) {
          assertThat(abs(optical[x, y].alpha - detailed[x, y].alpha), "coverage at $x,$y, fused=$fused")
            .isLessThan(2f / 255f)
        }
      }
    }
  }

  @Test
  fun brightTranslucentBackdrop_keepsHighlightRgbWithinAlpha() {
    for (fused in listOf(false, true)) {
      val transparent = render(fused, sourceAlpha = 0.4f, brightBackdrop = true).toPixelMap()
      val overBlack = render(fused, sourceAlpha = 0.4f, brightBackdrop = true, matte = Color.Black).toPixelMap()
      for (y in 0 until transparent.height) {
        for (x in 0 until transparent.width) {
          assertThat(overBlack[x, y].red - transparent[x, y].alpha, "highlight excess at $x,$y, fused=$fused")
            .isLessThan(2f / 255f)
        }
      }
    }
  }

  @Test
  fun extendedRangeTint_preservesAboveOneComponentDuringPremultiplication() {
    val style = GlassStyle.regular.then {
      tint(Color(1.5f, 0.25f, 0.1f, 1f, ColorSpaces.ExtendedSrgb))
      ambientResponse(0f)
    }
    for (fused in listOf(false, true)) {
      val overBlack = render(
        fused = fused,
        sourceAlpha = 0.4f,
        matte = Color.Black,
        baseStyle = style,
      ).toPixelMap()
      // The composed result fits in SDR, so readback can detect clipping of the original
      // extended-range component: 1.5 * 0.4 should remain 0.6, rather than being reduced to 0.4.
      assertThat(abs(overBlack[96, 96].red - 0.6f), "extended red over black, fused=$fused")
        .isLessThan(2f / 255f)
    }
  }

  @Test
  fun translucentSource_preservesCoverageWithoutInflatingAlpha() {
    for (fused in listOf(false, true)) {
      val opaque = render(fused, customOptics = true).toPixelMap()
      val translucent = render(fused, customOptics = true, sourceAlpha = 0.4f).toPixelMap()
      for (y in 0 until opaque.height) {
        for (x in 0 until opaque.width) {
          assertThat(abs(opaque[x, y].alpha * 0.4f - translucent[x, y].alpha), "source alpha at $x,$y, fused=$fused")
            .isLessThan(2f / 255f)
        }
      }
    }
  }

  private fun render(
    fused: Boolean,
    customOptics: Boolean = false,
    pill: Boolean = false,
    sourceAlpha: Float = 1f,
    progressiveBlur: Boolean = false,
    brightBackdrop: Boolean = false,
    matte: Color = Color.Transparent,
    baseStyle: GlassStyle = GlassStyle.regular,
  ): ImageBitmap {
    val size = Size(if (pill) 336f else 168f, 168f)
    val sampleSize = Size(size.width + 24f, size.height + 24f)
    val sourceColor = (if (brightBackdrop) Color.White else Color(0xFF131116)).copy(alpha = sourceAlpha)
    val effect = GlassRuntimeEffect().apply {
      style = baseStyle.then {
        shape(RoundedCornerShape(50))
        if (brightBackdrop) {
          ambientResponse(1f)
          tint(Color.Transparent)
        }
        if (customOptics) optics(GlassOptics())
        if (progressiveBlur) {
          optics(GlassDefaults.optics.copy(progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)))
        }
      }
    }
    val params = buildGlassRenderParams(
      resolveGlassStyle(effect, size, Density(3f), LayoutDirection.Ltr),
      GlassCoordinates(sampleSize, Offset(12f, 12f), size, 1f),
    )
    val detail = params.activeRefractionDetailEffectKey(params.refractionDetailIntensity)
    val blurKey = params.blurEffectKey()
    val horizontal = if (progressiveBlur) {
      createRuntimeShaderRenderEffect(
        RuntimeEffect.makeForShader(GlassShaders.buildBlur(horizontal = true, progressive = true)),
        arrayOf("content"),
        arrayOf(null),
      ) {
        setGlassBlurUniforms(blurKey, blurKey.plan.horizontalKernel, sampleSize.width, sampleSize.height)
        setChildShader("mask", checkNotNull(blurKey.progressive?.toShader(blurKey.maskSize)))
      }
    } else {
      null
    }
    val vertical = if (progressiveBlur) {
      createRuntimeShaderRenderEffect(
        RuntimeEffect.makeForShader(GlassShaders.buildBlur(horizontal = false, progressive = true)),
        arrayOf("content"),
        arrayOf(horizontal),
      ) {
        setGlassBlurUniforms(blurKey, blurKey.plan.verticalKernel, sampleSize.width, sampleSize.height)
        setChildShader("mask", checkNotNull(blurKey.progressive?.toShader(blurKey.maskSize)))
      }
    } else {
      null
    }
    val shader = if (fused) GlassShaders.buildFused(sharpDetail = detail != null) else GlassShaders.buildOptical()
    val filter = createRuntimeShaderRenderEffect(RuntimeEffect.makeForShader(shader), arrayOf("content"), arrayOf(vertical)) {
      setOpticalUniforms(params.opticalEffectKey())
      if (fused && detail != null) setRefractionDetailUniforms(detail)
    }
    return ImageBitmap(sampleSize.width.toInt(), sampleSize.height.toInt()).also { image ->
      with(Canvas(image)) {
        val bounds = Rect(Offset.Zero, sampleSize)
        drawRect(bounds, Paint().apply { color = matte })
        saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = filter })
        drawRect(bounds, Paint().apply { color = sourceColor })
        restore()
        if (detail != null) {
          if (!fused) {
            val coverage = createRuntimeShaderRenderEffect(
              RuntimeEffect.makeForShader(GlassShaders.buildRefractionDetail(coverageOnly = true)),
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
            drawRect(bounds, Paint().apply { color = sourceColor })
            restore()
          }
          val sharp = createRuntimeShaderRenderEffect(
            RuntimeEffect.makeForShader(GlassShaders.buildRefractionDetail()),
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
          drawRect(bounds, Paint().apply { color = sourceColor })
          restore()
        }
      }
    }
  }
}
