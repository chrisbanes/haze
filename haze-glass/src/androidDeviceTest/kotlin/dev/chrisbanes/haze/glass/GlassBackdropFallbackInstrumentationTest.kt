// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeFeatureFlags
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 33)
@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
class GlassBackdropFallbackInstrumentationTest {

  private var previousPlatformBackdropEnabled = false

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setUp() {
    previousPlatformBackdropEnabled = HazeFeatureFlags.isPlatformBackdropEnabled
    HazeFeatureFlags.isPlatformBackdropEnabled = false
  }

  @After
  fun tearDown() {
    HazeFeatureFlags.isPlatformBackdropEnabled = previousPlatformBackdropEnabled
  }

  @Test
  fun unsupportedPlatform_usesSourcesFallback() {
    val fallbackState = HazeState()
    val effect = GlassRuntimeEffect()
    var effectBounds = Rect.Zero

    composeTestRule.setContent {
      Box(Modifier.fillMaxSize().background(Color.White)) {
        Box(
          Modifier
            .align(Alignment.Center)
            .size(width = 200.dp, height = 100.dp)
            .hazeSource(fallbackState)
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
            .onGloballyPositioned { effectBounds = it.boundsInWindow() }
            .hazeGlass(
              factory = HazeEffectFactory { effect },
              performanceMode = null,
              expandLayerBounds = true,
              interactionSource = null,
              input = HazeInput.Backdrop(fallbackState),
              style = GlassStyle.regular.then {
                optics(
                  GlassOptics(
                    refractionStrength = 0f,
                    refractionHeightFraction = 0f,
                    refractionDisplacement = 0.dp,
                    depth = OpticalSizeValue.Fixed(1f),
                    blurRadius = OpticalSizeValue.Fixed(14.dp),
                    refractionDetailIntensity = 0f,
                  ),
                )
                specularIntensity(0f)
                edgeShadow(Color.Transparent)
                ambientResponse(0f)
                backgroundColor(Color.Transparent)
                tint(Color.Transparent)
                edgeSoftness(0.dp)
                chromaticAberrationStrength(0f)
                contrast(0f)
                whitePoint(0f)
                chromaMultiplier(1f)
              },
            ),
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      presentFrame()
      composeTestRule.runOnIdle {
        val delegate = effect.delegate as RuntimeShaderGlassDelegate
        delegate.displayedImmutableInput != null && delegate.immutableInputOwnerCount == 1
      }
    }

    presentFrame()
    val pixels = copyEffect(effectBounds)
    try {
      val centerX = pixels.width / 2
      val centerY = pixels.height / 2
      val blackInterior = Color(pixels.getPixel(centerX - 60, centerY)).red
      val blackNearEdge = Color(pixels.getPixel(centerX - 3, centerY)).red
      val whiteNearEdge = Color(pixels.getPixel(centerX + 3, centerY)).red
      val whiteInterior = Color(pixels.getPixel(centerX + 60, centerY)).red

      assertThat(blackNearEdge - blackInterior, "Fallback softens the black side")
        .isGreaterThan(0.05f)
      assertThat(whiteInterior - whiteNearEdge, "Fallback softens the white side")
        .isGreaterThan(0.05f)
    } finally {
      pixels.recycle()
    }
  }

  private fun presentFrame() {
    val latch = CountDownLatch(1)
    composeTestRule.runOnUiThread {
      val view = composeTestRule.activity.window.decorView
      view.postOnAnimation {
        view.invalidate()
        view.postOnAnimation { latch.countDown() }
      }
    }
    assertThat(latch.await(5, TimeUnit.SECONDS), "A following presentation frame arrived").isTrue()
    composeTestRule.waitForIdle()
  }

  private fun copyEffect(bounds: Rect): Bitmap {
    val rect = android.graphics.Rect(
      bounds.left.roundToInt(),
      bounds.top.roundToInt(),
      bounds.right.roundToInt(),
      bounds.bottom.roundToInt(),
    )
    assertThat(rect.width()).isGreaterThan(0)
    assertThat(rect.height()).isGreaterThan(0)
    val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
    val result = AtomicInteger(-1)
    PixelCopy.request(composeTestRule.activity.window, rect, bitmap, { result.set(it) }, Handler(Looper.getMainLooper()))
    try {
      composeTestRule.waitUntil(timeoutMillis = 5_000) { result.get() != -1 }
      assertThat(result.get()).isEqualTo(PixelCopy.SUCCESS)
      return bitmap
    } catch (failure: Throwable) {
      bitmap.recycle()
      throw failure
    }
  }
}
