// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * The RenderScript delegate keeps its presentation, capture and padded layers across updates. The
 * node is a zero-radius copy whose pixel size is not a multiple of 4, so the layer size is the node
 * size and padding applies.
 */
@OptIn(ExperimentalTestApi::class)
class RenderScriptLayerReuseInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun contentRoundedCapture_withoutPadding_coversAllEdges() {
    val h = ContentHarness(425, 384, 4)
    h.show()
    h.assertWhiteEdges()
    assertThat(h.delegate.captureLayer.layer?.size).isEqualTo(IntSize(336, 304))
    assertThat(h.delegate.paddedLayer.layer).isNull()
  }

  @Test
  fun contentRadiusCap_usesEffectiveUnpaddedCaptureScale() {
    val h = ContentHarness(409, 409, 80)
    h.show()
    h.assertWhiteEdges()
    assertThat(h.delegate.captureLayer.layer?.size).isEqualTo(IntSize(128, 128))
    assertThat(h.delegate.paddedLayer.layer).isNull()
  }

  @Test
  fun contentPaddedCapture_keepsRawCoverageBoundsAndLayersAcrossUpdates() {
    val h = ContentHarness(413, 384, 4)
    h.show()
    h.awaitOutput()
    val capture = checkNotNull(h.delegate.captureLayer.layer)
    val padded = checkNotNull(h.delegate.paddedLayer.layer)
    assertThat(capture.size).isEqualTo(IntSize(327, 304))
    assertThat(padded.size).isEqualTo(IntSize(328, 304))
    // Inspect the raw input before filtering: transparent padding is a separate filter boundary.
    val raw = kotlinx.coroutines.runBlocking { capture.toImageBitmap() }.toPixelMap()
    for (x in 0 until raw.width) assertThat(raw[x, raw.height - 1].alpha).isCloseTo(1f, 8f / 255f)
    for (y in 0 until raw.height) assertThat(raw[raw.width - 1, y].alpha).isCloseTo(1f, 8f / 255f)
    val pixels = h.capture()
    assertThat(pixels.width).isEqualTo(413)
    assertThat(pixels.height).isEqualTo(384)
    composeTestRule.runOnIdle { h.color = Color.Red }
    h.awaitOutput()
    assertThat(h.delegate.captureLayer.layer).isSameInstanceAs(capture)
    assertThat(h.delegate.paddedLayer.layer).isSameInstanceAs(padded)
    assertThat(h.capture()[200, 180].red).isCloseTo(1f, 8f / 255f)
  }

  @Test
  fun supersededSuccessfulModeUpdate_presentsItsOwnGeometryUntilTheNextOutputCompletes() {
    val h = ContentHarness(425, 384, 4, striped = true)
    h.mode = HazePerformanceMode.Quality
    h.show()
    h.awaitOutput()
    val quality = h.capture()
    composeTestRule.runOnIdle { h.mode = HazePerformanceMode.Default }
    h.awaitOutput()
    var heldWrite: (() -> Unit)? = null
    var writes = 0
    val originalWrite = h.delegate.writeToSurface
    composeTestRule.runOnIdle {
      h.delegate.writeToSurface = { rs, layer, density ->
        writes++
        heldWrite = { originalWrite(rs, layer, density) }
      }
      h.mode = HazePerformanceMode.Quality
    }
    composeTestRule.waitUntil(5_000) { writes == 1 && heldWrite != null }
    composeTestRule.runOnIdle {
      h.mode = HazePerformanceMode.Default
    }
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle {
      checkNotNull(heldWrite).invoke()
      heldWrite = null
    }
    composeTestRule.waitUntil(5_000) { writes == 2 && heldWrite != null }
    // A successful old Quality frame is now presented while the newer Default frame is pending.
    assertImagesClose(h.capture(), quality, "superseded successful output")
    composeTestRule.runOnIdle {
      h.delegate.writeToSurface = originalWrite
      checkNotNull(heldWrite).invoke()
      heldWrite = null
    }
    h.awaitOutput()
    val recovered = h.capture()
    composeTestRule.runOnIdle { h.attachment++ }
    h.awaitOutput()
    assertImagesClose(h.capture(), recovered, "fresh after successful superseded output")
  }

  @Test
  fun failedSurfaceUpdate_preservesCompletedGeometryAndLaterRetries() {
    val h = ContentHarness(425, 384, 4, striped = true)
    h.show()
    h.awaitOutput()
    val completed = h.capture()
    val originalWrite = h.delegate.writeToSurface
    var attempts = 0
    composeTestRule.runOnIdle {
      h.delegate.writeToSurface = { _, _, _ ->
        attempts++
        throw IllegalStateException("test surface failure")
      }
      h.mode = HazePerformanceMode.Quality
    }
    composeTestRule.waitUntil(5_000) { attempts > 0 && h.delegate.currentJob?.isActive != true }
    assertImagesClose(h.capture(), completed, "failed update retains completed scale")
    composeTestRule.runOnIdle {
      h.delegate.writeToSurface = originalWrite
      h.color = Color.Red
    }
    h.awaitOutput()
    val recovered = h.capture()
    composeTestRule.runOnIdle { h.attachment++ }
    h.awaitOutput()
    assertImagesClose(h.capture(), recovered, "fresh after surface failure")
  }

  @Test
  fun pendingModeAndSizeChanges_presentCompletedGeometryThenRecoverAfterInvalidationAndTrim() {
    val h = ContentHarness(425, 384, 4, striped = true)
    h.show()
    h.awaitOutput()
    val before = h.capture()
    var heldWrite: (() -> Unit)? = null
    val originalWrite = h.delegate.writeToSurface
    composeTestRule.runOnIdle {
      h.delegate.writeToSurface = { rs, layer, density -> heldWrite = { originalWrite(rs, layer, density) } }
      h.mode = HazePerformanceMode.Quality
    }
    composeTestRule.waitUntil(5_000) { heldWrite != null && h.delegate.currentJob?.isActive == true }
    // The new capture has not reached the surface; retain the previous output/scale pair.
    assertImagesClose(h.capture(), before, "pending mode change")
    composeTestRule.runOnIdle {
      h.width = 426
      h.mode = HazePerformanceMode.Default
    }
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle {
      h.delegate.invalidateRetainedOutput()
      heldWrite = null // The cancelled context owns this queued write; never replay it.
      h.delegate.writeToSurface = originalWrite
      h.color = Color.Red
    }
    h.awaitOutput()
    val recovered = h.capture()
    composeTestRule.runOnIdle { h.attachment++ }
    h.awaitOutput()
    assertImagesClose(h.capture(), recovered, "fresh after cancelled update")

    val freshWrite = h.delegate.writeToSurface
    composeTestRule.runOnIdle {
      h.delegate.writeToSurface = { rs, layer, density -> heldWrite = { freshWrite(rs, layer, density) } }
      h.mode = HazePerformanceMode.Quality
    }
    composeTestRule.waitUntil(5_000) { heldWrite != null && h.delegate.currentJob?.isActive == true }
    composeTestRule.runOnIdle {
      h.probe.effect.onTrimMemory(TrimMemoryLevel.MODERATE)
      heldWrite = null
      h.delegate.writeToSurface = freshWrite
      h.mode = HazePerformanceMode.Default
    }
    h.awaitOutput()
    val trimmed = h.capture()
    composeTestRule.runOnIdle { h.attachment++ }
    h.awaitOutput()
    assertImagesClose(h.capture(), trimmed, "fresh after trim during update")
  }

  private fun assertImagesClose(actual: PixelMap, expected: PixelMap, label: String) {
    assertThat(actual.width).isEqualTo(expected.width)
    assertThat(actual.height).isEqualTo(expected.height)
    var worst = 0f
    for (y in 0 until actual.height) for (x in 0 until actual.width) {
      val a = actual[x, y]
      val e = expected[x, y]
      worst = maxOf(worst, kotlin.math.abs(a.red - e.red), kotlin.math.abs(a.green - e.green), kotlin.math.abs(a.blue - e.blue))
    }
    assertThat(worst, label).isLessThanOrEqualTo(8f / 255f)
  }

  private inner class ContentHarness(width: Int, val height: Int, val radius: Int, val striped: Boolean = false) {
    val probe = Probe()
    var width by mutableStateOf(width)
    var mode by mutableStateOf<HazePerformanceMode>(HazePerformanceMode.Default)
    var color by mutableStateOf(Color.White)
    var attachment by mutableStateOf(0)
    val delegate get() = probe.effect.delegate as RenderScriptBlurVisualEffectDelegate

    fun show() {
      assumeTrue("RenderScript backend test requires API 30", Build.VERSION.SDK_INT == 30)
      composeTestRule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
          Box(Modifier.size(500.dp).background(Color.Magenta)) {
            key(attachment) {
              Canvas(
                Modifier.size(width.dp, height.dp).testTag(CONTENT_TAG).hazeEffect(
                  factory = probe,
                  input = HazeInput.Content,
                  expandLayerBounds = false,
                  style = BlurConfiguration(
                    HazeBlurStyle {
                      blurEnabled(true)
                      blurRadius(radius.dp)
                      noiseFactor(0f)
                      backgroundColor(Color.Transparent)
                      colorEffects(emptyList())
                      fallbackColorEffect(null)
                    },
                    mode,
                  ),
                ),
              ) {
                drawRect(color)
                if (striped) {
                  for (x in 0 until size.width.toInt() step 8) {
                    drawRect(Color.Black, topLeft = Offset(x.toFloat(), 0f), size = Size(4f, size.height))
                  }
                }
              }
            }
          }
        }
      }
    }

    fun awaitOutput() {
      composeTestRule.waitForIdle()
      composeTestRule.waitUntil(5_000) {
        probe.effect.delegate is RenderScriptBlurVisualEffectDelegate &&
          delegate.canDrawRetainedOutput() && delegate.currentJob?.isActive != true
      }
      composeTestRule.waitForIdle()
    }

    fun capture() = composeTestRule.onNodeWithTag(CONTENT_TAG).captureToImage().toPixelMap()

    fun assertWhiteEdges() {
      awaitOutput()
      val pixels = capture()
      assertThat(pixels.width).isEqualTo(width)
      assertThat(pixels.height).isEqualTo(height)
      val coords = buildSet {
        for (x in 0 until width) {
          add(x to 0)
          add(x to height - 1)
        }
        for (y in 0 until height) {
          add(0 to y)
          add(width - 1 to y)
        }
      }
      for ((x, y) in coords) {
        val c = pixels[x, y]
        assertThat(c.red, "edge ($x,$y) red").isCloseTo(1f, 8f / 255f)
        assertThat(c.green, "edge ($x,$y) green").isCloseTo(1f, 8f / 255f)
        assertThat(c.blue, "edge ($x,$y) blue").isCloseTo(1f, 8f / 255f)
      }
    }
  }

  @Test
  fun sourceColorChanges_keepEveryLayerAndTheOutputFollowsTheSource() {
    val h = Harness(widthPx = 101, heightPx = 101)
    h.show()
    h.awaitCenter { it.blue > it.red }
    val capture = checkNotNull(h.delegate.captureLayer.layer)
    val padded = checkNotNull(h.delegate.paddedLayer.layer)
    val presentation = checkNotNull(h.delegate.presentationLayer.layer)

    listOf(Color.Red, Color.Green, Color.Blue).forEach { color ->
      h.source = color
      h.awaitCenter { it.isDominantlyColored(color) }
    }

    assertThat(h.delegate.captureLayer.layer).isNotNull().isSameInstanceAs(capture)
    assertThat(h.delegate.paddedLayer.layer).isNotNull().isSameInstanceAs(padded)
    assertThat(h.delegate.presentationLayer.layer).isNotNull().isSameInstanceAs(presentation)
    listOf(capture, padded, presentation).forEach { assertThat(it.isReleased).isFalse() }
  }

  @Test
  fun resize_replacesThePaddedLayerUntilNoPaddingIsNeeded() {
    val h = Harness(widthPx = 101, heightPx = 101)
    h.show()
    h.awaitCenter { it.blue > it.red }
    val capture = checkNotNull(h.delegate.captureLayer.layer)
    val padded = checkNotNull(h.delegate.paddedLayer.layer)
    val presentation = checkNotNull(h.delegate.presentationLayer.layer)

    // Another padded size: 109 pads to 112 where 101 padded to 104.
    h.widthPx = 109
    h.awaitLayerSize(109)
    h.awaitCenter { it.blue > it.red }
    val resizedPadded = checkNotNull(h.delegate.paddedLayer.layer)
    assertThat(resizedPadded).isNotSameInstanceAs(padded)
    assertThat(padded.isReleased).isTrue()
    assertThat(h.delegate.captureLayer.layer).isNotNull().isSameInstanceAs(capture)
    assertThat(h.delegate.presentationLayer.layer).isNotNull().isSameInstanceAs(presentation)

    // Multiples of 4 need no padding.
    h.widthPx = 100
    h.heightPx = 100
    h.awaitLayerSize(100)
    h.awaitCenter { it.blue > it.red }
    assertThat(h.delegate.paddedLayer.layer).isNull()
    assertThat(resizedPadded.isReleased).isTrue()
    assertThat(h.delegate.captureLayer.layer).isNotNull().isSameInstanceAs(capture)
    assertThat(h.delegate.presentationLayer.layer).isNotNull().isSameInstanceAs(presentation)
  }

  @Test
  fun trimModerate_releasesEveryLayerAndALaterFrameRecreatesThem() {
    val h = Harness(widthPx = 101, heightPx = 101)
    h.show()
    h.awaitCenter { it.blue > it.red }
    val layers = h.layers()

    composeTestRule.runOnIdle { h.probe.effect.onTrimMemory(TrimMemoryLevel.MODERATE) }

    layers.forEach { assertThat(it.isReleased).isTrue() }
    h.awaitCenter { it.blue > it.red }
    h.layers().zip(layers).forEach { (now, before) -> assertThat(now).isNotSameInstanceAs(before) }
  }

  @Test
  fun detach_releasesEveryLayerAndTheNextAttachmentRendersCorrectly() {
    val h = Harness(widthPx = 101, heightPx = 101)
    h.show()
    h.awaitCenter { it.blue > it.red }
    val layers = h.layers()

    h.attachment++
    composeTestRule.waitForIdle()

    layers.forEach { assertThat(it.isReleased).isTrue() }
    h.awaitCenter { it.blue > it.red }
  }

  private fun Color.isDominantlyColored(expected: Color) = when (expected) {
    Color.Red -> red > blue && red > green
    Color.Green -> green > red && green > blue
    else -> blue > red && blue > green
  }

  private inner class Harness(widthPx: Int, heightPx: Int) {
    val probe = Probe()
    val hazeState = HazeState()
    var source by mutableStateOf(Color.Blue)
    var widthPx by mutableStateOf(widthPx)
    var heightPx by mutableStateOf(heightPx)
    var attachment by mutableStateOf(0)

    val delegate: RenderScriptBlurVisualEffectDelegate
      get() = probe.effect.delegate as RenderScriptBlurVisualEffectDelegate

    fun layers(): List<GraphicsLayer> = listOf(
      checkNotNull(delegate.captureLayer.layer),
      checkNotNull(delegate.paddedLayer.layer),
      checkNotNull(delegate.presentationLayer.layer),
    )

    fun show() {
      assumeTrue("RenderScript backend test requires API 30", Build.VERSION.SDK_INT == 30)
      composeTestRule.setContent {
        val density = LocalDensity.current
        Box(Modifier.size(100.dp).hazeSource(hazeState).background(source))
        key(attachment) {
          Box(
            Modifier
              .testTag(TAG)
              .width(with(density) { this@Harness.widthPx.toDp() })
              .height(with(density) { this@Harness.heightPx.toDp() })
              .background(Color.Black)
              .hazeEffect(
                factory = probe,
                input = HazeInput.Sources(hazeState),
                style = BlurConfiguration(
                  HazeBlurStyle {
                    blurEnabled(true)
                    blurRadius(0.dp)
                    backgroundColor(Color.Transparent)
                    colorEffects(emptyList())
                    fallbackColorEffect(null)
                  },
                  HazePerformanceMode.Quality,
                ),
              ),
          )
        }
      }
      composeTestRule.waitForIdle()
    }

    fun awaitCenter(predicate: (Color) -> Boolean) {
      composeTestRule.waitUntil(timeoutMillis = 5_000L) {
        val pixels = composeTestRule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        predicate(pixels[pixels.width / 2, pixels.height / 2])
      }
    }

    /** Waits until the capture layer has been recorded at the resized width. */
    fun awaitLayerSize(width: Int) {
      composeTestRule.waitUntil(timeoutMillis = 5_000L) { delegate.captureLayer.layer?.size?.width == width }
    }
  }

  private class Probe : HazeEffectFactory<BlurConfiguration> {
    lateinit var effect: BlurVisualEffect
      private set

    override fun createRenderer(): HazeEffectRenderer<BlurConfiguration> =
      BlurVisualEffect().also { effect = it }
  }

  private companion object {
    const val TAG = "render_script_layer_reuse"
    const val CONTENT_TAG = "render_script_content_geometry"
  }
}
