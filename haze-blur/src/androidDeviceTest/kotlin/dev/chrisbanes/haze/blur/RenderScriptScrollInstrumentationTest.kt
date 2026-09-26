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
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test

class RenderScriptScrollInstrumentationTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  @SdkSuppress(minSdkVersion = 26, maxSdkVersion = 30)
  fun scrollingSource_refreshesRenderScriptBlur() {
    val hazeState = HazeState()
    val listState = LazyListState()
    val activity = composeTestRule.activity
    composeTestRule.setContent {
      Box(Modifier.fillMaxSize()) {
        LazyColumn(
          Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .testTag("scroll_source"),
          state = listState,
        ) {
          items(20) { index ->
            Box(
              Modifier
                .fillMaxWidth()
                .height(800.dp)
                .background(if (index == 0) Color.Blue else Color(0xFFFFF3E0)),
            )
          }
        }
        // Hide the source itself so the sampled colors must come from the blur output.
        Box(Modifier.fillMaxSize().background(Color.Black))
        Box(
          Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .height(64.dp)
            .hazeBlur(
              input = HazeInput.Sources(hazeState),
              style = HazeBlurStyle {
                blurEnabled(true)
                blurRadius(2.dp)
                backgroundColor(Color.Transparent)
                colorEffects(emptyList())
                fallbackColorEffect(HazeColorEffect.tint(Color.Green))
                noiseFactor(0f)
              },
            ),
        )
      }
    }

    val initialPixel = activity.awaitCenterPixel {
      AndroidColor.blue(it) > 180 && AndroidColor.red(it) < 80
    }
    assertThat(AndroidColor.blue(initialPixel), "initial blue blur")
      .isGreaterThan(180)
    assertThat(AndroidColor.red(initialPixel), "initial blue blur")
      .isLessThan(80)

    composeTestRule.onNodeWithTag("scroll_source").performScrollToIndex(1)
    composeTestRule.waitForIdle()
    assertThat(listState.firstVisibleItemIndex, "source after scroll").isEqualTo(1)

    val scrolledPixel = activity.awaitCenterPixel {
      AndroidColor.red(it) > 220 && AndroidColor.green(it) > 200
    }
    assertThat(AndroidColor.red(scrolledPixel), "blur after scroll pixel=#${Integer.toHexString(scrolledPixel)}")
      .isGreaterThan(220)
    assertThat(AndroidColor.green(scrolledPixel), "blur after scroll")
      .isGreaterThan(200)

    composeTestRule.onNodeWithTag("scroll_source").performScrollToIndex(0)
    composeTestRule.waitForIdle()
    assertThat(listState.firstVisibleItemIndex, "source after scrolling back").isEqualTo(0)
    val returnedPixel = activity.awaitCenterPixel {
      AndroidColor.blue(it) > 180 && AndroidColor.red(it) < 80
    }
    assertThat(AndroidColor.blue(returnedPixel), "blur after scrolling back")
      .isGreaterThan(180)
    assertThat(AndroidColor.red(returnedPixel), "blur after scrolling back")
      .isLessThan(80)
  }

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
    assertThat(latch.await(5, TimeUnit.SECONDS), "Window PixelCopy completed").isTrue()
    assertThat(result, "Window PixelCopy result").isEqualTo(PixelCopy.SUCCESS)
    return bitmap
  }
}
