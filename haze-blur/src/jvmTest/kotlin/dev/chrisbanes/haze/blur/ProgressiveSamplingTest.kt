// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.createOffsetRenderEffect
import dev.chrisbanes.haze.createProgressiveBlurRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import dev.chrisbanes.haze.drawScaledContent
import dev.chrisbanes.haze.scaledContentScale
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.test.Test

class ProgressiveSamplingTest {
  private var graphIndex = 0

  @Test
  fun fullIntensity_qualityRadius24_preservesBandsAndCorners() = assertEdges(render("quality24"))

  @Test
  fun plain_qualityRadius24_preservesBandsAndCorners() = assertEdges(render("plain24", progressive = false))

  @Test
  fun zeroIntensity_qualityRadius24_preservesIdentity() = assertEdges(render("zero24", intensity = 0f))

  @Test
  fun prototype_eachAxisAndTapPath_hasDiscriminatingPixels() {
    val crop = Rect(2.25f, 4.25f, 61.75f, 59.75f)
    for (radius in listOf(1f, 24f, 25f)) {
      val expected = graphPixels(reference(radius, crop))
      for (axis in listOf(0, 1)) {
        for (odd in listOf(false, true)) {
          if (radius == 1f && !odd || radius == 24f && odd) continue
          val faulty = graphPixels(reference(radius, crop, omitAxis = axis, omitOdd = odd))
          val error = residual(expected, faulty)
          println("prototype omission axis=$axis odd=$odd radius=$radius residual=$error")
          assertThat(error, "axis=$axis odd=$odd radius=$radius").isGreaterThan(0.001f)
        }
      }
    }
  }

  @Test
  fun prototype_subpixelBounds_matchIndependentMidpointReference() {
    for (extent in listOf(0.25f, 0.75f, 1f)) for (vertical in listOf(false, true)) {
      val crop = Rect(2.25f, 4.5f, 2.25f + if (vertical) 20f else extent, 4.5f + if (vertical) extent else 20f)
      for (radius in listOf(1f, 24f, 25f)) {
        val expected = graphPixels(reference(radius, crop))
        val prototype = graphPixels(reference(radius, crop, shaderBounds = true))
        println("prototype subpixel extent=$extent vertical=$vertical radius=$radius residual=${residual(expected, prototype)}")
        assertThat(residual(expected, prototype)).isLessThan(0.02f)
        for (y in 0 until prototype.height) for (x in 0 until prototype.width) {
          assertThat(prototype[x, y].alpha.isFinite()).isTrue()
        }
      }
    }
  }

  @Test
  fun production_subpixelBounds_matchIndependentMidpointReference() {
    for (extent in listOf(0.25f, 0.75f, 1f)) for (vertical in listOf(false, true)) {
      val crop = Rect(2.25f, 4.5f, 2.25f + if (vertical) 20f else extent, 4.5f + if (vertical) extent else 20f)
      for (radius in listOf(1f, 24f, 25f)) {
        assertThat(residual(graphPixels(production(radius, crop)), graphPixels(reference(radius, crop))), "$crop radius=$radius").isLessThan(0.02f)
      }
    }
  }

  @Test
  fun production_pairedAndOddRadii_matchIndependentTapTable() {
    for (radius in listOf(1f, 24f, 25f, 149f, 150f, 151f)) {
      val extent = if (radius >= 149f) 384 else 64
      val crop = Rect(2.25f, 4.25f, extent - 2.25f, extent - 4.25f)
      val error = residual(graphPixels(production(radius, crop), extent = extent), graphPixels(reference(radius, crop), extent = extent))
      println("production reference radius=$radius residual=$error")
      assertThat(error, "radius=$radius").isLessThan(0.02f)
    }
  }

  @Test
  fun production_modesDensitiesRadiiAndAlpha_preserveBandsCornersAndPremultipliedOutput() {
    for (mode in listOf(HazePerformanceMode.Quality, HazePerformanceMode.Default, HazePerformanceMode.Performance, HazePerformanceMode.Fixed(5f / 12f))) {
      for (density in listOf(1f, 2.625f)) for (radius in listOf(1f, 24f, 25f)) for (alpha in listOf(1f, 0.5f)) {
        val size = if (mode == HazePerformanceMode.Fixed(5f / 12f)) Size(204f, 204f) else Size(140f, 140f)
        val label = "$mode-$density-$radius-$alpha"
        assertEdges(render(label, radius = radius, alpha = alpha, size = size, mode = mode, density = density), alpha)
        val transparent = render("transparent-$label", radius = radius, alpha = alpha, size = size, mode = mode, density = density, background = Color.Transparent)
        for (y in 0 until transparent.height) for (x in 0 until transparent.width) {
          val c = transparent[x, y]
          assertThat(abs(c.alpha - alpha), "$label alpha($x,$y)").isLessThan(0.02f)
          assertThat(abs(c.red * c.alpha - alpha), "$label premultiplied red($x,$y)").isLessThan(0.02f)
          assertThat(c.blue * c.alpha, "$label premultiplied blue($x,$y)").isLessThan(0.02f)
        }
      }
    }
  }

  @Test
  fun production_fractionalExpandedAndTransposedGeometry_matchIndependentReference() {
    for (transpose in listOf(false, true)) for (mode in listOf(HazePerformanceMode.Quality, HazePerformanceMode.Default, HazePerformanceMode.Performance)) {
      val size = if (transpose) Size(19f, 21f) else Size(21f, 19f)
      val layer = if (transpose) Size(23.4f, 25.2f) else Size(25.2f, 23.4f)
      val offset = if (transpose) Offset(4.5f, 2.25f) else Offset(2.25f, 4.5f)
      render("fractional-$transpose-$mode", radius = 25f, size = size, mode = mode, layerSize = layer, origin = offset, verifyReference = true, transposePattern = transpose)
    }
  }

  @Test
  fun production_rejectedTaps_doNotFoldOutsideSentinelIntoCrop() {
    val crop = Rect(2.25f, 4.25f, 61.75f, 59.75f)
    val actual = graphPixels(production(24f, crop), sentinel = true)
    val expected = graphPixels(reference(24f, crop), sentinel = true)
    val folded = graphPixels(reference(24f, crop, foldRejected = true), sentinel = true)
    assertThat(residual(actual, expected)).isLessThan(0.02f)
    val error = residual(actual, folded)
    println("rejected sentinel clamp-before-admission fault=$error")
    assertThat(error).isGreaterThan(0.02f)
  }

  @Test
  fun production_patternedGradients_preserveAuthoredIntensityAndGaussianResponse() {
    val crop = Rect(2.25f, 4.5f, 61.75f, 59.75f)
    for (vertical in listOf(false, true)) {
      val brush = if (vertical) Brush.verticalGradient(listOf(Color.Black, Color.Transparent), endY = 55.25f) else Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), endX = 59.5f)
      val mask = (brush as ShaderBrush).createShader(Size(64f, 64f))
      val actual = graphPixels(createProgressiveBlurRenderEffect(25f, crop.size, crop.topLeft, mask, crop.topLeft), patterned = true)
      val expected = graphPixels(gradientReference(crop, vertical), patterned = true)
      val error = residual(actual, expected)
      println("gradient vertical=$vertical reference residual=$error")
      assertThat(error).isLessThan(0.02f)
      val zeroMask = (Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)) as ShaderBrush).createShader(Size(64f, 64f))
      val identity = graphPixels(createProgressiveBlurRenderEffect(25f, crop.size, crop.topLeft, zeroMask, crop.topLeft), patterned = true)
      assertThat(residual(actual, identity), "gradient must blur the patterned input").isGreaterThan(0.1f)
    }
  }

  private fun gradientReference(crop: Rect, vertical: Boolean): PlatformRenderEffect {
    var input: PlatformRenderEffect? = null
    for (axis in 0..1) {
      val source = buildString {
        val channel = if (vertical) "y" else "x"
        val origin = if (vertical) crop.top else crop.left
        val extent = if (vertical) crop.height else crop.width
        append("uniform shader content; vec4 main(vec2 coord) { float radius = 25.0 * (1.0 - clamp((coord.$channel - $origin) / $extent, 0.0, 1.0)); float r = floor(radius + 0.5); float sigma = max(radius / 2.0, 1.0); vec4 result = content.eval(clamp(coord, vec2(${crop.left + .5f}, ${crop.top + .5f}), vec2(${crop.right - .5f}, ${crop.bottom - .5f}))); float sum = 1.0;\n")
        fun emit(distance: String, weight: String, condition: String) {
          for (sign in listOf("-", "+")) {
            val offset = if (axis == 0) "vec2($distance, 0.0)" else "vec2(0.0, $distance)"
            val admission = if (sign == "-") "p.x >= ${crop.left} && p.y >= ${crop.top}" else "p.x <= ${crop.right} && p.y <= ${crop.bottom}"
            append("{ vec2 p = coord $sign $offset; if ($condition && $admission) { result += $weight * content.eval(clamp(p, vec2(${crop.left + .5f}, ${crop.top + .5f}), vec2(${crop.right - .5f}, ${crop.bottom - .5f}))); sum += $weight; } }\n")
          }
        }
        for (i in 1..25 step 2) {
          append("{ float a = exp(-${i * i}.0 / (2.0 * sigma * sigma)); float b = exp(-${(i + 1) * (i + 1)}.0 / (2.0 * sigma * sigma)); float w = a + b;\n")
          emit("($i.0 + b / w)", "w", "r > $i.0")
          emit("$i.0", "a", "r == $i.0")
          append("}\n")
        }
        append("return result / sum; }")
      }
      input = createRuntimeShaderRenderEffect(createRuntimeEffect(source), arrayOf("content"), arrayOf(input))
    }
    return checkNotNull(input)
  }

  // #1772: the unweighted seed is clamped like every other tap. A zero mask keeps interior pixel
  // centres unchanged, and a sub-pixel crop collapses to its midpoint sample.
  @Test
  fun production_zeroMask_clampsSeedAndKeepsInteriorIdentity() {
    val mask = (Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)) as ShaderBrush).createShader(Size(64f, 64f))
    val subpixel = Rect(2.25f, 4.5f, 2.5f, 5.25f)
    val collapsed = graphPixels(createProgressiveBlurRenderEffect(25f, subpixel.size, subpixel.topLeft, mask, Offset(1.75f, 3.25f)), patterned = true)
    assertThat(residual(collapsed, graphPixels(reference(0f, subpixel), patterned = true))).isLessThan(0.02f)

    val crop = Rect(2f, 4f, 62f, 60f)
    val actual = graphPixels(createProgressiveBlurRenderEffect(25f, crop.size, crop.topLeft, mask, crop.topLeft), patterned = true)
    val identity = graphPixels(createOffsetRenderEffect(0f, 0f), patterned = true)
    for (y in 4 until 60) for (x in 2 until 62) {
      val a = actual[x, y]
      val e = identity[x, y]
      assertThat(maxOf(abs(a.red - e.red), abs(a.green - e.green), abs(a.blue - e.blue), abs(a.alpha - e.alpha)), "($x,$y)").isLessThan(0.02f)
    }
  }

  private fun production(radius: Float, crop: Rect): PlatformRenderEffect = createProgressiveBlurRenderEffect(
    radius,
    crop.size,
    crop.topLeft,
    (Brush.linearGradient(listOf(Color.Black, Color.Black)) as ShaderBrush).createShader(Size(64f, 64f)),
  )

  // Independent tap table: weights/paired offsets are calculated in Double on the CPU,
  // then unrolled; no production shader source or candidate pixels supply the oracle.
  private fun reference(radius: Float, crop: Rect, omitAxis: Int = -1, omitOdd: Boolean = false, shaderBounds: Boolean = false, foldRejected: Boolean = false): PlatformRenderEffect {
    val sigma = maxOf(radius.toDouble() / 2.0, 1.0)
    val r = floor(radius + 0.5f).toInt()
    fun weight(i: Int) = exp(-i.toDouble() * i / (2.0 * sigma * sigma))
    val taps = mutableListOf<Triple<Double, Double, Boolean>>()
    for (i in 1 until minOf(r, 150) step 2) {
      val a = weight(i)
      val b = weight(i + 1)
      taps += Triple(i + b / (a + b), a + b, false)
    }
    if (r < 150 && r % 2 == 1) taps += Triple(r.toDouble(), weight(r), true)
    fun number(n: Number) = n.toDouble().toString()
    val mx = (crop.left + crop.right) / 2f
    val my = (crop.top + crop.bottom) / 2f
    val lower = "vec2(${number(minOf(crop.left + 0.5f, mx))}, ${number(minOf(crop.top + 0.5f, my))})"
    val upper = "vec2(${number(maxOf(crop.right - 0.5f, mx))}, ${number(maxOf(crop.bottom - 0.5f, my))})"
    var input: PlatformRenderEffect? = null
    for (axis in 0..1) {
      val source = buildString {
        append("uniform shader content; vec4 main(vec2 coord) { vec4 result = content.eval(clamp(coord, $lower, $upper)); float sum = 1.0;\n")
        for ((distance, weight, odd) in taps) for (sign in listOf(-1, 1)) {
          val d = number(distance * sign)
          val offset = if (axis == 0) "vec2($d, 0.0)" else "vec2(0.0, $d)"
          val admission = if (sign < 0) "p.x >= ${number(crop.left)} && p.y >= ${number(crop.top)}" else "p.x <= ${number(crop.right)} && p.y <= ${number(crop.bottom)}"
          val bounds = if (shaderBounds) {
            val lo = "vec2(${number(crop.left)}, ${number(crop.top)})"
            val hi = "vec2(${number(crop.right)}, ${number(crop.bottom)})"
            "clamp(p, min($lo + vec2(0.5), ($lo + $hi) / 2.0), max($hi - vec2(0.5), ($lo + $hi) / 2.0))"
          } else {
            "clamp(p, $lower, $upper)"
          }
          val sample = if (axis == omitAxis && odd == omitOdd) "p" else bounds
          append("{ vec2 p = coord + $offset; if (${if (foldRejected) "true" else admission}) { result += ${number(weight)} * content.eval($sample); sum += ${number(weight)}; } }\n")
        }
        append("return result / sum; }")
      }
      input = createRuntimeShaderRenderEffect(createRuntimeEffect(source), arrayOf("content"), arrayOf(input))
    }
    return checkNotNull(input)
  }

  private fun graphPixels(effect: PlatformRenderEffect, sentinel: Boolean = false, patterned: Boolean = false, extent: Int = 64): PixelMap {
    val bitmap = ImageBitmap(extent, extent)
    val canvas = Canvas(bitmap)
    canvas.saveLayer(Rect(0f, 0f, extent.toFloat(), extent.toFloat()), Paint().apply { skiaPaint.imageFilter = effect })
    if (sentinel) canvas.drawRect(Rect(0f, 0f, extent.toFloat(), extent.toFloat()), Paint().apply { color = Color.Green })
    canvas.drawRect(Rect(2f, 4f, extent - 2f, extent - 4f), Paint().apply { color = Color.Red })
    canvas.drawRect(Rect(2f, 4f, 3f, extent - 4f), Paint().apply { color = Color.Black })
    canvas.drawRect(Rect(2f, 4f, extent - 2f, 5f), Paint().apply { color = Color.Black })
    if (patterned) {
      for (x in 6 until 60 step 6) canvas.drawRect(Rect(x.toFloat(), 4f, x + 2f, 60f), Paint().apply { color = Color.Black })
      for (y in 8 until 60 step 7) canvas.drawRect(Rect(2f, y.toFloat(), 62f, y + 2f), Paint().apply { color = Color.Blue })
    }
    canvas.restore()
    val test = Throwable().stackTrace.first { it.className == javaClass.name && it.methodName != "graphPixels" && !it.methodName.contains("default") }.methodName
    val file = File("build/outputs/1727-sampling/$test-${graphIndex++}.png")
    file.parentFile.mkdirs()
    org.jetbrains.skia.Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
      file.writeBytes(checkNotNull(image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)).bytes)
    }
    return bitmap.toPixelMap()
  }

  private fun residual(a: PixelMap, b: PixelMap): Float = (0 until a.height).maxOf { y ->
    (0 until a.width).maxOf { x ->
      maxOf(
        abs(a[x, y].red * a[x, y].alpha - b[x, y].red * b[x, y].alpha),
        abs(a[x, y].green * a[x, y].alpha - b[x, y].green * b[x, y].alpha),
        abs(a[x, y].blue * a[x, y].alpha - b[x, y].blue * b[x, y].alpha),
        abs(a[x, y].alpha - b[x, y].alpha),
      )
    }
  }

  private fun render(
    label: String,
    progressive: Boolean = true,
    intensity: Float = 1f,
    radius: Float = 24f,
    alpha: Float = 1f,
    size: Size = Size(140f, 140f),
    mode: HazePerformanceMode = HazePerformanceMode.Quality,
    density: Float = 1f,
    background: Color = Color.Magenta,
    layerSize: Size = size,
    origin: Offset = Offset.Zero,
    verifyReference: Boolean = false,
    transposePattern: Boolean = false,
  ): PixelMap {
    val base = RecordingDrawContext(recordedSize = size).also {
      it.renderDensity = Density(density)
      it.layerSize = layerSize
      it.layerOffset = origin
    }
    val graphics = base.requireGraphicsContext()
    val input = graphics.createGraphicsLayer()
    input.record(Density(1f), LayoutDirection.Ltr, IntSize(size.width.toInt(), size.height.toInt())) {
      drawRect(Color.Red.copy(alpha = alpha))
      if (verifyReference) {
        for (y in 0 until size.height.toInt()) for (x in 0 until size.width.toInt()) {
          val u = if (transposePattern) y else x
          val v = if (transposePattern) x else y
          val color = when {
            v % 7 < 2 -> Color.Blue
            u % 4 < 2 -> Color.Black
            else -> Color.Red
          }
          drawRect(color, Offset(x.toFloat(), y.toFloat()), Size(1f, 1f))
        }
      }
    }
    val context = object : HazeEffectRuntimeDrawScope by base {
      override fun DrawScope.drawInput() = drawLayer(input)
    }
    val effect = BlurVisualEffect()
    effect.update(
      BlurLifecycleScope,
      BlurConfiguration(
        HazeBlurStyle {
          blurRadius(radius.dp)
          noiseFactor(0f)
          colorEffects(emptyList())
          backgroundColor(Color.Transparent)
          if (progressive) progressive(HazeProgressive.Brush(SolidColor(Color.Black.copy(alpha = intensity))))
        },
        mode,
      ),
      HazeSampling.FullResolution,
    )
    val delegate = RenderEffectBlurVisualEffectDelegate(effect)
    try {
      base.render {
        drawRect(background)
        with(delegate) { draw(context) }
      }
      val output = checkNotNull(base.output)
      val file = File("build/outputs/1727-sampling/$label.png")
      file.parentFile.mkdirs()
      org.jetbrains.skia.Image.makeFromBitmap(output.asSkiaBitmap()).use { image ->
        file.writeBytes(checkNotNull(image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)).bytes)
      }
      val actual = output.toPixelMap()
      if (verifyReference) {
        val layer = checkNotNull(delegate.scaledContentLayer)
        val nominal = effect.resolveInputScaleFactor()
        val scale = scaledContentScale(layerSize, nominal)
        val crop = Rect(
          maxOf(0f, origin.x * scale.scaleX),
          maxOf(0f, origin.y * scale.scaleY),
          minOf(layer.size.width.toFloat(), (origin.x + size.width) * scale.scaleX),
          minOf(layer.size.height.toFloat(), (origin.y + size.height) * scale.scaleY),
        )
        layer.renderEffect = reference(radius * density * nominal, crop).asComposeRenderEffect()
        base.render {
          drawRect(background)
          drawScaledContent(-origin, scale, clip = layer.clip) { drawLayer(layer) }
        }
        val error = residual(actual, checkNotNull(base.output).toPixelMap())
        println("$label fractional reference residual=$error crop=$crop")
        assertThat(error, label).isLessThan(0.02f)
      }
      return actual
    } finally {
      delegate.detach()
      graphics.releaseGraphicsLayer(input)
    }
  }

  private fun assertEdges(pixels: PixelMap, alpha: Float = 1f) {
    val regions = linkedMapOf<String, Float>()
    fun measure(label: String, xs: IntRange, ys: IntRange) {
      regions[label] = ys.maxOf { y -> xs.maxOf { x -> abs(pixels[x, y].blue - (1f - alpha)) } }
    }
    val w = pixels.width
    val h = pixels.height
    measure("left", 0..3, 4 until h - 4)
    measure("right", w - 4 until w, 4 until h - 4)
    measure("top", 4 until w - 4, 0..3)
    measure("bottom", 4 until w - 4, h - 4 until h)
    measure("topLeft", 0..3, 0..3)
    measure("topRight", w - 4 until w, 0..3)
    measure("bottomLeft", 0..3, h - 4 until h)
    measure("bottomRight", w - 4 until w, h - 4 until h)
    println("sampling regions=$regions alpha=$alpha")
    for ((region, error) in regions) assertThat(error, region).isLessThan(0.02f)
  }
}
