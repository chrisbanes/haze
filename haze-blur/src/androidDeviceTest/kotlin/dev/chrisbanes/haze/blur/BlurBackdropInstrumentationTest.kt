// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeFeatureFlags
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalHazeApi::class)
class BlurBackdropInstrumentationTest {

  private lateinit var activityScenario: ActivityScenario<ComponentActivity>
  private lateinit var activity: ComponentActivity
  private var previousPlatformBackdropEnabled = false

  @Before
  fun setUp() {
    previousPlatformBackdropEnabled = HazeFeatureFlags.isPlatformBackdropEnabled
    HazeFeatureFlags.isPlatformBackdropEnabled = true
    activityScenario = ActivityScenario.launch(ComponentActivity::class.java)
    activityScenario.onActivity { activity = it }
  }

  @After
  fun tearDown() {
    try {
      activityScenario.close()
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = previousPlatformBackdropEnabled
    }
  }

  @Test
  fun backdropInput_blursWindowPixelsWithoutFallbackSources() {
    assumeTrue(
      "HazeInput.Backdrop requires Android 37.2 or the matching preview",
      isBackdropSdkSupported(),
    )
    val emptyFallbackState = HazeState()
    val drawReady = CountDownLatch(1)

    activityScenario.onActivity { activity ->
      activity.setContent {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          Box(
            Modifier
              .align(Alignment.Center)
              .size(width = 200.dp, height = 100.dp)
              .background(Color.Black),
          ) {
            Box(
              Modifier
                .align(Alignment.CenterEnd)
                .size(width = 100.dp, height = 100.dp)
                .background(Color.White),
            )
          }
          Box(
            Modifier
              .align(Alignment.Center)
              .size(width = 200.dp, height = 100.dp)
              .hazeBlur(
                input = HazeInput.Backdrop(emptyFallbackState),
                style = HazeBlurStyle {
                  blurRadius(14.dp)
                  noiseFactor(0f)
                  colorEffects(emptyList())
                },
              )
              .drawWithContent {
                drawContent()
                drawReady.countDown()
              },
          )
        }
      }
    }
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "Blur presented its first draw")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    val bitmap = activity.copyWindow()
    val density = Density(activity.resources.displayMetrics.density)
    bitmap.assertCenterEdgeSoftened(density)
  }

  @Test
  fun backdropInput_rebuildsRootEffectAfterStyleChange() {
    assumeTrue(
      "HazeInput.Backdrop requires Android 37.2 or the matching preview",
      isBackdropSdkSupported(),
    )
    val emptyFallbackState = HazeState()
    val radius = mutableStateOf(0.dp)
    val initialDraw = CountDownLatch(1)
    val updatedDraw = CountDownLatch(1)

    activityScenario.onActivity { activity ->
      activity.setContent {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          Box(
            Modifier
              .align(Alignment.Center)
              .size(width = 200.dp, height = 100.dp)
              .background(Color.Black),
          ) {
            Box(
              Modifier
                .align(Alignment.CenterEnd)
                .size(width = 100.dp, height = 100.dp)
                .background(Color.White),
            )
          }
          Box(
            Modifier
              .align(Alignment.Center)
              .size(width = 200.dp, height = 100.dp)
              .hazeBlur(
                input = HazeInput.Backdrop(emptyFallbackState),
                style = HazeBlurStyle {
                  blurRadius(radius.value)
                  noiseFactor(0f)
                  colorEffects(emptyList())
                },
              )
              .drawWithContent {
                drawContent()
                if (radius.value == 0.dp) initialDraw.countDown() else updatedDraw.countDown()
              },
          )
        }
      }
    }
    assertThat(initialDraw.await(5, TimeUnit.SECONDS), "Identity effect presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    activity.copyWindow().assertCenterEdgeSharp(
      Density(activity.resources.displayMetrics.density),
    )

    radius.value = 14.dp
    assertThat(updatedDraw.await(5, TimeUnit.SECONDS), "Updated blur effect presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    activity.copyWindow().assertCenterEdgeSoftened(
      Density(activity.resources.displayMetrics.density),
    )
  }

  @Test
  fun backdropInput_preservesProgressiveBlur() {
    assumeTrue(
      "HazeInput.Backdrop requires Android 37.2 or the matching preview",
      isBackdropSdkSupported(),
    )
    val emptyFallbackState = HazeState()
    val drawReady = CountDownLatch(1)

    activityScenario.onActivity { activity ->
      activity.setContent {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          Box(
            Modifier
              .align(Alignment.Center)
              .size(width = 200.dp, height = 100.dp)
              .background(Color.Black),
          ) {
            Box(
              Modifier
                .align(Alignment.CenterEnd)
                .size(width = 100.dp, height = 100.dp)
                .background(Color.White),
            )
          }
          Box(
            Modifier
              .align(Alignment.Center)
              .size(width = 200.dp, height = 100.dp)
              .hazeBlur(
                input = HazeInput.Backdrop(emptyFallbackState),
                style = HazeBlurStyle {
                  blurRadius(14.dp)
                  noiseFactor(0f)
                  colorEffects(emptyList())
                  progressive(
                    HazeProgressive.verticalGradient(
                      startIntensity = 1f,
                      endIntensity = 0f,
                    ),
                  )
                },
              )
              .drawWithContent {
                drawContent()
                drawReady.countDown()
              },
          )
        }
      }
    }
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "Progressive Blur presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    val bitmap = activity.copyWindow()
    val density = Density(activity.resources.displayMetrics.density)
    val topY = bitmap.height / 2 - 30.dp.roundToPx(density)
    val bottomY = bitmap.height / 2 + 48.dp.roundToPx(density)
    bitmap.assertEdgeSoftened(density, topY)
    assertThat(
      bitmap.blackEdgeSoftening(density, topY) -
        bitmap.blackEdgeSoftening(density, bottomY),
      "Progressive Blur is materially stronger at the authored start",
    ).isGreaterThan(0.08f)
  }

  @Test
  fun backdropInput_preservesTintAndAlpha() {
    assumeTrue(
      "HazeInput.Backdrop requires Android 37.2 or the matching preview",
      isBackdropSdkSupported(),
    )
    val emptyFallbackState = HazeState()
    val drawReady = CountDownLatch(1)

    activityScenario.onActivity { activity ->
      activity.setContent {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          Box(
            Modifier
              .align(Alignment.Center)
              .size(100.dp)
              .background(Color.Black),
          )
          Box(
            Modifier
              .align(Alignment.Center)
              .size(100.dp)
              .hazeBlur(
                input = HazeInput.Backdrop(emptyFallbackState),
                style = HazeBlurStyle {
                  blurRadius(0.dp)
                  noiseFactor(0f)
                  colorEffects(listOf(HazeColorEffect.tint(Color.Red)))
                  alpha(0.5f)
                },
              )
              .drawWithContent {
                drawContent()
                drawReady.countDown()
              },
          )
        }
      }
    }
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "Tinted Blur presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    val pixel = activity.copyWindow().let { bitmap ->
      bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    }
    assertThat(AndroidColor.red(pixel) / 255f, "Tint is grouped by effect alpha")
      .isGreaterThan(0.4f)
    assertThat(AndroidColor.red(pixel) / 255f, "Tint is grouped by effect alpha")
      .isLessThan(0.6f)
    assertThat(AndroidColor.green(pixel) / 255f, "Tint preserves the black backdrop")
      .isLessThan(0.05f)
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

  private companion object {
    fun isBackdropSdkSupported(): Boolean {
      val fullSdkInt = if (Build.VERSION.SDK_INT < 36) {
        Build.VERSION.SDK_INT * 100_000
      } else {
        Build.VERSION.SDK_INT_FULL
      }
      return fullSdkInt >= Build.VERSION_CODES_FULL.CINNAMON_BUN_2 ||
        (
          fullSdkInt == Build.VERSION_CODES_FULL.CINNAMON_BUN_1 &&
            Build.VERSION.PREVIEW_SDK_INT == 3_723
          )
    }
  }
}

private fun Bitmap.red(x: Int, y: Int): Float {
  val pixel = getPixel(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))
  return AndroidColor.red(pixel) / 255f
}

private fun Bitmap.assertCenterEdgeSoftened(density: Density) {
  assertEdgeSoftened(density, height / 2)
}

private fun Bitmap.assertEdgeSoftened(density: Density, y: Int) {
  val centerX = width / 2
  val whiteNearEdge = red(centerX + 3.dp.roundToPx(density), y)
  val whiteInterior = red(centerX + 60.dp.roundToPx(density), y)

  assertThat(blackEdgeSoftening(density, y), "Blur softens the black side")
    .isGreaterThan(0.05f)
  assertThat(whiteInterior - whiteNearEdge, "Blur softens the white side")
    .isGreaterThan(0.05f)
}

private fun Bitmap.blackEdgeSoftening(density: Density, y: Int): Float {
  val centerX = width / 2
  val blackInterior = red(centerX - 60.dp.roundToPx(density), y)
  val blackNearEdge = red(centerX - 3.dp.roundToPx(density), y)
  return blackNearEdge - blackInterior
}

private fun Bitmap.assertCenterEdgeSharp(density: Density) {
  assertEdgeSharp(density, height / 2)
}

private fun Bitmap.assertEdgeSharp(density: Density, y: Int) {
  val centerX = width / 2
  val blackInterior = red(centerX - 60.dp.roundToPx(density), y)
  val blackNearEdge = red(centerX - 3.dp.roundToPx(density), y)
  val whiteNearEdge = red(centerX + 3.dp.roundToPx(density), y)
  val whiteInterior = red(centerX + 60.dp.roundToPx(density), y)

  assertThat(blackNearEdge - blackInterior, "Identity keeps the black side sharp")
    .isLessThan(0.02f)
  assertThat(whiteInterior - whiteNearEdge, "Identity keeps the white side sharp")
    .isLessThan(0.02f)
}

private fun androidx.compose.ui.unit.Dp.roundToPx(density: Density): Int =
  kotlin.math.round(value * density.density).toInt()
