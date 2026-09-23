// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlin.test.Test

class GlassAdaptiveHostRegistryTest {

  @Test
  fun registrations_shareHostUntilLastSubscriberReleases() {
    val registry = GlassAdaptiveHostRegistry()
    val hostKey = Any()

    val first = registry.register(hostKey)
    val second = registry.register(hostKey)

    assertThat(second.host).isSameInstanceAs(first.host)
    assertThat(first.host.subscriberCount).isEqualTo(2)

    first.release()
    assertThat(second.host.subscriberCount).isEqualTo(1)

    second.release()
    assertThat(registry.hostCount).isEqualTo(0)
  }

  @Test
  fun hostDemand_stopsWhenLastActiveSubscriberBecomesIdle() {
    val registry = GlassAdaptiveHostRegistry()
    val first = registry.register(Any())
    val second = registry.register(first.host.key)

    first.setActiveDemand(true)
    second.setActiveDemand(true)
    assertThat(first.host.hasActiveDemand).isTrue()

    first.release()
    assertThat(second.host.hasActiveDemand).isTrue()

    second.setActiveDemand(false)
    assertThat(second.host.hasActiveDemand).isFalse()
    second.release()
  }

  @Test
  fun rebind_movesSubscriberBetweenDistinctHosts() {
    val registry = GlassAdaptiveHostRegistry()
    val firstKey = Any()
    val secondKey = Any()
    val moving = registry.register(firstKey)
    val stationary = registry.register(secondKey)
    moving.setActiveDemand(true)

    moving.rebind(secondKey)

    assertThat(moving.host).isSameInstanceAs(stationary.host)
    assertThat(stationary.host.subscriberCount).isEqualTo(2)
    assertThat(stationary.host.hasActiveDemand).isTrue()
    assertThat(registry.hostCount).isEqualTo(1)

    moving.release()
    stationary.release()
  }

  @Test
  fun lifecycleStopSuspendsDemandAndResumeRestoresIt() {
    val owner = HostTestLifecycleOwner()
    val registration = GlassAdaptiveHostRegistry().register(Any())
    registration.bindLifecycle(owner.lifecycle)
    registration.setActiveDemand(true)
    assertThat(registration.host.hasActiveDemand).isFalse()

    owner.handle(Lifecycle.Event.ON_CREATE)
    owner.handle(Lifecycle.Event.ON_START)
    assertThat(registration.host.hasActiveDemand).isTrue()

    owner.handle(Lifecycle.Event.ON_STOP)
    assertThat(registration.host.hasActiveDemand).isFalse()

    registration.release()
    owner.handle(Lifecycle.Event.ON_START)
    assertThat(registration.host.hasActiveDemand).isFalse()
  }

  @Test
  fun hostDisposalReleasesAllSubscribersAndTheirDemand() {
    val registry = GlassAdaptiveHostRegistry()
    val key = Any()
    val first = registry.register(key)
    val second = registry.register(key)
    first.setActiveDemand(true)

    registry.disposeHost(key)

    assertThat(registry.hostCount).isEqualTo(0)
    assertThat(first.host.hasActiveDemand).isFalse()
    assertThat(first.isReleased).isTrue()
    assertThat(second.isReleased).isTrue()
  }
}

private class HostTestLifecycleOwner : LifecycleOwner {
  private val registry = LifecycleRegistry.createUnsafe(this)

  override val lifecycle: Lifecycle get() = registry

  fun handle(event: Lifecycle.Event) = registry.handleLifecycleEvent(event)
}
