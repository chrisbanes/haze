// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRendererBackdrop
import dev.chrisbanes.haze.HazeEffectRendererDrawHooks
import dev.chrisbanes.haze.HazeEffectRendererInteraction
import dev.chrisbanes.haze.HazeEffectRendererLifecycle
import dev.chrisbanes.haze.HazeEffectRendererRetainedOutput
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.hazeSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Real Android immutable-input material through pressed response removal and hidden completion. */
@SdkSuppress(minSdkVersion = 33)
@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
class GlassInteractionExitSnapshotTest {
  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()
  private val fixtures = mutableListOf<Fixture>()
  private val consumers = mutableListOf<Pair<String, GraphicsLayer>>()

  @After fun closeConsumers() {
    val delegates = compose.runOnIdle {
      fixtures.flatMap { listOf(it.delegate, it.keeperObserver.effect.delegate as RuntimeShaderGlassDelegate) }.also { all ->
        all.forEach { delegate ->
          delegate.layers.source?.let { consumers += "current-source" to it }
          delegate.layers.optical?.let { consumers += "current-optical" to it }
        }
      }
    }
    compose.activityRule.scenario.close()
    delegates.forEach { assertThat(it.immutableInputOwnerCount).isEqualTo(0) }
    consumers.forEach { (role, layer) ->
      assertThat(layer.isReleased, "$role is released after activity close").isTrue()
    }
  }

  private enum class Channel { WhitePoint, Refraction, Lighting }

  @Test fun whitePointExit_selectedSourceMissingRetainsOpaqueMaterial() =
    checkExit(setOf(Channel.WhitePoint), detach = false)

  @Test fun refractionDetailExit_actualDetachRetainsOpaqueMaterial() =
    checkExit(setOf(Channel.Refraction), detach = true)

  @Test fun lightingExit_selectedSourceMissingRestoresPartialAlpha() =
    checkExit(setOf(Channel.Lighting), detach = false, restoredAlpha = .5f, transparent = true)

  @Test fun allResponsesExit_actualDetachRestoresPartialAlphaAfterTransparentSettlement() =
    checkExit(Channel.entries.toSet(), detach = true, restoredAlpha = .5f, transparent = true)

  @Test fun reducedMotionExit_selectedSourceMissingReleasesObsoleteStages() =
    checkExit(Channel.entries.toSet(), detach = false, reduced = true, transparent = true)

  @Test fun successiveOpticsThenLighting_actualDetachPreservesBaseAndIndependentStage() =
    checkExit(Channel.entries.toSet(), detach = true, firstRemaining = setOf(Channel.Lighting), transparent = true)

  @Test fun successiveLightingThenOptics_selectedSourceMissingPreservesBaseAndIndependentChannels() =
    checkExit(Channel.entries.toSet(), detach = false, firstRemaining = setOf(Channel.WhitePoint, Channel.Refraction))

  @Test fun interruptedExit_reintroductionThenFinalRemovalRetainsMaterial() =
    checkExit(Channel.entries.toSet(), detach = true, interrupted = true)

  private fun checkExit(
    channels: Set<Channel>,
    detach: Boolean,
    restoredAlpha: Float = 1f,
    reduced: Boolean = false,
    transparent: Boolean = false,
    firstRemaining: Set<Channel> = emptySet(),
    interrupted: Boolean = false,
  ) {
    val fixture = showFixture(reduced)
    val reference = materialPixels(fixture, "base reference")
    assertMaterial(reference)
    mutateAndPresent { fixture.style.value = fixture.response(channels) }
    compose.runOnIdle {
      assertThat(fixture.interactions.tryEmit(PressInteraction.Press(Offset(40f, 40f)))).isTrue()
    }
    compose.waitForIdle()
    assertMaterial(materialPixels(fixture, "pressed entry"))
    val delegate = fixture.delegate
    val source = checkNotNull(delegate.layers.source)
    val optical = checkNotNull(delegate.layers.optical)
    // A visible first image can still have a replacement capture pending on a slower device.
    compose.waitUntil(timeoutMillis = 5_000) {
      presentFrame()
      compose.runOnIdle { delegate.displayedImmutableInput != null && delegate.immutableInputOwnerCount == 1 }
    }
    val image = checkNotNull(delegate.displayedImmutableInput)
    val records = delegate.sourceRecordCount
    val lighting = delegate.layers.interactionLighting
    if (Channel.Lighting in channels) assertThat(lighting).isNotNull()
    if (Channel.WhitePoint in channels) assertThat(fixture.effect.currentInteractionState.whitePointDelta).isGreaterThan(0f)
    if (Channel.Refraction in channels) assertThat(fixture.effect.currentInteractionState.refractionMultiplier).isGreaterThan(1f)
    compose.mainClock.autoAdvance = false
    change { fixture.style.value = fixture.response(channels, 0f) }
    change {
      fixture.selected.value = false
      if (detach) fixture.attached.value = false else fixture.sourceColor.value = Color.Green
    }
    assertThat(fixture.observer.hasDrawableInput).isFalse()
    assertThat(fixture.observer.scope.inputSnapshot).isNull()
    assertThat(fixture.keeperObserver.hasDrawableInput).isEqualTo(!detach)
    change { fixture.style.value = fixture.response(channels) }
    assertMaterial(materialPixels(fixture, "pressed missing source"))
    fun preserved() {
      assertThat(delegate.layers.source).isSameInstanceAs(source)
      assertThat(delegate.layers.optical).isSameInstanceAs(optical)
      assertThat(delegate.displayedImmutableInput).isSameInstanceAs(image)
      assertThat(source.isReleased).isFalse()
      assertThat(optical.isReleased).isFalse()
      assertThat(delegate.sourceRecordCount).isEqualTo(records)
    }
    var outgoingChannels = channels
    fun exit(remaining: Set<Channel>, hidden: Boolean) {
      change { fixture.style.value = fixture.response(remaining) }
      advance(100)
      preserved()
      if (!reduced) {
        if (Channel.WhitePoint in outgoingChannels && Channel.WhitePoint !in remaining) {
          assertThat(fixture.effect.currentInteractionState.whitePointDelta).isGreaterThan(0f)
        }
        if (Channel.Refraction in outgoingChannels && Channel.Refraction !in remaining) {
          assertThat(fixture.effect.currentInteractionState.refractionMultiplier).isGreaterThan(1f)
        }
        if (lighting != null && Channel.Lighting in outgoingChannels && Channel.Lighting !in remaining) assertThat(lighting.isReleased).isFalse()
      }
      assertMaterial(materialPixels(fixture, "100ms exit remaining=$remaining"))
      if (hidden) change { fixture.style.value = fixture.response(remaining, 0f) }
      advance(600)
      preserved()
      if (Channel.Lighting !in remaining) lighting?.let { assertThat(it.isReleased).isTrue() }
      if (remaining.isEmpty()) assertThat(fixture.effect.interactionControllerForTest).isNull()
      outgoingChannels = remaining
    }
    if (interrupted) {
      change { fixture.style.value = fixture.base }
      advance(100)
      preserved()
      assertMaterial(materialPixels(fixture, "interrupted exit"))
      change { fixture.style.value = fixture.response(channels) }
      advance(100)
      assertMaterial(materialPixels(fixture, "reintroduced response"))
      preserved()
    }
    if (firstRemaining.isNotEmpty()) {
      exit(firstRemaining, hidden = transparent)
      if (Channel.Lighting in firstRemaining) {
        assertThat(delegate.layers.interactionLighting).isSameInstanceAs(lighting)
        assertThat(checkNotNull(lighting).isReleased).isFalse()
      }
      assertThat(fixture.effect.interactionControllerForTest).isNotNull()
      change { fixture.style.value = fixture.response(firstRemaining) }
    }
    exit(emptySet(), hidden = transparent)
    assertThat(delegate.layers.interactionLighting).isNull()
    assertThat(delegate.layers.interactionOptical).isNull()
    assertThat(delegate.layers.interactionRefractionDetail).isNull()
    repeat(2) {
      delegate.releaseObsoleteInteractionOutput(GlassInteractionTopology(false, false, 1f))
      preserved()
    }
    change { fixture.style.value = fixture.base.then { alpha(restoredAlpha) } }
    val restored = materialPixels(fixture, "settled restored alpha=$restoredAlpha detach=$detach")
    assertMaterial(restored)
    val expected = Color(
      reference.material.red * restoredAlpha + restored.control.red * (1f - restoredAlpha),
      reference.material.green * restoredAlpha + restored.control.green * (1f - restoredAlpha),
      reference.material.blue * restoredAlpha + restored.control.blue * (1f - restoredAlpha),
    )
    assertSameColor(restored.material, expected, "Settled base material uses retained source pixels and restored alpha")
    if (!detach) {
      assertThat(restored.control.green).isGreaterThan(.9f)
      assertThat(restored.control.red).isLessThan(.1f)
      assertThat(restored.keeper.green).isGreaterThan(reference.keeper.green + .2f)
    } else {
      assertThat(restored.control.red).isGreaterThan(.9f)
    }
    assertThat(restored.control.blue).isLessThan(.1f)
    assertThat(checkNotNull(fixture.effect.preparedRender).refractionDetailKey).isNotNull()
    preserved()
    log(
      "exit settled channels=$channels detach=$detach alpha=$restoredAlpha reduced=$reduced " +
        "records=$records owners=${delegate.immutableInputOwnerCount} controller=${fixture.effect.interactionControllerForTest}",
    )
  }

  private fun change(block: () -> Unit) {
    compose.runOnIdle(block)
    repeat(2) {
      compose.mainClock.advanceTimeByFrame()
      compose.waitForIdle()
    }
  }

  private fun advance(milliseconds: Long) {
    compose.mainClock.advanceTimeBy(milliseconds, ignoreFrameDuration = true)
    compose.waitForIdle()
  }

  private fun showFixture(reduced: Boolean = false): Fixture {
    val materialSize = 120.dp
    val fixture = Fixture(reduced)
    fixtures += fixture
    log(
      "device sdk=${Build.VERSION.SDK_INT} sdkFull=${if (Build.VERSION.SDK_INT >= 36) Build.VERSION.SDK_INT_FULL else "unavailable"} " +
        "preview=${Build.VERSION.PREVIEW_SDK_INT} release=${Build.VERSION.RELEASE} " +
        "hardware=${Build.HARDWARE} fingerprint=${Build.FINGERPRINT} " +
        "composeGraphics=1.12.1 density=${compose.activity.resources.displayMetrics.density} " +
        "densityDpi=${compose.activity.resources.displayMetrics.densityDpi} dp=${materialSize.value}",
    )
    compose.setContent {
      Box(Modifier.fillMaxSize()) {
        Box(
          Modifier.fillMaxSize()
            .then(if (fixture.attached.value) Modifier.hazeSource(fixture.state) else Modifier)
            .background(fixture.sourceColor.value),
        )
        Box(
          Modifier.align(Alignment.BottomCenter).size(materialSize)
            .onGloballyPositioned { fixture.keeperBounds = it.boundsInWindow() }
            .hazeGlass(
              factory = fixture.keeperFactory,
              input = HazeInput.Sources(fixture.state),
              style = fixture.base,
              interactionSource = null,
              performanceMode = HazePerformanceMode.Quality,
              expandLayerBounds = true,
            ),
        )
        Box(
          Modifier.align(Alignment.Center).size(materialSize)
            .onGloballyPositioned { fixture.materialBounds = it.boundsInWindow() }
            .hazeGlass(
              factory = fixture.factory,
              input = fixture.input,
              style = fixture.style.value,
              interactionSource = fixture.interactions,
              interactionReducedMotionPolicy = if (reduced) GlassReducedMotionPolicy.Reduced else GlassReducedMotionPolicy.Full,
              performanceMode = HazePerformanceMode.Quality,
              expandLayerBounds = true,
            ),
        )
        Box(
          Modifier.align(Alignment.TopStart).offset(y = 100.dp).size(60.dp)
            .onGloballyPositioned { fixture.controlBounds = it.boundsInWindow() },
        )
      }
    }
    compose.waitForIdle()
    compose.runOnIdle {
      consumers += "source" to checkNotNull(fixture.delegate.layers.source)
      consumers += "optical" to checkNotNull(fixture.delegate.layers.optical)
    }
    return fixture
  }

  private fun mutateAndPresent(block: () -> Unit) {
    compose.runOnIdle(block)
    compose.waitForIdle()
    presentFrame()
  }

  private fun presentFrame() {
    val latch = CountDownLatch(1)
    compose.runOnUiThread {
      val view = compose.activity.window.decorView
      view.postOnAnimation {
        view.invalidate()
        view.postOnAnimation { latch.countDown() }
      }
    }
    assertThat(latch.await(5, TimeUnit.SECONDS), "A following presentation frame arrived").isTrue()
    compose.waitForIdle()
  }

  private fun materialPixels(fixture: Fixture, phase: String): Pixels {
    presentFrame()
    val bitmap = copyWindow()
    return try {
      Pixels(bitmap.color(fixture.controlBounds), bitmap.color(fixture.materialBounds), bitmap.color(fixture.keeperBounds))
        .also {
          log(
            "pixels phase=$phase control=${it.control} material=${it.material} keeper=${it.keeper} " +
              "controlBounds=${fixture.controlBounds} captureCount=${fixture.delegate.immutableInputCaptureCount} owners=${fixture.delegate.immutableInputOwnerCount} sourceCount=${fixture.delegate.sourceRecordCount} geometry=${fixture.observer.scope.layerSize}",
          )
        }
    } finally {
      bitmap.recycle()
    }
  }

  private fun copyWindow(): Bitmap {
    val window = compose.activity.window
    val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    val result = AtomicInteger(-1)
    PixelCopy.request(window, bitmap, { result.set(it) }, Handler(Looper.getMainLooper()))
    try {
      compose.waitUntil(timeoutMillis = 5_000) { result.get() != -1 }
      assertThat(result.get()).isEqualTo(PixelCopy.SUCCESS)
      return bitmap
    } catch (failure: Throwable) {
      bitmap.recycle()
      throw failure
    }
  }

  private fun assertMaterial(pixels: Pixels) {
    assertThat(pixels.material.blue, "Real shader material differs from plain source")
      .isGreaterThan(pixels.control.blue + 0.2f)
  }

  private fun assertSameColor(actual: Color, expected: Color, label: String) {
    assertThat(abs(actual.red - expected.red), label).isLessThan(0.05f)
    assertThat(abs(actual.green - expected.green), label).isLessThan(0.05f)
    assertThat(abs(actual.blue - expected.blue), label).isLessThan(0.05f)
  }

  private class Fixture(val reduced: Boolean) {
    val state = HazeState()
    val attached = mutableStateOf(true)
    val selected = mutableStateOf(true)
    val sourceColor = mutableStateOf(Color.Red)
    val interactions = MutableInteractionSource()
    val effect = GlassRuntimeEffect()
    val observer = InputObserver(effect)
    val factory = HazeEffectFactory<GlassNodeConfiguration> { observer }
    val keeperObserver = InputObserver(GlassRuntimeEffect())
    val keeperFactory = HazeEffectFactory<GlassNodeConfiguration> { keeperObserver }
    val input = HazeInput.Sources(
      state,
      selection = HazeSourceSelection.All.where { selected.value },
      retention = HazeSourceRetention.KeepLastFrame,
    )
    val base = GlassStyle.regular.then {
      optics(
        GlassOptics(
          refractionStrength = 0.5f,
          refractionDetailIntensity = 0.75f,
          refractionDisplacement = 20.dp,
          blurRadius = OpticalSizeValue.Fixed(0.dp),
        ),
      )
      tint(Color.Blue.copy(alpha = 0.5f))
    }
    fun response(channels: Set<Channel>, opacity: Float = 1f) = base.then {
      if (channels.isNotEmpty()) {
        pressed {
          animate(tween(1), tween(500)) {
            if (Channel.WhitePoint in channels) whitePointDelta(0.2f)
            if (Channel.Refraction in channels) refractionMultiplier(1.8f)
            if (Channel.Lighting in channels) lightingIntensity(0.5f)
          }
        }
      }
      alpha(opacity)
    }
    val style = mutableStateOf(base)
    var materialBounds = Rect.Zero
    var keeperBounds = Rect.Zero
    var controlBounds = Rect.Zero
    val delegate get() = effect.delegate as RuntimeShaderGlassDelegate
  }

  private class InputObserver(val effect: GlassRuntimeEffect) :
    HazeEffectRenderer<GlassNodeConfiguration> by effect,
    HazeEffectRendererLifecycle<GlassNodeConfiguration> by effect,
    HazeEffectRendererDrawHooks<GlassNodeConfiguration> by effect,
    HazeEffectRendererRetainedOutput by effect,
    HazeEffectRendererInteraction by effect,
    HazeEffectRendererBackdrop<GlassNodeConfiguration> by effect {
    lateinit var scope: HazeEffectRuntimeDrawScope
    val hasDrawableInput get() = scope.hasDrawableInput
    override fun HazeEffectRuntimeDrawScope.prepareDraw(style: GlassNodeConfiguration) {
      scope = this
      with(effect) { prepareDraw(style) }
    }
    override fun HazeEffectDrawScope.draw(style: GlassNodeConfiguration) {
      with(effect) { draw(style) }
    }
  }

  private data class Pixels(val control: Color, val material: Color, val keeper: Color)

  private fun Bitmap.color(bounds: Rect): Color = color(bounds.center.x.roundToInt(), bounds.center.y.roundToInt())
  private fun Bitmap.color(x: Int, y: Int): Color {
    val pixel = getPixel(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))
    return Color(
      AndroidColor.red(pixel) / 255f,
      AndroidColor.green(pixel) / 255f,
      AndroidColor.blue(pixel) / 255f,
      AndroidColor.alpha(pixel) / 255f,
    )
  }

  private fun log(message: String) {
    Log.i("HazeInteractionExit", message)
  }
}
