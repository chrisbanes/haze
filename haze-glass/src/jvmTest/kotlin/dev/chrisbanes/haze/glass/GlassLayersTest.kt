// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalComposeUiApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isNull
import kotlin.test.Test
import sun.misc.Unsafe

class GlassLayersTest {
  @Test
  fun releaseLayer_withoutContext_releasesThroughAcquiringContext() {
    val acquiring = RecordingGraphicsContext()
    val layers = GlassLayers()
    val source = layers.ensureSource(acquiring)
    val rim = layers.ensureRim(acquiring)

    layers.releaseRim(null)
    layers.release(null)

    assertThat(acquiring.released).containsExactly(rim, source)
    assertThat(layers.source).isNull()
    assertThat(layers.rim).isNull()
    // A second release finds nothing left to release.
    layers.release(null)
    assertThat(acquiring.released).containsExactly(rim, source)
  }

  @Test
  fun release_usesAcquiringContextEvenWhenAnotherContextIsPassed() {
    val acquiring = RecordingGraphicsContext()
    val other = RecordingGraphicsContext()
    val layers = GlassLayers()
    val optical = layers.ensureOptical(acquiring)
    val lighting = layers.ensureRim(acquiring)

    layers.release(other)

    assertThat(acquiring.released).containsExactly(optical, lighting)
    assertThat(other.released).isEmpty()
  }

  @Test
  fun release_fallsBackToPassedContextForALayerAssignedDirectly() {
    val context = RecordingGraphicsContext()
    val layers = GlassLayers()
    val layer = context.createGraphicsLayer()
    layers.rim = layer

    layers.release(context)

    assertThat(context.released).containsExactly(layer)
  }

  @Test
  fun release_afterTheSameLayerIsAssignedBack_stillReleasesThroughAcquiringContext() {
    val acquiring = RecordingGraphicsContext()
    val layers = GlassLayers()
    val source = layers.ensureSource(acquiring)

    // A recording that reuses the live layer assigns the same instance back.
    layers.source = layers.source
    layers.release(null)

    assertThat(acquiring.released).containsExactly(source)
  }

  @Test
  fun adoptSource_recordsTheCreatorOfANewLayerAndKeepsItForTheSameLayer() {
    val creator = RecordingGraphicsContext()
    val layers = GlassLayers()
    val source = creator.createGraphicsLayer()

    layers.adoptSource(source, creator)
    layers.adoptSource(source, RecordingGraphicsContext())
    layers.release(null)

    assertThat(creator.released).containsExactly(source)
  }

  @Test
  fun groupAlphaRelease_withoutContext_releasesThroughAcquiringContext() {
    val acquiring = RecordingGraphicsContext()
    val groupAlpha = RetainedGlassGroupAlphaLayer()
    groupAlpha.prepare(required = true, graphicsContext = acquiring)
    val layer = checkNotNull(groupAlpha.layer)

    groupAlpha.release(null)

    assertThat(acquiring.released).containsExactly(layer)
    assertThat(groupAlpha.layer).isNull()
  }

  private class RecordingGraphicsContext : GraphicsContext {
    val released = mutableListOf<GraphicsLayer>()

    override fun createGraphicsLayer(): GraphicsLayer =
      unsafe.allocateInstance(GraphicsLayer::class.java) as GraphicsLayer

    override fun releaseGraphicsLayer(layer: GraphicsLayer) {
      released += layer
    }
  }
}

private val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
  isAccessible = true
  get(null) as Unsafe
}
