// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize
import dev.chrisbanes.haze.TrimMemoryLevel

internal fun shouldReleaseRetainedGlass(level: TrimMemoryLevel): Boolean =
  level == TrimMemoryLevel.UI_HIDDEN ||
    level.severity >= TrimMemoryLevel.MODERATE.severity

private val RetainedGlassReleaseOrder = arrayOf(
  GlassRetainedLayerKind.Source,
  GlassRetainedLayerKind.BlurPrefilter,
  GlassRetainedLayerKind.BlurHorizontal,
  GlassRetainedLayerKind.Blurred,
  GlassRetainedLayerKind.DepthMixed,
  GlassRetainedLayerKind.Optical,
  GlassRetainedLayerKind.RefractionDetail,
  GlassRetainedLayerKind.RefractionDetailCoverage,
  GlassRetainedLayerKind.RefractionComposite,
  GlassRetainedLayerKind.InteractionOptical,
  GlassRetainedLayerKind.InteractionDetail,
  GlassRetainedLayerKind.InteractionDetailCoverage,
  GlassRetainedLayerKind.InteractionComposite,
  GlassRetainedLayerKind.InteractionLighting,
  GlassRetainedLayerKind.Rim,
)

internal class GlassLayers {
  private val retained = arrayOfNulls<GraphicsLayer>(GlassRetainedLayerKind.entries.size)

  val groupAlpha = RetainedGlassGroupAlphaLayer()

  var source: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.Source)
    set(value) = set(GlassRetainedLayerKind.Source, value)

  var blurPrefiltered: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.BlurPrefilter)
    set(value) = set(GlassRetainedLayerKind.BlurPrefilter, value)

  var blurHorizontal: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.BlurHorizontal)
    set(value) = set(GlassRetainedLayerKind.BlurHorizontal, value)

  var blurred: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.Blurred)
    set(value) = set(GlassRetainedLayerKind.Blurred, value)

  var depthMixed: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.DepthMixed)
    set(value) = set(GlassRetainedLayerKind.DepthMixed, value)

  var optical: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.Optical)
    set(value) = set(GlassRetainedLayerKind.Optical, value)

  var refractionDetail: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.RefractionDetail)
    set(value) = set(GlassRetainedLayerKind.RefractionDetail, value)

  var refractionDetailCoverage: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.RefractionDetailCoverage)
    set(value) = set(GlassRetainedLayerKind.RefractionDetailCoverage, value)

  var refractionComposite: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.RefractionComposite)
    set(value) = set(GlassRetainedLayerKind.RefractionComposite, value)

  var interactionOptical: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.InteractionOptical)
    set(value) = set(GlassRetainedLayerKind.InteractionOptical, value)

  var interactionRefractionDetail: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.InteractionDetail)
    set(value) = set(GlassRetainedLayerKind.InteractionDetail, value)

  var interactionRefractionDetailCoverage: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.InteractionDetailCoverage)
    set(value) = set(GlassRetainedLayerKind.InteractionDetailCoverage, value)

  var interactionRefractionComposite: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.InteractionComposite)
    set(value) = set(GlassRetainedLayerKind.InteractionComposite, value)

  var interactionLighting: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.InteractionLighting)
    set(value) = set(GlassRetainedLayerKind.InteractionLighting, value)

  var rim: GraphicsLayer?
    get() = get(GlassRetainedLayerKind.Rim)
    set(value) = set(GlassRetainedLayerKind.Rim, value)

  var scaledSize: IntSize? = null
  var blurWorkingSize: IntSize? = null

  val hasSource: Boolean get() = has(GlassRetainedLayerKind.Source)
  val hasBlurPrefiltered: Boolean get() = has(GlassRetainedLayerKind.BlurPrefilter)
  val hasBlurHorizontal: Boolean get() = has(GlassRetainedLayerKind.BlurHorizontal)
  val hasBlurred: Boolean get() = has(GlassRetainedLayerKind.Blurred)
  val hasDepthMixed: Boolean get() = has(GlassRetainedLayerKind.DepthMixed)
  val hasOptical: Boolean get() = has(GlassRetainedLayerKind.Optical)
  val hasRefractionDetail: Boolean get() = has(GlassRetainedLayerKind.RefractionDetail)
  val hasRefractionDetailCoverage: Boolean
    get() = has(GlassRetainedLayerKind.RefractionDetailCoverage)
  val hasRefractionComposite: Boolean get() = has(GlassRetainedLayerKind.RefractionComposite)
  val hasInteractionOptical: Boolean get() = has(GlassRetainedLayerKind.InteractionOptical)
  val hasInteractionRefractionDetail: Boolean
    get() = has(GlassRetainedLayerKind.InteractionDetail)
  val hasInteractionRefractionDetailCoverage: Boolean
    get() = has(GlassRetainedLayerKind.InteractionDetailCoverage)
  val hasInteractionRefractionComposite: Boolean
    get() = has(GlassRetainedLayerKind.InteractionComposite)
  val hasInteractionLighting: Boolean get() = has(GlassRetainedLayerKind.InteractionLighting)
  val hasRim: Boolean get() = has(GlassRetainedLayerKind.Rim)

  val isEmpty: Boolean
    get() = groupAlpha.layer == null && retained.all { it == null }

  fun ensureSource(graphicsContext: GraphicsContext): GraphicsLayer =
    ensure(GlassRetainedLayerKind.Source, graphicsContext)

  fun ensureBlurred(graphicsContext: GraphicsContext): GraphicsLayer =
    ensure(GlassRetainedLayerKind.Blurred, graphicsContext)

  fun ensureBlurHorizontal(graphicsContext: GraphicsContext): GraphicsLayer =
    ensure(GlassRetainedLayerKind.BlurHorizontal, graphicsContext)

  fun ensureBlurPrefiltered(graphicsContext: GraphicsContext): GraphicsLayer =
    ensure(GlassRetainedLayerKind.BlurPrefilter, graphicsContext)

  fun releaseBlurPrefiltered(graphicsContext: GraphicsContext?) {
    release(GlassRetainedLayerKind.BlurPrefilter, graphicsContext)
  }

  fun releaseBlurIntermediates(graphicsContext: GraphicsContext?) {
    releaseBlurPrefiltered(graphicsContext)
    release(GlassRetainedLayerKind.BlurHorizontal, graphicsContext)
  }

  fun updateBlurWorkingSize(size: IntSize, graphicsContext: GraphicsContext?): Boolean {
    if (blurWorkingSize == size) return false
    if (blurWorkingSize != null) releaseBlurred(graphicsContext)
    blurWorkingSize = size
    return true
  }

  fun ensureDepthMixed(graphicsContext: GraphicsContext): GraphicsLayer =
    ensure(GlassRetainedLayerKind.DepthMixed, graphicsContext)

  fun ensureOptical(graphicsContext: GraphicsContext): GraphicsLayer =
    ensure(GlassRetainedLayerKind.Optical, graphicsContext)

  fun ensureRefractionDetail(graphicsContext: GraphicsContext): GraphicsLayer =
    ensure(GlassRetainedLayerKind.RefractionDetail, graphicsContext)

  fun ensureRefractionDetailCoverage(graphicsContext: GraphicsContext): GraphicsLayer =
    ensure(GlassRetainedLayerKind.RefractionDetailCoverage, graphicsContext)

  fun ensureRefractionComposite(graphicsContext: GraphicsContext): GraphicsLayer =
    ensure(GlassRetainedLayerKind.RefractionComposite, graphicsContext)

  fun prepareRefractionDetail(
    required: Boolean,
    graphicsContext: GraphicsContext,
  ) {
    if (required) ensureRefractionDetail(graphicsContext) else releaseRefractionDetail(graphicsContext)
  }

  fun ensureRim(graphicsContext: GraphicsContext): GraphicsLayer =
    ensure(GlassRetainedLayerKind.Rim, graphicsContext)

  fun prepareInteraction(
    optics: Boolean,
    detail: Boolean,
    lighting: Boolean,
    graphicsContext: GraphicsContext,
  ) {
    if (optics) {
      ensure(GlassRetainedLayerKind.InteractionOptical, graphicsContext)
    } else {
      release(GlassRetainedLayerKind.InteractionOptical, graphicsContext)
    }
    if (detail) {
      ensure(GlassRetainedLayerKind.InteractionDetail, graphicsContext)
      ensure(GlassRetainedLayerKind.InteractionDetailCoverage, graphicsContext)
      ensure(GlassRetainedLayerKind.InteractionComposite, graphicsContext)
    } else {
      releaseInteractionRefractionDetail(graphicsContext)
    }
    if (lighting) {
      ensure(GlassRetainedLayerKind.InteractionLighting, graphicsContext)
    } else {
      release(GlassRetainedLayerKind.InteractionLighting, graphicsContext)
    }
  }

  fun releaseBlurred(graphicsContext: GraphicsContext?) {
    releaseBlurIntermediates(graphicsContext)
    release(GlassRetainedLayerKind.Blurred, graphicsContext)
    blurWorkingSize = null
  }

  fun releaseDepthMixed(graphicsContext: GraphicsContext?) {
    release(GlassRetainedLayerKind.DepthMixed, graphicsContext)
  }

  fun releaseRim(graphicsContext: GraphicsContext?) {
    release(GlassRetainedLayerKind.Rim, graphicsContext)
  }

  fun releaseRefractionDetail(graphicsContext: GraphicsContext?) {
    release(GlassRetainedLayerKind.RefractionDetail, graphicsContext)
    release(GlassRetainedLayerKind.RefractionDetailCoverage, graphicsContext)
    release(GlassRetainedLayerKind.RefractionComposite, graphicsContext)
  }

  fun releaseInteractionRefractionDetail(graphicsContext: GraphicsContext?) {
    release(GlassRetainedLayerKind.InteractionDetail, graphicsContext)
    release(GlassRetainedLayerKind.InteractionDetailCoverage, graphicsContext)
    release(GlassRetainedLayerKind.InteractionComposite, graphicsContext)
  }

  fun prepareBackdrop(
    rim: Boolean,
    interactionLighting: Boolean,
    graphicsContext: GraphicsContext,
  ) {
    groupAlpha.release(graphicsContext)
    release(GlassRetainedLayerKind.Source, graphicsContext)
    releaseBlurred(graphicsContext)
    releaseDepthMixed(graphicsContext)
    release(GlassRetainedLayerKind.Optical, graphicsContext)
    releaseRefractionDetail(graphicsContext)
    prepareInteraction(
      optics = false,
      detail = false,
      lighting = interactionLighting,
      graphicsContext = graphicsContext,
    )
    if (rim) ensureRim(graphicsContext) else releaseRim(graphicsContext)
  }

  fun release(graphicsContext: GraphicsContext?) {
    groupAlpha.release(graphicsContext)
    RetainedGlassReleaseOrder.forEach { kind ->
      releaseLayer(get(kind), graphicsContext)
    }
    retained.fill(null)
    scaledSize = null
    blurWorkingSize = null
  }

  private operator fun get(kind: GlassRetainedLayerKind): GraphicsLayer? = retained[kind.ordinal]

  private operator fun set(kind: GlassRetainedLayerKind, layer: GraphicsLayer?) {
    retained[kind.ordinal] = layer
  }

  private fun has(kind: GlassRetainedLayerKind): Boolean = get(kind)?.isReleased == false

  private fun ensure(
    kind: GlassRetainedLayerKind,
    graphicsContext: GraphicsContext,
  ): GraphicsLayer = ensureLayer(get(kind), graphicsContext).also { set(kind, it) }

  private fun release(
    kind: GlassRetainedLayerKind,
    graphicsContext: GraphicsContext?,
  ) {
    releaseLayer(get(kind), graphicsContext)
    set(kind, null)
  }
}

internal fun ensureLayer(
  current: GraphicsLayer?,
  graphicsContext: GraphicsContext,
): GraphicsLayer = current?.takeUnless { it.isReleased } ?: graphicsContext.createGraphicsLayer()

internal fun releaseLayer(layer: GraphicsLayer?, graphicsContext: GraphicsContext?) {
  if (layer?.isReleased == false) {
    graphicsContext?.releaseGraphicsLayer(layer)
  }
}
