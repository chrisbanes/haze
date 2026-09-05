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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
import dev.chrisbanes.haze.hazeSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
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
  fun backdropInput_clearsClipWhenChangedToUnbounded() {
    assumeTrue(
      "HazeInput.Backdrop requires Android 37.2 or the matching preview",
      isBackdropSdkSupported(),
    )
    val fallbackState = HazeState()
    val unboundedStyle = mutableStateOf(false)
    val effectSize = mutableStateOf(100.dp)
    var drawReady = CountDownLatch(1)

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
              .size(effectSize.value)
              .hazeBlur(
                input = HazeInput.Backdrop(fallbackState),
                style = HazeBlurStyle {
                  blurRadius(14.dp)
                  noiseFactor(0f)
                  colorEffects(emptyList())
                  blurredEdgeTreatment(
                    if (unboundedStyle.value) {
                      BlurredEdgeTreatment.Unbounded
                    } else {
                      BlurredEdgeTreatment.Rectangle
                    },
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
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "Bounded Blur presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    val density = Density(activity.resources.displayMetrics.density)
    val bounded = activity.copyWindow()
    fun sampleOutsideTop(bitmap: Bitmap, size: Dp): Float {
      val centerX = bitmap.width / 2
      val centerY = bitmap.height / 2
      val outsideTop = centerY - size.roundToPx(density) / 2 - 3.dp.roundToPx(density)
      return bitmap.red(centerX, outsideTop)
    }

    drawReady = CountDownLatch(1)
    unboundedStyle.value = true
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "Unbounded Blur presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    val unboundedFrame = activity.copyWindow()
    assertThat(
      abs(
        sampleOutsideTop(unboundedFrame, 100.dp) - sampleOutsideTop(bounded, 100.dp),
      ),
      "Removing the clip changes the expanded edge",
    ).isGreaterThan(0.02f)

    drawReady = CountDownLatch(1)
    effectSize.value = 120.dp
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "Resized unbounded Blur presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    val resized = activity.copyWindow()
    assertThat(
      abs(
        sampleOutsideTop(unboundedFrame, 100.dp) - sampleOutsideTop(resized, 120.dp),
      ),
      "Unbounded resize updates the expanded edge",
    ).isGreaterThan(0.01f)

    drawReady = CountDownLatch(1)
    unboundedStyle.value = false
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "Restored bounded Blur presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    val restored = activity.copyWindow()

    fun captureFreshControl(unbounded: Boolean, size: Dp, generation: Int): Bitmap {
      val freshDraw = CountDownLatch(1)
      activityScenario.onActivity { controlledActivity ->
        controlledActivity.setContent {
          ClipTransitionScene(
            unbounded = unbounded,
            effectSize = size,
            drawReady = freshDraw,
            generation = generation,
          )
        }
      }
      assertThat(freshDraw.await(5, TimeUnit.SECONDS), "Fresh clip control presented")
        .isEqualTo(true)
      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      return activity.copyWindow()
    }
    val freshUnbounded100 = captureFreshControl(true, 100.dp, generation = 1)
    val freshUnbounded120 = captureFreshControl(true, 120.dp, generation = 2)
    val freshBounded120 = captureFreshControl(false, 120.dp, generation = 3)
    assertThat(
      abs(sampleOutsideTop(unboundedFrame, 100.dp) - sampleOutsideTop(freshUnbounded100, 100.dp)),
      "Unbounded 100dp transition matches a fresh attachment",
    ).isLessThan(0.03f)
    assertThat(
      abs(sampleOutsideTop(resized, 120.dp) - sampleOutsideTop(freshUnbounded120, 120.dp)),
      "Unbounded 120dp transition matches a fresh attachment",
    ).isLessThan(0.03f)
    assertThat(
      abs(sampleOutsideTop(restored, 120.dp) - sampleOutsideTop(freshBounded120, 120.dp)),
      "Bounded 120dp transition matches a fresh attachment",
    ).isLessThan(0.03f)
    assertThat(
      abs(
        sampleOutsideTop(freshUnbounded120, 120.dp) -
          sampleOutsideTop(freshBounded120, 120.dp),
      ),
      "Fresh clipped and unclipped 120dp controls differ outside the effect bounds",
    ).isGreaterThan(0.02f)
    unboundedFrame.recycle()
    resized.recycle()
    restored.recycle()
    bounded.recycle()
    freshUnbounded100.recycle()
    freshUnbounded120.recycle()
    freshBounded120.recycle()
  }

  @Test
  fun backdropInput_offscreenAncestorsPreserveInput() {
    assumeTrue(
      "HazeInput.Backdrop requires Android 37.2 or the matching preview",
      isBackdropSdkSupported(),
    )
    AncestorMode.entries.forEach { mode ->
      val native = renderOffscreenScene(mode, nativeEnabled = true)
      val fallback = renderOffscreenScene(mode, nativeEnabled = false)
      val density = Density(activity.resources.displayMetrics.density)
      native.assertCenterEdgeSoftened(density)
      fallback.assertCenterEdgeSoftened(density)
      val sampleX = native.width / 2 + 3.dp.roundToPx(density)
      val sampleY = native.height / 2
      assertThat(
        abs(native.red(sampleX, sampleY) - fallback.red(sampleX, sampleY)),
        "${mode.name} native/fallback edge agreement",
      ).isLessThan(0.12f)
      native.recycle()
      fallback.recycle()
    }
  }

  private fun renderOffscreenScene(
    mode: AncestorMode,
    nativeEnabled: Boolean,
  ): Bitmap {
    HazeFeatureFlags.isPlatformBackdropEnabled = nativeEnabled
    val captureState = HazeState()
    val ancestorState = HazeState()
    val drawReady = CountDownLatch(1)
    activityScenario.onActivity { activity ->
      activity.setContent {
        key(mode to nativeEnabled) {
          Box(Modifier.fillMaxSize().background(Color.White)) {
            Box(
              Modifier
                .align(Alignment.Center)
                .size(width = 200.dp, height = 100.dp)
                .hazeSource(captureState)
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
                .then(
                  if (mode == AncestorMode.EnclosingCapture) {
                    Modifier.hazeSource(ancestorState)
                  } else {
                    Modifier
                  },
                ),
            ) {
              Box(
                Modifier
                  .fillMaxSize()
                  .then(
                    when (mode) {
                      AncestorMode.Normal -> Modifier
                      AncestorMode.Alpha -> Modifier.graphicsLayer { alpha = 0.5f }
                      AncestorMode.Offscreen -> Modifier.graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                      }
                      AncestorMode.EnclosingCapture -> Modifier
                    },
                  )
                  .hazeBlur(
                    input = HazeInput.Backdrop(captureState),
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
            if (mode == AncestorMode.EnclosingCapture) {
              Box(
                Modifier
                  .size(1.dp)
                  .background(Color.Transparent)
                  .hazeBlur(
                    input = HazeInput.Sources(ancestorState),
                    style = HazeBlurStyle { blurRadius(0.dp) },
                  ),
              )
            }
          }
        }
      }
    }
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "${mode.name} scene presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    return activity.copyWindow()
  }

  private enum class AncestorMode {
    Normal,
    Alpha,
    Offscreen,
    EnclosingCapture,
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

@Composable
private fun ClipTransitionScene(
  unbounded: Boolean,
  effectSize: Dp,
  drawReady: CountDownLatch,
  generation: Int,
) {
  key(generation) {
    val fallbackState = dev.chrisbanes.haze.rememberHazeState()
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
          .size(effectSize)
          .hazeBlur(
            input = HazeInput.Backdrop(fallbackState),
            style = HazeBlurStyle {
              blurRadius(14.dp)
              noiseFactor(0f)
              colorEffects(emptyList())
              blurredEdgeTreatment(
                if (unbounded) BlurredEdgeTreatment.Unbounded else BlurredEdgeTreatment.Rectangle,
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
