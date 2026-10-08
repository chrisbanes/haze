// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.hazeSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Real-window controls for transitive material freshness and finite presentation work. */
@SdkSuppress(minSdkVersion = 33)
@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
class GlassNestedSourceSnapshotTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var delegates = emptyList<RuntimeShaderGlassDelegate>()

  @After fun closeConsumers() {
    compose.activityRule.scenario.close()
    compose.waitUntil(5_000) {
      var released = false
      InstrumentationRegistry.getInstrumentation().runOnMainSync {
        released = delegates.all { it.immutableInputOwnerCount == 0 }
      }
      released
    }
    delegates.forEach { assertThat(it.immutableInputOwnerCount).isEqualTo(0) }
  }

  @SdkSuppress(minSdkVersion = 34)
  @Test
  fun intrinsicSourceBlur_primaryAndPlatformCapturePreserveBlur() {
    val state = HazeState()
    val phase = mutableStateOf(0)
    val effect = GlassRuntimeEffect()
    compose.setContent {
      Box(Modifier.size(160.dp)) {
        Box(Modifier.fillMaxSize().hazeSource(state)) {
          Canvas(Modifier.fillMaxSize().blur(16.dp)) {
            val offset = phase.value
            val cell = size.width / 16f
            repeat(16) { y ->
              repeat(16) { x ->
                drawRect(
                  color = if ((x + y + offset) % 2 == 0) Color.Black else Color.White,
                  topLeft = Offset(x * cell, y * cell),
                  size = Size(cell, cell),
                )
              }
            }
          }
        }
        Box(
          Modifier.fillMaxSize().hazeGlass(
            factory = HazeEffectFactory { effect },
            input = HazeInput.Sources(state),
            style = GlassStyle { tint(Color.Transparent) },
            performanceMode = HazePerformanceMode.Quality,
            expandLayerBounds = true,
            interactionSource = null,
          ),
        ) {}
      }
    }
    attachDelegates(listOf(effect))
    settle()
    val delegate = delegates.single()
    fun checkBlur(route: String) {
      val raw = checkNotNull(checkNotNull(delegate.displayedImmutableInput).asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false))
      try {
        val reds = buildList {
          for (y in raw.height / 4 until raw.height * 3 / 4 step maxOf(1, raw.height / 48)) {
            for (x in raw.width / 4 until raw.width * 3 / 4 step maxOf(1, raw.width / 48)) {
              add(AndroidColor.red(raw.getPixel(x, y)) / 255f)
            }
          }
        }
        assertThat(delegate.immutableInputOwnerCount).isEqualTo(1)
        assertThat(reds.min(), "$route input blur minimum").isGreaterThan(.2f)
        assertThat(reds.max(), "$route input blur maximum").isLessThan(.8f)
      } finally {
        raw.recycle()
      }
    }
    checkBlur("default")
    for (hardware in listOf(true, false)) {
      val previousCount = delegate.immutableInputCaptureCount
      val previousImage = delegate.displayedImmutableInput
      var returned: ImageBitmap? = null
      compose.runOnIdle {
        delegate.captureImmutableInput = { layer ->
          (if (hardware) layer.captureHardwareBufferSnapshot() else layer.toImageBitmap())
            .also { returned = it }
        }
        phase.value = 1 - phase.value
        Snapshot.sendApplyNotifications()
      }
      settle()
      assertThat(delegate.immutableInputCaptureCount).isGreaterThan(previousCount)
      assertThat(delegate.displayedImmutableInput).isNotSameInstanceAs(previousImage)
      assertThat(delegate.displayedImmutableInput).isSameInstanceAs(checkNotNull(returned))
      checkBlur(if (hardware) "hardware" else "platform")
    }
  }

  @Test fun stackedCards_presentLowerMaterialAutomaticallyAndStopCapturing() = checkStackedCards(false)

  @Test fun stackedCards_platformCapturePreservesLowerMaterial() = checkStackedCards(true)

  private fun checkStackedCards(platformCapture: Boolean) {
    val label = mutableStateOf("Bank of Haze")
    val state = HazeState()
    val effects = List(3) { GlassRuntimeEffect() }
    val bounds = MutableList(3) { Rect.Zero }
    val style = GlassStyle {
      tint(Color.White.copy(alpha = .1f))
      optics(refractionStrength = .45f)
    }
    compose.setContent {
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(state).background(Brush.linearGradient(listOf(Color.Blue, Color.Cyan))))
        repeat(3) { index ->
          val reverseIndex = 2 - index
          Box(
            Modifier.align(Alignment.Center).fillMaxWidth(.7f - reverseIndex * .05f)
              .aspectRatio(16 / 9f).offset { IntOffset(0, reverseIndex * -100) }
              .onGloballyPositioned { bounds[index] = it.boundsInWindow() }
              .hazeSource(state, zIndex = 1f + index).clip(RoundedCornerShape(16.dp))
              .hazeGlass(
                factory = HazeEffectFactory { effects[index] },
                input = HazeInput.Sources(state),
                style = style,
                performanceMode = HazePerformanceMode.Default,
                expandLayerBounds = true,
                interactionSource = null,
              ),
          ) { BasicText(label.value, Modifier.padding(32.dp)) }
        }
      }
    }
    attachDelegates(effects)
    settle()
    if (platformCapture) {
      val previous = counts()
      val previousImages = compose.runOnIdle { delegates.map { it.displayedImmutableInput } }
      val returned = MutableList<ImageBitmap?>(delegates.size) { null }
      compose.runOnIdle {
        delegates.forEachIndexed { index, delegate ->
          delegate.captureImmutableInput = { layer -> layer.toImageBitmap().also { returned[index] = it } }
        }
        label.value = "Bank of Haze!"
        Snapshot.sendApplyNotifications()
      }
      settle()
      delegates.forEachIndexed { index, delegate ->
        assertThat(delegate.immutableInputCaptureCount).isGreaterThan(previous[index])
        assertThat(delegate.displayedImmutableInput).isNotSameInstanceAs(previousImages[index])
        assertThat(delegate.displayedImmutableInput).isSameInstanceAs(checkNotNull(returned[index]))
        assertThat(delegate.displayedImmutableInput?.asAndroidBitmap()?.config).isEqualTo(Bitmap.Config.HARDWARE)
      }
    }
    val window = copyWindow()
    try {
      // Identical pre-immutable baseline: front centre red=.859; stale candidate red=.596.
      assertThat(AndroidColor.red(window.getPixel(bounds[2].center.x.toInt(), bounds[2].center.y.toInt())) / 255f)
        .isGreaterThan(.8f)
      for (index in 1..2) {
        val hardware = checkNotNull(delegates[index].displayedImmutableInput).asAndroidBitmap()
        val raw = checkNotNull(hardware.copy(Bitmap.Config.ARGB_8888, false))
        try {
          // Gradient alone has zero red; lower Glass material must be in the copied input.
          assertThat(AndroidColor.red(raw.getPixel(raw.width / 2, raw.height / 2)) / 255f).isGreaterThan(.5f)
        } finally {
          raw.recycle()
        }
      }
    } finally {
      window.recycle()
    }
    assertIdle()
  }

  @Test fun twoNodeCycle_withOutsideConsumer_stopsAndRefreshesAfterExternalChange() = checkCycle(2)

  @Test fun threeNodeCycle_withOutsideConsumer_stopsAndRefreshesAfterExternalChange() = checkCycle(3)

  @Test fun twoNodeCycle_withoutClip_stopsAndRefreshesAfterExternalChange() = checkCycle(2, clipped = false)

  @Test fun twoNodeCycle_withAncestorSources_stopsAndRefreshesAfterExternalChange() =
    checkCycle(2, clipped = false, ancestorSources = true)

  @Test fun reusedModifier_hasIndependentCapturesAndReleasesEveryConsumer() {
    val state = HazeState()
    val effects = mutableListOf<GlassRuntimeEffect>()
    val visible = mutableStateOf(true)
    val shared = Modifier.hazeGlass(
      factory = HazeEffectFactory { GlassRuntimeEffect().also { effects += it } },
      input = HazeInput.Sources(state),
      style = GlassStyle.regular,
      performanceMode = HazePerformanceMode.Quality,
      expandLayerBounds = true,
      interactionSource = null,
    )
    compose.setContent {
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Blue))
        if (visible.value) {
          repeat(2) { index ->
            Box(
              Modifier.align(Alignment.Center).size(140.dp).offset(x = (index * 20).dp)
                .hazeSource(state, zIndex = 1f + index).then(shared),
            )
          }
        }
      }
    }
    attachDelegates(effects)
    assertThat(delegates.size).isEqualTo(2)
    settle()
    assertIdle()
    compose.runOnIdle { visible.value = false }
    compose.waitUntil(5_000) { compose.runOnIdle { delegates.all { it.immutableInputOwnerCount == 0 } } }
    compose.runOnIdle { visible.value = true }
    compose.waitForIdle()
    delegates = compose.runOnIdle { effects.takeLast(2).map { it.delegate as RuntimeShaderGlassDelegate } }
    settle()
    assertIdle()
  }

  private fun checkCycle(length: Int, clipped: Boolean = true, ancestorSources: Boolean = false) {
    val state = HazeState()
    val background = mutableStateOf(Color.Blue)
    val cyclic = mutableStateOf(true)
    val visible = mutableStateOf(true)
    val effects = List(length + 1) { GlassRuntimeEffect() }
    compose.setContent {
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(state, key = "background").background(background.value))
        if (visible.value) {
          repeat(length) { index ->
            val selection = HazeSourceSelection.All.where { area ->
              area.key != index && (cyclic.value || area.key == "background" || (area.key as? Int)?.let { it < index } == true)
            }
            val placement = Modifier.align(Alignment.Center).size(140.dp).offset(x = (index * 20).dp)
            val source = Modifier.hazeSource(state, zIndex = 1f + index, key = index)
            val material = Modifier
              .let { if (clipped) it.clip(RoundedCornerShape(16.dp)) else it }
              .hazeGlass(
                factory = HazeEffectFactory { effects[index] },
                input = HazeInput.Sources(state, selection = selection),
                style = GlassStyle.regular,
                performanceMode = HazePerformanceMode.Quality,
                expandLayerBounds = true,
                interactionSource = null,
              )
            if (ancestorSources) {
              Box(placement.then(source)) { Box(Modifier.fillMaxSize().then(material)) }
            } else {
              Box(placement.then(source).then(material))
            }
          }
        }
        Box(
          Modifier.align(Alignment.BottomCenter).size(140.dp).hazeGlass(
            factory = HazeEffectFactory { effects.last() },
            input = HazeInput.Sources(state, selection = HazeSourceSelection.All),
            style = GlassStyle.regular,
            performanceMode = HazePerformanceMode.Quality,
            expandLayerBounds = true,
            interactionSource = null,
          ),
        )
      }
    }
    attachDelegates(effects)
    settle()
    assertIdle()
    val before = counts()
    compose.runOnIdle { background.value = Color.Green }
    settle()
    compose.runOnIdle { delegates.indices.forEach { assertThat(delegates[it].immutableInputCaptureCount).isGreaterThan(before[it]) } }
    val displayed = compose.runOnIdle { checkNotNull(delegates.last().displayedImmutableInput).asAndroidBitmap() }
    val copied = checkNotNull(displayed.copy(Bitmap.Config.ARGB_8888, false))
    try {
      assertThat(copied.getPixel(copied.width / 2, copied.height / 2)).isEqualTo(AndroidColor.GREEN)
    } finally {
      copied.recycle()
    }
    assertIdle()
    compose.runOnIdle { cyclic.value = false }
    settle()
    assertIdle()
    compose.runOnIdle { cyclic.value = true }
    settle()
    assertIdle()
    compose.runOnIdle { visible.value = false }
    compose.waitForIdle()
    compose.waitUntil(5_000) { compose.runOnIdle { delegates.dropLast(1).all { it.immutableInputOwnerCount == 0 } } }
    delegates = listOf(delegates.last())
    settle()
    assertIdle()
  }

  private fun attachDelegates(effects: List<GlassRuntimeEffect>) {
    compose.waitForIdle()
    delegates = compose.runOnIdle { effects.map { it.delegate as RuntimeShaderGlassDelegate } }
  }

  private fun counts() = compose.runOnIdle { delegates.map { it.immutableInputCaptureCount } }

  private fun settle() {
    var previous = emptyList<Int>()
    var quietFrames = 0
    compose.waitUntil(10_000) {
      presentFrame()
      val current = counts()
      quietFrames = if (current == previous) quietFrames + 1 else 0
      previous = current
      compose.runOnIdle { delegates.all { it.displayedImmutableInput != null && it.immutableInputOwnerCount == 1 } } && quietFrames >= 3
    }
  }

  private fun assertIdle() {
    val before = counts()
    repeat(10) { presentFrame() }
    assertThat(counts()).isEqualTo(before)
  }

  private fun presentFrame() {
    compose.mainClock.advanceTimeByFrame()
    val latch = CountDownLatch(1)
    compose.runOnUiThread {
      val view = compose.activity.window.decorView
      view.postOnAnimation {
        view.invalidate()
        view.postOnAnimation { latch.countDown() }
      }
    }
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    compose.waitForIdle()
  }

  private fun copyWindow(): Bitmap {
    val window = compose.activity.window
    val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    val latch = CountDownLatch(1)
    var result = PixelCopy.ERROR_UNKNOWN
    PixelCopy.request(window, bitmap, {
      result = it
      latch.countDown()
    }, Handler(Looper.getMainLooper()))
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
      assertThat(result).isEqualTo(PixelCopy.SUCCESS)
      return bitmap
    } catch (failure: Throwable) {
      bitmap.recycle()
      throw failure
    }
  }
}
