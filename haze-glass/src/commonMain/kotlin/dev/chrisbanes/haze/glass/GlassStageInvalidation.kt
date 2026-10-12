// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.Poko

/** Inputs consumed by the retained Glass rendering stages. */
internal data class GlassStageInputs(
  val blur: Any?,
  val depth: Float,
  val optical: Any,
  val detail: Any?,
  val rim: Any?,
  /** Interaction uniforms folded into the fused optical effect, or null when they are not. */
  val opticalInteraction: GlassInteractionUniforms? = null,
) {
  /** Whether every input except [rim] equals [other]'s, with the same equality as [equals]. */
  fun hasSameBaseAs(other: GlassStageInputs): Boolean = blur == other.blur &&
    depth.compareTo(other.depth) == 0 &&
    optical == other.optical &&
    opticalInteraction == other.opticalInteraction &&
    detail == other.detail
}

/** Returns [previous] when it already holds these inputs, so an unchanged frame builds nothing. */
internal fun resolveGlassStageInputs(
  previous: GlassStageInputs?,
  blur: Any?,
  depth: Float,
  optical: Any,
  opticalInteraction: GlassInteractionUniforms?,
  detail: Any?,
  rim: Any?,
): GlassStageInputs {
  if (
    previous != null &&
    previous.blur == blur &&
    previous.depth.compareTo(depth) == 0 &&
    previous.optical == optical &&
    previous.opticalInteraction == opticalInteraction &&
    previous.detail == detail &&
    previous.rim == rim
  ) {
    return previous
  }
  return GlassStageInputs(blur, depth, optical, detail, rim, opticalInteraction)
}

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
    previous.optical != current.optical ||
    previous.opticalInteraction != current.opticalInteraction
  val detail = current.detail != null && (sourceChanged || previous?.detail != current.detail)
  val rim = current.rim != null && previous?.rim != current.rim
  return stageInvalidation(blur, depth, optical, detail, rim)
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
  return stageInvalidation(blur, depth, optical, detail, rim)
}

/** Shares [GlassStageInvalidation.None] when nothing is invalidated, so unchanged frames build nothing. */
private fun stageInvalidation(
  blur: Boolean,
  depth: Boolean,
  optical: Boolean,
  detail: Boolean,
  rim: Boolean,
): GlassStageInvalidation = if (blur || depth || optical || detail || rim) {
  GlassStageInvalidation(blur, depth, optical, detail, rim)
} else {
  GlassStageInvalidation.None
}

@Poko
internal class GlassRuntimeSourceSnapshot(
  val captureScale: Float,
  val layerSize: Size,
  val layerOffset: Offset,
  val inputSnapshot: HazeEffectInputSnapshot,
  val backgroundColor: Color = Color.Transparent,
  val mergedBackground: GlassMergedField? = null,
) {
  /** The drawable-source state holding this snapshot, built once so unchanged frames reuse it. */
  val state: GlassRuntimeSourceState by lazy(LazyThreadSafetyMode.NONE) {
    GlassRuntimeSourceState(hasDrawableSource = true, snapshot = this)
  }

  /** Whether a snapshot built from these inputs would equal this one, without building it. */
  fun matches(
    captureScale: Float,
    layerSize: Size,
    layerOffset: Offset,
    inputSnapshot: HazeEffectInputSnapshot,
    backgroundColor: Color,
    mergedBackground: GlassMergedField?,
  ): Boolean = this.captureScale.compareTo(captureScale) == 0 &&
    this.layerSize == layerSize &&
    this.layerOffset == layerOffset &&
    this.inputSnapshot == inputSnapshot &&
    this.backgroundColor == backgroundColor &&
    this.mergedBackground == mergedBackground
}

internal data class GlassRuntimeSourceState(
  val hasDrawableSource: Boolean,
  val snapshot: GlassRuntimeSourceSnapshot?,
) {
  companion object {
    val NoDrawableSource = GlassRuntimeSourceState(hasDrawableSource = false, snapshot = null)
    val UnknownSnapshot = GlassRuntimeSourceState(hasDrawableSource = true, snapshot = null)
  }
}

internal fun HazeEffectRuntimeDrawScope.resolveGlassRuntimeSourceState(
  captureScale: Float,
  backgroundColor: Color,
  previousSnapshot: GlassRuntimeSourceSnapshot? = null,
  mergedBackground: GlassMergedField? = null,
): GlassRuntimeSourceState = resolveGlassRuntimeSourceState(
  captureScale = captureScale,
  layerSize = layerSize,
  layerOffset = layerOffset,
  hasDrawableInput = hasDrawableInput,
  inputSnapshot = inputSnapshot,
  backgroundColor = backgroundColor,
  previousSnapshot = previousSnapshot,
  mergedBackground = mergedBackground,
)

internal fun resolveGlassRuntimeSourceState(
  captureScale: Float,
  layerSize: Size,
  layerOffset: Offset,
  hasDrawableInput: Boolean,
  inputSnapshot: HazeEffectInputSnapshot?,
  backgroundColor: Color = Color.Transparent,
  previousSnapshot: GlassRuntimeSourceSnapshot? = null,
  mergedBackground: GlassMergedField? = null,
): GlassRuntimeSourceState {
  if (!hasDrawableInput) return GlassRuntimeSourceState.NoDrawableSource
  val currentInputSnapshot = inputSnapshot ?: return GlassRuntimeSourceState.UnknownSnapshot
  if (
    previousSnapshot != null &&
    previousSnapshot.matches(
      captureScale,
      layerSize,
      layerOffset,
      currentInputSnapshot,
      backgroundColor,
      mergedBackground,
    )
  ) {
    return previousSnapshot.state
  }
  return GlassRuntimeSourceSnapshot(
    captureScale = captureScale,
    layerSize = layerSize,
    layerOffset = layerOffset,
    inputSnapshot = currentInputSnapshot,
    backgroundColor = backgroundColor,
    mergedBackground = mergedBackground,
  ).state
}
