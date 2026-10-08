// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
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
import org.junit.Rule
import org.junit.Test

/** Diagnostic supplement to physical release benchmarks; contains no performance thresholds. */
@SdkSuppress(minSdkVersion = 33)
@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
class GlassCaptureLifetimeObservationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun stableInteractionAndRepeatedLifetime_observeCaptureAndRelease() {
    val arguments = InstrumentationRegistry.getArguments()
    val qualification = arguments.getString("haze.physicalQualification") == "true"
    val emulator = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") ||
      Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.contains("emulator")
    assertThat(!qualification || !emulator, "Physical qualification explicitly rejects emulator execution").isTrue()
    val identity = arguments.getString("haze.buildIdentity") ?: "unspecified"
    assertThat(!qualification || identity != "unspecified", "Supply haze.buildIdentity with the tested source manifest digest").isTrue()
    log("identity=$identity fingerprint=${Build.FINGERPRINT} hardware=${Build.HARDWARE} sdk=${Build.VERSION.SDK_INT} physicalRequested=$qualification diagnostic=true releaseBenchmark=false")
    val current = mutableStateOf<Session?>(null)
    var completedCycles = 0
    var releasedConsumers = 0
    memory("baseline")
    compose.setContent {
      Box(Modifier.fillMaxSize()) {
        current.value?.let { session ->
          key(session) {
            Box(Modifier.fillMaxSize().then(if (session.attached.value) Modifier.hazeSource(session.state) else Modifier).background(Color.Red))
            Box(
              Modifier.align(Alignment.Center).size(120.dp).hazeGlass(
                factory = session.factory,
                input = session.input,
                style = session.style,
                interactionSource = session.interactions,
                performanceMode = HazePerformanceMode.Quality,
                expandLayerBounds = true,
              ),
            )
          }
        }
      }
    }
    try {
      repeat(3) { cycle ->
        releasedConsumers += observeCycle(cycle, current, arguments.getString("haze.captureExpected") != "false")
        completedCycles++
        // The cycle call has returned, so no sampler-owned Session/delegate/layer references remain.
        memory("settled-$cycle")
      }
    } finally {
      compose.mainClock.autoAdvance = true
      compose.runOnIdle { current.value = null }
      compose.waitForIdle()
      compose.activityRule.scenario.close()
      val closedAt = System.nanoTime()
      memory("activity-closed")
      if (qualification || arguments.getString("haze.observeDelayedMemory") == "true") {
        for (offsetMillis in listOf(250L, 1_000L, 5_000L)) {
          val remainingMillis = offsetMillis - (System.nanoTime() - closedAt) / 1_000_000
          if (remainingMillis > 0) SystemClock.sleep(remainingMillis)
          memory("activity-closed-${offsetMillis}ms")
          log("natural-settlement targetMs=$offsetMillis elapsedNs=${System.nanoTime() - closedAt} forcedGc=false reclamationThreshold=false")
        }
      }
      log("cleanup completedCycles=$completedCycles checkedReleasedConsumers=$releasedConsumers samplerStrongConsumerRefs=0 bitmapRecycle=false forcedGc=false gcOrDriverReleaseClaim=false")
    }
  }

  private fun observeCycle(cycle: Int, current: MutableState<Session?>, expectCapture: Boolean): Int {
    val consumers = mutableListOf<GraphicsLayer>()
    val session = Session(cycle)
    compose.runOnIdle { current.value = session }
    compose.waitUntil(10_000) { session.observer.installed && hasDisplayedInput(session.delegate) }
    compose.runOnIdle {
      assertThat(!expectCapture || ownerCount(session.delegate) != null, "Candidate must expose immutable input ownership").isTrue()
      ownerCount(session.delegate)?.let { assertThat(it).isGreaterThan(0) }
      val expected = expectCapture
      assertThat(session.observer.captureHookAvailable).isEqualTo(expected)
      rememberConsumers(session, consumers)
      logCounters(session, "attached")
    }
    memory("attached-$cycle")
    if (cycle == 0) {
      val capturesBefore = captureCount(session.delegate)
      compose.mainClock.autoAdvance = false
      compose.runOnIdle { assertThat(session.interactions.tryEmit(PressInteraction.Press(Offset(40f, 40f)))).isTrue() }
      repeat(12) {
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.runOnIdle {
          ownerCount(session.delegate)?.let { assertThat(it).isLessThan(4) }
          rememberConsumers(session, consumers)
          logCounters(session, "stable-source-pressed-$it")
        }
      }
      compose.runOnIdle {
        assertThat(session.effect.currentInteractionState.refractionMultiplier).isGreaterThan(1f)
        log("stable-source-summary capturesBefore=$capturesBefore capturesAfter=${captureCount(session.delegate)} sourceColor=Red refractionMultiplier=${session.effect.currentInteractionState.refractionMultiplier}")
      }
      memory("stable-source-pressed")
      compose.mainClock.autoAdvance = true
    }
    compose.runOnIdle { session.selected.value = false }
    compose.waitForIdle()
    awaitReleasedInput(session.delegate)
    compose.runOnIdle {
      ownerCount(session.delegate)?.let { assertThat(it).isEqualTo(0) }
      logCounters(session, "clear-unavailable")
    }
    assertReleased(consumers)
    compose.runOnIdle { session.selected.value = true }
    // Observe settled trim before normal drawing can start a new capture of the valid source.
    // Cancellation during native rendering is qualified separately by the backend boundary tests.
    compose.waitUntil(10_000) {
      var trimmed = false
      compose.runOnIdle {
        if (hasDisplayedInput(session.delegate) && ownerCount(session.delegate).let { it == null || it == 1 }) {
          rememberConsumers(session, consumers)
          session.effect.onTrimMemory(TrimMemoryLevel.COMPLETE)
          ownerCount(session.delegate)?.let { assertThat(it).isEqualTo(0) }
          assertReleased(consumers)
          logCounters(session, "trim")
          trimmed = true
        }
      }
      trimmed
    }
    compose.waitUntil(10_000) { hasDisplayedInput(session.delegate) }
    compose.runOnIdle {
      rememberConsumers(session, consumers)
      session.attached.value = false
    }
    compose.waitForIdle()
    awaitReleasedInput(session.delegate)
    compose.runOnIdle {
      ownerCount(session.delegate)?.let { assertThat(it).isEqualTo(0) }
      logCounters(session, "source-detached")
      current.value = null
    }
    compose.waitForIdle()
    compose.runOnIdle {
      ownerCount(session.delegate)?.let { assertThat(it).isEqualTo(0) }
      assertReleased(consumers)
    }
    val released = consumers.size
    consumers.clear()
    return released
  }

  private fun awaitReleasedInput(delegate: RuntimeShaderGlassDelegate) {
    compose.waitUntil(10_000) {
      var released = false
      InstrumentationRegistry.getInstrumentation().runOnMainSync {
        released = ownerCount(delegate).let { it == null || it == 0 }
      }
      released
    }
  }

  private fun rememberConsumers(session: Session, consumers: MutableList<GraphicsLayer>) {
    with(session.delegate.layers) {
      listOfNotNull(groupAlpha.layer, source, blurHorizontal, blurred, depthMixed, optical, refractionDetail, refractionDetailCoverage, refractionComposite, interactionOptical, interactionRefractionDetail, interactionRefractionDetailCoverage, interactionRefractionComposite, interactionLighting, rim)
    }.forEach {
      if (consumers.none { previous -> previous === it }) consumers += it
    }
  }

  private fun assertReleased(consumers: List<GraphicsLayer>) {
    consumers.forEach { assertThat(it.isReleased, "Tracked consumer released before owner teardown").isTrue() }
  }

  private fun logCounters(session: Session, phase: String) {
    log("phase=$phase cycle=${session.cycle} captures=${captureCount(session.delegate)} owners=${ownerCount(session.delegate)} sourceRecords=${session.delegate.sourceRecordCount} stageRecords=${session.delegate.stageRecordCounts}")
  }

  private fun memory(phase: String) {
    val info = Debug.MemoryInfo()
    Debug.getMemoryInfo(info)
    log("memory phase=$phase totalPssKb=${info.totalPss} nativePssKb=${info.nativePss} dalvikPssKb=${info.dalvikPss} otherPssKb=${info.otherPss} totalPrivateDirtyKb=${info.totalPrivateDirty} nativeHeapAllocatedBytes=${Debug.getNativeHeapAllocatedSize()} diagnostic=true")
  }

  private class Session(val cycle: Int) {
    val state = HazeState()
    val attached = mutableStateOf(true)
    val selected = mutableStateOf(true)
    val interactions = MutableInteractionSource()
    val effect = GlassRuntimeEffect()
    val observer = Observer(effect, cycle)
    val factory = HazeEffectFactory<GlassNodeConfiguration> { observer }
    val input = HazeInput.Sources(state, selection = HazeSourceSelection.All.where { selected.value }, retention = HazeSourceRetention.ClearWhenUnavailable)
    val style = GlassStyle.regular.then {
      tint(Color.Blue.copy(alpha = .5f))
      pressed {
        refractionMultiplier(1.5f)
        lightingIntensity(.5f)
      }
    }
    val delegate get() = checkNotNull(observer.runtimeDelegate)
  }

  private class Observer(val effect: GlassRuntimeEffect, val cycle: Int) :
    HazeEffectRenderer<GlassNodeConfiguration> by effect,
    HazeEffectRendererLifecycle<GlassNodeConfiguration> by effect,
    HazeEffectRendererDrawHooks<GlassNodeConfiguration> by effect,
    HazeEffectRendererRetainedOutput by effect,
    HazeEffectRendererInteraction by effect,
    HazeEffectRendererBackdrop<GlassNodeConfiguration> by effect {
    var installed = false
    var captureHookAvailable = false
    var runtimeDelegate: RuntimeShaderGlassDelegate? = null
    override fun HazeEffectRuntimeDrawScope.prepareDraw(style: GlassNodeConfiguration) {
      with(effect) { prepareDraw(style) }
      if (!installed) {
        val delegate = effect.delegate as RuntimeShaderGlassDelegate
        runtimeDelegate = delegate
        val getter = getter(delegate, "CaptureImmutableInput")
        val setter = delegate.javaClass.methods.singleOrNull { it.name.substringBefore('$') == "setCaptureImmutableInput" }
        if (getter != null && setter != null) {
          @Suppress("UNCHECKED_CAST")
          val original = getter.invoke(delegate) as suspend (GraphicsLayer) -> ImageBitmap
          var count = 0
          val measured: suspend (GraphicsLayer) -> ImageBitmap = { candidate ->
            val started = System.nanoTime()
            val image = original(candidate)
            val elapsed = System.nanoTime() - started
            val bitmap = image.asAndroidBitmap()
            log("capture cycle=$cycle count=${++count} asyncWallNs=$elapsed width=${bitmap.width} height=${bitmap.height} config=${bitmap.config} allocationBytes=${bitmap.allocationByteCount} owners=${ownerCount(delegate)} cpuReadback=false diagnostic=true")
            image
          }
          setter.invoke(delegate, measured)
          captureHookAvailable = true
        } else {
          log("capture cycle=$cycle hook=null captures=null allocationBytes=null asyncWallNs=null baselineAdapter=true")
        }
        installed = true
      }
    }
    override fun HazeEffectDrawScope.draw(style: GlassNodeConfiguration) {
      with(effect) { draw(style) }
    }
  }

  private companion object {
    // Optional only for the paired baseline, where immutable capture APIs do not exist.
    fun getter(delegate: RuntimeShaderGlassDelegate, property: String) =
      delegate.javaClass.methods.singleOrNull { it.name.substringBefore('$') == "get$property" && it.parameterCount == 0 }

    fun ownerCount(delegate: RuntimeShaderGlassDelegate): Int? =
      getter(delegate, "ImmutableInputOwnerCount")?.invoke(delegate) as Int?

    fun captureCount(delegate: RuntimeShaderGlassDelegate): Int? =
      getter(delegate, "ImmutableInputCaptureCount")?.invoke(delegate) as Int?

    fun hasDisplayedInput(delegate: RuntimeShaderGlassDelegate): Boolean =
      getter(delegate, "DisplayedImmutableInput")?.let { it.invoke(delegate) != null } ?: (delegate.layers.source != null)

    fun log(message: String) {
      Log.i("HazeCaptureLifetime", message)
    }
  }
}
