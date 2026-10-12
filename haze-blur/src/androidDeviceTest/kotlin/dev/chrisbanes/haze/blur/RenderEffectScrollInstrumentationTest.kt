// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test

class RenderEffectScrollInstrumentationTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  @SdkSuppress(minSdkVersion = 31)
  fun scrollingSource_refreshesRenderEffectBlur() {
    val hazeState = HazeState()
    val listState = LazyListState()
    val activity = composeTestRule.activity
    composeTestRule.setContent {
      Box(Modifier.fillMaxSize()) {
        // Every item stays composed and is only moved, as in a fling, so the source never redraws.
        LazyColumn(Modifier.fillMaxSize().hazeSource(hazeState), state = listState) {
          items(40) { index ->
            Box(
              Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(if (index % 2 == 0) Color.Blue else Color(0xFFFFF3E0)),
            )
          }
        }
        // Hide the source itself so the sampled colors must come from the blur output.
        Box(Modifier.fillMaxSize().background(Color.Black))
        Box(
          Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .height(32.dp)
            .hazeBlur(
              input = HazeInput.Sources(hazeState),
              style = HazeBlurStyle {
                blurRadius(2.dp)
                backgroundColor(Color.Transparent)
                colorEffects(emptyList())
                noiseFactor(0f)
              },
            ),
        )
      }
    }

    val initial = activity.awaitCenterPixel { true }
    val initialIsBlue = initial.isBlue()
    val itemPx = with(composeTestRule.density) { 200.dp.toPx() }

    composeTestRule.runOnUiThread { listState.dispatchRawDelta(itemPx) }
    composeTestRule.waitForIdle()
    val scrolled = activity.awaitCenterPixel { it.isBlue() != initialIsBlue }
    assertThat(scrolled.isBlue(), "blur after one item pixel=#${Integer.toHexString(scrolled)}")
      .isEqualTo(!initialIsBlue)

    composeTestRule.runOnUiThread { listState.dispatchRawDelta(-itemPx) }
    composeTestRule.waitForIdle()
    assertThat(listState.firstVisibleItemScrollOffset, "source back at the top").isEqualTo(0)
    val returned = activity.awaitCenterPixel { it.isBlue() == initialIsBlue }
    assertThat(returned.isBlue(), "blur after scrolling back pixel=#${Integer.toHexString(returned)}")
      .isEqualTo(initialIsBlue)
  }

  private fun Int.isBlue(): Boolean = AndroidColor.blue(this) > 180 && AndroidColor.red(this) < 80

  private fun ComponentActivity.awaitCenterPixel(predicate: (Int) -> Boolean): Int {
    var pixel = 0
    repeat(40) {
      val screenshot = copyWindow()
      pixel = screenshot.getPixel(screenshot.width / 2, screenshot.height / 2)
      screenshot.recycle()
      if (predicate(pixel)) return pixel
      CountDownLatch(1).await(100, TimeUnit.MILLISECONDS)
    }
    return pixel
  }

  private fun ComponentActivity.copyWindow(): Bitmap {
    val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    val latch = CountDownLatch(1)
    var result = PixelCopy.ERROR_UNKNOWN
    PixelCopy.request(window, bitmap, { copyResult ->
      result = copyResult
      latch.countDown()
    }, Handler(Looper.getMainLooper()))
    assertThat(latch.await(5, TimeUnit.SECONDS), "Window PixelCopy completed").isTrue()
    assertThat(result, "Window PixelCopy result").isEqualTo(PixelCopy.SUCCESS)
    return bitmap
  }
}
