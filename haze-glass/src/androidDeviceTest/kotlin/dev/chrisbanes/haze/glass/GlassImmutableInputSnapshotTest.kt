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
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
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
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.hazeSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Pixel qualification of the production draw-boundary immutable input path. */
@SdkSuppress(minSdkVersion = 33)
@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
class GlassImmutableInputSnapshotTest {
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

  @Test fun productionSnapshot_staticIdleDoesNotCaptureAgain() {
    val fixture = showFixture(120.dp)
    assertMaterial(materialPixels(fixture, "idle initial"))
    val before = compose.runOnIdle { fixture.delegate.immutableInputCaptureCount }
    repeat(10) { presentFrame() }
    compose.runOnIdle {
      assertThat(fixture.delegate.immutableInputCaptureCount).isEqualTo(before)
      assertThat(fixture.delegate.immutableInputOwnerCount).isEqualTo(1)
    }
  }

  @Test fun productionSnapshot_actualSourceDetach() = checkTransition(keeperRecapture = false)

  @Test fun productionSnapshot_keeperRecapture() = checkTransition(keeperRecapture = true)

  @Test fun clearWhenUnavailable_actualDetachClearsMaterial() {
    val fixture = showFixture(120.dp, clear = true)
    assertMaterial(materialPixels(fixture, "clear initial"))
    mutateAndPresent { fixture.attached.value = false }
    val cleared = materialPixels(fixture, "clear detached")
    assertSameColor(cleared.material, cleared.control, "ClearWhenUnavailable exposes plain source")
    compose.runOnIdle { assertThat(fixture.effect.canDrawRetainedOutput()).isFalse() }
  }

  @Test fun descendantColor_refreshesCurrentInput() = checkDescendant("color") {
    it.sourceColor.value = Color.Green
  }

  @Test fun descendantAlpha_refreshesCurrentInput() = checkDescendant("alpha") {
    it.childAlpha.value = 0f
  }

  @Test fun descendantTranslation_refreshesCurrentInput() = checkDescendant("translation") {
    it.childTranslation.value = 2000f
  }

  @Test fun lazyScroll_refreshesCurrentInput() {
    val fixture = showFixture(120.dp, lazy = true)
    val initial = materialPixels(fixture, "scroll initial")
    assertMaterial(initial)
    runBlocking { withContext(Dispatchers.Main) { fixture.listState.scrollToItem(1) } }
    presentFrame()
    val current = materialPixels(fixture, "scroll changed")
    assertMaterial(current)
    assertThat(current.material.green).isGreaterThan(initial.material.green + 0.2f)
    assertThat(initial.material.red).isGreaterThan(current.material.red + 0.2f)
  }

  @Test fun delayedCapture_geometryChangeKeepsCopiedPixelsThenRejectsIncompatibleCompletion() {
    val fixture = showFixture(120.dp)
    val initial = materialPixels(fixture, "delayed geometry initial")
    assertMaterial(initial)
    val delegate = fixture.delegate
    val originalCapture = delegate.captureImmutableInput
    val captured = CountDownLatch(1)
    val completion = CompletableDeferred<Unit>()
    val oldSourceCount = delegate.sourceRecordCount
    val oldBounds = fixture.materialBounds
    var holdNext = true
    compose.runOnIdle {
      delegate.captureImmutableInput = { candidate ->
        val image = originalCapture(candidate)
        if (holdNext) {
          holdNext = false
          captured.countDown()
          // Exercise a late completion even when the incompatible epoch cancels its job.
          withContext(NonCancellable) { completion.await() }
        }
        image
      }
    }
    try {
      mutateAndPresent { fixture.sourceColor.value = Color.Green }
      assertThat(captured.await(5, TimeUnit.SECONDS), "Real bitmap capture is held before publication").isTrue()
      mutateAndPresent { fixture.targetSize.value = 180.dp }
      val pending = copyWindow()
      try {
        val bounds = fixture.materialBounds
        val inside = pending.color(
          (bounds.left + oldBounds.width / 2).roundToInt(),
          (bounds.top + oldBounds.height / 2).roundToInt(),
        )
        val outside = pending.color((bounds.right - 5).roundToInt(), bounds.center.y.roundToInt())
        assertSameColor(inside, initial.material, "Pending input keeps old displayed pixels")
        assertThat(outside.green, "Old material is not stretched to the resized target").isGreaterThan(0.9f)
        assertThat(outside.blue).isLessThan(0.1f)
        log("delayed geometry pending oldBounds=$oldBounds newBounds=$bounds inside=$inside outside=$outside")
      } finally {
        pending.recycle()
      }
      compose.runOnIdle { delegate.captureImmutableInput = originalCapture }
      completion.complete(Unit)
      compose.waitForIdle()
      presentFrame()
      val current = copyWindow()
      try {
        val bounds = fixture.materialBounds
        val edge = current.color((bounds.right - 5).roundToInt(), bounds.center.y.roundToInt())
        assertThat(edge.blue, "New capture covers the resized target").isGreaterThan(0.7f)
        assertThat(edge.green).isGreaterThan(initial.material.green + 0.2f)
        log("delayed geometry current bounds=$bounds edge=$edge sourceCount=${delegate.sourceRecordCount}")
      } finally {
        current.recycle()
      }
      compose.runOnIdle {
        assertThat(delegate.sourceRecordCount, "The incompatible held image was never recorded for presentation")
          .isEqualTo(oldSourceCount + 1)
      }
    } finally {
      completion.complete(Unit)
      InstrumentationRegistry.getInstrumentation().runOnMainSync { delegate.captureImmutableInput = originalCapture }
    }
  }

  @Test fun heldPattern_scaleReductionAfterSourceLossKeepsPixelSpaceBlur() = checkHeldPattern(1f, .5f, delayed = false)

  @Test fun heldPattern_scaleIncreaseAfterSourceLossKeepsPixelSpaceBlur() = checkHeldPattern(.5f, 1f, delayed = false)

  @Test fun heldPattern_delayedScaleReductionKeepsPixelSpaceBlur() = checkHeldPattern(1f, .5f, delayed = true)

  @Test fun heldPattern_delayedScaleIncreaseKeepsPixelSpaceBlur() = checkHeldPattern(.5f, 1f, delayed = true)

  private fun checkHeldPattern(initialScale: Float, newScale: Float, delayed: Boolean) {
    val fixture = showFixture(120.dp, patterned = true, initialScale = initialScale)
    presentFrame()
    val baseline = copyWindow()
    val delegate = fixture.delegate
    val initial = checkNotNull(delegate.displayedImmutableInput)
    val heldCoordinates = checkNotNull(delegate.lastSuccessfulSourceSnapshot)
    val originalCapture = delegate.captureImmutableInput
    val completion = CompletableDeferred<Unit>()
    val captured = AtomicInteger()
    if (delayed) {
      compose.runOnIdle {
        delegate.captureImmutableInput = { candidate ->
          val image = originalCapture(candidate)
          captured.incrementAndGet()
          withContext(NonCancellable) { completion.await() }
          image
        }
      }
    }
    try {
      mutateAndPresent {
        if (!delayed) fixture.selected.value = false
        fixture.performanceMode.value = if (newScale == 1f) HazePerformanceMode.Quality else HazePerformanceMode.Performance
      }
      if (delayed) compose.waitUntil(timeoutMillis = 5_000) { captured.get() > 0 }
      val current = copyWindowWhileAdvancingCompose(compose.activity.window, baseline.width, baseline.height)
      try {
        val bounds = fixture.materialBounds
        var maximumDelta = 0f
        var variation = 0f
        var minimum = 1f
        var maximum = 0f
        for (x in (bounds.left + 15).roundToInt() until (bounds.right - 15).roundToInt() step 3) {
          val before = baseline.color(x, bounds.center.y.roundToInt())
          val after = current.color(x, bounds.center.y.roundToInt())
          minimum = minOf(minimum, before.green)
          maximum = maxOf(maximum, before.green)
          maximumDelta = maxOf(maximumDelta, abs(before.red - after.red), abs(before.green - after.green), abs(before.blue - after.blue))
        }
        variation = maximum - minimum
        log("held-pattern initialScale=$initialScale newScale=$newScale delayed=$delayed maxPixelDelta=$maximumDelta variation=$variation bounds=$bounds")
        assertThat(variation, "Pattern is visible through the real fused material").isGreaterThan(.1f)
        assertThat(maximumDelta, "Held capture keeps its original pixel-space material").isLessThan(.05f)
      } finally {
        current.recycle()
      }
      compose.runOnIdle {
        assertThat(delegate.displayedImmutableInput).isSameInstanceAs(initial)
        assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(heldCoordinates)
        assertThat(checkNotNull(fixture.effect.preparedRender).params.coordinates.scaleFactor).isEqualTo(newScale)
      }
    } finally {
      baseline.recycle()
      InstrumentationRegistry.getInstrumentation().runOnMainSync { delegate.captureImmutableInput = originalCapture }
      completion.complete(Unit)
    }
  }

  @Test fun sourceUpdates_presentNewMaterialBeforeUpdatesStop() {
    val fixture = showFixture(120.dp)
    val initial = materialPixels(fixture, "progress initial")
    assertMaterial(initial)
    val window = compose.activity.window
    val view = window.decorView
    val width = view.width
    val height = view.height
    val clockBefore = compose.mainClock.currentTime
    var stopped = false
    val frames = AtomicInteger()
    lateinit var update: Runnable
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      update = Runnable {
        if (!stopped) {
          val frame = frames.incrementAndGet()
          // Bound the fixture even if a future test API accidentally waits for idle.
          if (frame >= 180) {
            stopped = true
            return@Runnable
          }
          fixture.sourceColor.value = Color.Green
          fixture.childAlpha.value = if (frame % 2 == 0) 0.95f else 1f
          view.postOnAnimation(update)
        }
      }
      view.postOnAnimation(update)
    }
    try {
      compose.waitUntil(timeoutMillis = 5_000) { frames.get() >= 20 }
      val bitmap = copyWindowWhileAdvancingCompose(window, width, height)
      try {
        assertThat(frames.get(), "PixelCopy completed before source updates stopped").isLessThan(180)
        val pixels = Pixels(bitmap.color(fixture.controlBounds), bitmap.color(fixture.materialBounds), bitmap.color(fixture.keeperBounds))
        val clockAfter = compose.mainClock.currentTime
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
          log(
            "ongoing-progress frames=${frames.get()} clockBefore=$clockBefore clockAfter=$clockAfter " +
              "control=${pixels.control} material=${pixels.material} keeper=${pixels.keeper} " +
              "captureCount=${fixture.delegate.immutableInputCaptureCount} sourceCount=${fixture.delegate.sourceRecordCount} " +
              "keeperCaptureCount=${fixture.keeperObserver.effect.delegate.let { it as RuntimeShaderGlassDelegate }.immutableInputCaptureCount}",
          )
        }
        assertThat(clockAfter, "Compose's composition clock advanced during source updates").isGreaterThan(clockBefore)
        assertThat(pixels.control.green, "Live source was recomposed to green in the sampled bitmap").isGreaterThan(0.9f)
        assertThat(pixels.control.red).isLessThan(0.1f)
        assertThat(pixels.control.blue).isLessThan(0.1f)
        assertThat(pixels.material.green, "New input is presented during ongoing updates")
          .isGreaterThan(initial.material.green + 0.2f)
        assertThat(pixels.keeper.green, "Current keeper also presents new input during updates")
          .isGreaterThan(initial.keeper.green + 0.2f)
        assertMaterial(pixels)
        assertSameColor(pixels.material, pixels.keeper, "Both consumers present the new green input")
      } finally {
        bitmap.recycle()
      }
    } finally {
      InstrumentationRegistry.getInstrumentation().runOnMainSync {
        stopped = true
        view.removeCallbacks(update)
      }
    }
  }

  private fun checkDescendant(name: String, change: (Fixture) -> Unit) {
    val fixture = showFixture(120.dp)
    val initial = materialPixels(fixture, "$name initial")
    assertMaterial(initial)
    mutateAndPresent { change(fixture) }
    val current = materialPixels(fixture, "$name changed")
    assertThat(current.material.blue).isGreaterThan(0.7f)
    assertThat(abs(current.material.green - initial.material.green)).isGreaterThan(0.2f)
    assertSameColor(current.material, current.keeper, "Both current consumers see descendant changes")
  }

  private fun checkTransition(keeperRecapture: Boolean) {
    val fixture = showFixture(120.dp)
    val mode = "production keeper=$keeperRecapture"
    val initial = materialPixels(fixture, "$mode initial")
    assertMaterial(initial)
    assertThat(initial.control.red).isGreaterThan(0.9f)
    assertThat(initial.control.blue).isLessThan(0.1f)
    compose.runOnIdle {
      assertThat(fixture.observer.hasDrawableInput).isTrue()
      assertThat(fixture.observer.scope.inputSnapshot).isNotNull()
      assertThat(fixture.delegate.fusedShader).isNotNull()
      assertThat(fixture.effect.interactionControllerForTest).isNull()
    }
    val image = compose.runOnIdle { checkNotNull(fixture.delegate.displayedImmutableInput) }
    val readback = image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    try {
      assertThat(readback.color(readback.width / 2, readback.height / 2).red).isGreaterThan(0.9f)
      assertThat(readback.color(readback.width / 2, readback.height / 2).blue).isLessThan(0.1f)
    } finally {
      readback.recycle()
    }
    val source = checkNotNull(fixture.delegate.layers.source)
    val sourceCount = fixture.delegate.sourceRecordCount
    val geometry = fixture.observer.scope.layerSize
    val optical = fixture.delegate.layers.optical
    mutateAndPresent { fixture.style.value = fixture.base.then { alpha(0f) } }
    mutateAndPresent {
      fixture.selected.value = false
      if (keeperRecapture) fixture.sourceColor.value = Color.Green else fixture.attached.value = false
    }
    compose.runOnIdle {
      assertThat(fixture.observer.hasDrawableInput).isFalse()
      assertThat(fixture.observer.scope.inputSnapshot).isNull()
      assertThat(fixture.keeperObserver.hasDrawableInput).isEqualTo(keeperRecapture)
      if (!keeperRecapture) assertThat(fixture.keeperObserver.scope.inputSnapshot).isNull()
      log(
        "source-lifecycle $mode allSourceInput=${fixture.keeperObserver.hasDrawableInput} " +
          "allSourceSnapshot=${fixture.keeperObserver.scope.inputSnapshot} modifierPresent=${fixture.attached.value}",
      )
      assertThat(fixture.delegate.layers.source).isSameInstanceAs(source)
      assertThat(source.isReleased).isFalse()
      assertThat(fixture.delegate.sourceRecordCount).isEqualTo(sourceCount)
      assertThat(fixture.observer.scope.layerSize).isEqualTo(geometry)
    }
    mutateAndPresent { fixture.style.value = fixture.base }
    val restored = materialPixels(fixture, "$mode restored")
    compose.runOnIdle {
      assertThat(fixture.observer.hasDrawableInput).isFalse()
      assertThat(fixture.delegate.layers.source).isSameInstanceAs(source)
      assertThat(fixture.delegate.layers.optical).isSameInstanceAs(optical)
      assertThat(fixture.delegate.sourceRecordCount).isEqualTo(sourceCount)
      assertThat(fixture.observer.scope.layerSize).isEqualTo(geometry)
      assertThat(fixture.effect.preparedRender?.alpha).isEqualTo(1f)
      log(
        "state $mode sourceCount=$sourceCount shape=$geometry sourceReleased=${source.isReleased} " +
          "hasInput=${fixture.observer.hasDrawableInput}",
      )
    }
    assertMaterial(restored)
    if (keeperRecapture) {
      assertThat(restored.control.green).isGreaterThan(0.9f)
      assertThat(restored.control.red).isLessThan(0.1f)
      assertThat(restored.keeper.blue).isGreaterThan(restored.control.blue + 0.2f)
      assertThat(restored.material.red, "Target retains old red source frame")
        .isGreaterThan(restored.keeper.red + 0.2f)
      assertThat(restored.keeper.green, "Current consumer sees new green source frame")
        .isGreaterThan(restored.material.green + 0.2f)
    } else {
      assertThat(restored.control.red).isGreaterThan(0.9f)
      assertThat(restored.control.blue).isLessThan(0.1f)
    }
    assertSameColor(restored.material, initial.material, "Target keeps its original source pixels")
  }

  @Test fun heldBudget_sourceLossUsesTemporaryFallbackThenReplaysSameImage() = checkHeldBudget(delayed = false)

  @Test fun heldBudget_delayedReplacementUsesTemporaryFallbackWithoutPromotingUndrawnInput() = checkHeldBudget(delayed = true)

  private fun checkHeldBudget(delayed: Boolean) {
    val density = compose.activity.resources.displayMetrics.density
    val fixture = showFixture((2200f / density).dp, budget = true)
    val delegate = fixture.delegate
    fun fallbackGroup(): GraphicsLayer = run {
      val field = delegate.javaClass.getDeclaredField("heldInputBudgetFallback").apply { isAccessible = true }
      val fallback = checkNotNull(field.get(delegate))
      val group = fallback.javaClass.getDeclaredField("groupAlpha").apply { isAccessible = true }.get(fallback) as RetainedGlassGroupAlphaLayer
      checkNotNull(group.layer).also { consumers += "temporary fallback group" to it }
    }
    fun sample(phase: String): Color {
      presentFrame()
      val bitmap = copyWindow()
      return try {
        val control = bitmap.color(fixture.controlBounds)
        val color = bitmap.color((bitmap.width * .8f).roundToInt(), (bitmap.height * .6f).roundToInt())
        log("budget phase=$phase control=$control material=$color bounds=${fixture.materialBounds} currentPlan=${fixture.effect.preparedRender?.plan?.layers} sourceCount=${delegate.sourceRecordCount} captureCount=${delegate.immutableInputCaptureCount} owners=${delegate.immutableInputOwnerCount}")
        color
      } finally {
        bitmap.recycle()
      }
    }
    val reference = sample("reference")
    assertThat(reference.blue).isGreaterThan(.2f)
    val held = checkNotNull(delegate.displayedImmutableInput)
    val oldSource = checkNotNull(delegate.layers.source)
    val oldOptical = checkNotNull(delegate.layers.optical)
    val oldRender = checkNotNull(fixture.effect.preparedRender)
    assertThat(oldRender.params.coordinates.sampleSize).isEqualTo(Size(2200f, 2200f))
    assertThat(oldRender.plan.retainedPixelCountOrNull()).isEqualTo(9_680_000L)
    val original = delegate.captureImmutableInput
    val captured = AtomicInteger()
    val completion = CompletableDeferred<Unit>()
    if (delayed) {
      compose.runOnIdle {
        delegate.captureImmutableInput = { candidate ->
          val image = original(candidate)
          captured.incrementAndGet()
          withContext(NonCancellable) { completion.await() }
          image
        }
      }
    }
    try {
      mutateAndPresent {
        fixture.sourceColor.value = Color.Green
        if (!delayed) fixture.selected.value = false
      }
      if (delayed) compose.waitUntil(10_000) { captured.get() > 0 }
      mutateAndPresent {
        if (!delayed) fixture.selected.value = false
        fixture.performanceMode.value = HazePerformanceMode.Performance
        fixture.style.value = fixture.budgetBase.then {
          alpha(.5f)
          interactionLightRadiusFraction(.5f)
          pressed { lightingIntensity(.5f) }
        }
      }
      compose.runOnIdle { assertThat(fixture.interactions.tryEmit(PressInteraction.Press(Offset(1100f, 1100f)))).isTrue() }
      val fallback = sample("temporary fallback")
      assertThat(fixture.effect.preparedRender?.plan?.retainedPixelCountOrNull()).isEqualTo(8_470_000L)
      val actualHeld = fixture.effect.prepareCapturedInputRender(fixture.observer.scope, oldRender.params.coordinates, oldRender.params.backgroundColor, checkNotNull(fixture.effect.preparedRender))
      assertThat(actualHeld.plan.retainedPixelCountOrNull()).isEqualTo(19_360_000L)
      assertThat(actualHeld.plan.fitsGlassRenderBudget()).isFalse()
      assertThat(fallback.green).isGreaterThan(reference.green + .2f)
      assertThat(reference.red).isGreaterThan(fallback.red + .2f)
      assertThat(fallback.blue).isGreaterThan(.1f)
      assertThat(oldSource.isReleased).isTrue()
      assertThat(oldOptical.isReleased).isTrue()
      val temporaryGroup = fallbackGroup()
      val records = delegate.sourceRecordCount
      val captures = delegate.immutableInputCaptureCount
      repeat(3) {
        sample("refused idle $it")
        assertThat(fixture.effect.delegate).isSameInstanceAs(delegate)
        assertThat(delegate.displayedImmutableInput).isSameInstanceAs(held)
        assertThat(delegate.layers.source).isNull()
        assertThat(delegate.layers.optical).isNull()
        assertThat(delegate.layers.interactionLighting).isNull()
        assertThat(delegate.layers.groupAlpha.layer).isNull()
        assertThat(fallbackGroup()).isSameInstanceAs(temporaryGroup)
        assertThat(delegate.sourceRecordCount).isEqualTo(records)
        assertThat(delegate.immutableInputCaptureCount).isEqualTo(captures)
      }
      mutateAndPresent { fixture.style.value = fixture.budgetBase.then { alpha(0f) } }
      compose.mainClock.advanceTimeBy(600)
      compose.waitForIdle()
      assertThat(temporaryGroup.isReleased).isTrue()
      assertThat(delegate.shouldDrawRetainedOutput()).isTrue()
      assertThat(delegate.layers.source).isNull()
      assertThat(delegate.layers.optical).isNull()
      assertThat(delegate.displayedImmutableInput).isSameInstanceAs(held)
      assertThat(delegate.sourceRecordCount).isEqualTo(records)
      assertThat(delegate.immutableInputCaptureCount).isEqualTo(captures)
      mutateAndPresent { fixture.style.value = fixture.budgetBase.then { alpha(.5f) } }
      val partial = sample("hidden settlement partial-alpha recovery")
      assertSameColor(partial, Color(reference.red * .5f, reference.green * .5f + .5f, reference.blue * .5f), "Partial alpha blends the held material with the live green background")
      assertThat(delegate.displayedImmutableInput).isSameInstanceAs(held)
      mutateAndPresent { fixture.style.value = fixture.budgetBase }
      val restored = sample("valid recovery")
      assertSameColor(restored, reference, "Valid topology replays the same captured image")
      assertThat(temporaryGroup.isReleased).isTrue()
      assertThat(delegate.displayedImmutableInput).isSameInstanceAs(held)
      assertThat(delegate.layers.source).isNotNull()
      assertThat(delegate.sourceRecordCount).isEqualTo(records + 1)
      completion.complete(Unit)
      if (delayed) {
        compose.waitUntil(10_000) { delegate.displayedImmutableInput !== held }
        val fresh = sample("new compatible completion")
        assertThat(fresh.green).isGreaterThan(restored.green + .2f)
      } else {
        for (lifecycle in listOf("clear", "trim", "detach")) {
          if (lifecycle != "clear") {
            mutateAndPresent {
              fixture.selected.value = true
              fixture.performanceMode.value = HazePerformanceMode.Quality
              fixture.style.value = fixture.budgetBase
            }
            compose.waitUntil(10_000) { delegate.displayedImmutableInput != null }
            compose.runOnIdle {
              consumers += "recaptured source" to checkNotNull(delegate.layers.source)
              consumers += "recaptured optical" to checkNotNull(delegate.layers.optical)
            }
          }
          mutateAndPresent {
            fixture.selected.value = false
            fixture.performanceMode.value = HazePerformanceMode.Performance
            fixture.style.value = fixture.budgetBase.then {
              alpha(.5f)
              interactionLightRadiusFraction(.5f)
              pressed { lightingIntensity(.5f) }
            }
          }
          sample("fallback before $lifecycle")
          val groupBeforeTeardown = fallbackGroup()
          compose.runOnIdle {
            when (lifecycle) {
              "detach" -> fixture.targetAttached.value = false
              "trim" -> fixture.effect.onTrimMemory(TrimMemoryLevel.COMPLETE)
              else -> delegate.clearRetainedOutput()
            }
          }
          if (lifecycle == "detach") compose.waitForIdle()
          assertThat(groupBeforeTeardown.isReleased).isTrue()
          assertThat(delegate.immutableInputOwnerCount).isEqualTo(0)
          assertThat(delegate.shouldDrawRetainedOutput()).isFalse()
          assertThat(delegate.layers.source).isNull()
          assertThat(delegate.layers.optical).isNull()
        }
      }
    } finally {
      completion.complete(Unit)
      InstrumentationRegistry.getInstrumentation().runOnMainSync { delegate.captureImmutableInput = original }
    }
  }

  private fun showFixture(materialSize: Dp, clear: Boolean = false, lazy: Boolean = false, patterned: Boolean = false, initialScale: Float = 1f, budget: Boolean = false): Fixture {
    val fixture = Fixture(materialSize, clear, budget)
    fixture.performanceMode.value = if (initialScale == 1f) HazePerformanceMode.Quality else HazePerformanceMode.Performance
    if (budget) {
      fixture.style.value = fixture.budgetBase
    }
    if (patterned) {
      fixture.style.value = fixture.base.then {
        optics(GlassOptics(refractionStrength = .5f, refractionDisplacement = 20.dp, blurRadius = OpticalSizeValue.Fixed(10.dp)))
        tint(Color.Blue.copy(alpha = .25f))
      }
    }
    fixtures += fixture
    log(
      "device sdk=${Build.VERSION.SDK_INT} sdkFull=${if (Build.VERSION.SDK_INT >= 36) Build.VERSION.SDK_INT_FULL else "unavailable"} " +
        "preview=${Build.VERSION.PREVIEW_SDK_INT} release=${Build.VERSION.RELEASE} " +
        "hardware=${Build.HARDWARE} fingerprint=${Build.FINGERPRINT} " +
        "composeGraphics=1.12.1 density=${compose.activity.resources.displayMetrics.density} " +
        "densityDpi=${compose.activity.resources.displayMetrics.densityDpi} dp=${materialSize.value}",
    )
    compose.setContent {
      Box(Modifier.fillMaxSize().then(if (budget) Modifier.background(fixture.sourceColor.value) else Modifier)) {
        Box(
          (if (budget) Modifier.align(Alignment.TopStart).offset(x = 200.dp, y = 200.dp).requiredSize(materialSize) else Modifier.fillMaxSize()).then(if (fixture.attached.value) Modifier.hazeSource(fixture.state) else Modifier)
            .background(Color.White),
        ) {
          if (patterned) {
            Canvas(Modifier.fillMaxSize()) {
              for (stripe in 0..(size.width / 60f).toInt()) {
                drawRect(
                  if (stripe % 2 == 0) Color.Red else Color.White,
                  topLeft = Offset(stripe * 60f, 0f),
                  size = Size(60f, size.height),
                )
              }
            }
          } else if (lazy) {
            LazyColumn(Modifier.fillMaxSize(), state = fixture.listState) {
              item { Box(Modifier.fillParentMaxWidth().height(1200.dp).background(Color.Red)) }
              item { Box(Modifier.fillParentMaxWidth().height(1200.dp).background(Color.Green)) }
            }
          } else {
            Box(
              Modifier.fillMaxSize().graphicsLayer {
                alpha = fixture.childAlpha.value
                translationX = fixture.childTranslation.value
              }.background(fixture.sourceColor.value),
            )
          }
        }
        Box(
          Modifier.align(Alignment.BottomCenter).size(if (budget) 120.dp else materialSize)
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
          (
            if (budget) {
              Modifier.align(Alignment.TopStart).offset(x = 200.dp, y = 200.dp).requiredSize(fixture.targetSize.value)
            } else {
              Modifier.align(Alignment.Center).size(fixture.targetSize.value)
            }
            )
            .onGloballyPositioned { fixture.materialBounds = it.boundsInWindow() }
            .then(
              if (fixture.targetAttached.value) {
                Modifier.hazeGlass(
                  factory = fixture.factory,
                  input = fixture.input,
                  style = fixture.style.value,
                  interactionSource = if (budget) fixture.interactions else null,
                  interactionReducedMotionPolicy = if (budget) GlassReducedMotionPolicy.Reduced else GlassReducedMotionPolicy.System,
                  performanceMode = fixture.performanceMode.value,
                  expandLayerBounds = !budget,
                )
              } else {
                Modifier
              },
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

  private fun copyWindowWhileAdvancingCompose(window: Window, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val result = AtomicInteger(-1)
    PixelCopy.request(window, bitmap, { result.set(it) }, Handler(Looper.getMainLooper()))
    try {
      // Service Compose's v2 test dispatcher/frame clock while awaiting the real window copy.
      compose.waitUntil(timeoutMillis = 5_000) { result.get() != -1 }
      assertThat(result.get()).isEqualTo(PixelCopy.SUCCESS)
      return bitmap
    } catch (failure: Throwable) {
      bitmap.recycle()
      throw failure
    }
  }

  private fun copyWindow(
    window: Window = compose.activity.window,
    width: Int = window.decorView.width,
    height: Int = window.decorView.height,
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(
      width,
      height,
      Bitmap.Config.ARGB_8888,
    )
    val latch = CountDownLatch(1)
    var result = PixelCopy.ERROR_UNKNOWN
    PixelCopy.request(window, bitmap, {
      result = it
      latch.countDown()
    }, Handler(Looper.getMainLooper()))
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS), "Window PixelCopy completed").isTrue()
      assertThat(result).isEqualTo(PixelCopy.SUCCESS)
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

  private class Fixture(val materialSize: Dp, clear: Boolean, budget: Boolean = false) {
    val interactions = MutableInteractionSource()
    val targetAttached = mutableStateOf(true)
    val targetSize = mutableStateOf(materialSize)
    val performanceMode = mutableStateOf(HazePerformanceMode.Quality)
    val state = HazeState()
    val attached = mutableStateOf(true)
    val selected = mutableStateOf(true)
    val sourceColor = mutableStateOf(Color.Red)
    val childAlpha = mutableStateOf(1f)
    val childTranslation = mutableStateOf(0f)
    val listState = LazyListState()
    val effect = GlassRuntimeEffect()
    val observer = InputObserver(effect)
    val factory = HazeEffectFactory<GlassNodeConfiguration> { observer }
    val keeperObserver = InputObserver(GlassRuntimeEffect())
    val keeperFactory = HazeEffectFactory<GlassNodeConfiguration> { keeperObserver }
    val input = HazeInput.Sources(
      state,
      selection = HazeSourceSelection.All.where { selected.value },
      retention = if (clear) HazeSourceRetention.ClearWhenUnavailable else HazeSourceRetention.KeepLastFrame,
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
    val budgetBase = base.then {
      optics(GlassOptics(refractionStrength = .5f, refractionDisplacement = 0.dp, blurRadius = OpticalSizeValue.Fixed(0.dp)))
      backgroundColor(Color.Transparent)
      specularIntensity(0f)
      edgeShadow(Color.Transparent)
      edgeSoftness(0.dp)
    }
    val style = mutableStateOf(if (budget) budgetBase else base)
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
    Log.i("HazeImmutableInput", message)
  }
}
