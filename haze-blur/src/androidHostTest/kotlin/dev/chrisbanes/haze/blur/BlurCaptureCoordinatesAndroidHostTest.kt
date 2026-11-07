// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.test.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31, 32], qualifiers = "+w160dp-h320dp")
class BlurCaptureCoordinatesAndroidHostTest : ScreenshotTest() {
  @Test
  fun fractionalCapture_sharedTranslatedShader_retainsPixelsAndCallerMatrix() = runScreenshotTest {
    val scaleY = 23f / 23.4f
    val start = scaleY * 10.5f
    val end = scaleY * 26.5f
    fun gradient(from: Float, to: Float) = LinearGradient(0f, from, 0f, to, android.graphics.Color.BLACK, android.graphics.Color.WHITE, Shader.TileMode.CLAMP)
    fun bitmap() = Bitmap.createBitmap(25, 23, Bitmap.Config.ARGB_8888)
    fun immutable(b: Bitmap) = checkNotNull(b.copy(Bitmap.Config.ARGB_8888, false))
    fun effect(b: Bitmap) = RenderEffect.createShaderEffect(BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
    fun code(color: Int) = android.graphics.Color.red(color)

    // Independent identity-Canvas references, including phases on both sides of half an LSB.
    val reference = bitmap().also { Canvas(it).drawPaint(Paint().apply { shader = gradient(start, end) }) }.let(::immutable)
    val phases = listOf(0.2f, 0.8f)
    val phaseBitmap = bitmap().also { b ->
      val canvas = Canvas(b)
      phases.forEachIndexed { index, phase ->
        val phaseStart = 0.5f - (40f + phase) * (end - start) / 255f
        canvas.drawRect(index * 10f, 0f, index * 10f + 10f, 23f, Paint().apply { shader = gradient(phaseStart, phaseStart + end - start) })
      }
    }.let(::immutable)
    val paletteCodes = listOf(0, 1, 32, 67, 116, 132, 181, 254, 255)
    val palette = bitmap().also { b -> paletteCodes.forEachIndexed { x, q -> b.setPixel(x, 0, android.graphics.Color.rgb(q, q, q)) } }.let(::immutable)

    val shader = gradient(4f, 20f).apply { setLocalMatrix(Matrix().apply { setTranslate(0f, 2f) }) }
    fun matrix() = FloatArray(9).also { values -> Matrix().also { shader.getLocalMatrix(it) }.getValues(values) }.toList()
    val originalMatrix = matrix()
    val brush = object : ShaderBrush() {
      override fun createShader(size: Size): Shader = shader
    }
    val first = capture(brush, 25.2f, 23.4f, 2.25f, 4.5f)
    val firstEffect = effect(first.bitmap)
    var effects by mutableStateOf(listOf(firstEffect, effect(reference), effect(phaseBitmap), effect(palette)))
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Row(Modifier.background(Color.Black)) {
          effects.forEachIndexed { index, current ->
            Box(
              Modifier.size(27.dp, 25.dp).testTag("capture$index").graphicsLayer {
                renderEffect = current.asComposeRenderEffect()
              }.background(Color.White),
            )
          }
        }
      }
    }
    waitForIdle()
    val before = captureRootPixels()
    fun origin(index: Int): Pair<Int, Int> = onNodeWithTag("capture$index").fetchSemanticsNode().boundsInRoot.let { it.left.roundToInt() to it.top.roundToInt() }
    fun rootColor(index: Int, x: Int, y: Int): Int {
      val (left, top) = origin(index)
      return before[left + x, top + y].toArgb()
    }
    fun rootCode(index: Int, x: Int, y: Int) = code(rootColor(index, x, y))
    val rows = (10..22).toList()
    val captureRows = rows.map { code(first.bitmap.getPixel(10, it)) }
    val rootRows = rows.map { rootCode(0, 10, it) }
    val captureRgb = rows.map { first.bitmap.getPixel(10, it) and 0x00ffffff }
    val rootRgb = rows.map { rootColor(0, 10, it) and 0x00ffffff }
    val referenceRows = rows.map { code(reference.getPixel(10, it)) }
    val phaseRows = phases.indices.map { index -> (0..8).map { y -> code(phaseBitmap.getPixel(index * 10 + 5, y)) } }
    val paletteRoot = paletteCodes.indices.map { rootColor(3, it, 0) and 0x00ffffff }
    println("T1_CALIBRATION sdk=${android.os.Build.VERSION.SDK_INT} config=${first.bitmap.config} colorspace=${first.bitmap.colorSpace} reference=$referenceRows capture=$captureRows root=$rootRows captureRgb=$captureRgb rootRgb=$rootRgb phases=$phaseRows palette=$paletteRoot")

    // Freeze the independently sampled rounding model and downstream palette before candidate checks.
    assertThat(paletteRoot, "integer RGB palette survives effect and root readback")
      .isEqualTo(paletteCodes.map { android.graphics.Color.rgb(it, it, it) and 0x00ffffff })
    phases.forEachIndexed { index, phase ->
      val expected = (0..8).map { y -> floor(40.0 + phase + y * 255.0 / (end - start)).toInt() }
      assertThat(phaseRows[index], "independent floor calibration phase=$phase").isEqualTo(expected)
    }
    assertThat(rows.map { rootColor(1, 10, it) and 0x00ffffff }, "reference RGB root readback")
      .isEqualTo(rows.map { reference.getPixel(10, it) and 0x00ffffff })
    assertThat(first.bitmap.isMutable).isFalse()
    assertThat(rootRgb, "candidate capture and root RGB codes").isEqualTo(captureRgb)
    assertThat(captureRgb, "all capture RGB channels follow the calibrated ramp")
      .isEqualTo(captureRows.map { android.graphics.Color.rgb(it, it, it) and 0x00ffffff })
    val lower = rows.zip(rootRows).maxOf { (y, q) -> if (q == 255) Double.NEGATIVE_INFINITY else (y + 0.5) / scaleY - 4.5 + 8.0 - 16.0 * (q + 1) / 255.0 }
    val upper = rows.zip(rootRows).minOf { (y, q) -> if (q == 0) Double.POSITIVE_INFINITY else (y + 0.5) / scaleY - 4.5 + 8.0 - 16.0 * q / 255.0 }
    println("T1_INTERVAL sdk=${android.os.Build.VERSION.SDK_INT} lower=$lower upper=$upper")
    assertThat(upper - lower, "nonempty authored crossing interval").isGreaterThan(0.0)
    assertThat(lower, "whole interval lower bound").isGreaterThan(13.975)
    assertThat(upper, "whole interval upper bound").isLessThanOrEqualTo(14.025)
    assertThat(captureRows, "identity Canvas reference").isEqualTo(referenceRows)

    val (left, top) = origin(0)
    val oldPixels = (0 until first.height).flatMap { y -> (0 until first.width).map { x -> before[left + x, top + y] } }
    val second = capture(brush, 26.4f, 24.6f, 1.5f, 3.25f)
    effects = listOf(firstEffect, effect(second.bitmap), firstEffect)
    waitForIdle()
    val after = captureRootPixels()
    val (againLeft, againTop) = origin(2)
    val repeated = (0 until first.height).flatMap { y -> (0 until first.width).map { x -> after[againLeft + x, againTop + y] } }
    assertThat(repeated, "retained effect after alternate geometry").isEqualTo(oldPixels)
    assertThat(matrix(), "caller shader matrix").isEqualTo(originalMatrix)
    val (otherLeft, otherTop) = origin(1)
    assertThat(kotlin.math.abs(after[left + 10, top + 17].red - after[otherLeft + 10, otherTop + 17].red), "alternate geometry changes output").isGreaterThan(0.025f)
  }

  @Test
  @Config(sdk = [35])
  fun sdk35GradientTint_overWhiteAndBlackInput() = runScreenshotTest {
    val brush = Brush.verticalGradient(listOf(Color.White, Color.Black), startY = 6f, endY = 12f)
    val effects = listOf(Offset.Zero, Offset.Zero, Offset(2f, 4f), Offset(2.25f, 4.5f)).mapIndexed { index, offset ->
      createRenderEffect(
        ApplicationProvider.getApplicationContext(),
        Density(1f),
        RenderEffectParams(
          blurRadius = 0.dp, noiseFactor = 0f, scale = 1f, contentSize = Size(21f, 19f),
          contentOffset = offset, captureScale = if (index == 3) ScaleFactor(25f / 25.2f, 23f / 23.4f) else ScaleFactor(1f, 1f),
          captureSize = Size(25f, 23f), colorEffects = listOf(HazeColorEffect.tint(brush)), blurTileMode = TileMode.Clamp,
        ),
      )
    }
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Row {
          listOf(Color.White, Color.Black).forEachIndexed { index, color ->
            Box(
              Modifier.size(27.dp, 25.dp).testTag("tint$index").graphicsLayer {
                renderEffect = effects[index].asComposeRenderEffect()
              }.background(color),
            )
          }
        }
      }
    }
    composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
    waitForIdle()
    val pixels = captureRootPixels()
    for (index in 0..1) {
      val bounds = onNodeWithTag("tint$index").fetchSemanticsNode().boundsInRoot
      val red = pixels[bounds.left.roundToInt() + 4, bounds.top.roundToInt() + 8].red
      println("SDK35_TINT input=$index red=$red")
      assertThat(red, "tint over input $index").isCloseTo(7f / 12f, 0.025f)
    }
  }

  @Test
  @Config(sdk = [31, 32, 35])
  fun quality_vertical_mask_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Quality, horizontal = false, tint = false)

  @Test
  @Config(sdk = [31, 32, 35])
  fun quality_vertical_tint_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Quality, horizontal = false, tint = true)

  @Test
  @Config(sdk = [31, 32, 35])
  fun quality_horizontal_mask_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Quality, horizontal = true, tint = false)

  @Test
  @Config(sdk = [31, 32, 35])
  fun quality_horizontal_tint_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Quality, horizontal = true, tint = true)

  @Test
  @Config(sdk = [31, 32, 35])
  fun default_vertical_mask_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Default, horizontal = false, tint = false)

  @Test
  @Config(sdk = [31, 32, 35])
  fun default_vertical_tint_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Default, horizontal = false, tint = true)

  @Test
  @Config(sdk = [31, 32, 35])
  fun default_horizontal_mask_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Default, horizontal = true, tint = false)

  @Test
  @Config(sdk = [31, 32, 35])
  fun default_horizontal_tint_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Default, horizontal = true, tint = true)

  @Test
  @Config(sdk = [31, 32, 35])
  fun fixed_vertical_mask_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Fixed(0.5f), horizontal = false, tint = false)

  @Test
  @Config(sdk = [31, 32, 35])
  fun fixed_vertical_tint_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Fixed(0.5f), horizontal = false, tint = true)

  @Test
  @Config(sdk = [31, 32, 35])
  fun fixed_horizontal_mask_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Fixed(0.5f), horizontal = true, tint = false)

  @Test
  @Config(sdk = [31, 32, 35])
  fun fixed_horizontal_tint_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Fixed(0.5f), horizontal = true, tint = true)

  @Test
  @Config(sdk = [31, 32, 35])
  fun performance_vertical_mask_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Performance, horizontal = false, tint = false)

  @Test
  @Config(sdk = [31, 32, 35])
  fun performance_vertical_tint_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Performance, horizontal = false, tint = true)

  @Test
  @Config(sdk = [31, 32, 35])
  fun performance_horizontal_mask_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Performance, horizontal = true, tint = false)

  @Test
  @Config(sdk = [31, 32, 35])
  fun performance_horizontal_tint_preservesGeometry() = builtInGeometryMatrix(HazePerformanceMode.Performance, horizontal = true, tint = true)

  private fun builtInGeometryMatrix(mode: HazePerformanceMode, horizontal: Boolean, tint: Boolean) = runScreenshotTest {
    val geometries = listOf(
      Size(21f, 19f) to Offset.Zero,
      Size(21.2f, 19.2f) to Offset.Zero,
      Size(21f, 21f) to Offset.Zero,
      Size(25f, 23f) to Offset(2f, 4f),
      Size(25.2f, 23.4f) to Offset(2.25f, 4.5f),
    )
    fun buildEffects(mode: HazePerformanceMode, horizontal: Boolean): List<RenderEffect> {
      val nominal = mode.resolveBlurInputScale()
      val colors = if (tint) listOf(Color.White, Color.Black) else listOf(Color.Black, Color.Transparent)
      val brush = if (horizontal) {
        Brush.horizontalGradient(colors, startX = 6f, endX = 12f)
      } else {
        Brush.verticalGradient(colors, startY = 6f, endY = 12f)
      }
      return geometries.map { (layer, offset) ->
        val capture = Size((layer.width * nominal).roundToInt().toFloat(), (layer.height * nominal).roundToInt().toFloat())
        createRenderEffect(
          context = ApplicationProvider.getApplicationContext(),
          density = Density(1f),
          params = RenderEffectParams(
            blurRadius = 0.dp, noiseFactor = 0f, scale = nominal,
            contentSize = Size(21f, 19f), contentOffset = offset,
            captureScale = ScaleFactor(capture.width / layer.width, capture.height / layer.height),
            captureSize = capture, colorEffects = if (tint) listOf(HazeColorEffect.tint(brush)) else emptyList(),
            mask = if (tint) null else brush, blurTileMode = TileMode.Clamp,
          ),
        )
      }
    }
    val effects = buildEffects(mode, horizontal)
    var current by mutableStateOf(effects.first())
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(if (tint) Modifier else Modifier.background(Color.Black)) {
          key(current) {
            Box(
              Modifier.size(27.dp, 25.dp).testTag("matrix").graphicsLayer {
                renderEffect = current.asComposeRenderEffect()
              }.background(Color.White),
            )
          }
        }
      }
    }
    val nominal = mode.resolveBlurInputScale()
    geometries.forEachIndexed { index, (layer, offset) ->
      composeTestRule.runOnIdle {
        current = effects[index]
        composeTestRule.activity.window.decorView.invalidate()
      }
      waitForIdle()
      val pixels = captureRootPixels()
      val bounds = onNodeWithTag("matrix").fetchSemanticsNode().boundsInRoot
      val scale = if (horizontal) (layer.width * nominal).roundToInt() / layer.width else (layer.height * nominal).roundToInt() / layer.height
      val shift = if (horizontal) offset.x else offset.y
      val center = ((9f + shift) * scale).toInt()
      for (coordinate in listOf(center - 1, center, center + 1)) {
        val x = if (horizontal) coordinate else 4
        val y = if (horizontal) 4 else coordinate
        val expected = ((12f - ((coordinate + 0.5f) / scale - shift)) / 6f).coerceIn(0f, 1f)
        val actual = pixels[bounds.left.roundToInt() + x, bounds.top.roundToInt() + y].red
        assertThat(actual, "$mode $index horizontal=$horizontal tint=$tint coordinate=$coordinate").isCloseTo(expected, 0.025f)
      }
    }
  }

  @Test
  @Config(sdk = [35])
  fun productionCustomMask_fractionalCapture_preservesTranslatedCoordinates() = runScreenshotTest {
    val source = LinearGradient(0f, 4f, 0f, 20f, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
    source.setLocalMatrix(Matrix().apply { setTranslate(0f, 2f) })
    fun matrix() = FloatArray(9).also { values -> Matrix().also { source.getLocalMatrix(it) }.getValues(values) }.toList()
    val originalMatrix = matrix()
    val brush = object : ShaderBrush() {
      override fun createShader(size: Size): Shader = source
    }
    fun makeEffect(layerSize: Size, offset: Offset): RenderEffect {
      val captureSize = Size(layerSize.width.roundToInt().toFloat(), layerSize.height.roundToInt().toFloat())
      return createRenderEffect(
        context = ApplicationProvider.getApplicationContext(),
        density = Density(1f),
        params = RenderEffectParams(
          blurRadius = 0.dp,
          noiseFactor = 0f,
          scale = 1f,
          contentSize = Size(21f, 19f),
          contentOffset = offset,
          captureScale = ScaleFactor(captureSize.width / layerSize.width, captureSize.height / layerSize.height),
          captureSize = captureSize,
          mask = brush,
          blurTileMode = TileMode.Clamp,
        ),
      )
    }
    val first = makeEffect(Size(25.2f, 23.4f), Offset(2.25f, 4.5f))
    var productionEffects by mutableStateOf(listOf(first))
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Row(Modifier.background(Color.Black)) {
          productionEffects.forEachIndexed { index, effect ->
            Box(
              Modifier.size(27.dp, 25.dp).testTag("production$index").graphicsLayer {
                renderEffect = effect.asComposeRenderEffect()
              }.background(Color.White),
            )
          }
        }
      }
    }
    waitForIdle()
    val before = captureRootPixels()
    fun bounds(index: Int) = onNodeWithTag("production$index").fetchSemanticsNode().boundsInRoot
    val beforeBounds = bounds(0)
    val beforeRows = (12..21).map { y -> before[beforeBounds.left.roundToInt() + 10, beforeBounds.top.roundToInt() + y] }
    val second = makeEffect(Size(26.4f, 24.6f), Offset(1.5f, 3.25f))
    productionEffects = listOf(first, second, first)
    waitForIdle()
    val pixels = captureRootPixels()
    val a = bounds(0)
    val b = bounds(1)
    val again = bounds(2)
    val scaleY = 23f / 23.4f
    for (y in 12..21) {
      val expected = ((22f - ((y + 0.5f) / scaleY - 4.5f)) / 16f).coerceIn(0f, 1f)
      val actual = pixels[a.left.roundToInt() + 10, a.top.roundToInt() + y].red
      assertThat(actual, "production mask y=$y").isCloseTo(expected, 0.025f)
      assertThat(pixels[again.left.roundToInt() + 10, again.top.roundToInt() + y], "retained effect y=$y")
        .isEqualTo(beforeRows[y - 12])
    }
    assertThat(kotlin.math.abs(pixels[a.left.roundToInt() + 10, a.top.roundToInt() + 17].red - pixels[b.left.roundToInt() + 10, b.top.roundToInt() + 17].red))
      .isGreaterThan(0.025f)
    assertThat(matrix(), "caller shader matrix").isEqualTo(originalMatrix)
  }

  @Test
  @Config(sdk = [35])
  fun horizontalSharedCustomMask_preservesGeometryAndEarlierEffect() = runScreenshotTest {
    val source = LinearGradient(6f, 0f, 12f, 0f, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
    source.setLocalMatrix(Matrix().apply { setTranslate(2f, 0f) })
    fun matrix() = FloatArray(9).also { values -> Matrix().also { source.getLocalMatrix(it) }.getValues(values) }.toList()
    val originalMatrix = matrix()
    val brush = object : ShaderBrush() {
      override fun createShader(size: Size): Shader = source
    }
    val geometries = listOf(
      Size(21f, 19f) to Offset.Zero,
      Size(21f, 21f) to Offset.Zero,
      Size(25f, 23f) to Offset(2f, 4f),
      Size(25.2f, 23.4f) to Offset(2.25f, 4.5f),
      Size(21.2f, 19.2f) to Offset.Zero,
    )
    fun effect(layer: Size, offset: Offset, nominal: Float): RenderEffect {
      val capture = Size((layer.width * nominal).roundToInt().toFloat(), (layer.height * nominal).roundToInt().toFloat())
      return createRenderEffect(
        ApplicationProvider.getApplicationContext(),
        Density(1f),
        RenderEffectParams(
          blurRadius = 0.dp, noiseFactor = 0f, scale = nominal,
          contentSize = Size(21f, 19f), contentOffset = offset,
          captureScale = ScaleFactor(capture.width / layer.width, capture.height / layer.height), captureSize = capture,
          mask = brush, blurTileMode = TileMode.Clamp,
        ),
      )
    }
    val first = effect(geometries.first().first, geometries.first().second, 1f)
    var current by mutableStateOf(first)
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(Modifier.background(Color.Black)) {
          key(current) {
            Box(
              Modifier.size(27.dp, 25.dp).testTag("customHorizontal").graphicsLayer {
                renderEffect = current.asComposeRenderEffect()
              }.background(Color.White),
            )
          }
        }
      }
    }
    fun read(): List<Float> {
      waitForIdle()
      val pixels = captureRootPixels()
      val bounds = onNodeWithTag("customHorizontal").fetchSemanticsNode().boundsInRoot
      return (0..24).map { x -> pixels[bounds.left.roundToInt() + x, bounds.top.roundToInt() + 4].red }
    }
    val before = read()
    val scales = if (android.os.Build.VERSION.SDK_INT >= 33) listOf(1f, 0.5f) else listOf(1f)
    for (nominal in scales) for ((layer, offset) in geometries) {
      val next = effect(layer, offset, nominal)
      composeTestRule.runOnIdle { current = next }
      val row = read()
      val scale = (layer.width * nominal).roundToInt() / layer.width
      val center = ((11f + offset.x) * scale).toInt()
      for (x in listOf(center - 1, center, center + 1)) {
        val expected = ((14f - ((x + 0.5f) / scale - offset.x)) / 6f).coerceIn(0f, 1f)
        assertThat(row[x], "custom horizontal $layer $offset x=$x").isCloseTo(expected, 0.025f)
      }
      assertThat(matrix(), "shared caller matrix").isEqualTo(originalMatrix)
    }
    composeTestRule.runOnIdle { current = first }
    assertThat(read(), "earlier effect after other geometries").isEqualTo(before)
  }

  @Test
  @Config(sdk = [31, 32, 35])
  fun customBrush_performancePreservesPlatformScaleConstraint() {
    val shader = LinearGradient(0f, 0f, 20f, 0f, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
    val brush = object : ShaderBrush() {
      override fun createShader(size: Size): Shader = shader
    }
    val effect = BlurVisualEffect()
    effect.update(
      HostLifecycleScope(ApplicationProvider.getApplicationContext()),
      BlurConfiguration(HazeBlurStyle { mask(brush) }, HazePerformanceMode.Performance),
      HazeSampling.FullResolution,
    )
    assertThat(effect.resolveInputScaleFactor()).isEqualTo(if (android.os.Build.VERSION.SDK_INT <= 32) 1f else 0.5f)
  }

  @Test
  @Config(sdk = [33])
  fun sdk33RuntimeShader_readsScaleAndOriginUniforms() = runScreenshotTest {
    val shader = RuntimeShader(
      """
      uniform float2 scale;
      uniform float2 origin;
      half4 main(float2 p) {
        bool matches = scale.x == 2.0 && scale.y == 0.5 && origin.x == 4.0 && origin.y == 2.0;
        return matches ? half4(0.0, 1.0, 0.0, 1.0) : half4(1.0, 0.0, 0.0, 1.0);
      }
      """.trimIndent(),
    ).apply {
      setFloatUniform("scale", floatArrayOf(2f, 0.5f))
      setFloatUniform("origin", floatArrayOf(4f, 2f))
    }
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
          Box(
            Modifier.size(24.dp).testTag("uniforms").drawBehind {
              drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, Paint().apply { this.shader = shader }) }
            },
          )
        }
      }
    }
    composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
    waitForIdle()
    val pixels = captureRootPixels()
    val bounds = onNodeWithTag("uniforms").fetchSemanticsNode().boundsInRoot
    val pixel = pixels[bounds.center.x.roundToInt(), bounds.center.y.roundToInt()]
    assertThat(pixel).isEqualTo(Color.Green)
  }

  @Test
  @Config(sdk = [32])
  fun customMaskCacheAndSlot_trackFractionalCaptureInputs() {
    val shader = LinearGradient(0f, 0f, 0f, 20f, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
    val brush = object : ShaderBrush() {
      override fun createShader(size: Size): Shader = shader
    }
    fun params(scale: ScaleFactor, size: Size) = RenderEffectParams(
      blurRadius = 0.dp,
      noiseFactor = 0f,
      scale = 1f,
      contentSize = Size(21f, 19f),
      contentOffset = Offset(2.25f, 4.5f),
      captureScale = scale,
      captureSize = size,
      mask = brush,
      blurTileMode = TileMode.Clamp,
    )
    val original = params(ScaleFactor(25f / 25.2f, 23f / 23.4f), Size(25f, 23f))
    val changedScale = params(ScaleFactor(25f / 25.3f, 23f / 23.4f), Size(25f, 23f))
    val changedSize = params(original.captureScale, Size(26f, 23f))
    assertThat(original.renderEffectCacheKey(Density(1f))).isNotEqualTo(changedScale.renderEffectCacheKey(Density(1f)))
    assertThat(original.renderEffectCacheKey(Density(1f))).isEqualTo(changedSize.renderEffectCacheKey(Density(1f)))
    val graphParams = RenderEffectParams(
      blurRadius = 0.dp,
      noiseFactor = 0f,
      scale = 1f,
      contentSize = original.contentSize,
      contentOffset = original.contentOffset,
      captureScale = original.captureScale,
      captureSize = original.captureSize,
      blurTileMode = TileMode.Clamp,
    )
    val effect = createRenderEffect(ApplicationProvider.getApplicationContext(), Density(1f), graphParams)
    val slot = BlurRenderEffectSlot()
    slot.store(0, 1f, original, effect)
    fun lookup(p: RenderEffectParams) = slot.lookup(
      generation = 0, densityScale = 1f, blurRadius = p.blurRadius, noiseFactor = p.noiseFactor,
      scale = p.scale, captureScale = p.captureScale, captureSize = p.captureSize,
      colorEffects = p.colorEffects, colorEffectsAlphaModulate = p.colorEffectsAlphaModulate,
      backgroundColor = p.backgroundColor, contentSize = p.contentSize, contentOffset = p.contentOffset,
      mask = p.mask, progressiveMask = p.progressiveMask, retainInputWhenMasked = p.retainInputWhenMasked,
      progressive = p.progressive, blurTileMode = p.blurTileMode,
    )
    assertThat(lookup(original)).isSameInstanceAs(effect)
    assertThat(lookup(changedScale)).isNull()
    assertThat(lookup(changedSize)).isNull()
  }

  @Test
  @Config(sdk = [31, 32])
  fun productionLogicalCustomMask_verticalRetainsCoordinatesAcrossGeometry() = logicalCustomMask(horizontal = false)

  @Test
  @Config(sdk = [31, 32])
  fun productionLogicalCustomMask_horizontalRetainsCoordinatesAcrossGeometry() = logicalCustomMask(horizontal = true)

  private fun logicalCustomMask(horizontal: Boolean) = runScreenshotTest {
    val source = if (horizontal) {
      LinearGradient(6f, 0f, 12f, 0f, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
    } else {
      LinearGradient(0f, 4f, 0f, 20f, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }
    source.setLocalMatrix(Matrix().apply { setTranslate(if (horizontal) 2f else 0f, if (horizontal) 0f else 2f) })
    fun matrix() = FloatArray(9).also { Matrix().also(source::getLocalMatrix).getValues(it) }.toList()
    val originalMatrix = matrix()
    val brush = object : ShaderBrush() {
      override fun createShader(size: Size): Shader = source
    }
    var dimensions by mutableStateOf(Size(21f, 19f))
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(Modifier.background(Color.Black)) {
          Box(
            Modifier.size(dimensions.width.dp, dimensions.height.dp).testTag("logicalCustom")
              .hazeBlur(
                input = HazeInput.Content,
                performanceMode = HazePerformanceMode.Quality,
                style = HazeBlurStyle {
                  blurRadius(2.dp)
                  noiseFactor(0f)
                  backgroundColor(Color.Transparent)
                  colorEffects(emptyList())
                  mask(brush)
                },
              ).drawBehind { drawRect(Color.White) },
          )
        }
      }
    }
    fun read(): List<Color> {
      waitForIdle()
      val pixels = captureRootPixels()
      val bounds = onNodeWithTag("logicalCustom").fetchSemanticsNode().boundsInRoot
      return (8..13).map { coordinate ->
        val x = if (horizontal) coordinate else 10
        val y = if (horizontal) 10 else coordinate
        pixels[bounds.left.roundToInt() + x, bounds.top.roundToInt() + y]
      }
    }
    val first = read()
    for (geometry in listOf(Size(21.2f, 19.2f), Size(25.2f, 23.4f), Size(21f, 19f))) {
      composeTestRule.runOnIdle { dimensions = geometry }
      val actual = read()
      actual.forEachIndexed { index, color ->
        val coordinate = index + 8.5f
        val expected = if (horizontal) (14f - coordinate) / 6f else (22f - coordinate) / 16f
        assertThat(color.red, "logical mask horizontal=$horizontal geometry=$geometry coordinate=$coordinate").isCloseTo(expected, 0.025f)
      }
      assertThat(matrix(), "caller shader matrix").isEqualTo(originalMatrix)
    }
    assertThat(read(), "first geometry after alternatives").isEqualTo(first)
  }

  @Test
  @Config(sdk = [31, 32, 35])
  fun qualityProgressive_blursStripedInputMoreAtHighIntensity() = progressiveIntensity(HazePerformanceMode.Quality, horizontal = false)

  @Test
  @Config(sdk = [31, 32, 35])
  fun performanceProgressive_blursStripedInputMoreAtHighIntensity() = progressiveIntensity(HazePerformanceMode.Performance, horizontal = false)

  @Test
  @Config(sdk = [31, 32, 35])
  fun qualityHorizontalProgressive_blursMoreAtHighIntensity() = progressiveIntensity(HazePerformanceMode.Quality, horizontal = true)

  @Test
  @Config(sdk = [31, 32, 35])
  fun performanceHorizontalProgressive_blursMoreAtHighIntensity() = progressiveIntensity(HazePerformanceMode.Performance, horizontal = true)

  private fun progressiveIntensity(mode: HazePerformanceMode, horizontal: Boolean) = runScreenshotTest {
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(
          Modifier.size(80.dp).testTag("progressive")
            .hazeBlur(
              input = HazeInput.Content,
              performanceMode = mode,
              style = HazeBlurStyle {
                blurRadius(8.dp)
                noiseFactor(0f)
                backgroundColor(Color.Transparent)
                colorEffects(emptyList())
                progressive(
                  if (horizontal) {
                    HazeProgressive.horizontalGradient(startX = 0f, endX = 80f, startIntensity = 1f, endIntensity = 0f)
                  } else {
                    HazeProgressive.verticalGradient(startY = 0f, endY = 80f, startIntensity = 1f, endIntensity = 0f)
                  },
                )
              },
            ).drawBehind {
              drawRect(Color.White)
              for (x in 0 until 80 step 16) {
                drawRect(
                  Color.Black,
                  topLeft = if (horizontal) Offset(0f, x.toFloat()) else Offset(x.toFloat(), 0f),
                  size = if (horizontal) Size(80f, 8f) else Size(8f, 80f),
                )
              }
            },
        )
      }
    }
    waitForIdle()
    val pixels = captureRootPixels()
    val bounds = onNodeWithTag("progressive").fetchSemanticsNode().boundsInRoot
    val x = bounds.left.roundToInt() + 17
    val y = bounds.top.roundToInt()
    val high = if (horizontal) pixels[bounds.left.roundToInt() + 8, y + 17] else pixels[x, y + 8]
    val low = if (horizontal) pixels[bounds.left.roundToInt() + 70, y + 17] else pixels[x, y + 70]
    assertThat(high.red - low.red, "$mode horizontal=$horizontal progressive intensity").isGreaterThan(0.05f)
  }

  private fun capture(brush: ShaderBrush, width: Float, height: Float, offsetX: Float, offsetY: Float): Capture {
    val pixelWidth = width.roundToInt()
    val pixelHeight = height.roundToInt()
    val bitmap = Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
      scale(pixelWidth / width, pixelHeight / height)
      translate(offsetX, offsetY)
      drawPaint(Paint().apply { shader = brush.createShader(Size(width, height)) })
    }
    return Capture(checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)), pixelWidth, pixelHeight)
  }

  private data class Capture(val bitmap: Bitmap, val width: Int, val height: Int)
}
