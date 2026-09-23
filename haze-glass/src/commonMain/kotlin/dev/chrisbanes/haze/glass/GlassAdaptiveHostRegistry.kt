// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

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
    release(registration)
    hostFor(key).also { host ->
      registration.host = host
      host.registrations.add(registration)
    }
  }

  internal fun release(registration: GlassAdaptiveHostRegistration) {
    val host = registration.host
    host.registrations.remove(registration)
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

  val hasActiveDemand: Boolean get() = activeDemandCount > 0
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
    if (activeDemand) host.activeDemandCount += if (value) 1 else -1
    foreground = value
  }

  fun rebind(key: Any) {
    if (released || host.key === key) return
    val wasActive = activeDemand
    setActiveDemand(false)
    registry.rebind(this, key)
    setActiveDemand(wasActive)
  }

  fun setActiveDemand(active: Boolean) {
    if (released || activeDemand == active) return
    activeDemand = active
    if (foreground) host.activeDemandCount += if (active) 1 else -1
  }

  fun release() {
    if (released) return
    setActiveDemand(false)
    lifecycle?.removeObserver(lifecycleObserver)
    lifecycle = null
    released = true
    registry.release(this)
  }
}

internal val glassAdaptiveHostRegistry = GlassAdaptiveHostRegistry()
