// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalHazeApi::class)
class GlassBackdropInstrumentationTest {

  private lateinit var activityScenario: ActivityScenario<ComponentActivity>
  private lateinit var activity: ComponentActivity

  @Before
  fun setUp() {
    activityScenario = ActivityScenario.launch(ComponentActivity::class.java)
    activityScenario.onActivity { activity = it }
  }

  @After
  fun tearDown() {
    activityScenario.close()
  }

  @Test
  fun backdropInput_rendersGlassFromWindowPixelsWithoutFallbackSources() {
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
              .hazeGlass(
                input = HazeInput.Backdrop(HazeInput.Sources(emptyFallbackState)),
                style = pureBlurGlassStyle(14.dp),
              )
              .drawWithContent {
                drawContent()
                drawReady.countDown()
              },
          )
        }
      }
    }
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "Glass presented its first draw")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    val bitmap = activity.copyWindow()
    val density = Density(activity.resources.displayMetrics.density)
    val centerX = bitmap.width / 2
    val centerY = bitmap.height / 2
    val blackInterior = bitmap.red(centerX - 60.dp.roundToPx(density), centerY)
    val blackNearEdge = bitmap.red(centerX - 3.dp.roundToPx(density), centerY)
    val whiteNearEdge = bitmap.red(centerX + 3.dp.roundToPx(density), centerY)
    val whiteInterior = bitmap.red(centerX + 60.dp.roundToPx(density), centerY)
    assertThat(blackNearEdge - blackInterior, "Glass softens the black side")
      .isGreaterThan(0.05f)
    assertThat(whiteInterior - whiteNearEdge, "Glass softens the white side")
      .isGreaterThan(0.05f)
  }

  @Test
  fun backdropInput_rebuildsFusedRootAfterStyleChange() {
    assumeTrue(
      "HazeInput.Backdrop requires Android 37.2 or the matching preview",
      isBackdropSdkSupported(),
    )
    val emptyFallbackState = HazeState()
    val blurRadius = mutableStateOf(0.dp)
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
              .hazeGlass(
                input = HazeInput.Backdrop(HazeInput.Sources(emptyFallbackState)),
                style = pureBlurGlassStyle(blurRadius.value),
              )
              .drawWithContent {
                drawContent()
                if (blurRadius.value == 0.dp) initialDraw.countDown() else updatedDraw.countDown()
              },
          )
        }
      }
    }
    assertThat(initialDraw.await(5, TimeUnit.SECONDS), "Identity Glass presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    activity.copyWindow().assertCenterEdgeSharp(
      Density(activity.resources.displayMetrics.density),
    )

    blurRadius.value = 14.dp
    assertThat(updatedDraw.await(5, TimeUnit.SECONDS), "Updated Glass presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    activity.copyWindow().assertCenterEdgeSoftened(
      Density(activity.resources.displayMetrics.density),
    )
  }

  @Test
  fun backdropInput_nineSiblingsRenderIndependently() {
    assumeTrue(
      "HazeInput.Backdrop requires Android 37.2 or the matching preview",
      isBackdropSdkSupported(),
    )
    val emptyFallbackState = HazeState()
    val drawReady = CountDownLatch(9)

    activityScenario.onActivity { activity ->
      activity.setContent {
        Box(Modifier.fillMaxSize().background(Color.Gray)) {
          Column(Modifier.align(Alignment.Center)) {
            repeat(3) {
              Row {
                repeat(3) {
                  Box(Modifier.size(width = 72.dp, height = 48.dp)) {
                    Row {
                      Box(Modifier.size(width = 36.dp, height = 48.dp).background(Color.Black))
                      Box(Modifier.size(width = 36.dp, height = 48.dp).background(Color.White))
                    }
                    Box(
                      Modifier
                        .fillMaxSize()
                        .hazeGlass(
                          input = HazeInput.Backdrop(HazeInput.Sources(emptyFallbackState)),
                          style = pureBlurGlassStyle(10.dp),
                        )
                        .drawWithContent {
                          drawContent()
                          drawReady.countDown()
                        },
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "Nine Glass nodes presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    val bitmap = activity.copyWindow()
    val density = Density(activity.resources.displayMetrics.density)
    val gridLeft = bitmap.width / 2 - 108.dp.roundToPx(density)
    val gridTop = bitmap.height / 2 - 72.dp.roundToPx(density)
    repeat(3) { row ->
      repeat(3) { column ->
        val centerX = gridLeft + (column * 72 + 36).dp.roundToPx(density)
        val centerY = gridTop + (row * 48 + 24).dp.roundToPx(density)
        bitmap.assertEdgeSoftened(
          centerX = centerX,
          centerY = centerY,
          density = density,
          label = "Glass[$row,$column]",
          interiorDistance = 14.dp,
          edgeDistance = 2.dp,
        )
      }
    }
  }

  @Test
  fun backdropInput_pressedMaterialAndContentTransformScalesChildContent() {
    assumeTrue(
      "HazeInput.Backdrop requires Android 37.2 or the matching preview",
      isBackdropSdkSupported(),
    )
    val emptyFallbackState = HazeState()
    val interactionSource = MutableInteractionSource()
    val pressedDrawRequested = AtomicBoolean(false)
    val initialDraw = CountDownLatch(1)
    val pressedDraw = CountDownLatch(1)

    activityScenario.onActivity { activity ->
      activity.setContent {
        Box(Modifier.fillMaxSize().background(Color.Blue)) {
          Box(
            Modifier
              .align(Alignment.Center)
              .size(width = 200.dp, height = 100.dp)
              .hazeGlass(
                input = HazeInput.Backdrop(HazeInput.Sources(emptyFallbackState)),
                style = GlassStyle.regular.then {
                  pressed {
                    lightingIntensity(1f)
                    refractionMultiplier(1.5f)
                    whitePointDelta(0.3f)
                    scale(0.6f)
                  }
                },
                interactionSource = interactionSource,
                interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
                interactionTransformPivot = GlassTransformPivot.Center,
                interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
              )
              .drawWithContent {
                drawContent()
                if (pressedDrawRequested.get()) pressedDraw.countDown() else initialDraw.countDown()
              },
          ) {
            Box(Modifier.fillMaxSize().background(Color.Red))
          }
        }
      }
    }
    assertThat(initialDraw.await(5, TimeUnit.SECONDS), "Unpressed Glass presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    val density = Density(activity.resources.displayMetrics.density)
    val sampleX = activity.window.decorView.width / 2 + 80.dp.roundToPx(density)
    val sampleY = activity.window.decorView.height / 2
    assertThat(activity.copyWindow().red(sampleX, sampleY), "Unpressed child fills its bounds")
      .isGreaterThan(0.8f)

    pressedDrawRequested.set(true)
    assertThat(
      interactionSource.tryEmit(PressInteraction.Press(Offset.Unspecified)),
      "Press interaction was accepted",
    ).isEqualTo(true)
    assertThat(pressedDraw.await(5, TimeUnit.SECONDS), "Pressed Glass presented")
      .isEqualTo(true)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    assertThat(
      activity.copyWindow().red(sampleX, sampleY),
      "Pressed material-and-content transform exposes the backdrop at the original edge",
    ).isLessThan(0.5f)
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
    fun pureBlurGlassStyle(blurRadius: androidx.compose.ui.unit.Dp) = GlassStyle.regular.then {
      optics(
        GlassOptics(
          refractionStrength = 0f,
          refractionHeightFraction = 0f,
          refractionDisplacement = 0.dp,
          depth = OpticalSizeValue.Fixed(1f),
          blurRadius = OpticalSizeValue.Fixed(blurRadius),
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
    }

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
  assertEdgeSoftened(
    centerX = width / 2,
    centerY = height / 2,
    density = density,
    label = "Glass",
    interiorDistance = 60.dp,
    edgeDistance = 3.dp,
  )
}

private fun Bitmap.assertEdgeSoftened(
  centerX: Int,
  centerY: Int,
  density: Density,
  label: String,
  interiorDistance: androidx.compose.ui.unit.Dp,
  edgeDistance: androidx.compose.ui.unit.Dp,
) {
  val blackInterior = red(centerX - interiorDistance.roundToPx(density), centerY)
  val blackNearEdge = red(centerX - edgeDistance.roundToPx(density), centerY)
  val whiteNearEdge = red(centerX + edgeDistance.roundToPx(density), centerY)
  val whiteInterior = red(centerX + interiorDistance.roundToPx(density), centerY)
  assertThat(blackNearEdge - blackInterior, "$label softens the black side")
    .isGreaterThan(0.05f)
  assertThat(whiteInterior - whiteNearEdge, "$label softens the white side")
    .isGreaterThan(0.05f)
}

private fun Bitmap.assertCenterEdgeSharp(density: Density) {
  val centerX = width / 2
  val centerY = height / 2
  val blackInterior = red(centerX - 60.dp.roundToPx(density), centerY)
  val blackNearEdge = red(centerX - 3.dp.roundToPx(density), centerY)
  val whiteNearEdge = red(centerX + 3.dp.roundToPx(density), centerY)
  val whiteInterior = red(centerX + 60.dp.roundToPx(density), centerY)
  assertThat(blackNearEdge - blackInterior, "Identity keeps the black side sharp")
    .isLessThan(0.02f)
  assertThat(whiteInterior - whiteNearEdge, "Identity keeps the white side sharp")
    .isLessThan(0.02f)
}

private fun androidx.compose.ui.unit.Dp.roundToPx(density: Density): Int =
  (value * density.density).roundToInt()
