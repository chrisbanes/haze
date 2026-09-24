// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectLifecycleScope

/** Supplies one rendering-host identity to Adaptive Glass within this composition root. */
@ExperimentalHazeApi
@Composable
public fun GlassAdaptiveHost(content: @Composable () -> Unit) {
  val token = remember { GlassAdaptiveHostToken() }
  DisposableEffect(token) {
    onDispose { token.dispose() }
  }
  CompositionLocalProvider(LocalGlassAdaptiveHostToken provides token, content = content)
}

internal class GlassAdaptiveHostToken {
  var isDisposed: Boolean = false
    private set

  fun dispose() {
    isDisposed = true
    glassAdaptiveHostRegistry.disposeHost(this)
  }
}

internal val LocalGlassAdaptiveHostToken = staticCompositionLocalOf<GlassAdaptiveHostToken?> { null }

internal expect fun platformGlassAdaptiveHost(scope: HazeEffectLifecycleScope): Any?

internal expect fun platformGlassAdaptiveTimingSource(
  scope: HazeEffectLifecycleScope,
  hostKey: Any,
): GlassAdaptiveTimingSource?

internal class GlassAdaptiveHostBinding(
  private val registry: GlassAdaptiveHostRegistry = glassAdaptiveHostRegistry,
) {
  private var registration: GlassAdaptiveHostRegistration? = null

  val host: GlassAdaptiveHost? get() = registration?.host

  fun update(scope: HazeEffectLifecycleScope, adaptive: Boolean) {
    if (!adaptive) {
      detach()
      return
    }
    val token = try {
      scope.currentValueOf(LocalGlassAdaptiveHostToken)
    } catch (_: IllegalStateException) {
      null
    } catch (_: ClassCastException) {
      null
    }
    val platformKey = platformGlassAdaptiveHost(scope)
    val lifecycle = try {
      scope.currentValueOf(LocalLifecycleOwner).lifecycle
    } catch (_: IllegalStateException) {
      null
    } catch (_: ClassCastException) {
      null
    }
    if (token?.isDisposed == true || lifecycle == null) {
      detach()
      return
    }
    val timingHostKey = platformKey ?: token
    if (timingHostKey == null) {
      detach()
      return
    }
    val timingSource = platformGlassAdaptiveTimingSource(scope, timingHostKey)
    val key = if (token != null && timingSource != null) {
      val currentKey = registration?.host?.key as? GlassAdaptiveHostKey
      currentKey?.takeIf {
        it.wrapper === token && it.timingIdentity === timingSource.identity
      } ?: GlassAdaptiveHostKey(token, timingSource.identity)
    } else {
      token ?: timingHostKey
    }
    val existing = registration
    if (existing == null || existing.isReleased) {
      registration = registry.register(key).also { it.bindLifecycle(lifecycle) }
    } else {
      existing.rebind(key)
      existing.bindLifecycle(lifecycle)
    }
    registration?.bindTimingSource(timingSource)
  }

  fun detach() {
    registration?.release()
    registration = null
  }

  fun renewDemandLease(scope: HazeEffectLifecycleScope) {
    registration?.renewDemandLease(scope.coroutineScope)
  }
}
