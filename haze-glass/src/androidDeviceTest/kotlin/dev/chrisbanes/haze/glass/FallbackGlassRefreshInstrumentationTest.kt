// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class)

package dev.chrisbanes.haze.glass

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 31, maxSdkVersion = 31)
class FallbackGlassRefreshInstrumentationTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
  private val pattern = mutableIntStateOf(0)
  private val route = mutableStateOf(true)
  private var hardware = false
  private var effect = GlassRuntimeEffect()
  private val style = GlassStyle {
    shape(RoundedCornerShape(12.dp))
    optics(GlassOptics(blurRadius = OpticalSizeValue.Fixed(12.dp), depth = OpticalSizeValue.Fixed(1f)))
    backgroundColor(Color.Transparent)
    tint(Color.Transparent)
    specularIntensity(0f)
    edgeSoftness(0.dp)
    edgeShadow(Color.Transparent)
    ambientResponse(0f)
    contrast(0f)
    whitePoint(0f)
    chromaMultiplier(1f)
  }

  // The runner uninstalls its package after the test; allow time to pull its evidence.
  @After
  fun retainEvidenceForCollection() {
    java.util.concurrent.CountDownLatch(1).await(20, java.util.concurrent.TimeUnit.SECONDS)
  }

  @Test
  fun single_refreshesAcrossSourceChangesAndReentry() {
    rule.setContent {
      if (route.value) {
        val source = androidx.compose.runtime.remember { HazeState() }
        effect = androidx.compose.runtime.remember { GlassRuntimeEffect() }
        Box(Modifier.size(300.dp, 330.dp).testTag("root")) {
          Canvas(
            Modifier.size(300.dp, 280.dp).hazeSource(source).drawBehind {
              hardware = drawContext.canvas.nativeCanvas.isHardwareAccelerated
            },
          ) {
            val colors = when (pattern.intValue) {
              0 -> listOf(Color.Red, Color.White)
              1 -> listOf(Color.Blue, Color.Cyan)
              else -> listOf(Color.Black, Color.White)
            }
            val stripe = 8.dp.toPx()
            repeat((size.width / stripe).toInt() + 1) { i ->
              drawRect(colors[(i + if (pattern.intValue == 2) 1 else 0) % 2], Offset(i * stripe, 0f), Size(stripe, size.height))
            }
          }
          Box(
            Modifier.offset(y = 10.dp).size(280.dp, 110.dp).testTag("glass").hazeGlass(
              factory = HazeEffectFactory { effect },
              input = HazeInput.Sources(source),
              style = style,
              performanceMode = null,
              expandLayerBounds = true,
              interactionSource = null,
            ),
          )
          BasicText("Away", Modifier.offset(y = 290.dp).background(Color.White).clickable { route.value = false })
        }
      } else {
        BasicText("Return", Modifier.clickable { route.value = true })
      }
    }
    rule.waitForIdle()
    val bounds = rule.onNodeWithTag("glass").fetchSemanticsNode().boundsInRoot
    val directory = checkNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("haze-1731-native"))
    val metrics = File(directory, "single-metrics.txt")
    metrics.writeText("bounds=$bounds\n")
    fun sequence(label: String, values: List<Int> = listOf(0, 1, 2, 0)) {
      values.forEachIndexed { index, value ->
        rule.runOnIdle { pattern.intValue = value }
        rule.waitForIdle()
        val image = rule.onNodeWithTag("root").captureToImage()
        File(directory, "$label-$index.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        val pixels = image.toPixelMap()
        val density = rule.density.density
        val values = (30..220).map { x -> pixels[(x * density).toInt(), (60 * density).toInt()] }
        val sharp = (30..220).map { x -> pixels[(x * density).toInt(), (200 * density).toInt()] }
        val meanRed = values.map { it.red }.average()
        val meanBlue = values.map { it.blue }.average()
        val filteredChannel = values.map {
          when (value) {
            0 -> it.blue
            1 -> it.green
            else -> it.red
          }
        }
        val sharpChannel = sharp.map {
          when (value) {
            0 -> it.blue
            1 -> it.green
            else -> it.red
          }
        }
        val blurContrast = filteredChannel.max() - filteredChannel.min()
        val sharpContrast = sharpChannel.max() - sharpChannel.min()
        metrics.appendText("$label-$index pattern=$value size=${pixels.width}x${pixels.height} density=$density red=$meanRed blue=$meanBlue blur=$blurContrast sharp=$sharpContrast sample=${sharp.first()}\n")
        assertThat(hardware).isTrue()
        val delegate = effect.delegate as FallbackGlassDelegate
        assertThat(delegate.filteredInputEffectForTest).isNotNull()
        assertThat(rule.onNodeWithTag("glass").fetchSemanticsNode().boundsInRoot).isEqualTo(bounds)
        assertThat(sharpContrast, "size=${pixels.width}x${pixels.height} density=$density first=${sharp.first()} glass=${values.first()} red=$meanRed blue=$meanBlue blur=$blurContrast").isGreaterThan(0.6f)
        assertThat(blurContrast).isLessThan(sharpContrast * 0.8f)
        if (value == 0) assertThat(meanRed.toFloat() - meanBlue.toFloat()).isGreaterThan(0.3f)
        if (value == 1) {
          assertThat(meanBlue.toFloat()).isGreaterThan(0.8f)
          assertThat(meanRed.toFloat()).isLessThan(0.2f)
        }
        if (value == 2) {
          assertThat(meanRed.toFloat()).isGreaterThan(0.3f)
          assertThat(meanRed.toFloat()).isLessThan(0.7f)
          assertThat(meanBlue.toFloat()).isGreaterThan(0.3f)
          assertThat(meanBlue.toFloat()).isLessThan(0.7f)
        }
      }
    }
    sequence("attached")
    val previous = checkNotNull((effect.delegate as FallbackGlassDelegate).filteredInputLayerForTest)
    rule.onNodeWithText("Away").performClick()
    rule.waitForIdle()
    assertThat(previous.isReleased).isTrue()
    rule.runOnIdle { pattern.intValue = 1 }
    rule.onNodeWithText("Return").performClick()
    rule.waitForIdle()
    sequence("reentry-current", listOf(1))
    val reacquired = checkNotNull((effect.delegate as FallbackGlassDelegate).filteredInputLayerForTest)
    assertThat(reacquired).isNotSameInstanceAs(previous)
    sequence("reentry")
  }

  @Test fun progressive_refreshesAcrossSourceChangesAndReentry() = exerciseSurface("progressive", false)

  @Test fun merged_refreshesAcrossSourceChangesAndReentry() = exerciseSurface("merged", false)

  @Test fun single_preservesAlphaShapeAndForeground() = exerciseSurface("single", true)

  @Test fun progressive_preservesAlphaShapeAndForeground() = exerciseSurface("progressive", true)

  @Test fun merged_preservesAlphaShapeAndForeground() = exerciseSurface("merged", true)

  private fun exerciseSurface(kind: String, visual: Boolean) {
    val opacity = mutableFloatStateOf(1f)
    val scenePattern = mutableIntStateOf(0)
    val visible = mutableStateOf(true)
    var firstEffect = GlassRuntimeEffect()
    val directory = checkNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("haze-1731-native"))
    val key = "$kind-${if (visual) "visual" else "refresh"}"
    val log = File(directory, "$key-metrics.txt")
    log.writeText("")
    rule.setContent {
      if (visible.value) {
        val source = androidx.compose.runtime.remember { HazeState() }
        firstEffect = androidx.compose.runtime.remember { GlassRuntimeEffect() }
        val secondEffect = androidx.compose.runtime.remember { GlassRuntimeEffect() }
        val current = style.then {
          alpha(opacity.floatValue)
          if (kind == "progressive") optics { copy(progressive = HazeProgressive.verticalGradient()) }
        }
        Box(Modifier.size(300.dp, 330.dp).testTag("extra-root")) {
          Canvas(
            Modifier.size(300.dp, 280.dp).hazeSource(source).drawBehind {
              hardware = drawContext.canvas.nativeCanvas.isHardwareAccelerated
            },
          ) {
            val colors = when (scenePattern.intValue) {
              0 -> listOf(Color.Red, Color.White)
              1 -> listOf(Color.Blue, Color.Cyan)
              else -> listOf(Color.Black, Color.White)
            }
            val stripe = 8.dp.toPx()
            repeat((size.width / stripe).toInt() + 1) { i ->
              drawRect(colors[(i + if (scenePattern.intValue == 2) 1 else 0) % 2], Offset(i * stripe, 0f), Size(stripe, size.height))
            }
          }
          if (kind == "merged") {
            Box(Modifier.offset(y = 10.dp).size(300.dp, 110.dp).testTag("extra-glass").hazeGlassContainer(24.dp)) {
              Box(
                Modifier.size(180.dp, 110.dp).hazeGlass(
                  factory = HazeEffectFactory { firstEffect },
                  input = HazeInput.Sources(source),
                  style = current,
                  performanceMode = null,
                  expandLayerBounds = true,
                  interactionSource = null,
                  memberTransition = GlassMemberTransition.None,
                ),
              ) {
                if (visual) Box(Modifier.offset(x = 120.dp, y = 40.dp).size(20.dp).background(Color.Green))
              }
              Box(
                Modifier.offset(x = 100.dp).size(140.dp, 110.dp).hazeGlass(
                  factory = HazeEffectFactory { secondEffect },
                  input = HazeInput.Sources(source),
                  style = current,
                  performanceMode = null,
                  expandLayerBounds = true,
                  interactionSource = null,
                  memberTransition = GlassMemberTransition.None,
                ),
              )
              Box(
                Modifier.offset(x = 250.dp).size(50.dp, 110.dp).hazeGlass(
                  input = HazeInput.Sources(source),
                  style = current,
                  performanceMode = null,
                  memberTransition = GlassMemberTransition.None,
                ),
              )
            }
          } else {
            Box(
              Modifier.offset(y = 10.dp).size(280.dp, 110.dp).testTag("extra-glass").hazeGlass(
                factory = HazeEffectFactory { firstEffect },
                input = HazeInput.Sources(source),
                style = current,
                performanceMode = null,
                expandLayerBounds = true,
                interactionSource = null,
              ),
            ) {
              if (visual) Box(Modifier.offset(x = 120.dp, y = 40.dp).size(20.dp).background(Color.Green))
            }
          }
          if (!visual && kind == "progressive") {
            Box(
              Modifier.offset(y = 140.dp).size(280.dp, 110.dp).hazeGlass(
                input = HazeInput.Sources(source),
                style = style,
                performanceMode = null,
              ),
            )
          }
          BasicText("Away", Modifier.offset(y = 290.dp).background(Color.White).clickable { visible.value = false })
        }
      } else {
        BasicText("Return", Modifier.clickable { visible.value = true })
      }
    }
    rule.waitForIdle()
    val density = rule.density.density
    val bounds = rule.onNodeWithTag("extra-glass").fetchSemanticsNode().boundsInRoot
    fun take(phase: String): androidx.compose.ui.graphics.PixelMap {
      rule.waitForIdle()
      val image = rule.onNodeWithTag("extra-root").captureToImage()
      File(directory, "$key-$phase.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
      return image.toPixelMap()
    }
    fun androidx.compose.ui.graphics.PixelMap.at(x: Int, y: Int) = this[(x * density).toInt(), (y * density).toInt()]
    fun signature(pixels: androidx.compose.ui.graphics.PixelMap, value: Int, label: String) {
      val points = (30..250).map { pixels.at(it, 65) }
      val red = points.map { it.red }.average().toFloat()
      val blue = points.map { it.blue }.average().toFloat()
      log.appendText("$label pattern=$value red=$red blue=$blue bounds=$bounds\n")
      if (value == 0) assertThat(red - blue).isGreaterThan(0.3f)
      if (value == 1) assertThat(blue).isGreaterThan(0.8f)
      if (value == 2) {
        assertThat(red).isGreaterThan(0.3f)
        assertThat(red).isLessThan(0.7f)
      }
      assertThat(rule.onNodeWithTag("extra-glass").fetchSemanticsNode().boundsInRoot).isEqualTo(bounds)
      assertThat(hardware).isTrue()
    }
    if (!visual) {
      fun sequence(prefix: String) {
        listOf(0, 1, 2, 0).forEachIndexed { i, value ->
          rule.runOnIdle { scenePattern.intValue = value }
          val pixels = take("$prefix-$i")
          signature(pixels, value, "$prefix-$i")
          if (kind == "progressive") {
            fun contrast(y: Int): Float {
              val values = (30..250).map { x -> pixels.at(x, y).let { if (value == 0) it.blue else if (value == 1) it.green else it.red } }
              return values.max() - values.min()
            }
            val sharp = contrast(260)
            val full = contrast(200)
            val a = contrast(20)
            val middle = contrast(65)
            val b = contrast(110)
            log.appendText("progressive sharp=$sharp full=$full top=$a middle=$middle bottom=$b\n")
            assertThat(sharp).isGreaterThan(0.6f)
            assertThat(kotlin.math.abs(a - sharp)).isLessThan(0.2f)
            assertThat(kotlin.math.abs(b - full)).isLessThan(0.25f)
            assertThat(kotlin.math.abs(a - b)).isGreaterThan(0.15f)
            assertThat(middle).isGreaterThan(minOf(a, b))
            assertThat(middle).isLessThan(maxOf(a, b))
          } else {
            val owner = checkNotNull(firstEffect.memberBindingForTest?.node?.container)
            assertThat(owner.renderer.runtimeSurfaces.size).isEqualTo(1)
            val runtime = owner.renderer.runtimeSurfaces.single()
            val mergedDelegate = runtime.delegate as FallbackGlassDelegate
            assertThat(mergedDelegate.filteredInputRecordCountForTest).isGreaterThan(0)
            assertThat(mergedDelegate.filteredInputEffectForTest).isNotNull()
            val snapshot = runtime.javaClass.getDeclaredField("surfaceSnapshot").run {
              isAccessible = true
              get(runtime) as GlassSurfaceSnapshot
            }
            assertThat(snapshot.key.optics).isEqualTo(firstEffect.optics)
            val left = pixels.at(60, 65)
            val overlap = pixels.at(140, 65)
            val right = pixels.at(220, 65)
            val gap = pixels.at(245, 65)
            val gapControl = pixels.at(245, 260)
            assertThat(kotlin.math.abs(gap.red - gapControl.red)).isLessThan(0.1f)
            assertThat(kotlin.math.abs(gap.blue - gapControl.blue)).isLessThan(0.1f)
            log.appendText("merged left=$left overlap=$overlap right=$right\n")
            fun channel(color: Color): Float = if (value == 0) color.blue else if (value == 1) color.green else color.red
            assertThat(kotlin.math.abs(channel(left) - channel(overlap))).isLessThan(0.2f)
            assertThat(kotlin.math.abs(channel(right) - channel(overlap))).isLessThan(0.2f)
            for (range in listOf(30..90, 110..160, 190..230)) {
              val blurred = range.map { channel(pixels.at(it, 65)) }
              val sharp = range.map { channel(pixels.at(it, 260)) }
              assertThat(sharp.max() - sharp.min()).isGreaterThan(0.6f)
              assertThat(blurred.max() - blurred.min()).isLessThan((sharp.max() - sharp.min()) * 0.8f)
            }
          }
        }
      }
      sequence("attached")
      val previousLayer = if (kind == "merged") {
        val owner = checkNotNull(firstEffect.memberBindingForTest?.node?.container)
        (owner.renderer.runtimeSurfaces.single().delegate as FallbackGlassDelegate).filteredInputLayerForTest
      } else {
        (firstEffect.delegate as FallbackGlassDelegate).filteredInputLayerForTest
      }
      assertThat(previousLayer).isNotNull()
      if (kind == "merged") {
        val owner = checkNotNull(firstEffect.memberBindingForTest?.node?.container)
        val runtime = owner.renderer.runtimeSurfaces.single()
        val delegate = runtime.delegate as FallbackGlassDelegate
        val before = delegate.filteredInputRecordCountForTest
        rule.runOnIdle { checkNotNull(runtime.attachedContextForTest).invalidateDraw() }
        take("recording-check")
        assertThat(delegate.filteredInputRecordCountForTest).isEqualTo(before + 1)
      }
      rule.onNodeWithText("Away").performClick()
      rule.waitForIdle()
      assertThat(checkNotNull(previousLayer).isReleased).isTrue()
      rule.runOnIdle { scenePattern.intValue = 1 }
      rule.onNodeWithText("Return").performClick()
      rule.waitForIdle()
      signature(take("reentry-current"), 1, "reentry-current")
      val reacquired = if (kind == "merged") {
        val owner = checkNotNull(firstEffect.memberBindingForTest?.node?.container)
        (owner.renderer.runtimeSurfaces.single().delegate as FallbackGlassDelegate).filteredInputLayerForTest
      } else {
        (firstEffect.delegate as FallbackGlassDelegate).filteredInputLayerForTest
      }
      assertThat(checkNotNull(reacquired)).isNotSameInstanceAs(checkNotNull(previousLayer))
      sequence("reentry")
    } else {
      take("initial")
      val activeDelegate = if (kind == "merged") {
        val owner = checkNotNull(firstEffect.memberBindingForTest?.node?.container)
        owner.renderer.runtimeSurfaces.single().delegate as FallbackGlassDelegate
      } else {
        firstEffect.delegate as FallbackGlassDelegate
      }
      val activeLayer = checkNotNull(activeDelegate.filteredInputLayerForTest)
      val captures = listOf(0f, 0.5f, 1f).map { value ->
        rule.runOnIdle { opacity.floatValue = value }
        take("alpha-$value").also {
          if (value == 0f) {
            assertThat(activeLayer.isReleased).isTrue()
            assertThat(activeDelegate.filteredInputLayerForTest).isNull()
          }
        }
      }
      val alphaY = if (kind == "progressive") 100 else 65
      val sharp = captures[0].at(84, alphaY)
      val half = captures[1].at(84, alphaY)
      val full = captures[2].at(84, alphaY)
      log.appendText("alpha sharp=$sharp half=$half full=$full\n")
      assertThat(kotlin.math.abs(full.blue - sharp.blue)).isGreaterThan(0.15f)
      val separation = kotlin.math.abs(full.blue - sharp.blue)
      val tolerance = minOf(0.02f, separation / 8f)
      val expectedHalf = (sharp.blue + full.blue) / 2f
      assertThat(kotlin.math.abs(half.blue - expectedHalf)).isLessThan(tolerance)
      assertThat(kotlin.math.abs(sharp.blue + (full.blue - sharp.blue) * 0.25f - expectedHalf))
        .isGreaterThan(tolerance)
      assertThat(kotlin.math.abs(full.blue - expectedHalf)).isGreaterThan(tolerance)
      val corner = captures[2].at(1, 11)
      val outside = captures[2].at(1, 140)
      assertThat(kotlin.math.abs(corner.blue - outside.blue)).isLessThan(0.1f)
      if (kind == "merged") {
        val gap = captures[2].at(245, 65)
        val gapControl = captures[2].at(245, 260)
        assertThat(kotlin.math.abs(gap.red - gapControl.red)).isLessThan(0.1f)
        assertThat(kotlin.math.abs(gap.blue - gapControl.blue)).isLessThan(0.1f)
      }
      fun assertForeground(pixels: androidx.compose.ui.graphics.PixelMap) {
        for (x in listOf(122, 137)) for (y in listOf(52, 67)) {
          val foreground = pixels.at(x, y)
          assertThat(foreground.green).isGreaterThan(0.8f)
          assertThat(foreground.red).isLessThan(0.2f)
          assertThat(foreground.blue).isLessThan(0.2f)
        }
      }
      assertForeground(captures[2])
      rule.runOnIdle { scenePattern.intValue = 1 }
      val changed = take("foreground-changed")
      assertForeground(changed)
      assertThat(changed.at(80, 65).blue).isGreaterThan(0.8f)
    }
  }
}
