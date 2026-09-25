// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeInput
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test

class RenderScriptFirstFrameInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  @SdkSuppress(minSdkVersion = 26, maxSdkVersion = 30)
  fun staticContent_displaysFirstRenderScriptOutputWithoutAnotherDraw() {
    val activity = composeTestRule.activity
    // Set content without the rule's idle synchronization so PixelCopy can observe
    // whether the asynchronous output causes its own presentation draw.
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      activity.setContent {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          Box(
            Modifier
              .align(Alignment.Center)
              .size(100.dp)
              .hazeBlur(
                input = HazeInput.Content,
                style = HazeBlurStyle {
                  blurEnabled(true)
                  blurRadius(2.dp)
                  backgroundColor(Color.Transparent)
                  colorEffects(emptyList())
                  // A scrim fallback would cover the content in green, so blue/red
                  // confirms that the RenderScript output was actually presented.
                  fallbackColorEffect(HazeColorEffect.tint(Color.Green))
                  noiseFactor(0f)
                },
              )
              .background(Color.Blue),
          )
        }
      }
    }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    fun awaitCenterPixel(predicate: (Int) -> Boolean): Int {
      var pixel = 0
      repeat(40) {
        // Read the presented frame without triggering another Compose draw.
        val screenshot = activity.copyWindow()
        pixel = screenshot.getPixel(screenshot.width / 2, screenshot.height / 2)
        screenshot.recycle()
        if (predicate(pixel)) return pixel
        CountDownLatch(1).await(100, TimeUnit.MILLISECONDS)
      }
      return pixel
    }

    val firstPixel = awaitCenterPixel {
      AndroidColor.blue(it) > 200 && AndroidColor.red(it) < 50
    }
    assertThat(AndroidColor.blue(firstPixel), "first async blur output, pixel=#${Integer.toHexString(firstPixel)}")
      .isGreaterThan(200)
    assertThat(AndroidColor.red(firstPixel), "first async blur output")
      .isLessThan(50)
  }

  private fun ComponentActivity.copyWindow(): Bitmap {
    val bitmap = Bitmap.createBitmap(
      window.decorView.width,
      window.decorView.height,
      Bitmap.Config.ARGB_8888,
    )
    val latch = CountDownLatch(1)
    var result = PixelCopy.ERROR_UNKNOWN
    PixelCopy.request(window, bitmap, { copyResult ->
      result = copyResult
      latch.countDown()
    }, Handler(Looper.getMainLooper()))
    assertThat(latch.await(5, TimeUnit.SECONDS), "Window PixelCopy completed").isEqualTo(true)
    assertThat(result, "Window PixelCopy result").isEqualTo(PixelCopy.SUCCESS)
    return bitmap
  }
}
