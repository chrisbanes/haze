// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ColorSpace
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ColorFilter as ComposeColorFilter
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode as ComposeTileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRendererDrawHooks
import dev.chrisbanes.haze.HazeEffectRendererLifecycle
import dev.chrisbanes.haze.HazeEffectRendererRetainedOutput
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.drawScaledContent
import dev.chrisbanes.haze.hazeEffect
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
@SdkSuppress(minSdkVersion = 31, maxSdkVersion = 32)
class BlurCustomShaderHardwareInstrumentationTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun logicalOutputStage_rendersSharedHardwareMaskAndRetainsFirstOutput() {
    val pixels = IntArray(32 * 32) { i ->
      val x = i % 32
      val y = i / 32
      android.graphics.Color.argb((24 + x * 5 + y * 2).coerceAtMost(255), x * 7, y * 7, (x * 3 + y * 4).coerceAtMost(255))
    }
    val bitmap = checkNotNull(Bitmap.createBitmap(pixels, 32, 32, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.HARDWARE, false))
    assertThat(bitmap.config).isEqualTo(Bitmap.Config.HARDWARE)
    fun bitmapShader() = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
      setLocalMatrix(Matrix().apply { setTranslate(2.25f, 3.5f) })
    }
    fun gradient() = LinearGradient(0f, 0f, 0f, 24f, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP).apply {
      setLocalMatrix(Matrix().apply { setTranslate(0f, 1.5f) })
    }
    val firstChild = bitmapShader()
    val secondChild = gradient()
    val mask = ComposeShader(firstChild, secondChild, PorterDuff.Mode.DST_IN)
    val referenceMask = ComposeShader(bitmapShader(), gradient(), PorterDuff.Mode.DST_IN)
    fun matrix(shader: Shader) = FloatArray(9).also { Matrix().also(shader::getLocalMatrix).getValues(it) }.toList()
    val matrices = listOf(matrix(firstChild), matrix(secondChild), matrix(mask))
    lateinit var content: GraphicsLayer
    lateinit var makeAlternate: () -> GraphicsLayer
    var activeContent by mutableStateOf<GraphicsLayer?>(null)
    var activeMask by mutableStateOf<Shader>(mask)
    var alternateGeometry by mutableStateOf(false)
    Log.i("Haze1730", "logicalStage sdk=${Build.VERSION.SDK_INT} fingerprint=${Build.FINGERPRINT} config=${bitmap.config} space=${bitmap.colorSpace} matrices=$matrices")
    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        val graphics = LocalGraphicsContext.current
        fun makeInput(width: Int, height: Int, layerWidth: Float, layerHeight: Float, x: Float, y: Float): GraphicsLayer =
          graphics.createGraphicsLayer().apply {
            record(density = Density(1f), layoutDirection = LayoutDirection.Ltr, size = IntSize(width, height)) {
              drawRect(ComposeColor.White)
              scale(width / layerWidth, height / layerHeight, pivot = Offset.Zero) {
                translate(x, y) { drawRect(ComposeColor.Blue, topLeft = Offset(5f, 0f), size = Size(6f, 23f)) }
              }
            }
            renderEffect = RenderEffect.createBlurEffect(2f, 2f, Shader.TileMode.CLAMP).asComposeRenderEffect()
          }
        content = remember { makeInput(25, 23, 25.2f, 23.4f, 2.25f, 4.5f) }
        val referenceContent = remember { makeInput(25, 23, 25.2f, 23.4f, 2.25f, 4.5f) }
        makeAlternate = { makeInput(26, 25, 26.4f, 24.6f, 1.5f, 3.25f) }
        Row {
          listOf("actualStage", "referenceStage", "originFault", "blackStage", "whiteStage").forEach { tag ->
            Box(
              Modifier.size(25.dp, 23.dp).testTag(tag).drawBehind {
                drawIntoCanvas { canvas ->
                  val native = canvas.nativeCanvas
                  Log.i("Haze1730", "logicalStage hardwareCanvas=${native.isHardwareAccelerated} renderer=${native.javaClass.name}")
                  val clip = native.save()
                  native.clipRect(0f, 0f, size.width, size.height)
                  if (tag == "blackStage") native.drawColor(android.graphics.Color.BLACK)
                  if (tag == "whiteStage") native.drawColor(android.graphics.Color.WHITE)
                  val layer = native.saveLayer(0f, 0f, size.width, size.height, null)
                  val alternate = alternateGeometry && tag != "referenceStage"
                  drawScaledContent(
                    offset = if (alternate) Offset(-1.5f, -3.25f) else Offset(-2.25f, -4.5f),
                    captureScale = if (alternate) ScaleFactor(26f / 26.4f, 25f / 24.6f) else ScaleFactor(25f / 25.2f, 23f / 23.4f),
                    clip = false,
                  ) {
                    drawLayer(if (tag == "referenceStage") referenceContent else activeContent ?: content)
                  }
                  if (tag == "originFault") native.translate(0f, 4.5f)
                  native.drawPaint(
                    Paint().apply {
                      shader = if (tag == "referenceStage") referenceMask else activeMask
                      xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    },
                  )
                  native.restoreToCount(layer)
                  native.restoreToCount(clip)
                }
              },
            )
          }
        }
      }
    }
    rule.waitForIdle()
    fun read(tag: String) = rule.onNodeWithTag(tag).captureToImage().toPixelMap().let { image ->
      (5..17 step 3).flatMap { y -> (5..17 step 3).map { x -> image[x, y] } }
    }
    val before = read("actualStage")
    val reference = read("referenceStage")
    val wrong = read("originFault")
    val black = read("blackStage")
    val white = read("whiteStage")
    val alternate = ComposeShader(bitmapShader().apply { setLocalMatrix(Matrix().apply { setTranslate(6f, 7f) }) }, gradient(), PorterDuff.Mode.DST_IN)
    lateinit var alternateContent: GraphicsLayer
    rule.runOnIdle {
      alternateContent = makeAlternate()
      activeMask = alternate
      activeContent = alternateContent
      alternateGeometry = true
    }
    rule.waitForIdle()
    val alternateOutput = read("actualStage")
    Log.i("Haze1730", "logicalStage afterAlternate firstReleased=${content.isReleased} alternateReleased=${alternateContent.isReleased}")
    rule.runOnIdle {
      activeMask = mask
      activeContent = content
      alternateGeometry = false
    }
    rule.waitForIdle()
    val after = read("actualStage")
    Log.i("Haze1730", "logicalStage restored firstReleased=${content.isReleased} alternateReleased=${alternateContent.isReleased} first=${before.first()} after=${after.first()}")
    fun error(a: List<androidx.compose.ui.graphics.Color>, b: List<androidx.compose.ui.graphics.Color>) =
      a.zip(b).maxOf { (l, r) -> maxOf(abs(l.red - r.red), abs(l.green - r.green), abs(l.blue - r.blue)) }
    Log.i("Haze1730", "logicalStage before=$before reference=$reference fault=$wrong alternate=$alternateOutput restored=$after")
    val alphaErrors = (5..17 step 3).flatMap { y ->
      (5..17 step 3).map { x ->
        val i = ((y - 5) / 3) * 5 + (x - 5) / 3
        val inferredAlpha = listOf(
          1f - (white[i].red - black[i].red),
          1f - (white[i].green - black[i].green),
          1f - (white[i].blue - black[i].blue),
        )
        val bitmapX = (x + 0.5f - 2.25f).toInt().coerceIn(0, 31)
        val bitmapY = (y + 0.5f - 3.5f).toInt().coerceIn(0, 31)
        val expectedAlpha = (24 + bitmapX * 5 + bitmapY * 2).coerceAtMost(255) / 255f *
          (1f - (y + 0.5f - 1.5f) / 24f).coerceIn(0f, 1f)
        inferredAlpha.maxOf { abs(it - expectedAlpha) }
      }
    }
    Log.i("Haze1730", "logicalStage black=$black white=$white alphaErrors=$alphaErrors")
    assertThat(alphaErrors.max(), "independent analytical alpha").isLessThan(0.025f)
    assertThat(error(before, reference), "independent reference").isLessThan(0.025f)
    assertThat(error(before, wrong), "origin fault").isGreaterThan(0.025f)
    assertThat(error(before, alternateOutput), "alternate geometry").isGreaterThan(0.025f)
    assertThat(after).isEqualTo(before)
    assertThat(listOf(matrix(firstChild), matrix(secondChild), matrix(mask))).isEqualTo(matrices)
  }

  @Test
  fun logicalOutputStage_broadCrossingAndTransformControls() {
    val pixels = IntArray(32 * 32) { i -> android.graphics.Color.rgb((i % 32) * 7, (i / 32) * 7, ((i % 32) * 3 + (i / 32) * 4)) }
    val bitmap = checkNotNull(Bitmap.createBitmap(pixels, 32, 32, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.HARDWARE, false))
    assertThat(bitmap.config).isEqualTo(Bitmap.Config.HARDWARE)
    fun mask(): Shader {
      val child = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setTranslate(2.25f, 3.5f) })
      }
      val ramp = LinearGradient(0f, 4f, 0f, 20f, android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setTranslate(0f, 2f) })
      }
      return ComposeShader(child, ramp, PorterDuff.Mode.DST_IN)
    }
    val actual = mask()
    val reference = LinearGradient(0f, 6f, 0f, 22f, android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK, Shader.TileMode.CLAMP)
    val scaleY = 23f / 23.4f
    val tags = listOf("normal", "referenceRamp", "omittedScale", "wrongOrigin", "plusHalf", "minusHalf", "restored", "phaseLow", "phaseHigh", "palette", "dynamic")
    var activeFault by mutableStateOf("normal")
    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        val graphics = LocalGraphicsContext.current
        val captured = remember {
          graphics.createGraphicsLayer().apply {
            record(density = Density(1f), layoutDirection = LayoutDirection.Ltr, size = IntSize(25, 23)) {
              drawRect(ComposeColor.White)
            }
            renderEffect = RenderEffect.createBlurEffect(2f, 2f, Shader.TileMode.CLAMP).asComposeRenderEffect()
          }
        }
        Row {
          tags.forEach { tag ->
            Box(
              Modifier.size(25.dp, 23.dp).testTag(tag).drawBehind {
                drawIntoCanvas { canvas ->
                  val native = canvas.nativeCanvas
                  val save = native.save()
                  native.clipRect(0f, 0f, size.width, size.height)
                  native.drawColor(android.graphics.Color.BLACK)
                  val effectiveTag = if (tag == "dynamic") activeFault else tag
                  if (effectiveTag.startsWith("phase")) {
                    val phase = if (effectiveTag == "phaseLow") 0.2f else 0.8f
                    val start = 0.5f - (40f + phase) * 16f / 255f
                    native.drawPaint(
                      Paint().apply {
                        shader = LinearGradient(0f, start, 0f, start + 16f, android.graphics.Color.BLACK, android.graphics.Color.WHITE, Shader.TileMode.CLAMP)
                      },
                    )
                  } else {
                    val layer = native.saveLayer(0f, 0f, size.width, size.height, null)
                    drawScaledContent(offset = Offset(-2.25f, -1.5f), captureScale = ScaleFactor(25f / 25.2f, scaleY), clip = false) {
                      drawLayer(captured)
                    }
                    when (effectiveTag) {
                      // Drawing an unscaled capture-space mask through inverse presentation omits
                      // the required capture-scale compensation while retaining the origin.
                      "omittedScale" -> native.scale(1f, 1f / scaleY)
                      "wrongOrigin" -> native.translate(0f, 2f)
                      "plusHalf" -> native.translate(0f, 0.5f / scaleY)
                      "minusHalf" -> native.translate(0f, -0.5f / scaleY)
                    }
                    if (effectiveTag == "palette") {
                      listOf(0, 1, 32, 67, 116, 132, 181, 254, 255).forEachIndexed { y, code ->
                        native.drawRect(
                          0f,
                          y.toFloat(),
                          size.width,
                          y + 1f,
                          Paint().apply {
                            color = android.graphics.Color.argb(code, 0, 0, 0)
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                          },
                        )
                      }
                    } else {
                      native.drawPaint(
                        Paint().apply {
                          shader = if (effectiveTag == "referenceRamp") reference else actual
                          xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                        },
                      )
                    }
                    native.restoreToCount(layer)
                  }
                  native.restoreToCount(save)
                }
              },
            )
          }
        }
      }
    }
    rule.waitForIdle()
    fun codes(tag: String, rows: IntRange) = rule.onNodeWithTag(tag).captureToImage().toPixelMap().let { image ->
      rows.map { y ->
        val pixel = image[10, y]
        val red = (pixel.red * 255f).roundToInt()
        assertThat((pixel.green * 255f).roundToInt(), "$tag green y=$y").isEqualTo(red)
        assertThat((pixel.blue * 255f).roundToInt(), "$tag blue y=$y").isEqualTo(red)
        red
      }
    }
    val low = codes("phaseLow", 0..8)
    val high = codes("phaseHigh", 0..8)
    val palette = codes("palette", 0..8)
    val rows = 7..20
    val observed = tags.take(7).associateWith { codes(it, rows) }
    Log.i("Haze1730", "crossing sdk=${Build.VERSION.SDK_INT} fingerprint=${Build.FINGERPRINT} config=${bitmap.config} space=${bitmap.colorSpace} low=$low high=$high rows=$observed")
    Log.i("Haze1730", "crossing palette=$palette")
    assertThat(palette, "integer alpha palette survives mask and readback").isEqualTo(listOf(0, 1, 32, 67, 116, 132, 181, 254, 255))
    val quantization = calibratedRampQuantization(low, high)
    val intervals = observed.mapValues { calibratedRampInterval(it.value, rows, quantization) }
    Log.i("Haze1730", "crossing quantization=$quantization intervals=$intervals")
    val normal = intervals.getValue("normal")
    assertThat(normal.second - normal.first, "nonempty interval").isGreaterThan(0.0)
    assertThat(normal.first, "whole crossing lower").isGreaterThan(13.975)
    assertThat(normal.second, "whole crossing upper").isLessThan(14.025)
    assertThat(observed.getValue("normal")).isEqualTo(observed.getValue("referenceRamp"))
    assertThat(observed.getValue("restored")).isEqualTo(observed.getValue("normal"))
    assertThat(codes("dynamic", rows), "dynamic initial").isEqualTo(observed.getValue("normal"))
    listOf("omittedScale", "wrongOrigin", "plusHalf", "minusHalf").forEach { fault ->
      rule.runOnIdle { activeFault = fault }
      rule.waitForIdle()
      val injected = codes("dynamic", rows)
      assertThat(injected, "$fault injected on live output").isEqualTo(observed.getValue(fault))
      rule.runOnIdle { activeFault = "normal" }
      rule.waitForIdle()
      val restored = codes("dynamic", rows)
      Log.i("Haze1730", "crossing live fault=$fault injected=$injected restored=$restored")
      assertThat(restored, "$fault live restored").isEqualTo(observed.getValue("normal"))
      val result = intervals.getValue(fault)
      assertThat(result.first < 13.975 || result.second > 14.025, "$fault must fail crossing containment").isEqualTo(true)
    }
  }

  @Test
  fun logicalOutputStage_mixedOrderMatchesCompleteRenderEffect() {
    fun tintShader(first: Boolean): Shader = if (first) {
      LinearGradient(0f, 0f, 25f, 0f, android.graphics.Color.argb(160, 255, 0, 0), android.graphics.Color.argb(160, 0, 255, 0), Shader.TileMode.CLAMP)
    } else {
      LinearGradient(0f, 0f, 0f, 23f, android.graphics.Color.argb(128, 0, 0, 255), android.graphics.Color.argb(128, 255, 255, 0), Shader.TileMode.CLAMP)
    }
    fun progressiveShader(): Shader = LinearGradient(0f, 0f, 0f, 23f, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
    fun userShader(): Shader = LinearGradient(0f, 0f, 25f, 0f, android.graphics.Color.argb(220, 0, 0, 0), android.graphics.Color.argb(100, 0, 0, 0), Shader.TileMode.CLAMP)
    val prog = progressiveShader()
    val user = userShader()
    val first = tintShader(true)
    val second = tintShader(false)
    val filterColor = android.graphics.Color.argb(128, 0, 255, 255)
    val ax = 25f / 25.2f
    val ay = 23f / 23.4f
    val ox = 2.25f
    val oy = 4.5f
    fun point(x: Float, y: Float) = Offset(ax * (x + ox), ay * (y + oy))
    fun referenceTint(firstTint: Boolean): Brush = if (firstTint) {
      Brush.linearGradient(listOf(ComposeColor.Red.copy(alpha = 160f / 255f), ComposeColor.Green.copy(alpha = 160f / 255f)), start = point(0f, 0f), end = point(25f, 0f))
    } else {
      Brush.linearGradient(listOf(ComposeColor.Blue.copy(alpha = 128f / 255f), ComposeColor.Yellow.copy(alpha = 128f / 255f)), start = point(0f, 0f), end = point(0f, 23f))
    }
    fun referenceProgressive(): Brush = Brush.linearGradient(listOf(ComposeColor.Black, ComposeColor.Transparent), start = point(0f, 0f), end = point(0f, 23f))
    fun referenceUser(): Brush = Brush.linearGradient(listOf(ComposeColor.Black.copy(alpha = 220f / 255f), ComposeColor.Black.copy(alpha = 100f / 255f)), start = point(0f, 0f), end = point(25f, 0f))
    val reference = createRenderEffect(
      rule.activity,
      Density(1f),
      RenderEffectParams(
        blurRadius = 2.dp,
        noiseFactor = 0.25f,
        scale = 1f,
        contentSize = Size(25f, 23f),
        contentOffset = Offset.Zero,
        colorEffects = listOf(
          HazeColorEffect.tint(referenceTint(true)),
          HazeColorEffect.colorFilter(ComposeColorFilter.tint(ComposeColor(0f, 1f, 1f, 128f / 255f))),
          HazeColorEffect.tint(referenceTint(false)),
        ),
        progressive = HazeProgressive.Brush(referenceProgressive()),
        progressiveMask = referenceProgressive(),
        retainInputWhenMasked = true,
        mask = referenceUser(),
        blurTileMode = ComposeTileMode.Clamp,
      ),
    )
    fun input(effect: RenderEffect?): RenderNode = RenderNode("haze1730-mixed").apply {
      setPosition(0, 0, 25, 23)
      val c = beginRecording()
      c.drawColor(android.graphics.Color.rgb(30, 80, 130))
      c.scale(ax, ay)
      c.translate(ox, oy)
      c.drawRect(6f, 0f, 12f, 23f, Paint().apply { color = android.graphics.Color.YELLOW })
      endRecording()
      setRenderEffect(effect)
    }
    val original = input(null)
    val blurred = input(RenderEffect.createBlurEffect(2f, 2f, Shader.TileMode.CLAMP))
    val graph = input(reference)
    val noise = rule.activity.createNoiseShader(1f)
    val area = android.graphics.RectF(0f, 0f, 25f, 23f)
    fun android.graphics.Canvas.applyMask(shader: Shader) {
      drawPaint(
        Paint().apply {
          this.shader = shader
          xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        },
      )
    }
    fun android.graphics.Canvas.maskedLayer(mode: android.graphics.BlendMode, shader: Shader?, draw: () -> Unit) {
      val level = saveLayer(area, Paint().apply { blendMode = mode })
      draw()
      if (shader != null) applyMask(shader)
      restoreToCount(level)
    }
    fun android.graphics.Canvas.present(node: RenderNode) {
      val saved = save()
      translate(-ox, -oy)
      scale(1f / ax, 1f / ay)
      drawRenderNode(node)
      restoreToCount(saved)
    }
    fun android.graphics.Canvas.drawBase() {
      present(blurred)
      maskedLayer(android.graphics.BlendMode.SOFT_LIGHT, prog) {
        val saved = save()
        translate(-ox, -oy)
        scale(1f / ax, 1f / ay)
        drawPaint(
          Paint().apply {
            shader = noise
            alpha = (0.25f * 255).roundToInt()
          },
        )
        restoreToCount(saved)
      }
    }
    fun android.graphics.Canvas.beforeFilter() {
      drawBase()
      maskedLayer(android.graphics.BlendMode.SRC_OVER, prog) { drawPaint(Paint().apply { shader = first }) }
    }
    fun android.graphics.Canvas.filterStage() {
      val filtered = saveLayer(area, Paint().apply { colorFilter = PorterDuffColorFilter(filterColor, PorterDuff.Mode.SRC_IN) })
      beforeFilter()
      restoreToCount(filtered)
    }
    fun android.graphics.Canvas.stage(reverse: Boolean) {
      val outer = saveLayer(area, Paint().apply { alpha = 204 })
      present(original)
      val styled = saveLayer(area, null)
      beforeFilter()
      if (!reverse) maskedLayer(android.graphics.BlendMode.SRC_OVER, prog) { filterStage() }
      maskedLayer(android.graphics.BlendMode.SRC_OVER, prog) { drawPaint(Paint().apply { shader = second }) }
      if (reverse) maskedLayer(android.graphics.BlendMode.SRC_OVER, prog) { filterStage() }
      applyMask(prog)
      restoreToCount(styled)
      applyMask(user)
      restoreToCount(outer)
    }
    var reverseLive by mutableStateOf(false)
    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Row {
          listOf("staged", "graph", "reversed", "liveOrder").forEach { tag ->
            Box(
              Modifier.size(25.dp, 23.dp).testTag(tag).drawBehind {
                drawIntoCanvas { canvas ->
                  val c = canvas.nativeCanvas
                  val saved = c.save()
                  c.clipRect(0f, 0f, size.width, size.height)
                  if (tag == "graph") {
                    val alphaLayer = c.saveLayer(area, Paint().apply { alpha = 204 })
                    c.present(graph)
                    c.restoreToCount(alphaLayer)
                  } else {
                    c.stage(tag == "reversed" || (tag == "liveOrder" && reverseLive))
                  }
                  c.restoreToCount(saved)
                }
              },
            )
          }
        }
      }
    }
    rule.waitForIdle()
    fun read(tag: String) = rule.onNodeWithTag(tag).captureToImage().toPixelMap().let { image ->
      (4..16 step 3).flatMap { y -> (4..16 step 3).map { x -> image[x, y] } }
    }
    val staged = read("staged")
    val expected = read("graph")
    val fault = read("reversed")
    val liveBefore = read("liveOrder")
    rule.runOnIdle { reverseLive = true }
    rule.waitForIdle()
    val liveFault = read("liveOrder")
    rule.runOnIdle { reverseLive = false }
    rule.waitForIdle()
    val liveAfter = read("liveOrder")
    fun error(a: List<androidx.compose.ui.graphics.Color>, b: List<androidx.compose.ui.graphics.Color>) =
      a.zip(b).maxOf { (l, r) -> maxOf(abs(l.red - r.red), abs(l.green - r.green), abs(l.blue - r.blue)) }
    Log.i("Haze1730", "mixed sdk=${Build.VERSION.SDK_INT} fingerprint=${Build.FINGERPRINT} staged=$staged graph=$expected reversed=$fault errors=${error(staged, expected)},${error(staged, fault)}")
    Log.i("Haze1730", "mixed errors normal=${error(staged, expected)} reversed=${error(staged, fault)}")
    assertThat(liveBefore, "live initial order").isEqualTo(staged)
    assertThat(liveFault, "live reversed order").isEqualTo(fault)
    assertThat(liveAfter, "live restored order").isEqualTo(liveBefore)
    assertThat(error(staged, fault), "reversed order fault").isGreaterThan(0.025f)
    assertThat(error(staged, expected), "mixed output versus graph").isLessThan(0.025f)
  }

  @Test
  fun productionDelegate_hardwareBitmapAsDestinationPreservesLogicalOutput() = productionHardwareMask(bitmapIsSource = false)

  @Test
  fun productionDelegate_hardwareBitmapAsSourcePreservesLogicalOutput() = productionHardwareMask(bitmapIsSource = true)

  private fun productionHardwareMask(bitmapIsSource: Boolean) {
    val pixels = IntArray(32 * 32) { i -> android.graphics.Color.rgb((i % 32) * 7, (i / 32) * 7, ((i % 32) * 3 + (i / 32) * 4)) }
    val bitmap = checkNotNull(Bitmap.createBitmap(pixels, 32, 32, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.HARDWARE, false))
    assertThat(bitmap.config).isEqualTo(Bitmap.Config.HARDWARE)
    val child = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
      setLocalMatrix(Matrix().apply { setTranslate(2.25f, 3.5f) })
    }
    val ramp = LinearGradient(0f, 4f, 0f, 20f, android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK, Shader.TileMode.CLAMP).apply {
      setLocalMatrix(Matrix().apply { setTranslate(0f, 2f) })
    }
    val nested = if (bitmapIsSource) ComposeShader(ramp, child, PorterDuff.Mode.SRC_IN) else ComposeShader(child, ramp, PorterDuff.Mode.DST_IN)
    fun matrix(shader: Shader) = FloatArray(9).also { Matrix().also(shader::getLocalMatrix).getValues(it) }.toList()
    val matrices = listOf(matrix(child), matrix(ramp), matrix(nested))
    val paletteCodes = listOf(0, 1, 32, 67, 116, 132, 181, 254, 255)
    val paletteBitmap = Bitmap.createBitmap(
      paletteCodes.map { android.graphics.Color.argb(it, 0, 0, 0) }.toIntArray(),
      1,
      paletteCodes.size,
      Bitmap.Config.ARGB_8888,
    )
    fun phaseShader(phase: Float): Shader {
      val start = 0.5f - (40f + phase) * 16f / 255f
      return LinearGradient(0f, start, 0f, start + 16f, android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK, Shader.TileMode.CLAMP)
    }
    val shaders = mapOf(
      "production" to nested,
      "phaseLow" to phaseShader(0.2f),
      "phaseHigh" to phaseShader(0.8f),
      "palette" to BitmapShader(paletteBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
    )
    val brushes = shaders.mapValues { (_, shader) ->
      object : ShaderBrush() {
        override fun createShader(size: Size): Shader = shader
      }
    }
    var height by mutableStateOf(19.dp)
    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Row(Modifier.background(ComposeColor.Black)) {
          brushes.forEach { (tag, brush) ->
            Box(
              Modifier.size(21.dp, height).testTag(tag)
                .hazeBlur(
                  input = HazeInput.Content,
                  performanceMode = HazePerformanceMode.Quality,
                  style = HazeBlurStyle {
                    blurRadius(2.dp)
                    noiseFactor(0f)
                    backgroundColor(ComposeColor.Transparent)
                    colorEffects(emptyList())
                    mask(brush)
                  },
                ).drawBehind { drawRect(ComposeColor.White) },
            )
          }
        }
      }
    }
    rule.waitForIdle()
    fun codes(tag: String, rows: IntRange) = rule.onNodeWithTag(tag).captureToImage().toPixelMap().let { image ->
      rows.map { y ->
        val pixel = image[10, y]
        val red = (pixel.red * 255f).roundToInt()
        assertThat((pixel.green * 255f).roundToInt(), "$tag green y=$y").isEqualTo(red)
        assertThat((pixel.blue * 255f).roundToInt(), "$tag blue y=$y").isEqualTo(red)
        red
      }
    }
    fun calibration(): String {
      val low = codes("phaseLow", 0..8)
      val high = codes("phaseHigh", 0..8)
      assertThat(codes("palette", 0..8), "production-path integer alpha palette").isEqualTo(paletteCodes)
      val quantization = calibratedRampQuantization(low, high)
      Log.i("Haze1730", "production calibration sdk=${Build.VERSION.SDK_INT} low=$low high=$high quantization=$quantization")
      return quantization
    }
    val rows = 8..17
    fun checkCrossing(label: String): List<Int> {
      val quantization = calibration()
      val values = codes("production", rows)
      val interval = calibratedRampInterval(values, rows, quantization)
      Log.i("Haze1730", "production crossing sdk=${Build.VERSION.SDK_INT} sourceChild=$bitmapIsSource label=$label rows=$values interval=$interval")
      assertThat(interval.second - interval.first, "$label nonempty crossing").isGreaterThan(0.0)
      assertThat(interval.first, "$label whole crossing lower").isGreaterThan(13.975)
      assertThat(interval.second, "$label whole crossing upper").isLessThan(14.025)
      return values
    }
    val first = checkCrossing("first")
    rule.runOnIdle { height = 19.2f.dp }
    rule.waitForIdle()
    checkCrossing("alternate")
    rule.runOnIdle { height = 19.dp }
    rule.waitForIdle()
    assertThat(checkCrossing("restored")).isEqualTo(first)
    assertThat(listOf(matrix(child), matrix(ramp), matrix(nested))).isEqualTo(matrices)
  }

  @Test
  fun productionDelegate_translatedTintCrossingRejectsShiftAndRestores() {
    fun ramp(crossing: Float): ShaderBrush {
      val shader = LinearGradient(0f, crossing - 8f, 0f, crossing + 8f, android.graphics.Color.BLACK, android.graphics.Color.WHITE, Shader.TileMode.CLAMP)
      return object : ShaderBrush() {
        override fun createShader(size: Size): Shader = shader
      }
    }
    fun translated(offset: Float): Pair<Shader, ShaderBrush> {
      val shader = LinearGradient(0f, 4f, 0f, 20f, android.graphics.Color.BLACK, android.graphics.Color.WHITE, Shader.TileMode.CLAMP)
      shader.setLocalMatrix(Matrix().apply { setTranslate(0f, offset) })
      return shader to object : ShaderBrush() {
        override fun createShader(size: Size): Shader = shader
      }
    }
    val (shader, brush) = translated(2f)
    val (_, faultBrush) = translated(2.1f)
    val originalMatrix = FloatArray(9).also { Matrix().also(shader::getLocalMatrix).getValues(it) }.toList()
    val paletteCodes = listOf(0, 1, 32, 67, 116, 132, 181, 254, 255)
    val bitmap = Bitmap.createBitmap(paletteCodes.map { android.graphics.Color.rgb(it, it, it) }.toIntArray(), 1, paletteCodes.size, Bitmap.Config.ARGB_8888)
    val palette = object : ShaderBrush() {
      override fun createShader(size: Size): Shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    var referenceCrossing by mutableStateOf<Float?>(null)
    var fault by mutableStateOf(false)
    var height by mutableStateOf(19.dp)
    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Row(Modifier.background(ComposeColor.Black)) {
          listOf("production", "palette").forEach { tag ->
            val current = if (tag == "palette") palette else referenceCrossing?.let(::ramp) ?: if (fault) faultBrush else brush
            val base = Modifier.size(21.dp, height).testTag(tag)
            val drawing = if (tag == "production" && referenceCrossing != null) {
              base.drawBehind {
                drawIntoCanvas { canvas ->
                  val native = canvas.nativeCanvas
                  val layer = native.saveLayer(0f, 0f, size.width, size.height, null)
                  drawRect(current)
                  native.restoreToCount(layer)
                }
              }
            } else {
              base.hazeBlur(
                input = HazeInput.Content,
                performanceMode = HazePerformanceMode.Quality,
                style = HazeBlurStyle {
                  blurRadius(2.25f.dp)
                  noiseFactor(0f)
                  backgroundColor(ComposeColor.Transparent)
                  colorEffects(listOf(HazeColorEffect.tint(current)))
                },
              ).drawBehind { drawRect(ComposeColor.Black) }
            }
            Box(drawing)
          }
        }
      }
    }
    rule.waitForIdle()
    fun codes(tag: String, rows: IntRange) = rule.onNodeWithTag(tag).captureToImage().toPixelMap().let { image ->
      rows.map { y ->
        val pixel = image[10, y]
        val red = (pixel.red * 255f).roundToInt()
        assertThat((pixel.green * 255f).roundToInt(), "$tag green y=$y").isEqualTo(red)
        assertThat((pixel.blue * 255f).roundToInt(), "$tag blue y=$y").isEqualTo(red)
        red
      }
    }
    assertThat(codes("palette", 0..8), "integer RGB palette").isEqualTo(paletteCodes)
    val rows = 8..17
    val first = codes("production", rows)
    // Independently authored, identity-matrix references calibrate each row at the same output pixel.
    val samples = (0..80).map { i ->
      val crossing = 13.8f + i * 0.005f
      rule.runOnIdle { referenceCrossing = crossing }
      rule.waitForIdle()
      val sample = codes("production", rows)
      Log.i("Haze1730", "tint reference sdk=${Build.VERSION.SDK_INT} crossing=$crossing rows=$sample")
      crossing.toDouble() to sample
    }
    (0 until rows.count()).forEach { row ->
      val values = samples.map { it.second[row] }
      assertThat(values.zipWithNext().all { (a, b) -> a >= b }, "reference monotonic row=$row").isTrue()
      assertThat(values.first(), "reference spans codes row=$row").isGreaterThan(values.last())
    }
    fun interval(values: List<Int>): Pair<Double, Double> {
      val ranges = values.indices.map { row ->
        val matching = samples.filter { it.second[row] == values[row] }
        assertThat(matching.isNotEmpty(), "calibrated code row=$row").isTrue()
        assertThat(matching.first().first, "lower calibration boundary row=$row").isGreaterThan(13.8)
        assertThat(matching.last().first, "upper calibration boundary row=$row").isLessThan(14.2)
        (matching.first().first - 0.0051) to (matching.last().first + 0.0051)
      }
      return ranges.maxOf { it.first } to ranges.minOf { it.second }
    }
    fun check(values: List<Int>, label: String) {
      val bounds = interval(values)
      Log.i("Haze1730", "tint sdk=${Build.VERSION.SDK_INT} $label rows=$values interval=$bounds")
      assertThat(bounds.second, "$label nonempty").isGreaterThan(bounds.first)
      assertThat(bounds.first, "$label whole lower").isGreaterThan(13.975)
      assertThat(bounds.second, "$label whole upper").isLessThan(14.025)
    }
    rule.runOnIdle { referenceCrossing = null }
    rule.waitForIdle()
    check(first, "first")
    assertThat(codes("production", rows)).isEqualTo(first)
    rule.runOnIdle { fault = true }
    rule.waitForIdle()
    val faultCodes = codes("production", rows)
    val faultInterval = interval(faultCodes)
    Log.i("Haze1730", "tint sdk=${Build.VERSION.SDK_INT} fault rows=$faultCodes interval=$faultInterval")
    assertThat(faultInterval.first < 13.975 || faultInterval.second > 14.025 || faultInterval.second <= faultInterval.first, "shift must fail").isTrue()
    rule.runOnIdle { fault = false }
    rule.waitForIdle()
    assertThat(codes("production", rows)).isEqualTo(first)
    rule.runOnIdle { height = 20.2f.dp }
    rule.waitForIdle()
    check(codes("production", rows), "alternate")
    rule.runOnIdle { height = 19.dp }
    rule.waitForIdle()
    val restored = codes("production", rows)
    assertThat(restored).isEqualTo(first)
    check(restored, "restored")
    assertThat(FloatArray(9).also { Matrix().also(shader::getLocalMatrix).getValues(it) }.toList()).isEqualTo(originalMatrix)
  }

  private fun calibratedRampQuantization(low: List<Int>, high: List<Int>): String {
    val floorLow = (0..8).map { floor(40.2 + it * 255.0 / 16.0).toInt() }
    val floorHigh = (0..8).map { floor(40.8 + it * 255.0 / 16.0).toInt() }
    val nearestLow = (0..8).map { floor(40.2 + it * 255.0 / 16.0 + 0.5).toInt() }
    val nearestHigh = (0..8).map { floor(40.8 + it * 255.0 / 16.0 + 0.5).toInt() }
    val quantization = when {
      low == floorLow && high == floorHigh -> "floor"
      low == nearestLow && high == nearestHigh -> "nearest"
      else -> "unresolved"
    }
    assertThat(quantization, "independent quantization").isNotEqualTo("unresolved")
    return quantization
  }

  private fun calibratedRampInterval(codes: List<Int>, rows: IntRange, quantization: String): Pair<Double, Double> {
    val shift = if (quantization == "nearest") 0.5 else 0.0
    val lower = rows.zip(codes).maxOf { (y, q) -> y + 0.5 + 8.0 - 16.0 * (q + 1.0 - shift) / 255.0 }
    val upper = rows.zip(codes).minOf { (y, q) -> y + 0.5 + 8.0 - 16.0 * (q - shift) / 255.0 }
    return lower to upper
  }

  @Test
  fun productionDelegate_customLayeredProgressiveMatchesBuiltInBrushes() = productionProgressiveParity(layered = true, HazePerformanceMode.Quality)

  @Test
  fun productionDelegate_customMaskedProgressiveMatchesBuiltInBrushes() = productionProgressiveParity(layered = false, HazePerformanceMode.Quality)

  @Test
  fun productionDelegate_performanceCustomLayeredProgressiveMatchesBuiltInBrushes() = productionProgressiveParity(layered = true, HazePerformanceMode.Performance)

  @Test
  fun productionDelegate_performanceCustomMaskedProgressiveMatchesBuiltInBrushes() = productionProgressiveParity(layered = false, HazePerformanceMode.Performance)

  @Test
  fun productionDelegate_nonSrcOverTintAndFilterMatchBuiltInAndRejectFault() =
    productionProgressiveParity(layered = false, HazePerformanceMode.Quality, nonSrc = true)

  @Test
  fun productionDelegate_translatedRadialMaskAndSweepTintMatchReference() =
    productionProgressiveParity(layered = false, HazePerformanceMode.Quality, shaderShapes = true)

  @Test
  fun productionDelegate_hardwareRgbTintAndSoftwareProgressiveMatchReference() =
    productionProgressiveParity(layered = false, HazePerformanceMode.Quality, bitmapShaders = true)

  private fun productionProgressiveParity(layered: Boolean, mode: HazePerformanceMode, nonSrc: Boolean = false, shaderShapes: Boolean = false, bitmapShaders: Boolean = false) {
    fun nativeBrush(start: Int, end: Int, horizontal: Boolean): ShaderBrush {
      val shader = LinearGradient(0f, 0f, if (horizontal) 60f else 0f, if (horizontal) 0f else 50f, start, end, Shader.TileMode.CLAMP)
      return object : ShaderBrush() {
        override fun createShader(size: Size): Shader = shader
      }
    }
    fun colorBrush(start: ComposeColor, end: ComposeColor, horizontal: Boolean): Brush = Brush.linearGradient(
      listOf(start, end),
      start = Offset.Zero,
      end = if (horizontal) Offset(60f, 0f) else Offset(0f, 50f),
    )
    val alphaStart = android.graphics.Color.argb(220, 0, 0, 0)
    val alphaEnd = android.graphics.Color.argb(100, 0, 0, 0)
    val red = android.graphics.Color.argb(160, 255, 0, 0)
    val green = android.graphics.Color.argb(160, 0, 255, 0)
    val blue = android.graphics.Color.argb(128, 0, 0, 255)
    val yellow = android.graphics.Color.argb(128, 255, 255, 0)
    val shaderMatrices = mutableListOf<Pair<Shader, List<Float>>>()
    fun matrix(shader: Shader) = FloatArray(9).also { Matrix().also(shader::getLocalMatrix).getValues(it) }.toList()
    fun translated(shader: Shader, x: Float = 3f, y: Float = 4f): ShaderBrush {
      shader.setLocalMatrix(Matrix().apply { setTranslate(x, y) })
      shaderMatrices += shader to matrix(shader)
      return object : ShaderBrush() {
        override fun createShader(size: Size): Shader = shader
      }
    }
    val customMask = if (shaderShapes) translated(RadialGradient(25f, 20f, 30f, alphaStart, alphaEnd, Shader.TileMode.CLAMP)) else nativeBrush(alphaStart, alphaEnd, true)
    val faultMask = if (shaderShapes) translated(RadialGradient(25f, 20f, 30f, alphaStart, alphaEnd, Shader.TileMode.CLAMP), 8f) else customMask
    val builtMask = if (shaderShapes) Brush.radialGradient(listOf(ComposeColor.Black.copy(alpha = 220f / 255f), ComposeColor.Black.copy(alpha = 100f / 255f)), center = Offset(28f, 24f), radius = 30f) else colorBrush(ComposeColor.Black.copy(alpha = 220f / 255f), ComposeColor.Black.copy(alpha = 100f / 255f), true)
    val rgbHardware by lazy {
      val pixels = IntArray(64 * 64) { i ->
        val fraction = (i % 64) / 63f
        android.graphics.Color.argb(160, (255 * (1f - fraction)).roundToInt(), (255 * fraction).roundToInt(), 0)
      }
      checkNotNull(Bitmap.createBitmap(pixels, 64, 64, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.HARDWARE, false)).also {
        assertThat(it.config).isEqualTo(Bitmap.Config.HARDWARE)
      }
    }
    val progressiveBitmap by lazy {
      val pixels = IntArray(64 * 64) { i -> android.graphics.Color.argb((255 * (1f - (i / 64) / 63f)).roundToInt(), 0, 0, 0) }
      Bitmap.createBitmap(pixels, 64, 64, Bitmap.Config.ARGB_8888)
    }
    val customFirst = when {
      shaderShapes -> translated(SweepGradient(25f, 20f, red, green))
      bitmapShaders -> translated(BitmapShader(rgbHardware, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
      else -> nativeBrush(red, green, true)
    }
    val faultFirst = when {
      bitmapShaders -> translated(BitmapShader(rgbHardware, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP), 27f)
      shaderShapes -> translated(SweepGradient(25f, 20f, red, green), 20f)
      else -> customFirst
    }
    val builtFirst = when {
      shaderShapes -> Brush.sweepGradient(listOf(ComposeColor.Red.copy(alpha = 160f / 255f), ComposeColor.Green.copy(alpha = 160f / 255f)), center = Offset(28f, 24f))
      bitmapShaders -> Brush.linearGradient(listOf(ComposeColor.Red.copy(alpha = 160f / 255f), ComposeColor.Green.copy(alpha = 160f / 255f)), start = Offset(3.5f, 0f), end = Offset(66.5f, 0f))
      else -> colorBrush(ComposeColor.Red.copy(alpha = 160f / 255f), ComposeColor.Green.copy(alpha = 160f / 255f), true)
    }
    val customSecond = nativeBrush(blue, yellow, false)
    val builtSecond = colorBrush(ComposeColor.Blue.copy(alpha = 128f / 255f), ComposeColor.Yellow.copy(alpha = 128f / 255f), false)
    var secondaryFault by mutableStateOf(false)
    fun progressive(custom: Boolean): HazeProgressive = if (layered) {
      HazeProgressive.LinearGradient(easing = LinearEasing, start = Offset.Zero, end = Offset(0f, 50f), startIntensity = 0f, endIntensity = 1f)
    } else {
      HazeProgressive.Brush(
        if (bitmapShaders) {
          if (custom) translated(BitmapShader(progressiveBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP), y = if (secondaryFault) 22f else 4f) else Brush.linearGradient(listOf(ComposeColor.Black, ComposeColor.Transparent), start = Offset(0f, 4.5f), end = Offset(0f, 67.5f))
        } else if (custom) nativeBrush(android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT, false) else colorBrush(ComposeColor.Black, ComposeColor.Transparent, false),
      )
    }
    var liveFault by mutableStateOf(false)
    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Row(Modifier.background(ComposeColor.Black)) {
          (if (nonSrc) listOf("built", "custom", "fault") else listOf("built", "custom")).forEach { variant ->
            val custom = variant != "built"
            val faulted = variant == "fault" || (variant == "custom" && liveFault)
            val tintMode = if (nonSrc && !faulted) BlendMode.Multiply else BlendMode.SrcOver
            val filterMode = if (nonSrc && !faulted) BlendMode.Screen else BlendMode.SrcOver
            Box(
              Modifier.size(60.2f.dp, 50.2f.dp).testTag("${variant}Parity")
                .hazeBlur(
                  input = HazeInput.Content,
                  performanceMode = if (custom) mode else HazePerformanceMode.Quality,
                  style = HazeBlurStyle {
                    blurRadius(4.dp)
                    noiseFactor(0.25f)
                    backgroundColor(ComposeColor.Transparent)
                    alpha(0.8f)
                    mask(if (custom && shaderShapes && liveFault) faultMask else if (custom) customMask else builtMask)
                    colorEffects(
                      listOf(
                        HazeColorEffect.tint(if (custom && ((bitmapShaders && liveFault) || (shaderShapes && secondaryFault))) faultFirst else if (custom) customFirst else builtFirst, tintMode),
                        HazeColorEffect.colorFilter(ComposeColorFilter.tint(ComposeColor(0f, 1f, 1f, 128f / 255f)), filterMode),
                        HazeColorEffect.tint(if (custom) customSecond else builtSecond, tintMode),
                      ),
                    )
                    progressive(progressive(custom))
                  },
                ).drawBehind {
                  drawRect(ComposeColor(0.12f, 0.31f, 0.51f))
                  for (x in 0 until 60 step 16) drawRect(ComposeColor.Yellow, topLeft = Offset(x.toFloat(), 0f), size = Size(8f, 50f))
                },
            )
          }
        }
      }
    }
    rule.waitForIdle()
    fun read(tag: String) = rule.onNodeWithTag(tag).captureToImage().toPixelMap().let { image ->
      (8..40 step 4).flatMap { y -> (8..52 step 4).map { x -> image[x, y] } }
    }
    val built = read("builtParity")
    val custom = read("customParity")
    val difference = built.zip(custom).maxOf { (a, b) -> maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) }
    Log.i("Haze1730", "productionParity sdk=${Build.VERSION.SDK_INT} mode=$mode layered=$layered maxRgbDifference=$difference built=$built custom=$custom")
    if (shaderShapes || bitmapShaders) {
      rule.runOnIdle { liveFault = true }
      rule.waitForIdle()
      val injected = read("customParity")
      val faultDifference = built.zip(injected).maxOf { (a, b) -> maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) }
      rule.runOnIdle { liveFault = false }
      rule.waitForIdle()
      assertThat(read("customParity"), "restored shader").isEqualTo(custom)
      assertThat(faultDifference, "translated shader fault").isGreaterThan(0.025f)
      Log.i("Haze1730", "shape/bitmap offset faultDifference=$faultDifference")
      rule.runOnIdle { secondaryFault = true }
      rule.waitForIdle()
      val secondary = read("customParity")
      val secondaryDifference = built.zip(secondary).maxOf { (a, b) -> maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) }
      rule.runOnIdle { secondaryFault = false }
      rule.waitForIdle()
      assertThat(read("customParity"), "secondary restored shader").isEqualTo(custom)
      assertThat(secondaryDifference, "secondary translated shader fault").isGreaterThan(0.025f)
      Log.i("Haze1730", "secondary offset faultDifference=$secondaryDifference")
    }
    if (nonSrc) {
      val fault = read("faultParity")
      val faultDifference = built.zip(fault).maxOf { (a, b) -> maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) }
      rule.runOnIdle { liveFault = true }
      rule.waitForIdle()
      val injected = read("customParity")
      rule.runOnIdle { liveFault = false }
      rule.waitForIdle()
      val restored = read("customParity")
      Log.i("Haze1730", "nonSrc faultDifference=$faultDifference injected=$injected restored=$restored")
      assertThat(injected, "live incorrect blend modes").isEqualTo(fault)
      assertThat(restored, "restored blend modes").isEqualTo(custom)
      assertThat(faultDifference, "incorrect blend modes must differ").isGreaterThan(0.025f)
    }
    assertThat(difference, "custom versus built-in progressive mode=$mode layered=$layered").isLessThan(0.025f)
    shaderMatrices.forEach { (shader, original) -> assertThat(matrix(shader), "caller shader matrix").isEqualTo(original) }
  }

  @Test
  fun productionDelegate_customTransitionsClearAndTrimRecoverFreshOutput() {
    val shader = LinearGradient(0f, 0f, 0f, 50f, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
    shader.setLocalMatrix(Matrix().apply { setTranslate(0f, 2f) })
    val matrix = FloatArray(9).also { Matrix().also(shader::getLocalMatrix).getValues(it) }.toList()
    val brush = object : ShaderBrush() {
      override fun createShader(size: Size): Shader = shader
    }
    val custom = HazeBlurStyle {
      blurRadius(4.dp)
      noiseFactor(0f)
      backgroundColor(ComposeColor.Transparent)
      mask(brush)
    }
    val ordinary = HazeBlurStyle {
      blurRadius(4.dp)
      noiseFactor(0f)
      backgroundColor(ComposeColor.Transparent)
    }
    val banded = HazeBlurStyle {
      blurRadius(4.dp)
      noiseFactor(0f)
      backgroundColor(ComposeColor.Transparent)
      progressive(HazeProgressive.verticalGradient())
    }
    lateinit var effect: BlurVisualEffect
    val factory = object : HazeEffectFactory<BlurConfiguration> {
      override fun createRenderer(): HazeEffectRenderer<BlurConfiguration> = BlurVisualEffect().also { effect = it }
    }
    var style by mutableStateOf(custom)
    var color by mutableStateOf(ComposeColor.Red)
    var attachment by mutableStateOf(0)
    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        key(attachment) {
          Box(Modifier.background(ComposeColor.Black).size(60.dp, 50.dp).testTag("lifetime").hazeEffect(factory = factory, input = HazeInput.Content, style = BlurConfiguration(style, HazePerformanceMode.Quality)).drawBehind { drawRect(color) })
        }
      }
    }
    rule.waitForIdle()
    fun read() = rule.onNodeWithTag("lifetime").captureToImage().toPixelMap().let { image ->
      (8..40 step 4).flatMap { y -> (8..52 step 4).map { x -> image[x, y] } }
    }
    fun difference(a: List<ComposeColor>, b: List<ComposeColor>) = a.zip(b).maxOf { (x, y) -> maxOf(abs(x.red - y.red), abs(x.green - y.green), abs(x.blue - y.blue)) }
    val first = read()
    rule.runOnIdle {
      style = ordinary
      color = ComposeColor.Green
    }
    rule.waitForIdle()
    assertThat(difference(first, read()), "changed input visible").isGreaterThan(0.025f)
    rule.runOnIdle { style = banded }
    rule.waitForIdle()
    val bandCount = (effect.delegate as RenderEffectBlurVisualEffectDelegate).bandSlotCount
    assertThat(bandCount).isGreaterThan(0)
    rule.runOnIdle {
      style = custom
      color = ComposeColor.Red
    }
    rule.waitForIdle()
    val restored = read()
    assertThat(difference(first, restored), "custom transition restored").isLessThan(0.025f)
    rule.runOnIdle {
      effect.renderEffectCache.evictAll()
      effect.clearRenderEffectCache()
      color = ComposeColor.Blue
    }
    rule.waitForIdle()
    val changed = read()
    assertThat(difference(restored, changed), "fresh input after cache clear").isGreaterThan(0.025f)
    rule.runOnIdle { effect.onTrimMemory(TrimMemoryLevel.MODERATE) }
    rule.waitForIdle()
    val trimmed = read()
    assertThat(difference(changed, trimmed), "trimmed output").isLessThan(0.025f)
    rule.runOnIdle { attachment++ }
    rule.waitForIdle()
    assertThat(difference(trimmed, read()), "fresh attachment").isLessThan(0.025f)
    assertThat(FloatArray(9).also { Matrix().also(shader::getLocalMatrix).getValues(it) }.toList()).isEqualTo(matrix)
    Log.i("Haze1730", "lifetime sdk=${Build.VERSION.SDK_INT} bands=$bandCount first=$first restored=$restored changed=$changed trimmed=$trimmed")
  }

  @Test
  fun productionDelegate_firstOutputSurvivesAlternateGraphAndCacheReset() {
    val shader = LinearGradient(0f, 0f, 0f, 50f, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
    shader.setLocalMatrix(Matrix().apply { setTranslate(0f, 2f) })
    val matrix = FloatArray(9).also { Matrix().also(shader::getLocalMatrix).getValues(it) }.toList()
    val brush = object : ShaderBrush() {
      override fun createShader(size: Size): Shader = shader
    }
    val style = HazeBlurStyle {
      blurRadius(4.dp)
      noiseFactor(0f)
      backgroundColor(ComposeColor.Transparent)
      mask(brush)
    }
    lateinit var firstEffect: BlurVisualEffect
    var retainedOnly by mutableStateOf(false)
    var redraw by mutableStateOf(0)
    var inputRecordings = 0
    var retainedDraws = 0
    val firstFactory = object : HazeEffectFactory<BlurConfiguration> {
      override fun createRenderer(): HazeEffectRenderer<BlurConfiguration> {
        val effect = BlurVisualEffect().also { firstEffect = it }
        return object :
          HazeEffectRenderer<BlurConfiguration> by effect,
          HazeEffectRendererLifecycle<BlurConfiguration> by effect,
          HazeEffectRendererDrawHooks<BlurConfiguration> by effect,
          HazeEffectRendererRetainedOutput by effect {
          override fun HazeEffectDrawScope.draw(style: BlurConfiguration) {
            val context = this as HazeEffectRuntimeDrawScope
            val noInput = retainedOnly
            redraw
            if (noInput) retainedDraws++
            val gated = object : HazeEffectRuntimeDrawScope by context {
              override val hasDrawableInput: Boolean = !noInput && context.hasDrawableInput
              override val inputSnapshot = if (noInput) null else context.inputSnapshot
              override fun drawInput() {
                check(!noInput) { "Fresh input must not rescue a retained-only draw" }
                inputRecordings++
                context.drawInput()
              }
              override fun DrawScope.drawInput() {
                check(!noInput) { "Fresh input must not rescue a retained-only capture" }
                inputRecordings++
                with(context) { this@drawInput.drawInput() }
              }
            }
            with(effect) { gated.draw(style) }
          }
        }
      }
    }
    val secondFactory = object : HazeEffectFactory<BlurConfiguration> {
      override fun createRenderer(): HazeEffectRenderer<BlurConfiguration> = BlurVisualEffect()
    }
    var alternate by mutableStateOf(false)
    var attachment by mutableStateOf(0)
    var firstColor by mutableStateOf(ComposeColor.Red)
    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Row(Modifier.background(ComposeColor.Black)) {
          key(attachment) {
            Box(Modifier.size(60.dp, 50.dp).testTag("firstOutput").hazeEffect(factory = firstFactory, input = HazeInput.Content, style = BlurConfiguration(style, HazePerformanceMode.Quality)).drawBehind { drawRect(firstColor) })
          }
          Box(Modifier.size(if (alternate) 72.dp else 60.dp, if (alternate) 58.dp else 50.dp).testTag("secondOutput").hazeEffect(factory = secondFactory, input = HazeInput.Content, style = BlurConfiguration(style, HazePerformanceMode.Quality)).drawBehind { drawRect(if (alternate) ComposeColor.Blue else ComposeColor.Green) })
        }
      }
    }
    rule.waitForIdle()
    fun read(tag: String) = rule.onNodeWithTag(tag).captureToImage().toPixelMap().let { image ->
      (8..40 step 4).flatMap { y -> (8..52 step 4).map { x -> image[x, y] } }
    }
    fun difference(a: List<ComposeColor>, b: List<ComposeColor>) = a.zip(b).maxOf { (x, y) -> maxOf(abs(x.red - y.red), abs(x.green - y.green), abs(x.blue - y.blue)) }
    val first = read("firstOutput")
    val second = read("secondOutput")
    val delegate = firstEffect.delegate as RenderEffectBlurVisualEffectDelegate
    val heldCapture = checkNotNull(delegate.scaledContentLayer)
    val heldLogical = checkNotNull(delegate.logicalLayer.layer)
    assertThat(delegate.logicalPathActive).isTrue()
    assertThat(delegate.logicalSlot.platformEffect != null, "populated logical slot").isTrue()
    assertThat(firstEffect.canDrawRetainedOutput()).isTrue()
    val recorded = inputRecordings
    rule.runOnIdle { retainedOnly = true }
    rule.waitForIdle()
    fun checkHeld(label: String) {
      assertThat(difference(first, read("firstOutput")), label).isLessThan(0.025f)
      assertThat(inputRecordings, "$label no recapture").isEqualTo(recorded)
      assertThat(retainedDraws, "$label retained branch reached").isGreaterThan(0)
      assertThat(firstEffect.canDrawRetainedOutput(), "$label available").isTrue()
      assertThat(heldCapture.isReleased, "$label capture owned").isFalse()
      assertThat(delegate.scaledContentLayer, "$label capture identity").isSameInstanceAs(heldCapture)
      assertThat(delegate.logicalLayer.layer, "$label logical identity").isSameInstanceAs(heldLogical)
    }
    checkHeld("retained-only initial")
    assertThat(first.maxOf { it.red }, "visible first output").isGreaterThan(0.1f)
    rule.runOnIdle { alternate = true }
    rule.waitForIdle()
    assertThat(difference(second, read("secondOutput")), "alternate graph differs").isGreaterThan(0.025f)
    rule.runOnIdle { redraw++ }
    rule.waitForIdle()
    checkHeld("retained-only after alternate graph")
    val misses = firstEffect.renderEffectCache.missCount()
    rule.runOnIdle {
      firstEffect.renderEffectCache.evictAll()
      redraw++
    }
    rule.waitForIdle()
    checkHeld("retained-only after eviction")
    assertThat(firstEffect.renderEffectCache.missCount(), "populated slot survives eviction").isEqualTo(misses)
    val oldPlatform = delegate.logicalSlot.platformEffect
    val generation = firstEffect.renderEffectGeneration
    rule.runOnIdle {
      firstEffect.clearRenderEffectCache()
      redraw++
    }
    rule.waitForIdle()
    assertThat(firstEffect.renderEffectGeneration, "cache generation advanced").isGreaterThan(generation)
    checkHeld("retained-only after generation clear")
    assertThat(firstEffect.renderEffectCache.missCount(), "populated slot misses new generation").isGreaterThan(misses)
    assertThat(delegate.logicalSlot.platformEffect === oldPlatform, "generation replaces platform effect").isFalse()
    val hits = firstEffect.renderEffectCache.hitCount()
    rule.runOnIdle {
      delegate.logicalSlot.reset()
      assertThat(delegate.logicalSlot.platformEffect == null, "slot reset").isTrue()
      redraw++
    }
    rule.waitForIdle()
    checkHeld("retained-only after slot reset")
    assertThat(firstEffect.renderEffectCache.hitCount(), "reset slot consults cache").isGreaterThan(hits)
    Log.i("Haze1730", "retained-only sdk=${Build.VERSION.SDK_INT} draws=$retainedDraws recordings=$recorded generation=$generation->${firstEffect.renderEffectGeneration} misses=$misses->${firstEffect.renderEffectCache.missCount()}")
    rule.runOnIdle {
      retainedOnly = false
      firstEffect.onTrimMemory(TrimMemoryLevel.MODERATE)
      assertThat(heldCapture.isReleased, "trim releases held capture").isTrue()
      firstColor = ComposeColor.Blue
    }
    rule.waitForIdle()
    assertThat(inputRecordings, "fresh recording after trim").isGreaterThan(recorded)
    rule.runOnIdle { firstColor = ComposeColor.Red }
    rule.waitForIdle()
    assertThat(difference(first, read("firstOutput")), "first recovers after separate trim").isLessThan(0.025f)
    rule.runOnIdle { firstEffect.invalidateRetainedOutput() }
    assertThat(firstEffect.canDrawRetainedOutput(), "invalidated retained output").isFalse()
    rule.runOnIdle { firstColor = ComposeColor.Blue }
    rule.waitForIdle()
    assertThat(difference(first, read("firstOutput")), "fresh input after retained loss").isGreaterThan(0.025f)
    rule.runOnIdle { firstColor = ComposeColor.Red }
    rule.waitForIdle()
    assertThat(difference(first, read("firstOutput")), "first recovers after retained loss").isLessThan(0.025f)
    rule.runOnIdle { attachment++ }
    rule.waitForIdle()
    assertThat(difference(first, read("firstOutput")), "fresh first attachment").isLessThan(0.025f)
    assertThat(FloatArray(9).also { Matrix().also(shader::getLocalMatrix).getValues(it) }.toList()).isEqualTo(matrix)
  }

  @Test
  fun productionDelegate_wideGamutF16MatchesHardwareReference() {
    val window = rule.activity.window
    val previousMode = window.colorMode
    var eraseLowAlpha by mutableStateOf(false)
    try {
      rule.runOnUiThread { window.colorMode = ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT }
      val supported = rule.activity.display?.isWideColorGamut == true
      Log.i("Haze1730", "wide sdk=${Build.VERSION.SDK_INT} fingerprint=${Build.FINGERPRINT} displayWide=$supported windowWide=${window.isWideColorGamut}")
      // Wide-colour qualification waived for this delivery: haze#1730 issuecomment-6709516338.
      assumeTrue("Wide-colour/F16 coverage unavailable on this target", supported && window.isWideColorGamut)
      val p3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
      fun p3Shader(lowAlpha: Float = 0.02f) = LinearGradient(
        0f,
        0f,
        60f,
        0f,
        longArrayOf(
          android.graphics.Color.pack(1f, 0.05f, 0f, 0.8f, p3),
          android.graphics.Color.pack(0f, 1f, 0.05f, lowAlpha, p3),
          android.graphics.Color.pack(0f, 1f, 0.05f, lowAlpha, p3),
        ),
        floatArrayOf(0f, 0.75f, 1f),
        Shader.TileMode.CLAMP,
      )
      val custom = object : ShaderBrush() {
        override fun createShader(size: Size): Shader = p3Shader(if (eraseLowAlpha) 0f else 0.02f)
      }
      val reference = p3Shader()
      val actualOrigins = Array(2) { Offset.Zero }
      val referenceOrigins = Array(2) { Offset.Zero }
      rule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
          Column {
            listOf(ComposeColor.White, ComposeColor.Black).forEachIndexed { index, background ->
              Row {
                Box(
                  Modifier.size(60.2f.dp, 40.2f.dp).onGloballyPositioned { actualOrigins[index] = it.boundsInWindow().topLeft }
                    .hazeBlur(
                      input = HazeInput.Content,
                      performanceMode = HazePerformanceMode.Quality,
                      style = HazeBlurStyle {
                        blurRadius(0.dp)
                        noiseFactor(0f)
                        backgroundColor(ComposeColor.Transparent)
                        colorEffects(listOf(HazeColorEffect.tint(custom)))
                      },
                    ).drawBehind { drawRect(background) },
                )
                Box(
                  Modifier.size(60.2f.dp, 40.2f.dp).onGloballyPositioned { referenceOrigins[index] = it.boundsInWindow().topLeft }
                    .drawBehind {
                      drawRect(background)
                      drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val save = native.save()
                        native.clipRect(0f, 0f, size.width, size.height)
                        native.drawPaint(Paint().apply { shader = reference })
                        native.restoreToCount(save)
                      }
                    },
                )
              }
            }
          }
        }
      }
      rule.waitForIdle()
      val target = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.RGBA_F16, true, p3)
      assertThat(target.config).isEqualTo(Bitmap.Config.RGBA_F16)
      assertThat(target.colorSpace).isEqualTo(p3)
      fun copyWindow() {
        val latch = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(window, target, { code ->
          result = code
          latch.countDown()
        }, Handler(Looper.getMainLooper()))
        assertThat(latch.await(5, TimeUnit.SECONDS), "F16 window copy completed").isTrue()
        assertThat(result, "F16 window copy").isEqualTo(PixelCopy.SUCCESS)
      }
      val probes = listOf(8, 16, 24, 32, 40, 48, 56, 59)
      fun colors(origin: Offset) = probes.map { x -> target.getColor((origin.x + x).roundToInt(), (origin.y + 20).roundToInt()) }

      // Compare the native reference's observable low-alpha response; opaque window alpha is one.
      fun lowAlphaResponse(rows: List<List<android.graphics.Color>>): Float {
        val index = probes.indexOf(56)
        val white = rows[0][index]
        val black = rows[1][index]
        return maxOf(
          abs(black.red()),
          abs(black.green()),
          abs(black.blue()),
          abs(1f - white.red()),
          abs(1f - white.green()),
          abs(1f - white.blue()),
        )
      }
      copyWindow()
      val actual = actualOrigins.map(::colors)
      val expected = referenceOrigins.map(::colors)
      val maxError = actual.flatten().zip(expected.flatten()).maxOf { (a, b) -> maxOf(abs(a.red() - b.red()), abs(a.green() - b.green()), abs(a.blue() - b.blue()), abs(a.alpha() - b.alpha())) }
      fun whiteBlackResponse(rows: List<List<android.graphics.Color>>) = rows[0].zip(rows[1]).map { (white, black) -> 1f - (white.red() - black.red()) }
      val alphaError = whiteBlackResponse(actual).zip(whiteBlackResponse(expected)).maxOf { (a, b) -> abs(a - b) }
      Log.i("Haze1730", "wide config=${target.config} space=${target.colorSpace} actual=$actual reference=$expected maxError=$maxError alphaError=$alphaError")
      assertThat(maxError, "wide-gamut F16 RGB").isLessThan(0.025f)
      assertThat(alphaError, "wide-gamut F16 white/black response").isLessThan(0.025f)
      val expectedLow = lowAlphaResponse(expected)
      assertThat(expectedLow, "native low-alpha plateau must remain observable").isGreaterThan(0.001f)
      fun checkLowAlpha(rows: List<List<android.graphics.Color>>) {
        assertThat(abs(lowAlphaResponse(rows) - expectedLow), "low-alpha response relative error")
          .isLessThan(expectedLow * 0.1f)
      }
      checkLowAlpha(actual)
      rule.runOnIdle { eraseLowAlpha = true }
      rule.waitForIdle()
      copyWindow()
      val erased = lowAlphaResponse(actualOrigins.map(::colors))
      assertThat(erased, "live zero-alpha fault is active").isLessThan(expectedLow * 0.1f)
      assertThat(abs(erased - expectedLow), "erasure must fail the relative guard").isGreaterThan(expectedLow * 0.1f)
      rule.runOnIdle { eraseLowAlpha = false }
      rule.waitForIdle()
      copyWindow()
      checkLowAlpha(actualOrigins.map(::colors))
    } finally {
      rule.runOnIdle { eraseLowAlpha = false }
      rule.runOnUiThread { window.colorMode = previousMode }
    }
  }
}
