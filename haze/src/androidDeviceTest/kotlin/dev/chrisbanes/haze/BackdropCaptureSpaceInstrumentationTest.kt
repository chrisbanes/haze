// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the coordinate space in which HWUI runs a backdrop RuntimeShader on a RenderNode drawn under
 * a canvas scale, as Haze does for a pressed standalone Glass surface on native Backdrop.
 */
class BackdropCaptureSpaceInstrumentationTest {

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
  fun scaledBackdropNode_samplesInUnscaledLocalSpace() {
    assumeTrue(
      "Backdrop RenderNode requires Android 37.2 or the matching preview",
      isHazeBackdropSdkSupported(fullSdkInt(), Build.VERSION.PREVIEW_SDK_INT),
    )
    // Output point p maps to local q = pivot + (p - pivot) / s. Local space samples local q + d,
    // which shows the source pixel at p + s * d. Device space would show p + d instead.
    for (scale in listOf(1f, 0.5f)) {
      val origin = renderScene(scale)
      val bitmap = activity.copyWindow()
      val y = (origin.y + NODE_TOP + NODE_SIZE / 2).roundToInt()
      // Probe inside the scaled node, either side of the pivot.
      for (offset in listOf(-20f, 20f)) {
        val p = NODE_LEFT + NODE_SIZE / 2 + offset
        val x = (origin.x + p).roundToInt()
        val sampled = bitmap.pixel(x, y).red * GRADIENT_WIDTH
        assertThat(sampled, "scale=$scale sample at p=$p")
          .isBetween(p + scale * DX - TOLERANCE, p + scale * DX + TOLERANCE)
      }
      bitmap.recycle()
    }
  }

  private fun renderScene(scale: Float): Offset {
    var origin = Offset.Zero
    val drawReady = CountDownLatch(1)
    activityScenario.onActivity { activity ->
      activity.setContent {
        val renderer = remember(scale) { checkNotNull(createHazeBackdropRenderer()) }
        DisposableEffect(renderer) { onDispose { renderer.release() } }
        val effect = remember {
          val shader = RuntimeShader(DISPLACE_SKSL).apply { setFloatUniform("d", DX, 0f) }
          RenderEffect.createRuntimeShaderEffect(shader, "content")
        }
        Box(
          Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .drawWithContent {
              // One red level per pixel across the gradient, so red encodes the source x.
              drawRect(
                brush = Brush.horizontalGradient(
                  colors = listOf(Color.Black, Color.Red),
                  startX = 0f,
                  endX = GRADIENT_WIDTH,
                ),
                size = Size(GRADIENT_WIDTH, size.height),
              )
              translate(NODE_LEFT, NODE_TOP) {
                scale(scale, pivot = Offset(NODE_SIZE / 2, NODE_SIZE / 2)) {
                  check(renderer.isSupported(drawContext.canvas))
                  check(
                    renderer.configure(
                      bounds = Rect(0f, 0f, NODE_SIZE, NODE_SIZE),
                      clip = null,
                      effect = effect,
                    ),
                  )
                  check(renderer.draw(drawContext.canvas))
                }
              }
              drawReady.countDown()
            },
        )
      }
    }
    assertThat(drawReady.await(5, TimeUnit.SECONDS), "scale=$scale presented").isTrue()
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    return origin
  }

  private companion object {
    const val GRADIENT_WIDTH = 255f
    const val NODE_LEFT = 40f
    const val NODE_TOP = 100f
    const val NODE_SIZE = 200f
    const val DX = 40f
    const val TOLERANCE = 3f

    val DISPLACE_SKSL = """
      uniform shader content;
      uniform float2 d;
      half4 main(float2 coord) { return content.eval(coord + d); }
    """.trimIndent()

    fun fullSdkInt(): Int {
      if (Build.VERSION.SDK_INT < 36) return Build.VERSION.SDK_INT * 100_000
      return Build.VERSION.SDK_INT_FULL
    }
  }
}
