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
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

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

  @Test
  fun timingObserver_isSharedAndStopsAtIdleOrBackground() {
    val registry = GlassAdaptiveHostRegistry()
    val owner = HostTestLifecycleOwner()
    owner.handle(Lifecycle.Event.ON_CREATE)
    owner.handle(Lifecycle.Event.ON_START)
    val first = registry.register(Any())
    val second = registry.register(first.host.key)
    val source = FakeGlassTimingSource()
    first.bindLifecycle(owner.lifecycle)
    second.bindLifecycle(owner.lifecycle)
    first.bindTimingSource(source)
    second.bindTimingSource(source)

    first.setActiveDemand(true)
    second.setActiveDemand(true)
    assertThat(source.startCount).isEqualTo(1)
    first.release()
    assertThat(source.stopCount).isEqualTo(0)
    owner.handle(Lifecycle.Event.ON_STOP)
    assertThat(source.stopCount).isEqualTo(1)
    owner.handle(Lifecycle.Event.ON_START)
    assertThat(source.startCount).isEqualTo(2)
    second.setActiveDemand(false)
    assertThat(source.stopCount).isEqualTo(2)
    second.release()
  }

  @Test
  fun timingObserver_movesToRemainingActiveSubscriberSource() {
    val registry = GlassAdaptiveHostRegistry()
    val first = registry.register(Any())
    val second = registry.register(first.host.key)
    val firstSource = FakeGlassTimingSource()
    val secondSource = FakeGlassTimingSource()
    first.bindTimingSource(firstSource)
    second.bindTimingSource(secondSource)
    first.setActiveDemand(true)
    second.setActiveDemand(true)

    first.setActiveDemand(false)

    assertThat(firstSource.stopCount).isEqualTo(1)
    assertThat(secondSource.startCount).isEqualTo(1)
    second.release()
    first.release()
  }

  @Test
  fun stoppedSourceCannotFeedHostAfterRebind() {
    val registry = GlassAdaptiveHostRegistry()
    val registration = registry.register(Any())
    val source = FakeGlassTimingSource()
    registration.bindTimingSource(source)
    registration.setActiveDemand(true)
    val oldCallback = source.callback

    registration.rebind(Any())
    assertThat(source.stopCount).isEqualTo(1)
    oldCallback?.invoke(GlassFrameHealthSample(1, 1, 1, 1), 1)
    assertThat(registration.host.hasTimingEvidence).isFalse()
    registration.release()
  }

  @Test
  fun staleSampleDoesNotBecomeTimingEvidence() {
    val registration = GlassAdaptiveHostRegistry().register(Any())
    val source = FakeGlassTimingSource()
    registration.bindTimingSource(source)
    registration.setActiveDemand(true)

    source.callback?.invoke(GlassFrameHealthSample(1, 100L, 16L, 17L), 300_000_101L)
    assertThat(registration.host.hasTimingEvidence).isFalse()
    source.callback?.invoke(GlassFrameHealthSample(2, 300_000_102L, 16L, 17L), 300_000_102L)
    assertThat(registration.host.hasTimingEvidence).isTrue()

    registration.setActiveDemand(false)
    assertThat(registration.host.hasTimingEvidence).isFalse()
    registration.release()
  }

  @Test
  fun sharedHostDecisionVersionChangesForTierAndEvidenceWhileOtherHostStaysIndependent() {
    val registry = GlassAdaptiveHostRegistry()
    val first = registry.register(Any())
    val sibling = registry.register(first.host.key)
    val separate = registry.register(Any())
    val source = FakeGlassTimingSource()
    first.bindTimingSource(source)
    first.setActiveDemand(true)

    val initialVersion = first.host.decision.version
    assertThat(sibling.host).isSameInstanceAs(first.host)
    assertThat(first.host.decision.timingTier).isEqualTo(null)
    assertThat(separate.host.decision.version).isEqualTo(0)

    repeat(30) { index ->
      val timestamp = (index + 1) * 20_000_000L
      source.callback?.invoke(GlassFrameHealthSample(index.toLong(), timestamp, 16_000_000L, 17_000_000L), timestamp)
    }
    assertThat(first.host.decision.version).isEqualTo(initialVersion + 1)
    assertThat(first.host.decision.timingTier).isEqualTo(GlassAdaptiveTier.BALANCED)

    repeat(31) { index ->
      val timestamp = 620_000_000L + index * 34_000_000L
      source.callback?.invoke(GlassFrameHealthSample((index + 30).toLong(), timestamp, 20_000_000L, 17_000_000L), timestamp)
    }
    assertThat(sibling.host.decision.timingTier).isEqualTo(GlassAdaptiveTier.AGGRESSIVE)
    assertThat(sibling.host.decision.version).isEqualTo(initialVersion + 2)
    assertThat(separate.host.decision.version).isEqualTo(0)

    first.setActiveDemand(false)
    assertThat(first.host.decision.timingTier).isEqualTo(null)
    assertThat(first.host.decision.version).isEqualTo(initialVersion + 3)
    first.release()
    sibling.release()
    separate.release()
  }

  @Test
  fun demandLease_expiresAfterLastUpdateAndStopsObservation() = runTest {
    val registration = GlassAdaptiveHostRegistry().register(Any())
    val source = FakeGlassTimingSource()
    registration.bindTimingSource(source)

    registration.renewDemandLease(this)
    assertThat(source.startCount).isEqualTo(1)
    delay(200)
    registration.renewDemandLease(this)
    delay(200)
    assertThat(source.stopCount).isEqualTo(0)
    delay(200)
    assertThat(source.stopCount).isEqualTo(1)
    registration.release()
  }

  @Test
  fun leasedDemand_doesNotTransferToAnotherHostOnRebind() = runTest {
    val registration = GlassAdaptiveHostRegistry().register(Any())
    val source = FakeGlassTimingSource()
    registration.bindTimingSource(source)
    registration.renewDemandLease(this)

    registration.rebind(Any())

    assertThat(source.stopCount).isEqualTo(1)
    assertThat(registration.host.hasActiveDemand).isFalse()
    registration.release()
  }

  @Test
  fun leasedDemand_stopsOnBackgroundAndNeedsFreshUpdateOnResume() = runTest {
    val owner = HostTestLifecycleOwner()
    owner.handle(Lifecycle.Event.ON_CREATE)
    owner.handle(Lifecycle.Event.ON_START)
    val registration = GlassAdaptiveHostRegistry().register(Any())
    val source = FakeGlassTimingSource()
    registration.bindLifecycle(owner.lifecycle)
    registration.bindTimingSource(source)
    registration.renewDemandLease(this)

    owner.handle(Lifecycle.Event.ON_STOP)
    owner.handle(Lifecycle.Event.ON_START)

    assertThat(source.stopCount).isEqualTo(1)
    assertThat(source.startCount).isEqualTo(1)
    assertThat(registration.host.hasActiveDemand).isFalse()
    registration.release()
  }
}

private class FakeGlassTimingSource : GlassAdaptiveTimingSource {
  override val identity: Any = Any()
  var startCount = 0
  var stopCount = 0
  var callback: ((GlassFrameHealthSample, Long) -> Unit)? = null

  override fun start(onSample: (GlassFrameHealthSample, Long) -> Unit): Boolean {
    startCount++
    callback = onSample
    return true
  }

  override fun stop() {
    stopCount++
    callback = null
  }
}

private class HostTestLifecycleOwner : LifecycleOwner {
  private val registry = LifecycleRegistry.createUnsafe(this)

  override val lifecycle: Lifecycle get() = registry

  fun handle(event: Lifecycle.Event) = registry.handleLifecycleEvent(event)
}
