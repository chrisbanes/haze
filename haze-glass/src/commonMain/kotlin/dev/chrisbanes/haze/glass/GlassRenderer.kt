// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.unit.Density
import dev.chrisbanes.haze.InteractiveVisualEffect
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.RetainedOutputVisualEffect
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.VisualEffectTransform

@OptIn(InternalHazeApi::class)
internal class GlassRenderer(
  configuration: GlassVisualEffect,
) : VisualEffect, RetainedOutputVisualEffect, InteractiveVisualEffect {
  private var configuration: GlassVisualEffect? = configuration
  private val configurationKey = configuration.rendererCacheKey
  internal val runtimeForTest = GlassRuntimeEffect(configuration)
  private var observedConfigurationRevision = configuration.configurationRevision
  private var observedConfigurationFieldVersions = configuration.configurationFieldVersions()

  private fun synchronizeConfiguration() {
    val configuration = checkNotNull(configuration) {
      "A detached Glass renderer must be acquired before use."
    }
    val revision = configuration.configurationRevision
    if (revision != observedConfigurationRevision) {
      val fieldVersions = configuration.configurationFieldVersions()
      var changedFields = 0
      fieldVersions.indices.forEach { index ->
        if (fieldVersions[index] != observedConfigurationFieldVersions[index]) {
          changedFields = changedFields or (1 shl index)
        }
      }
      runtimeForTest.synchronizeConfigurationFrom(configuration, changedFields)
      observedConfigurationRevision = revision
      observedConfigurationFieldVersions = fieldVersions
    }
  }

  override val observesPointerEvents: Boolean
    get() {
      synchronizeConfiguration()
      return runtimeForTest.observesPointerEvents
    }

  override fun attach(context: VisualEffectContext) {
    synchronizeConfiguration()
    runtimeForTest.attach(context)
  }

  override fun update(context: VisualEffectContext) {
    synchronizeConfiguration()
    runtimeForTest.update(context)
  }

  override fun detach(context: VisualEffectContext) {
    runtimeForTest.detach(context)
    runtimeForTest.clearConfigurationReferences()
    configuration = null
    GlassRendererCache.recycle(configurationKey, this)
  }

  internal fun acquire(configuration: GlassVisualEffect) {
    check(this.configuration == null) { "Glass renderer is already acquired." }
    check(configuration.rendererCacheKey === configurationKey) {
      "Glass renderer cache key does not match its configuration."
    }
    val fieldVersions = configuration.configurationFieldVersions()
    val runtimeEffectFactoryIndex = GlassDirtyFields.RuntimeEffectFactory.countTrailingZeroBits()
    val runtimeEffectFactoryChanged =
      fieldVersions[runtimeEffectFactoryIndex] !=
        observedConfigurationFieldVersions[runtimeEffectFactoryIndex]
    runtimeForTest.reseedConfiguration(configuration, runtimeEffectFactoryChanged)
    this.configuration = configuration
    observedConfigurationRevision = configuration.configurationRevision
    observedConfigurationFieldVersions = fieldVersions
  }

  override fun DrawScope.prepareDraw(context: VisualEffectContext) {
    synchronizeConfiguration()
    with(runtimeForTest) { prepareDraw(context) }
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    synchronizeConfiguration()
    with(runtimeForTest) { draw(context) }
  }

  override fun DrawScope.drawForeground(context: VisualEffectContext) {
    synchronizeConfiguration()
    with(runtimeForTest) { drawForeground(context) }
  }

  override fun onPointerEvent(event: PointerEvent, context: VisualEffectContext) {
    synchronizeConfiguration()
    runtimeForTest.onPointerEvent(event, context)
  }

  override fun onCancelPointerInput(context: VisualEffectContext) {
    runtimeForTest.onCancelPointerInput(context)
  }

  override fun currentContentTransform(context: VisualEffectContext): VisualEffectTransform {
    synchronizeConfiguration()
    return runtimeForTest.currentContentTransform(context)
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    runtimeForTest.onTrimMemory(context, level)
  }

  override fun canDrawRetainedOutput(context: VisualEffectContext): Boolean =
    runtimeForTest.canDrawRetainedOutput(context)

  override fun shouldDrawRetainedOutput(context: VisualEffectContext): Boolean =
    runtimeForTest.shouldDrawRetainedOutput(context)

  override fun clearRetainedOutput() {
    runtimeForTest.clearRetainedOutput()
  }

  override fun shouldDrawContentBehind(context: VisualEffectContext): Boolean =
    runtimeForTest.shouldDrawContentBehind(context)

  override fun shouldClipToNodeBounds(): Boolean {
    synchronizeConfiguration()
    return runtimeForTest.shouldClipToNodeBounds()
  }

  override fun shouldPreferClipToAreaBounds(): Boolean =
    runtimeForTest.shouldPreferClipToAreaBounds()

  override fun calculateLayerBounds(rect: Rect, density: Density): Rect {
    synchronizeConfiguration()
    return runtimeForTest.calculateLayerBounds(rect, density)
  }
}

/**
 * Bounded handoff cache for preserving detached shader handles when Compose replaces a modifier
 * node. Renderers release attached resources before entering the cache and do not retain their
 * configuration while cached.
 *
 * The cache is main-thread confined by the modifier-node lifecycle. Bounding it prevents detached
 * effects that are never reattached from accumulating indefinitely.
 */
internal object GlassRendererCache {
  private const val MAX_ENTRIES = 8

  private val entries = mutableListOf<Pair<Any, GlassRenderer>>()

  fun acquire(configuration: GlassVisualEffect): GlassRenderer {
    val index = entries.indexOfFirst { (key) -> key === configuration.rendererCacheKey }
    return if (index >= 0) {
      entries.removeAt(index).second.also { it.acquire(configuration) }
    } else {
      GlassRenderer(configuration)
    }
  }

  fun recycle(key: Any, renderer: GlassRenderer) {
    entries.removeAll { (_, cachedRenderer) -> cachedRenderer === renderer }
    entries += key to renderer
    if (entries.size > MAX_ENTRIES) {
      entries.removeAt(0).second.runtimeForTest.release()
    }
  }
}
