// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.coroutines.ContinuationInterceptor
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal interface GlassAdaptiveTimingSource {
  val identity: Any
  fun start(onSample: (GlassFrameHealthSample, Long) -> Unit): Boolean
  fun stop()
}

internal class GlassAdaptiveHostRegistry {
  private val hosts = mutableListOf<GlassAdaptiveHost>()

  val hostCount: Int get() = hosts.size

  fun register(key: Any): GlassAdaptiveHostRegistration {
    val host = hostFor(key)
    return GlassAdaptiveHostRegistration(this, host).also(host.registrations::add)
  }

  private fun hostFor(key: Any): GlassAdaptiveHost =
    hosts.firstOrNull { it.key === key }
      ?: GlassAdaptiveHost(key).also(hosts::add)

  internal fun rebind(registration: GlassAdaptiveHostRegistration, key: Any) {
    registration.bindTimingSource(null)
    release(registration)
    hostFor(key).also { host ->
      registration.host = host
      host.registrations.add(registration)
      host.syncTiming()
    }
  }

  internal fun release(registration: GlassAdaptiveHostRegistration) {
    val host = registration.host
    host.registrations.remove(registration)
    host.syncTiming()
    if (host.registrations.isEmpty()) hosts.remove(host)
  }

  fun disposeHost(key: Any) {
    hosts.firstOrNull { it.key === key }?.registrations?.toList()?.forEach { it.release() }
  }
}

internal class GlassAdaptiveHost(
  val key: Any,
) {
  internal val registrations = mutableSetOf<GlassAdaptiveHostRegistration>()

  val subscriberCount: Int get() = registrations.size

  internal var activeDemandCount: Int = 0
    private set

  val hasActiveDemand: Boolean get() = activeDemandCount > 0

  val controller = GlassAdaptiveTierController()
  private var lastValidSample: TimeMark? = null
  private var lastBudgetNanos: Long? = null
  val hasTimingEvidence: Boolean
    get() = lastValidSample?.elapsedNow()?.let { it <= 250.milliseconds } == true

  private var source: GlassAdaptiveTimingSource? = null
  private var observing = false
  private var generation = 0

  internal fun changeActiveDemand(delta: Int) {
    activeDemandCount += delta
    syncTiming()
  }

  internal fun syncTiming() {
    val next = registrations.firstNotNullOfOrNull { registration ->
      if (registration.isDemanding) registration.timingSource else null
    }
    if (source?.identity !== next?.identity) {
      stopTiming()
      source = next
    }
    if (hasActiveDemand && source != null && !observing) {
      val activeSource = source ?: return
      val currentGeneration = ++generation
      observing = true
      if (!activeSource.start { sample, nowNanos ->
          if (observing && generation == currentGeneration && hasActiveDemand) {
            val previousBudget = lastBudgetNanos
            if (
              sample.isValid && previousBudget != null &&
              abs(sample.budgetNanos - previousBudget) > previousBudget / 20
            ) {
              controller.suspendEvidence()
            }
            if (sample.isValid) lastBudgetNanos = sample.budgetNanos
            if (
              sample.isValid && sample.timestampNanos <= nowNanos &&
              nowNanos - sample.timestampNanos <= 250_000_000L
            ) {
              lastValidSample = TimeSource.Monotonic.markNow()
            } else {
              lastValidSample = null
            }
            controller.observe(sample, nowNanos)
          }
        }
      ) {
        observing = false
      }
    } else if (!hasActiveDemand) {
      stopTiming()
    }
  }

  private fun stopTiming() {
    if (observing) {
      generation++
      observing = false
      source?.stop()
    }
    lastValidSample = null
    lastBudgetNanos = null
    controller.suspendEvidence()
  }
}

internal class GlassAdaptiveHostRegistration(
  private val registry: GlassAdaptiveHostRegistry,
  host: GlassAdaptiveHost,
) {
  var host: GlassAdaptiveHost = host
    internal set

  private var released = false
  val isReleased: Boolean get() = released
  private var activeDemand = false
  private var foreground = true
  internal val isDemanding: Boolean get() = activeDemand && foreground
  private var demandLeaseJob: Job? = null
  internal var timingSource: GlassAdaptiveTimingSource? = null
    private set
  private var lifecycle: Lifecycle? = null
  private val lifecycleObserver = LifecycleEventObserver { _, _ ->
    updateForeground(lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true)
  }

  fun bindLifecycle(value: Lifecycle) {
    if (released || lifecycle === value) return
    lifecycle?.removeObserver(lifecycleObserver)
    lifecycle = value
    updateForeground(value.currentState.isAtLeast(Lifecycle.State.STARTED))
    value.addObserver(lifecycleObserver)
  }

  private fun updateForeground(value: Boolean) {
    if (foreground == value) return
    if (!value && demandLeaseJob != null) {
      demandLeaseJob?.cancel()
      demandLeaseJob = null
      setActiveDemand(false)
    }
    foreground = value
    if (activeDemand) host.changeActiveDemand(if (value) 1 else -1)
  }

  /** A natural node update renews demand; silence expires without requesting frames forever. */
  fun renewDemandLease(nodeScope: CoroutineScope) {
    if (released || !foreground) return
    val dispatcher = nodeScope.coroutineContext[ContinuationInterceptor] ?: return
    demandLeaseJob?.cancel()
    val owner = SupervisorJob()
    demandLeaseJob = owner
    setActiveDemand(true)
    CoroutineScope(owner + dispatcher).launch {
      delay(350)
      if (demandLeaseJob === owner) {
        demandLeaseJob = null
        setActiveDemand(false)
        owner.cancel()
      }
    }
  }

  fun bindTimingSource(value: GlassAdaptiveTimingSource?) {
    if (released || timingSource?.identity === value?.identity) return
    timingSource = value
    host.syncTiming()
  }

  fun rebind(key: Any) {
    if (released || host.key === key) return
    val wasActive = activeDemand
    val wasLeased = demandLeaseJob != null
    setActiveDemand(false)
    registry.rebind(this, key)
    if (!wasLeased) setActiveDemand(wasActive)
  }

  fun setActiveDemand(active: Boolean) {
    if (released || activeDemand == active) return
    if (!active) {
      demandLeaseJob?.cancel()
      demandLeaseJob = null
    }
    activeDemand = active
    if (foreground) host.changeActiveDemand(if (active) 1 else -1)
  }

  fun release() {
    if (released) return
    demandLeaseJob?.cancel()
    demandLeaseJob = null
    setActiveDemand(false)
    lifecycle?.removeObserver(lifecycleObserver)
    lifecycle = null
    timingSource = null
    released = true
    registry.release(this)
  }
}

internal val glassAdaptiveHostRegistry = GlassAdaptiveHostRegistry()
