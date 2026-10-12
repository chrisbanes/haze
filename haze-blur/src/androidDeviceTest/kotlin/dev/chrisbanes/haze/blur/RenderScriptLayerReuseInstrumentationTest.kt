// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isFalse
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
  }
}
