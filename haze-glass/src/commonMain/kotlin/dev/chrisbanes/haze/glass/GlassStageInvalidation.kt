// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.layer.GraphicsLayer
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.VisualEffectContext

/** Inputs consumed by the retained Glass rendering stages. */
internal data class GlassStageInputs(
  val blur: Any?,
  val depth: Float,
  val optical: Any,
  val detail: Any?,
  val rim: Any?,
)

/** Retained stages that need to be re-recorded. */
internal data class GlassStageInvalidation(
  val blur: Boolean,
  val depth: Boolean,
  val optical: Boolean,
  val detail: Boolean,
  val rim: Boolean,
) {
  companion object {
    val None = GlassStageInvalidation(
      blur = false,
      depth = false,
      optical = false,
      detail = false,
      rim = false,
    )
  }
}

/** Whether each active retained stage currently has all of the layers it requires. */
internal data class GlassStageAvailability(
  val blur: Boolean,
  val depth: Boolean,
  val optical: Boolean,
  val detail: Boolean,
  val rim: Boolean,
)

internal fun calculateStageInvalidation(
  previous: GlassStageInputs?,
  current: GlassStageInputs,
  sourceChanged: Boolean,
): GlassStageInvalidation {
  val blurInputChanged = previous?.blur != current.blur
  val blur = current.blur != null && (sourceChanged || blurInputChanged)
  val depth = sourceChanged || blurInputChanged || previous?.depth != current.depth
  val optical = previous == null || depth || previous.optical != current.optical
  val detail = current.detail != null && (sourceChanged || previous?.detail != current.detail)
  val rim = current.rim != null && previous?.rim != current.rim
  return GlassStageInvalidation(blur, depth, optical, detail, rim)
}

internal fun calculateRequiredStageInvalidation(
  previous: GlassStageInputs?,
  current: GlassStageInputs,
  sourceChanged: Boolean,
  availability: GlassStageAvailability,
): GlassStageInvalidation {
  val changed = calculateStageInvalidation(previous, current, sourceChanged)
  val blur = changed.blur || current.blur != null && !availability.blur
  val depth = changed.depth || blur || !availability.depth
  val optical = changed.optical || depth || !availability.optical
  val detail = changed.detail || current.detail != null && !availability.detail
  val rim = changed.rim || current.rim != null && !availability.rim
  return GlassStageInvalidation(blur, depth, optical, detail, rim)
}

/** A drawable source area represented without retaining its graphics objects. */
internal class GlassSourceArea(
  val areaIdentity: Any,
  val contentLayerIdentity: Any?,
  val contentVersion: Long?,
  val position: Offset,
  val size: Size,
) {
  val isDrawable: Boolean
    get() = contentLayerIdentity != null && size.width > 0f && size.height > 0f

  override fun equals(other: Any?): Boolean =
    other is GlassSourceArea &&
      areaIdentity === other.areaIdentity &&
      contentLayerIdentity === other.contentLayerIdentity &&
      contentVersion == other.contentVersion &&
      position == other.position &&
      size == other.size

  override fun hashCode(): Int {
    var result = areaIdentity.hashCode()
    result = 31 * result + (contentLayerIdentity?.hashCode() ?: 0)
    result = 31 * result + (contentVersion?.hashCode() ?: 0)
    result = 31 * result + position.hashCode()
    return 31 * result + size.hashCode()
  }
}

/** A known, immutable description of a source capture. */
internal class GlassSourceSnapshot(
  val captureScale: Float,
  val layerSize: Size,
  val layerOffset: Offset,
  areas: List<GlassSourceArea>,
) {
  val areas: List<GlassSourceArea> = areas.toList()

  override fun equals(other: Any?): Boolean =
    other is GlassSourceSnapshot &&
      captureScale == other.captureScale &&
      layerSize == other.layerSize &&
      layerOffset == other.layerOffset &&
      areas == other.areas

  override fun hashCode(): Int {
    var result = captureScale.hashCode()
    result = 31 * result + layerSize.hashCode()
    result = 31 * result + layerOffset.hashCode()
    return 31 * result + areas.hashCode()
  }

  fun matches(
    captureScale: Float,
    layerSize: Size,
    layerOffset: Offset,
    areas: List<GlassSourceArea>,
  ): Boolean {
    if (
      this.captureScale != captureScale ||
      this.layerSize != layerSize ||
      this.layerOffset != layerOffset
    ) {
      return false
    }
    var drawableIndex = 0
    for (area in areas) {
      if (!area.isDrawable) continue
      val previous = this.areas.getOrNull(drawableIndex++) ?: return false
      if (
        area.contentVersion == null ||
        previous.areaIdentity !== area.areaIdentity ||
        previous.contentLayerIdentity !== area.contentLayerIdentity ||
        previous.contentVersion != area.contentVersion ||
        previous.position != area.position ||
        previous.size != area.size
      ) {
        return false
      }
    }
    return drawableIndex == this.areas.size
  }
}

/** The drawable-source status for a capture, including its known snapshot when available. */
internal data class GlassSourceState(
  val hasDrawableSource: Boolean,
  val snapshot: GlassSourceSnapshot?,
)

/**
 * Resolves source-capture state from value inputs. A drawable source with an unknown content
 * version intentionally has no snapshot, forcing its caller to recapture.
 */
internal fun resolveGlassSourceState(
  captureScale: Float,
  layerSize: Size,
  layerOffset: Offset,
  areas: List<GlassSourceArea>,
  previousSnapshot: GlassSourceSnapshot? = null,
): GlassSourceState {
  if (previousSnapshot?.matches(captureScale, layerSize, layerOffset, areas) == true) {
    return GlassSourceState(hasDrawableSource = true, snapshot = previousSnapshot)
  }
  var drawableAreas: MutableList<GlassSourceArea>? = null
  for (area in areas) {
    if (!area.isDrawable) continue
    if (area.contentVersion == null) return GlassSourceState(hasDrawableSource = true, snapshot = null)
    if (drawableAreas == null) drawableAreas = mutableListOf()
    drawableAreas += area
  }
  drawableAreas ?: return GlassSourceState(hasDrawableSource = false, snapshot = null)
  return GlassSourceState(
    hasDrawableSource = true,
    snapshot = GlassSourceSnapshot(captureScale, layerSize, layerOffset, drawableAreas),
  )
}

@OptIn(InternalHazeApi::class)
internal fun VisualEffectContext.resolveGlassSourceState(
  captureScale: Float,
  previousSnapshot: GlassSourceSnapshot? = null,
): GlassSourceState {
  if (previousSnapshot?.matches(captureScale, this) == true) {
    return GlassSourceState(hasDrawableSource = true, snapshot = previousSnapshot)
  }
  return resolveGlassSourceState(
    captureScale = captureScale,
    layerSize = layerSize,
    layerOffset = layerOffset,
    areas = areas.map { area -> area.toGlassSourceArea(this) },
  )
}

@OptIn(InternalHazeApi::class)
private fun GlassSourceSnapshot.matches(
  captureScale: Float,
  context: VisualEffectContext,
): Boolean {
  if (
    this.captureScale != captureScale ||
    layerSize != context.layerSize ||
    layerOffset != context.layerOffset
  ) {
    return false
  }
  var drawableIndex = 0
  for (area in context.areas) {
    val layer = area.contentLayer?.takeUnless { it.isReleased || !it.isDrawable } ?: continue
    val previous = areas.getOrNull(drawableIndex++) ?: return false
    val contentVersion = context.contentVersionOf(area) ?: return false
    if (
      previous.areaIdentity !== area ||
      previous.contentLayerIdentity !== layer ||
      previous.contentVersion != contentVersion ||
      previous.position != context.positionOf(area) ||
      previous.size != area.size
    ) {
      return false
    }
  }
  return drawableIndex == areas.size
}

@OptIn(InternalHazeApi::class)
private fun HazeArea.toGlassSourceArea(context: VisualEffectContext): GlassSourceArea {
  val layer = contentLayer?.takeUnless { it.isReleased || !it.isDrawable }
  return GlassSourceArea(
    areaIdentity = this,
    contentLayerIdentity = layer,
    contentVersion = context.contentVersionOf(this),
    position = context.positionOf(this),
    size = size,
  )
}

private val GraphicsLayer.isDrawable: Boolean
  get() = size.width > 0 && size.height > 0
