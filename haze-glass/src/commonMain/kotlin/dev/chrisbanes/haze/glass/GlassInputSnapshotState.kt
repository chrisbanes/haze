// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Owner-thread publication state. Completion is ready; only a successful draw is displayed. */
internal class GlassInputSnapshotState<K : Any, V : Any>(
  private val compatible: (K, K) -> Boolean = { _, _ -> false },
  private val release: (V) -> Unit,
) {
  internal class Frame<K, V>(val key: K, val value: V)

  private var epoch = 0L
  private var requested: K? = null
  private var attempted: K? = null
  private var pending: Any? = null
  private var job: Job? = null
  internal var ready: Frame<K, V>? = null
    private set
  internal var displayed: Frame<K, V>? = null
    private set

  internal val isCapturing: Boolean get() = pending != null
  internal val frame: Frame<K, V>? get() = ready ?: displayed

  fun update(key: K?) {
    if (requested == key) return
    val previous = requested
    requested = key
    if (previous != null && key != null && compatible(previous, key)) return
    epoch++
    attempted = null
    job?.cancel()
    ready?.let { release(it.value) }
    ready = null
  }

  fun needsCapture(): Boolean = requested != null && pending == null && ready == null &&
    attempted != requested && displayed?.key != requested

  fun capture(scope: CoroutineScope, capture: suspend () -> V, settled: () -> Unit, finished: () -> Unit = {}) {
    if (!needsCapture()) return
    val key = checkNotNull(requested)
    val captureEpoch = epoch
    val token = Any()
    pending = token
    attempted = key
    val launched = scope.launch(start = CoroutineStart.UNDISPATCHED) {
      var result: V? = null
      try {
        currentCoroutineContext().ensureActive()
        result = capture()
        if (epoch == captureEpoch && requested?.let { it == key || compatible(key, it) } == true) {
          ready = Frame(key, result)
          result = null
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        // Keep only the previously displayed frame. Retry after a new input revision.
      } finally {
        result?.let(release)
      }
    }
    if (pending === token) job = launched
    launched.invokeOnCompletion {
      try {
        finished()
      } finally {
        if (pending === token) {
          pending = null
          job = null
        }
        settled()
      }
    }
  }

  fun didDraw(frame: Frame<K, V>) {
    if (ready !== frame) return
    val previous = displayed
    displayed = frame
    ready = null
    previous?.let { release(it.value) }
  }

  /** Consumers must have released their graphics layers before calling this. */
  fun clear() {
    epoch++
    requested = null
    attempted = null
    job?.cancel()
    ready?.let { release(it.value) }
    displayed?.let { release(it.value) }
    ready = null
    displayed = null
  }
}
