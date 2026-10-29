// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.test.ScreenshotTest
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class SiblingComposeViewAndroidRegressionTest : ScreenshotTest() {
  private var previousScene: FrameLayout? = null

  @Test
  fun canvasSource_siblingRoots_matchSingleRootPixels() = assertAlignment(nativeSource = false)

  @Test
  fun androidViewSource_siblingRoots_matchSingleRootPixels() = assertAlignment(nativeSource = true)

  private fun assertAlignment(nativeSource: Boolean) {
    val sharp = capture(splitRoots = false, blur = false, nativeSource = nativeSource)
    val single = capture(splitRoots = false, blur = true, nativeSource = nativeSource)
    val sibling = capture(splitRoots = true, blur = true, nativeSource = nativeSource)
    val blurDistance = distance(sharp, single)
    val alignmentDistance = distance(single, sibling)
    val mixed = single.count { color -> red(color) in 65..225 && blue(color) in 65..225 }

    println("Sibling ComposeViews: nativeSource=$nativeSource, blurDistance=$blurDistance, mixed=$mixed, alignmentDistance=$alignmentDistance")
    assertThat(blurDistance, "source is actually blurred").isGreaterThan(500)
    assertThat(mixed, "blur mixes red and blue").isGreaterThan(5)
    assertThat(alignmentDistance, "sibling roots sample the same screen region").isLessThan(200)
  }

  private fun capture(splitRoots: Boolean, blur: Boolean, nativeSource: Boolean): List<Int> {
    val state = HazeState()
    val activity = composeTestRule.activity
    val density = activity.resources.displayMetrics.density
    fun px(dp: Int) = (dp * density).roundToInt()
    lateinit var scene: FrameLayout

    composeTestRule.runOnUiThread {
      previousScene?.let { old ->
        for (index in 0 until old.childCount) {
          (old.getChildAt(index) as? ComposeView)?.disposeComposition()
        }
      }
      scene = FrameLayout(activity)
      previousScene = scene
      scene.addView(
        ComposeView(activity).apply {
          setContent {
            Box(Modifier.fillMaxSize()) {
              Box(Modifier.fillMaxSize().hazeSource(state)) {
                if (nativeSource) {
                  AndroidView(factory = { ColorBandsView(it) }, modifier = Modifier.fillMaxSize())
                } else {
                  Canvas(Modifier.fillMaxSize()) {
                    val band = 40.dp.toPx()
                    for (index in 0..(size.width / band).toInt()) {
                      drawRect(
                        color = if (index % 2 == 0) Color(0xffff3030) else Color(0xff3030ff),
                        topLeft = Offset(index * band, 0f),
                        size = Size(band, size.height),
                      )
                    }
                  }
                }
              }
              if (!splitRoots && blur) Effect(state, Modifier.offset(65.dp, 120.dp))
            }
          }
        },
        FrameLayout.LayoutParams(-1, -1),
      )
      if (splitRoots) {
        scene.addView(
          ComposeView(activity).apply { setContent { Effect(state, Modifier) } },
          FrameLayout.LayoutParams(px(200), px(120)).apply {
            leftMargin = px(65)
            topMargin = px(120)
          },
        )
      }
      activity.setContentView(scene)
    }
    composeTestRule.waitForIdle()
    repeat(3) {
      composeTestRule.mainClock.advanceTimeByFrame()
      composeTestRule.runOnIdle { activity.window.decorView.invalidate() }
      composeTestRule.waitForIdle()
    }

    lateinit var bitmap: Bitmap
    val origin = IntArray(2)
    var result = -1
    composeTestRule.runOnIdle {
      val window = activity.window
      scene.getLocationInWindow(origin)
      bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
      PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
    }
    composeTestRule.waitForIdle()
    assertThat(result, "window PixelCopy succeeds").isEqualTo(PixelCopy.SUCCESS)
    return try {
      (90..230 step 5).map { x -> bitmap.getPixel(origin[0] + px(x), origin[1] + px(180)) }
    } finally {
      bitmap.recycle()
    }
  }

  @Composable
  private fun Effect(state: HazeState, modifier: Modifier) {
    Box(
      modifier.size(200.dp, 120.dp).clip(RoundedCornerShape(20.dp))
        .hazeBlur(
          HazeInput.Sources(state),
          HazeBlurStyle {
            blurRadius(20.dp)
            noiseFactor(0f)
          },
        ),
    )
  }

  private fun distance(a: List<Int>, b: List<Int>): Int = a.zip(b).sumOf { (first, second) ->
    abs(red(first) - red(second)) +
      abs(android.graphics.Color.green(first) - android.graphics.Color.green(second)) +
      abs(blue(first) - blue(second))
  }

  private fun red(color: Int) = android.graphics.Color.red(color)
  private fun blue(color: Int) = android.graphics.Color.blue(color)
}

private class ColorBandsView(context: Context) : View(context) {
  private val paint = Paint()

  override fun onDraw(canvas: android.graphics.Canvas) {
    super.onDraw(canvas)
    val band = 40 * resources.displayMetrics.density
    for (index in 0..(width / band).toInt()) {
      paint.color = if (index % 2 == 0) 0xffff3030.toInt() else 0xff3030ff.toInt()
      canvas.drawRect(index * band, 0f, (index + 1) * band, height.toFloat(), paint)
    }
  }
}
