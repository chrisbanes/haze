// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)

package dev.chrisbanes.haze

import android.os.Build
import android.util.Log
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Window PixelCopy checks for input sharing on Android's hardware renderer.
 *
 * The fixture is a 40 dp source at (30, 30) in a black 100 dp root. It draws blue in its bounds and
 * 30 dp of green, yellow, red and magenta beyond its left, top, right and bottom edges. The right
 * half of the root is covered and sampled by a passthrough effect, so the left, top and bottom
 * probes see the source on screen and the effect probes see only the effect's input.
 */
class HazeInputSharingInstrumentationTest {

  private lateinit var activityScenario: ActivityScenario<ComponentActivity>
  private lateinit var activity: ComponentActivity

  private val state = HazeState()
  private val inputSharing = mutableStateOf(HazeInputSharing.Always)
  private val outsets = mutableStateOf(LayerOutsets.Zero)
  private val consumerCount = mutableIntStateOf(1)
  private var rootInWindow = Offset.Unspecified

  @Before
  fun setUp() {
    assumeTrue("Source capture pixels are checked on API 31+", Build.VERSION.SDK_INT >= 31)
    activityScenario = ActivityScenario.launch(ComponentActivity::class.java)
    activityScenario.onActivity { activity ->
      this.activity = activity
      activity.setContent {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
          Box(
            Modifier
              .offset(16.dp, 96.dp)
              .size(100.dp)
              .onGloballyPositioned { rootInWindow = it.positionInWindow() },
          ) {
            Box(
              Modifier
                .offset(30.dp, 30.dp)
                .size(40.dp)
                .hazeSource(state, inputSharing = inputSharing.value, inputSharingOutsets = outsets.value)
                .drawBehind {
                  val overflow = 30.dp.toPx()
                  drawRect(Color.Blue)
                  drawRect(Color.Green, Offset(-overflow, 0f), Size(overflow, size.height))
                  drawRect(Color.Yellow, Offset(0f, -overflow), Size(size.width, overflow))
                  drawRect(Color.Red, Offset(size.width, 0f), Size(overflow, size.height))
                  drawRect(Color.Magenta, Offset(0f, size.height), Size(size.width, overflow))
                },
            )
            Box(Modifier.offset(x = 50.dp).size(50.dp, 100.dp).background(Color.Black))
            if (consumerCount.intValue >= 1) {
              Box(
                Modifier
                  .offset(x = 50.dp)
                  .size(50.dp, 100.dp)
                  .hazeEffect(factory = PassthroughFactory, input = HazeInput.Sources(state), style = Unit),
              )
            }
            if (consumerCount.intValue >= 2) {
              // A second consumer, away from every probe.
              Box(
                Modifier
                  .size(1.dp)
                  .hazeEffect(factory = PassthroughFactory, input = HazeInput.Sources(state), style = Unit),
              )
            }
          }
        }
      }
    }
  }

  @After
  fun tearDown() {
    if (::activityScenario.isInitialized) activityScenario.close()
  }

  @Test
  fun inputSharingAlways_clipsWindowAndEffectToAsymmetricOutsets() {
    awaitFrames()
    assertLayer(CompositingStrategy.Offscreen)
    assertProbes("always-zero-outsets", ZERO_OUTSET_PROBES)

    update { outsets.value = ASYMMETRIC_OUTSETS }
    assertLayer(CompositingStrategy.Offscreen)
    assertProbes("always-asymmetric-outsets", ASYMMETRIC_OUTSET_PROBES)

    update { inputSharing.value = HazeInputSharing.Never }
    assertLayer(CompositingStrategy.Auto)
    assertProbes("never-asymmetric-outsets", UNCLIPPED_PROBES)

    update { inputSharing.value = HazeInputSharing.Always }
    assertLayer(CompositingStrategy.Offscreen)
    assertProbes("always-asymmetric-outsets-again", ASYMMETRIC_OUTSET_PROBES)
  }

  @Test
  fun inputSharingAuto_followsConsumerCountThroughOneTwoOneZero() {
    update {
      inputSharing.value = HazeInputSharing.Auto
      outsets.value = ASYMMETRIC_OUTSETS
    }
    assertLayer(CompositingStrategy.Auto)
    assertProbes("auto-1", UNCLIPPED_PROBES)

    update { consumerCount.intValue = 2 }
    assertLayer(CompositingStrategy.Offscreen)
    assertProbes("auto-2", ASYMMETRIC_OUTSET_PROBES)

    update { consumerCount.intValue = 1 }
    assertLayer(CompositingStrategy.Auto)
    assertProbes("auto-1-again", UNCLIPPED_PROBES)

    update { consumerCount.intValue = 0 }
    activityScenario.onActivity {
      val area = state.areas.single()
      assertThat(area.contentLayer, "released content layer").isNull()
      assertThat(area.inputBounds, "released input bounds").isNull()
    }
    // Without an effect, the cover hides the right half and the source draws directly.
    assertProbes("auto-0", UNCLIPPED_PROBES.mapValues { (probe, color) -> if (probe.x > 50) Color.Black else color })
  }

  private fun update(block: () -> Unit) {
    activityScenario.onActivity { block() }
    awaitFrames()
  }

  private fun awaitFrames(count: Int = 5) {
    repeat(count) {
      val latch = CountDownLatch(1)
      activityScenario.onActivity { Choreographer.getInstance().postFrameCallback { latch.countDown() } }
      assertThat(latch.await(5, TimeUnit.SECONDS), "frame callback").isTrue()
    }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
  }

  private fun assertLayer(expected: CompositingStrategy) {
    activityScenario.onActivity {
      val layer = requireNotNull(state.areas.single().contentLayer) { "content layer" }
      assertThat(layer.compositingStrategy, "content layer compositing").isEqualTo(expected)
    }
  }

  private fun assertProbes(phase: String, expected: Map<Probe, Color>) {
    val density = activity.resources.displayMetrics.density
    val bitmap = activity.copyWindow()
    val actual = expected.keys.associateWith { probe ->
      bitmap.pixel(
        (rootInWindow.x + probe.x * density).roundToInt(),
        (rootInWindow.y + probe.y * density).roundToInt(),
      )
    }
    for ((probe, color) in expected) {
      val found = actual.getValue(probe)
      Log.i(TAG, "$phase ${probe.name} (${probe.x}dp, ${probe.y}dp) expected=${color.hex()} actual=${found.hex()}")
    }
    val mismatches = expected
      .filter { (probe, color) -> !actual.getValue(probe).matches(color) }
      .map { (probe, color) -> "${probe.name}: expected ${color.hex()}, was ${actual.getValue(probe).hex()}" }
    assertThat(mismatches, "$phase mismatched probes").isEmpty()
  }

  private data class Probe(val name: String, val x: Int, val y: Int)

  private companion object {
    const val TAG = "HazeInputSharingTest"

    val ASYMMETRIC_OUTSETS = LayerOutsets(left = 4.dp, top = 8.dp, right = 12.dp, bottom = 16.dp)

    val LEFT_BODY = Probe("leftBody", 32, 50)
    val LEFT_INSIDE = Probe("leftInsideOutset", 28, 50)
    val LEFT_BEYOND = Probe("leftBeyondOutset", 24, 50)
    val TOP_BODY = Probe("topBody", 40, 32)
    val TOP_INSIDE = Probe("topInsideOutset", 40, 24)
    val TOP_BEYOND = Probe("topBeyondOutset", 40, 20)
    val BOTTOM_BODY = Probe("bottomBody", 40, 68)
    val BOTTOM_INSIDE = Probe("bottomInsideOutset", 40, 84)
    val BOTTOM_BEYOND = Probe("bottomBeyondOutset", 40, 88)
    val EFFECT_BODY = Probe("effectBody", 68, 50)
    val EFFECT_RIGHT_INSIDE = Probe("effectRightInsideOutset", 80, 50)
    val EFFECT_RIGHT_BEYOND = Probe("effectRightBeyondOutset", 84, 50)
    val EFFECT_TOP_INSIDE = Probe("effectTopInsideOutset", 60, 24)
    val EFFECT_TOP_BEYOND = Probe("effectTopBeyondOutset", 60, 20)
    val EFFECT_BOTTOM_INSIDE = Probe("effectBottomInsideOutset", 60, 84)
    val EFFECT_BOTTOM_BEYOND = Probe("effectBottomBeyondOutset", 60, 88)

    val UNCLIPPED_PROBES = mapOf(
      LEFT_BODY to Color.Blue,
      LEFT_INSIDE to Color.Green,
      LEFT_BEYOND to Color.Green,
      TOP_BODY to Color.Blue,
      TOP_INSIDE to Color.Yellow,
      TOP_BEYOND to Color.Yellow,
      BOTTOM_BODY to Color.Blue,
      BOTTOM_INSIDE to Color.Magenta,
      BOTTOM_BEYOND to Color.Magenta,
      EFFECT_BODY to Color.Blue,
      EFFECT_RIGHT_INSIDE to Color.Red,
      EFFECT_RIGHT_BEYOND to Color.Red,
      EFFECT_TOP_INSIDE to Color.Yellow,
      EFFECT_TOP_BEYOND to Color.Yellow,
      EFFECT_BOTTOM_INSIDE to Color.Magenta,
      EFFECT_BOTTOM_BEYOND to Color.Magenta,
    )

    val ASYMMETRIC_OUTSET_PROBES = UNCLIPPED_PROBES +
      listOf(LEFT_BEYOND, TOP_BEYOND, BOTTOM_BEYOND, EFFECT_RIGHT_BEYOND, EFFECT_TOP_BEYOND, EFFECT_BOTTOM_BEYOND)
        .associateWith { Color.Black }

    val ZERO_OUTSET_PROBES = ASYMMETRIC_OUTSET_PROBES +
      listOf(LEFT_INSIDE, TOP_INSIDE, BOTTOM_INSIDE, EFFECT_RIGHT_INSIDE, EFFECT_TOP_INSIDE, EFFECT_BOTTOM_INSIDE)
        .associateWith { Color.Black }
  }
}

private object PassthroughFactory : HazeEffectFactory<Unit> {
  override fun createRenderer(): HazeEffectRenderer<Unit> = object : HazeEffectRenderer<Unit> {
    override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()
  }
}

private fun Color.matches(other: Color): Boolean =
  abs(red - other.red) < 0.05f && abs(green - other.green) < 0.05f && abs(blue - other.blue) < 0.05f

private fun Color.hex(): String =
  "#%02X%02X%02X".format((red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt())
