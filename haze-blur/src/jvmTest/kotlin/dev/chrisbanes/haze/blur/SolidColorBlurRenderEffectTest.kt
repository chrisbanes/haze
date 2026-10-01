// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.PlatformContext
import kotlin.test.Test

class SolidColorBlurRenderEffectTest {
  @Test
  fun solidMask_matchesConstantShaderBrush() {
    val control = raster(mask = constant(Color.Transparent))
    assertThat(control[16, 16].alpha, "transparent ShaderBrush control").isCloseTo(0f, TOLERANCE)
    assertThat(raster(mask = SolidColor(Color.Transparent))[16, 16].alpha, "supplied transparent-mask reproduction")
      .isCloseTo(0f, TOLERANCE)
    for (base in listOf(Color.Black, Color.Green)) {
      for (alpha in listOf(0f, 0.25f, 0.5f, 1f)) {
        val color = base.copy(alpha = alpha)
        val actual = raster(mask = SolidColor(color))
        compare(actual, raster(mask = constant(color)), "mask $color")
        for ((x, y) in PROBES) {
          assertThat(actual[x, y].alpha, "mask alpha $color at $x,$y").isCloseTo(alpha, TOLERANCE)
          if (alpha > 0f) {
            assertThat(actual[x, y].red).isCloseTo(if (x < 32) 1f else 0f, TOLERANCE)
            assertThat(actual[x, y].blue).isCloseTo(if (x < 32) 0f else 1f, TOLERANCE)
          }
        }
      }
    }
  }

  @Test
  fun solidProgressive_matchesConstantShaderBrush() {
    for (alpha in listOf(0f, 0.25f, 0.5f, 1f)) {
      val color = Color.Green.copy(alpha = alpha)
      val tint = listOf(HazeColorEffect.tint(Color.Green))
      val actual = raster(effects = tint, progressive = HazeProgressive.Brush(SolidColor(color)))
      compare(actual, raster(effects = tint, progressive = HazeProgressive.Brush(constant(color))), "progressive $alpha")
      assertThat(actual[16, 16].green).isCloseTo(alpha, TOLERANCE)
      assertThat(actual[16, 16].red).isCloseTo(1f - alpha, TOLERANCE)
    }
    val sharp = raster(checker = true)
    for (alpha in listOf(0f, 0.5f, 1f)) {
      val color = Color.Black.copy(alpha = alpha)
      val actual = raster(progressive = HazeProgressive.Brush(SolidColor(color)), blur = 8f, checker = true)
      val reference = raster(progressive = HazeProgressive.Brush(constant(color)), blur = 8f, checker = true)
      compare(actual, reference, "runtime checker $alpha", CHECKER_PROBES)
      if (alpha == 0f) compare(actual, sharp, "zero intensity preserves sharp input", CHECKER_PROBES)
      if (alpha == 1f) {
        assertThat(kotlin.math.abs(actual[8, 12].red - sharp[8, 12].red), "full intensity blurs checker edge")
          .isCloseTo(0.5f, 0.2f)
      }
    }
  }

  @Test
  fun solidTint_matchesColorTint() {
    // Dst discards its tint source; compare each API variant against the destination itself.
    val dstColors = listOf(0f, 0.002f, 0.004f, 0.005f, 1f / 255, 2f / 255, 0.25f, 0.5f, 1f).map { Color.Green.copy(alpha = it) } + Color(0.2f, 0.7f, 0.4f, 0.5f)
    for (color in dstColors) {
      val destination = raster()
      for (effect in listOf(HazeColorEffect.tint(color, BlendMode.Dst), HazeColorEffect.tint(SolidColor(color), BlendMode.Dst))) {
        val actual = raster(effects = listOf(effect))
        compare(actual, destination, "Dst identity $color $effect")
        assertInput(actual, 1f, "Dst unchanged input")
      }
    }
    val dstRamp = Brush.linearGradient(listOf(Color.Transparent, Color.Black), start = Offset.Zero, end = Offset(64f, 0f))
    val dstProgressives = listOf(null, constant(Color.Transparent), constant(Color.Black.copy(alpha = 0.5f)), constant(Color.Black), dstRamp)
    val dstEffects = listOf(
      HazeColorEffect.tint(Color.Green.copy(alpha = 0.25f), BlendMode.Dst),
      HazeColorEffect.tint(SolidColor(Color.Green.copy(alpha = 0.25f)), BlendMode.Dst),
    )
    for (modulate in listOf(0f, 0.25f, 0.5f, 1f)) for (brush in dstProgressives) {
      val progressive = brush?.let { HazeProgressive.Brush(it) }
      for (mask in listOf(null, SolidColor(Color.Black.copy(alpha = 0.5f)))) {
        val destination = raster(mask = mask, progressive = progressive, modulate = modulate)
        for (effect in dstEffects) {
          val actual = raster(mask = mask, effects = listOf(effect), progressive = progressive, modulate = modulate)
          compare(actual, destination, "Dst modulated identity $modulate $brush mask=$mask")
          assertInput(actual, if (mask == null) 1f else 0.5f, "Dst preserves overall mask")
        }
      }
    }
    for (brush in dstProgressives.filterNotNull()) {
      val progressive = HazeProgressive.Brush(brush)
      for (mask in listOf(null, SolidColor(Color.Black.copy(alpha = 0.5f)))) {
        val destination = raster(mask = mask, progressive = progressive, blur = 8f, checker = true)
        for (effect in dstEffects) {
          compare(raster(mask = mask, effects = listOf(effect), progressive = progressive, blur = 8f, checker = true), destination, "Dst preserves progressive blurred checker and mask", CHECKER_PROBES)
        }
      }
    }
    val earlier = HazeColorEffect.tint(Color.Green.copy(alpha = 0.5f))
    val earlierDestination = raster(effects = listOf(earlier))
    for (effect in dstEffects) {
      val actual = raster(effects = listOf(earlier, effect))
      compare(actual, earlierDestination, "Dst preserves preceding tint")
      for ((x, y) in PROBES) {
        val pixel = actual[x, y]
        assertThat(pixel.green).isCloseTo(0.5f, TOLERANCE)
        assertThat(pixel.red).isCloseTo(if (x < 32) 0.5f else 0f, TOLERANCE)
        assertThat(pixel.blue).isCloseTo(if (x < 32) 0f else 0.5f, TOLERANCE)
      }
    }

    // DstIn with packed-opaque source preserves the destination only without a tint mask.
    val opaqueCases = listOf(
      Color.Green to 1f,
      Color(0f, 1f, 0f, 0.999f, ColorSpaces.DisplayP3) to 1f,
      Color(0f, 1f, 0f, 1f, ColorSpaces.DisplayP3) to 0.999f,
    )
    for ((color, modulate) in opaqueCases) {
      val resolved = if (modulate < 1f) color.copy(alpha = color.alpha * modulate) else color
      assertThat(resolved.toArgb() ushr 24).isEqualTo(255)
      if (color.colorSpace == ColorSpaces.DisplayP3) assertThat(resolved.alpha).isLessThan(1f)
      for (hasEarlier in listOf(false, true)) for (mask in listOf(null, SolidColor(Color.Black.copy(alpha = 0.5f)))) {
        val earlierEffects = if (hasEarlier) listOf(HazeColorEffect.tint(Color.Green.copy(alpha = 0.5f))) else emptyList()
        val destination = raster(mask = mask, effects = earlierEffects, modulate = modulate)
        for (effect in listOf(HazeColorEffect.tint(color, BlendMode.DstIn), HazeColorEffect.tint(SolidColor(color), BlendMode.DstIn))) {
          val actual = raster(mask = mask, effects = earlierEffects + effect, modulate = modulate)
          compare(actual, destination, "packed opaque DstIn $color/$modulate")
          assertDestination(actual, if (mask == null) 1f else 0.5f, if (hasEarlier) 0.5f * modulate else 0f, "unmasked DstIn destination")
        }
      }
    }
    for ((color, modulate) in listOf(Color.Green.copy(alpha = 254f / 255) to 1f, Color.Green to (254f / 255))) {
      val resolved = if (modulate < 1f) color.copy(alpha = color.alpha * modulate) else color
      assertThat(resolved.toArgb() ushr 24).isEqualTo(254)
      for (effect in listOf(HazeColorEffect.tint(color, BlendMode.DstIn), HazeColorEffect.tint(SolidColor(color), BlendMode.DstIn))) {
        assertDestination(raster(effects = listOf(effect), modulate = modulate), 254f / 255, 0f, "nearby nonidentity DstIn")
      }
    }
    for ((color, modulate) in opaqueCases) {
      val resolved = if (modulate < 1f) color.copy(alpha = color.alpha * modulate) else color
      assertThat(resolved.toArgb() ushr 24).isEqualTo(255)
      if (color.colorSpace == ColorSpaces.DisplayP3) assertThat(resolved.alpha).isLessThan(1f)
      for ((brush, intensity) in listOf(
        constant(Color.Transparent) to 0f,
        constant(Color.Black.copy(alpha = 0.5f)) to 0.5f,
        constant(Color.Black) to 1f,
        dstRamp to -1f,
      )) {
        if (color.colorSpace == ColorSpaces.DisplayP3 && intensity !in listOf(0.5f, -1f)) continue
        val progressive = HazeProgressive.Brush(brush)
        for (hasEarlier in listOf(false, true)) for (mask in listOf(null, SolidColor(Color.Black.copy(alpha = 0.5f)))) {
          val earlierEffects = if (hasEarlier) listOf(HazeColorEffect.tint(Color.Green.copy(alpha = 0.5f))) else emptyList()
          for (effect in listOf(HazeColorEffect.tint(color, BlendMode.DstIn), HazeColorEffect.tint(SolidColor(color), BlendMode.DstIn))) {
            val actual = raster(mask = mask, effects = earlierEffects + effect, progressive = progressive, modulate = modulate)
            for ((x, y) in PROBES) {
              val m = if (intensity >= 0f) intensity else (x + 0.5f) / 64
              val alpha = m * if (mask == null) 1f else 0.5f
              val green = if (hasEarlier) 0.5f * modulate * m else 0f
              assertDestinationProbe(actual, x, y, alpha, green, "masked DstIn $color/$modulate intensity=$intensity earlier=$hasEarlier mask=$mask at $x,$y", premultiplied = true)
            }
          }
        }
      }
    }
    for (alpha in listOf(0.002f, 0.004f, 0.005f)) {
      assertThat(Color.Green.copy(alpha = alpha).alpha).isEqualTo(1f / 255)
    }
    val belowCutoff = Color.Green.copy(alpha = 1f / 255)
    val aboveCutoff = Color.Green.copy(alpha = 2f / 255)
    assertThat(belowCutoff.alpha).isEqualTo(1f / 255)
    assertThat(aboveCutoff.alpha).isEqualTo(2f / 255)
    assertThat(belowCutoff.alpha).isLessThan(0.005f)
    assertThat(aboveCutoff.alpha).isGreaterThan(0.005f)
    for (brushTint in listOf(false, true)) {
      val below = if (brushTint) HazeColorEffect.tint(SolidColor(belowCutoff), BlendMode.Clear) else HazeColorEffect.tint(belowCutoff, BlendMode.Clear)
      val above = if (brushTint) HazeColorEffect.tint(SolidColor(aboveCutoff), BlendMode.Clear) else HazeColorEffect.tint(aboveCutoff, BlendMode.Clear)
      assertDestination(raster(effects = listOf(below)), 1f, 0f, "below-cutoff Clear preserves input")
      assertDestination(raster(effects = listOf(above)), 0f, 0f, "above-cutoff Clear removes input")
    }
    val colorControl = raster(effects = listOf(HazeColorEffect.tint(Color.Green)))
    assertThat(colorControl[16, 16].green, "Color tint control").isCloseTo(1f, TOLERANCE)
    val supplied = raster(effects = listOf(HazeColorEffect.tint(SolidColor(Color.Green))))
    assertThat(supplied[16, 16].green, "supplied green-brush-tint reproduction").isCloseTo(1f, TOLERANCE)
    assertThat(supplied[16, 16].red).isCloseTo(0f, TOLERANCE)
    val colors = listOf(0f, 0.002f, 0.004f, 0.005f, 1f / 255, 2f / 255, 0.25f, 0.5f, 1f).map { Color.Green.copy(alpha = it) } + Color(0.2f, 0.7f, 0.4f, 0.5f)
    for (mode in MODES) for (color in colors) {
      try {
        val expected = raster(effects = listOf(HazeColorEffect.tint(color, mode)))
        compare(raster(effects = listOf(HazeColorEffect.tint(SolidColor(color), mode))), expected, "tint $mode $color")
      } catch (failure: RuntimeException) {
        throw IllegalStateException("Color/brush tint mode=$mode color=$color", failure)
      }
    }
    val ramp = Brush.linearGradient(listOf(Color.Transparent, Color.Black), start = Offset.Zero, end = Offset(64f, 0f))
    val progressives = listOf(null, constant(Color.Transparent), constant(Color.Black.copy(alpha = 0.5f)), constant(Color.Black), ramp)
    for (mode in listOf(BlendMode.SrcOver, BlendMode.Src, BlendMode.Multiply, BlendMode.Screen)) {
      for (modulate in listOf(0f, 0.25f, 0.5f, 1f)) for (brush in progressives) {
        val progressive = brush?.let { HazeProgressive.Brush(it) }
        for (mask in listOf(null, SolidColor(Color.Black.copy(alpha = 0.5f)))) {
          compare(
            raster(mask = mask, effects = listOf(HazeColorEffect.tint(SolidColor(Color.Green), mode)), progressive = progressive, modulate = modulate),
            raster(mask = mask, effects = listOf(HazeColorEffect.tint(Color.Green, mode)), progressive = progressive, modulate = modulate),
            "modulated $mode $modulate $brush mask=$mask",
          )
        }
      }
    }
    // Independent RGB oracle preserves the existing ShaderBrush DstIn modulation path.
    val shaderTint = listOf(HazeColorEffect.tint(constant(Color.Green)))
    for (modulate in listOf(0.25f, 0.5f, 1f)) {
      for (intensity in listOf(1f, 0.5f)) {
        val progressive = if (intensity == 1f) null else HazeProgressive.Brush(constant(Color.Black.copy(alpha = intensity)))
        val actual = raster(effects = shaderTint, progressive = progressive, modulate = modulate)
        for ((x, y) in PROBES) {
          val pixel = actual[x, y]
          val green = modulate * intensity
          assertThat(pixel.green, "ShaderBrush RGB green $modulate/$intensity").isCloseTo(green, TOLERANCE)
          assertThat(pixel.red).isCloseTo(if (x < 32) 1f - green else 0f, TOLERANCE)
          assertThat(pixel.blue).isCloseTo(if (x < 32) 0f else 1f - green, TOLERANCE)
        }
      }
      val progressive = HazeProgressive.Brush(ramp)
      val actual = raster(effects = shaderTint, progressive = progressive, modulate = modulate)
      compare(actual, raster(effects = listOf(HazeColorEffect.tint(Color.Green)), progressive = progressive, modulate = modulate), "ShaderBrush ramp Color parity $modulate")
      for ((x, y) in PROBES) {
        val pixel = actual[x, y]
        val green = modulate * (x + 0.5f) / 64
        assertThat(pixel.green, "ShaderBrush ramp green $modulate at $x,$y").isCloseTo(green, TOLERANCE)
        assertThat(pixel.red).isCloseTo(if (x < 32) 1f - green else 0f, TOLERANCE)
        assertThat(pixel.blue).isCloseTo(if (x < 32) 0f else 1f - green, TOLERANCE)
      }
    }
  }

  private fun assertInput(pixels: PixelMap, alpha: Float, label: String) {
    for ((x, y) in PROBES) {
      val pixel = pixels[x, y]
      assertThat(pixel.alpha, "$label alpha").isCloseTo(alpha, TOLERANCE)
      assertThat(pixel.red, "$label red").isCloseTo(if (x < 32) 1f else 0f, TOLERANCE)
      assertThat(pixel.green, "$label green").isCloseTo(0f, TOLERANCE)
      assertThat(pixel.blue, "$label blue").isCloseTo(if (x < 32) 0f else 1f, TOLERANCE)
    }
  }

  private fun assertDestination(pixels: PixelMap, alpha: Float, green: Float, label: String) {
    for ((x, y) in PROBES) assertDestinationProbe(pixels, x, y, alpha, green, label)
  }

  private fun assertDestinationProbe(pixels: PixelMap, x: Int, y: Int, alpha: Float, green: Float, label: String, premultiplied: Boolean = false) {
    val pixel = pixels[x, y]
    val red = if (x < 32) 1f - green else 0f
    val blue = if (x < 32) 0f else 1f - green
    assertThat(pixel.alpha, "$label alpha").isCloseTo(alpha, TOLERANCE)
    if (premultiplied) {
      // Compare physical output at 8-bit precision without low-alpha unpremultiplication error.
      assertThat(pixel.red * pixel.alpha, "$label premultiplied red").isCloseTo(red * alpha, TOLERANCE)
      assertThat(pixel.green * pixel.alpha, "$label premultiplied green").isCloseTo(green * alpha, TOLERANCE)
      assertThat(pixel.blue * pixel.alpha, "$label premultiplied blue").isCloseTo(blue * alpha, TOLERANCE)
    } else if (alpha > 0f) {
      assertThat(pixel.red, "$label red").isCloseTo(red, TOLERANCE)
      assertThat(pixel.green, "$label green").isCloseTo(green, TOLERANCE)
      assertThat(pixel.blue, "$label blue").isCloseTo(blue, TOLERANCE)
    }
  }

  private fun constant(color: Color) = Brush.linearGradient(listOf(color, color))

  private fun compare(actual: PixelMap, expected: PixelMap, label: String, probes: List<Pair<Int, Int>> = PROBES) {
    for ((x, y) in probes) {
      val a = actual[x, y]
      val e = expected[x, y]
      assertThat(a.red, "$label red at $x,$y").isCloseTo(e.red, TOLERANCE)
      assertThat(a.green, "$label green at $x,$y").isCloseTo(e.green, TOLERANCE)
      assertThat(a.blue, "$label blue at $x,$y").isCloseTo(e.blue, TOLERANCE)
      assertThat(a.alpha, "$label alpha at $x,$y").isCloseTo(e.alpha, TOLERANCE)
    }
  }

  private fun raster(
    mask: Brush? = null,
    effects: List<HazeColorEffect> = emptyList(),
    progressive: HazeProgressive? = null,
    modulate: Float = 1f,
    blur: Float = 0f,
    checker: Boolean = false,
  ): PixelMap {
    val effect = createRenderEffect(
      PlatformContext.INSTANCE,
      Density(1f),
      RenderEffectParams(
        blurRadius = blur.dp,
        noiseFactor = 0f,
        scale = 1f,
        contentSize = Size(64f, 64f),
        contentOffset = Offset.Zero,
        colorEffects = effects,
        colorEffectsAlphaModulate = modulate,
        mask = mask,
        progressive = progressive,
        retainInputWhenMasked = false,
        blurTileMode = TileMode.Clamp,
      ),
    )
    return ImageBitmap(64, 64).also { bitmap ->
      with(Canvas(bitmap)) {
        saveLayer(Rect(0f, 0f, 64f, 64f), Paint().apply { skiaPaint.imageFilter = effect })
        if (checker) {
          for (x in 0..7) for (y in 0..7) {
            drawRect(
              Rect(x * 8f, y * 8f, (x + 1) * 8f, (y + 1) * 8f),
              Paint().apply {
                color = if ((x + y) % 2 == 0) Color.Black else Color.White
              },
            )
          }
        } else {
          drawRect(Rect(0f, 0f, 32f, 64f), Paint().apply { color = Color.Red })
          drawRect(Rect(32f, 0f, 64f, 64f), Paint().apply { color = Color.Blue })
        }
        restore()
      }
    }.toPixelMap()
  }

  private companion object {
    val MODES = listOf(
      BlendMode.Clear,
      BlendMode.Src,
      BlendMode.Dst,
      BlendMode.SrcOver,
      BlendMode.DstOver,
      BlendMode.SrcIn,
      BlendMode.DstIn,
      BlendMode.SrcOut,
      BlendMode.DstOut,
      BlendMode.SrcAtop,
      BlendMode.DstAtop,
      BlendMode.Xor,
      BlendMode.Plus,
      BlendMode.Modulate,
      BlendMode.Screen,
      BlendMode.Overlay,
      BlendMode.Darken,
      BlendMode.Lighten,
      BlendMode.ColorDodge,
      BlendMode.ColorBurn,
      BlendMode.Hardlight,
      BlendMode.Softlight,
      BlendMode.Difference,
      BlendMode.Exclusion,
      BlendMode.Multiply,
      BlendMode.Hue,
      BlendMode.Saturation,
      BlendMode.Color,
      BlendMode.Luminosity,
    )
    const val TOLERANCE = 2f / 255
    val PROBES = listOf(16 to 16, 48 to 16, 16 to 48)
    val CHECKER_PROBES = listOf(8 to 12, 12 to 12, 32 to 28, 48 to 52)
  }
}
