// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope

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
  val optical = previous == null ||
    depth ||
    previous.optical != current.optical
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

internal class GlassRuntimeSourceSnapshot(
  val captureScale: Float,
  val layerSize: Size,
  val layerOffset: Offset,
  val inputSnapshot: HazeEffectInputSnapshot,
  val backgroundColor: Color = Color.Transparent,
) {
  override fun equals(other: Any?): Boolean =
    other is GlassRuntimeSourceSnapshot &&
      captureScale == other.captureScale &&
      layerSize == other.layerSize &&
      layerOffset == other.layerOffset &&
      inputSnapshot == other.inputSnapshot &&
      backgroundColor == other.backgroundColor

  override fun hashCode(): Int {
    var result = captureScale.hashCode()
    result = 31 * result + layerSize.hashCode()
    result = 31 * result + layerOffset.hashCode()
    result = 31 * result + inputSnapshot.hashCode()
    return 31 * result + backgroundColor.hashCode()
  }
}

internal data class GlassRuntimeSourceState(
  val hasDrawableSource: Boolean,
  val snapshot: GlassRuntimeSourceSnapshot?,
)

internal fun HazeEffectRuntimeDrawScope.resolveGlassRuntimeSourceState(
  captureScale: Float,
  backgroundColor: Color,
  previousSnapshot: GlassRuntimeSourceSnapshot? = null,
): GlassRuntimeSourceState = resolveGlassRuntimeSourceState(
  captureScale = captureScale,
  layerSize = layerSize,
  layerOffset = layerOffset,
  hasDrawableInput = hasDrawableInput,
  inputSnapshot = inputSnapshot,
  backgroundColor = backgroundColor,
  previousSnapshot = previousSnapshot,
)

internal fun resolveGlassRuntimeSourceState(
  captureScale: Float,
  layerSize: Size,
  layerOffset: Offset,
  hasDrawableInput: Boolean,
  inputSnapshot: HazeEffectInputSnapshot?,
  backgroundColor: Color = Color.Transparent,
  previousSnapshot: GlassRuntimeSourceSnapshot? = null,
): GlassRuntimeSourceState {
  if (!hasDrawableInput) return GlassRuntimeSourceState(false, null)
  val currentInputSnapshot = inputSnapshot ?: return GlassRuntimeSourceState(true, null)
  val current = GlassRuntimeSourceSnapshot(
    captureScale = captureScale,
    layerSize = layerSize,
    layerOffset = layerOffset,
    inputSnapshot = currentInputSnapshot,
    backgroundColor = backgroundColor,
  )
  return GlassRuntimeSourceState(
    hasDrawableSource = true,
    snapshot = previousSnapshot?.takeIf { it == current } ?: current,
  )
}
