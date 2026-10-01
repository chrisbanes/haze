// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.chrisbanes.haze.blur

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.asBrush
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 31)
class SolidColorBlurRenderEffectInstrumentationTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val currentEffect = mutableStateOf<RenderEffect?>(null)
  private val checker = mutableStateOf(false)
  private var nodeSize = IntSize.Zero
  private var density = Density(1f)
  private var hardwareDraws = 0

  @Test
  fun solidMask_matchesConstantShaderBrush() {
    install()
    verifyInputControls()
    for (base in listOf(Color.Black, Color.Green)) {
      for (alpha in listOf(0f, 0.25f, 0.5f, 1f)) {
        val color = base.copy(alpha = alpha)
        val reference = raster(mask = constant(color))
        assertMaskedInput(reference, alpha, "gradient control $color")
        val actual = raster(mask = SolidColor(color))
        compare(actual, reference, "solid mask $color")
        assertMaskedInput(actual, alpha, "solid mask $color")
      }
    }
  }

  @Test
  fun solidProgressive_matchesConstantShaderBrush() {
    install()
    verifyInputControls()
    if (Build.VERSION.SDK_INT <= 32) {
      for (alpha in listOf(0f, 0.25f, 0.5f, 1f)) {
        val color = Color.Green.copy(alpha = alpha)
        val solid = HazeProgressive.Brush(SolidColor(color))
        val shader = HazeProgressive.Brush(constant(color))
        val params = parameters(mask = solid.asBrush())
        assertThat(params.progressive).isNull()
        assertThat(params.mask).isEqualTo(solid.asBrush())
        assertThat(params.retainInputWhenMasked).isEqualTo(false)
        val reference = captureEffect(parameters(mask = shader.asBrush()))
        assertMaskedInput(reference, alpha, "minimum-platform ShaderBrush fallback $alpha")
        val actual = captureEffect(params)
        compare(actual, reference, "minimum-platform SolidColor fallback $alpha")
        assertMaskedInput(actual, alpha, "minimum-platform non-retaining caller mask $alpha")
        val tint = listOf(HazeColorEffect.tint(Color.Green.copy(alpha = 0.5f)))
        compare(
          raster(mask = solid.asBrush(), effects = tint, blur = 8f, useChecker = true),
          raster(mask = shader.asBrush(), effects = tint, blur = 8f, useChecker = true),
          "styled blurred caller fallback $alpha",
        )
      }
    } else {
      for (alpha in listOf(0f, 0.25f, 0.5f, 1f)) {
        val color = Color.Green.copy(alpha = alpha)
        val tint = listOf(HazeColorEffect.tint(Color.Green))
        val actual = raster(effects = tint, progressive = HazeProgressive.Brush(SolidColor(color)))
        compare(actual, raster(effects = tint, progressive = HazeProgressive.Brush(constant(color))), "runtime progressive $alpha")
        assertThat(probe(actual, 0.25f, 0.25f).green).isCloseTo(alpha, TOLERANCE)
        assertThat(probe(actual, 0.25f, 0.25f).red).isCloseTo(1f - alpha, TOLERANCE)
      }
      val sharp = raster(useChecker = true)
      for (alpha in listOf(0f, 0.5f, 1f)) {
        val color = Color.Black.copy(alpha = alpha)
        val actual = raster(progressive = HazeProgressive.Brush(SolidColor(color)), blur = 8f, useChecker = true)
        val reference = raster(progressive = HazeProgressive.Brush(constant(color)), blur = 8f, useChecker = true)
        compare(actual, reference, "runtime checker $alpha", CHECKER_PROBES)
        if (alpha == 0f) compare(actual, sharp, "zero intensity preserves sharp checker", CHECKER_PROBES)
        if (alpha == 1f) {
          assertThat(kotlin.math.abs(probe(actual, 0.125f, 0.1875f).red - probe(sharp, 0.125f, 0.1875f).red))
            .isGreaterThan(0.1f)
        }
      }
    }
  }

  @Test
  fun solidTint_matchesColorTint() {
    install()
    verifyInputControls()
    // Dst discards its tint source; compare each API variant against the destination itself.
    val dstColors = listOf(0f, 0.002f, 0.004f, 0.005f, 1f / 255, 2f / 255, 0.25f, 0.5f, 1f).map { Color.Green.copy(alpha = it) } + Color(0.2f, 0.7f, 0.4f, 0.5f)
    for (color in dstColors) {
      val destination = raster()
      for (effect in listOf(HazeColorEffect.tint(color, BlendMode.Dst), HazeColorEffect.tint(SolidColor(color), BlendMode.Dst))) {
        val actual = raster(effects = listOf(effect))
        compare(actual, destination, "Dst identity $color $effect")
        assertMaskedInput(actual, 1f, "Dst unchanged input")
      }
    }
    val dstRamp = Brush.linearGradient(listOf(Color.Transparent, Color.Black), start = Offset.Zero, end = Offset(nodeSize.width.toFloat(), 0f))
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
          assertMaskedInput(actual, if (mask == null) 1f else 0.5f, "Dst preserves overall mask")
        }
      }
    }
    for (brush in dstProgressives.filterNotNull()) {
      val progressive = HazeProgressive.Brush(brush)
      for (mask in listOf(null, SolidColor(Color.Black.copy(alpha = 0.5f)))) {
        val destination = raster(mask = mask, progressive = progressive, blur = 8f, useChecker = true)
        for (effect in dstEffects) {
          compare(raster(mask = mask, effects = listOf(effect), progressive = progressive, blur = 8f, useChecker = true), destination, "Dst preserves progressive blurred checker and mask", CHECKER_PROBES)
        }
      }
    }
    val earlier = HazeColorEffect.tint(Color.Green.copy(alpha = 0.5f))
    val earlierDestination = raster(effects = listOf(earlier))
    for (effect in dstEffects) {
      val actual = raster(effects = listOf(earlier, effect))
      compare(actual, earlierDestination, "Dst preserves preceding tint")
      for ((x, y) in PROBES) {
        val pixel = probe(actual, x, y)
        assertThat(pixel.green).isCloseTo(0.5f, TOLERANCE)
        assertThat(pixel.red).isCloseTo(if (x < 0.5f) 0.5f else 0f, TOLERANCE)
        assertThat(pixel.blue).isCloseTo(if (x < 0.5f) 0f else 0.5f, TOLERANCE)
      }
    }
    if (Build.VERSION.SDK_INT <= 32) {
      for (alpha in listOf(0f, 0.5f, 1f)) {
        val caller = HazeProgressive.Brush(SolidColor(Color.Black.copy(alpha = alpha)))
        val destination = raster(mask = caller.asBrush())
        for (effect in dstEffects) {
          val params = parameters(mask = caller.asBrush(), effects = listOf(effect))
          assertThat(params.progressive).isNull()
          assertThat(params.retainInputWhenMasked).isEqualTo(false)
          val actual = captureEffect(params)
          compare(actual, destination, "Dst caller minimum-platform fallback $alpha")
          assertMaskedInput(actual, alpha, "Dst caller fallback composite")
        }
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
              val m = if (intensity >= 0f) intensity else ((actual.width * x).toInt() + 0.5f) / actual.width
              val alpha = m * if (mask == null) 1f else 0.5f
              val green = if (hasEarlier) 0.5f * modulate * m else 0f
              assertDestinationProbe(actual, x, y, alpha, green, "masked DstIn $color/$modulate intensity=$intensity")
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
    assertThat(probe(colorControl, 0.25f, 0.25f).green, "Color tint control").isCloseTo(1f, TOLERANCE)
    val supplied = raster(effects = listOf(HazeColorEffect.tint(SolidColor(Color.Green))))
    assertThat(probe(supplied, 0.25f, 0.25f).green, "supplied green-brush-tint reproduction").isCloseTo(1f, TOLERANCE)
    assertThat(probe(supplied, 0.25f, 0.25f).red).isCloseTo(0f, TOLERANCE)
    val colors = listOf(0f, 0.002f, 0.004f, 0.005f, 1f / 255, 2f / 255, 0.25f, 0.5f, 1f).map { Color.Green.copy(alpha = it) } + Color(0.2f, 0.7f, 0.4f, 0.5f)
    for (mode in MODES) for (color in colors) {
      compare(
        raster(effects = listOf(HazeColorEffect.tint(SolidColor(color), mode))),
        raster(effects = listOf(HazeColorEffect.tint(color, mode))),
        "tint $mode $color",
      )
    }
    val ramp = Brush.linearGradient(listOf(Color.Transparent, Color.Black), start = Offset.Zero, end = Offset(nodeSize.width.toFloat(), 0f))
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
          val pixel = probe(actual, x, y)
          val green = modulate * intensity
          assertThat(pixel.green, "ShaderBrush RGB green $modulate/$intensity").isCloseTo(green, TOLERANCE)
          assertThat(pixel.red).isCloseTo(if (x < 0.5f) 1f - green else 0f, TOLERANCE)
          assertThat(pixel.blue).isCloseTo(if (x < 0.5f) 0f else 1f - green, TOLERANCE)
        }
      }
      val progressive = HazeProgressive.Brush(ramp)
      val actual = raster(effects = shaderTint, progressive = progressive, modulate = modulate)
      compare(actual, raster(effects = listOf(HazeColorEffect.tint(Color.Green)), progressive = progressive, modulate = modulate), "ShaderBrush ramp Color parity $modulate")
      for ((x, y) in PROBES) {
        val pixel = probe(actual, x, y)
        val green = modulate * ((nodeSize.width * x).toInt() + 0.5f) / nodeSize.width
        assertThat(pixel.green, "ShaderBrush ramp green $modulate at $x,$y").isCloseTo(green, TOLERANCE)
        assertThat(pixel.red).isCloseTo(if (x < 0.5f) 1f - green else 0f, TOLERANCE)
        assertThat(pixel.blue).isCloseTo(if (x < 0.5f) 0f else 1f - green, TOLERANCE)
      }
    }
  }

  private fun install() {
    rule.setContent {
      density = LocalDensity.current
      Box(Modifier.size(64.dp).background(Color.Magenta).testTag(TAG)) {
        Box(
          Modifier.size(64.dp)
            .onSizeChanged { nodeSize = it }
            .graphicsLayer { renderEffect = currentEffect.value }
            .drawBehind {
              assertThat(drawContext.canvas.nativeCanvas.isHardwareAccelerated, "real child draw uses hardware canvas").isTrue()
              hardwareDraws++
              if (checker.value) {
                val tile = size.width / 8
                for (x in 0..7) for (y in 0..7) {
                  drawRect(if ((x + y) % 2 == 0) Color.Black else Color.White, Offset(x * tile, y * tile), Size(tile, tile))
                }
              } else {
                drawRect(Color.Red, size = Size(size.width / 2, size.height))
                drawRect(Color.Blue, Offset(size.width / 2, 0f), Size(size.width / 2, size.height))
              }
            },
        )
      }
    }
    rule.waitForIdle()
    assertThat(nodeSize.width).isGreaterThan(0)
    assertThat(nodeSize.height).isGreaterThan(0)
    assertThat(hardwareDraws).isGreaterThan(0)
  }

  private fun verifyInputControls() {
    val unfiltered = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
    assertMaskedInput(unfiltered, 1f, "unfiltered red/blue input")
    assertMaskedInput(raster(mask = constant(Color.Transparent)), 0f, "transparent shader reveals magenta underlay")
  }

  private fun parameters(
    mask: Brush? = null,
    effects: List<HazeColorEffect> = emptyList(),
    progressive: HazeProgressive? = null,
    modulate: Float = 1f,
    blur: Float = 0f,
  ) = RenderEffectParams(
    blurRadius = blur.dp,
    noiseFactor = 0f,
    scale = 1f,
    contentSize = Size(nodeSize.width.toFloat(), nodeSize.height.toFloat()),
    contentOffset = Offset.Zero,
    colorEffects = effects,
    colorEffectsAlphaModulate = modulate,
    backgroundColor = Color.Transparent,
    mask = mask,
    progressive = progressive,
    retainInputWhenMasked = false,
    blurTileMode = TileMode.Clamp,
  )

  private fun raster(
    mask: Brush? = null,
    effects: List<HazeColorEffect> = emptyList(),
    progressive: HazeProgressive? = null,
    modulate: Float = 1f,
    blur: Float = 0f,
    useChecker: Boolean = false,
  ): PixelMap = captureEffect(parameters(mask, effects, progressive, modulate, blur), useChecker)

  private fun captureEffect(params: RenderEffectParams, useChecker: Boolean = false): PixelMap {
    rule.runOnIdle {
      checker.value = useChecker
      currentEffect.value = createRenderEffect(rule.activity, density, params).asComposeRenderEffect()
    }
    rule.waitForIdle()
    return rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
  }

  private fun assertDestination(pixels: PixelMap, alpha: Float, green: Float, label: String) {
    for ((x, y) in PROBES) assertDestinationProbe(pixels, x, y, alpha, green, label)
  }

  private fun assertDestinationProbe(pixels: PixelMap, x: Float, y: Float, alpha: Float, green: Float, label: String) {
    val pixel = probe(pixels, x, y)
    val red = if (x < 0.5f) 1f - green else 0f
    val blue = if (x < 0.5f) 0f else 1f - green
    assertThat(pixel.red, "$label red").isCloseTo(red * alpha + 1f - alpha, TOLERANCE)
    assertThat(pixel.green, "$label green").isCloseTo(green * alpha, TOLERANCE)
    assertThat(pixel.blue, "$label blue").isCloseTo(blue * alpha + 1f - alpha, TOLERANCE)
  }

  private fun constant(color: Color) = Brush.linearGradient(listOf(color, color))

  private fun probe(pixels: PixelMap, x: Float, y: Float) = pixels[(pixels.width * x).toInt(), (pixels.height * y).toInt()]

  private fun assertMaskedInput(pixels: PixelMap, alpha: Float, label: String) {
    for ((x, y) in PROBES) {
      val actual = probe(pixels, x, y)
      // The opaque magenta parent reveals the transparent part of the filtered child.
      assertThat(actual.red, "$label red at $x,$y").isCloseTo(if (x < 0.5f) 1f else 1f - alpha, TOLERANCE)
      assertThat(actual.green, "$label green at $x,$y").isCloseTo(0f, TOLERANCE)
      assertThat(actual.blue, "$label blue at $x,$y").isCloseTo(if (x < 0.5f) 1f - alpha else 1f, TOLERANCE)
    }
  }

  private fun compare(actual: PixelMap, expected: PixelMap, label: String, probes: List<Pair<Float, Float>> = PROBES) {
    for ((x, y) in probes) {
      val a = probe(actual, x, y)
      val e = probe(expected, x, y)
      assertThat(a.red, "$label red at $x,$y").isCloseTo(e.red, TOLERANCE)
      assertThat(a.green, "$label green at $x,$y").isCloseTo(e.green, TOLERANCE)
      assertThat(a.blue, "$label blue at $x,$y").isCloseTo(e.blue, TOLERANCE)
    }
  }

  private companion object {
    const val TAG = "solid_color_blur_output"
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
    val PROBES = listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.25f to 0.75f)
    val CHECKER_PROBES = listOf(0.125f to 0.1875f, 0.1875f to 0.1875f, 0.5f to 0.4375f, 0.75f to 0.8125f)
  }
}
