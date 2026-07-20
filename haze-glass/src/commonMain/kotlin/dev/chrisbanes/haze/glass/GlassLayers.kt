// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize

internal class GlassLayers {
  var source: GraphicsLayer? = null
  var blurPrefiltered: GraphicsLayer? = null
  var blurHorizontal: GraphicsLayer? = null
  var blurred: GraphicsLayer? = null
  var depthMixed: GraphicsLayer? = null
  var optical: GraphicsLayer? = null
  var refractionDetail: GraphicsLayer? = null
  var interactionOptical: GraphicsLayer? = null
  var interactionRefractionDetail: GraphicsLayer? = null
  var interactionLighting: GraphicsLayer? = null
  var rim: GraphicsLayer? = null
  var scaledSize: IntSize? = null
  var blurWorkingSize: IntSize? = null

  val hasSource: Boolean get() = source?.isReleased == false
  val hasBlurPrefiltered: Boolean get() = blurPrefiltered?.isReleased == false
  val hasBlurHorizontal: Boolean get() = blurHorizontal?.isReleased == false
  val hasBlurred: Boolean get() = blurred?.isReleased == false
  val hasDepthMixed: Boolean get() = depthMixed?.isReleased == false
  val hasOptical: Boolean get() = optical?.isReleased == false
  val hasRefractionDetail: Boolean get() = refractionDetail?.isReleased == false
  val hasInteractionOptical: Boolean get() = interactionOptical?.isReleased == false
  val hasInteractionRefractionDetail: Boolean
    get() = interactionRefractionDetail?.isReleased == false
  val hasInteractionLighting: Boolean get() = interactionLighting?.isReleased == false
  val hasRim: Boolean get() = rim?.isReleased == false

  val isEmpty: Boolean
    get() = source == null && blurPrefiltered == null && blurHorizontal == null && blurred == null &&
      depthMixed == null && optical == null && refractionDetail == null &&
      interactionOptical == null && interactionRefractionDetail == null &&
      interactionLighting == null && rim == null

  fun ensureSource(graphicsContext: GraphicsContext): GraphicsLayer =
    ensureLayer(source, graphicsContext).also { source = it }

  fun ensureBlurred(graphicsContext: GraphicsContext): GraphicsLayer =
    ensureLayer(blurred, graphicsContext).also { blurred = it }

  fun ensureBlurHorizontal(graphicsContext: GraphicsContext): GraphicsLayer =
    ensureLayer(blurHorizontal, graphicsContext).also { blurHorizontal = it }

  fun ensureBlurPrefiltered(graphicsContext: GraphicsContext): GraphicsLayer =
    ensureLayer(blurPrefiltered, graphicsContext).also { blurPrefiltered = it }

  fun releaseBlurPrefiltered(graphicsContext: GraphicsContext?) {
    releaseLayer(blurPrefiltered, graphicsContext)
    blurPrefiltered = null
  }

  fun updateBlurWorkingSize(size: IntSize, graphicsContext: GraphicsContext?): Boolean {
    if (blurWorkingSize == size) return false
    if (blurWorkingSize != null) releaseBlurred(graphicsContext)
    blurWorkingSize = size
    return true
  }

  fun ensureDepthMixed(graphicsContext: GraphicsContext): GraphicsLayer =
    ensureLayer(depthMixed, graphicsContext).also { depthMixed = it }

  fun ensureOptical(graphicsContext: GraphicsContext): GraphicsLayer =
    ensureLayer(optical, graphicsContext).also { optical = it }

  fun ensureRefractionDetail(graphicsContext: GraphicsContext): GraphicsLayer =
    ensureLayer(refractionDetail, graphicsContext).also { refractionDetail = it }

  fun prepareRefractionDetail(
    required: Boolean,
    graphicsContext: GraphicsContext,
  ) {
    if (required) ensureRefractionDetail(graphicsContext) else releaseRefractionDetail(graphicsContext)
  }

  fun ensureRim(graphicsContext: GraphicsContext): GraphicsLayer =
    ensureLayer(rim, graphicsContext).also { rim = it }

  fun prepareInteraction(
    optics: Boolean,
    detail: Boolean,
    lighting: Boolean,
    graphicsContext: GraphicsContext,
  ) {
    if (optics) {
      interactionOptical = ensureLayer(interactionOptical, graphicsContext)
    } else {
      releaseLayer(interactionOptical, graphicsContext)
      interactionOptical = null
    }
    if (detail) {
      interactionRefractionDetail = ensureLayer(interactionRefractionDetail, graphicsContext)
    } else {
      releaseLayer(interactionRefractionDetail, graphicsContext)
      interactionRefractionDetail = null
    }
    if (lighting) {
      interactionLighting = ensureLayer(interactionLighting, graphicsContext)
    } else {
      releaseLayer(interactionLighting, graphicsContext)
      interactionLighting = null
    }
  }

  fun releaseBlurred(graphicsContext: GraphicsContext?) {
    releaseBlurPrefiltered(graphicsContext)
    releaseLayer(blurHorizontal, graphicsContext)
    blurHorizontal = null
    releaseLayer(blurred, graphicsContext)
    blurred = null
    blurWorkingSize = null
  }

  fun releaseDepthMixed(graphicsContext: GraphicsContext?) {
    releaseLayer(depthMixed, graphicsContext)
    depthMixed = null
  }

  fun releaseRim(graphicsContext: GraphicsContext?) {
    releaseLayer(rim, graphicsContext)
    rim = null
  }

  fun releaseRefractionDetail(graphicsContext: GraphicsContext?) {
    releaseLayer(refractionDetail, graphicsContext)
    refractionDetail = null
  }

  fun release(graphicsContext: GraphicsContext?) {
    listOfNotNull(
      source,
      blurPrefiltered,
      blurHorizontal,
      blurred,
      depthMixed,
      optical,
      refractionDetail,
      interactionOptical,
      interactionRefractionDetail,
      interactionLighting,
      rim,
    ).forEach { layer ->
      releaseLayer(layer, graphicsContext)
    }
    source = null
    blurPrefiltered = null
    blurHorizontal = null
    blurred = null
    depthMixed = null
    optical = null
    refractionDetail = null
    interactionOptical = null
    interactionRefractionDetail = null
    interactionLighting = null
    rim = null
    scaledSize = null
    blurWorkingSize = null
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
