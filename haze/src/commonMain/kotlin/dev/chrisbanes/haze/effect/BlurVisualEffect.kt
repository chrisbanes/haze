// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.effect

import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.DirtyFields
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectNode
import dev.chrisbanes.haze.HazeEffectNode.Companion.TAG
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.takeOrElse

/**
 * Abstract class for blur visual effects.
 */
internal class BlurVisualEffect : VisualEffect {

  internal var attachedNode: HazeEffectNode? = null
    private set

  internal var delegate: Delegate = ScrimBlurVisualEffectDelegate(this)
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "delegate changed. Current $field. New: $value" }
        // attach new delgate
        value.attach()
        // detach old delegate
        field.detach()
        field = value
      }
    }

  override fun attach(node: HazeEffectNode) {
    attachedNode = node
  }

  override fun update() {
    val node = requireNode()
    compositionLocalStyle = node.currentValueOf(LocalHazeStyle)
  }

  override fun DrawScope.drawEffect(node: HazeEffectNode) {
    updateDelegate(this)

    with(delegate) { draw() }
  }

  override fun detach() {
    attachedNode = null
  }

  override fun shouldClip(): Boolean = blurredEdgeTreatment.shape != null

  internal fun requireNode(): HazeEffectNode = attachedNode ?: error("VisualEffect is not attached")

  var dirtyTracker = Bitmask()
    private set

  fun resetDirtyTracker() {
    dirtyTracker = Bitmask()
  }

  internal var blurEnabledSet: Boolean = false
  var blurEnabled: Boolean = resolveBlurEnabled()
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "blurEnabled changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += DirtyFields.BlurEnabled
      }
      // Mark the set flag, to indicate that this value should take precedence
      blurEnabledSet = true
    }

  var blurRadius: Dp = Dp.Unspecified
    set(value) {
      if (value != field) {
        field = value
        dirtyTracker += DirtyFields.BlurRadius
      }
    }

  var noiseFactor: Float = -1f
    set(value) {
      if (value != field) {
        field = value
        dirtyTracker += DirtyFields.NoiseFactor
      }
    }

  var mask: Brush? = null
    set(value) {
      if (value != field) {
        field = value
        dirtyTracker += DirtyFields.Mask
      }
    }

  var backgroundColor: Color = Color.Unspecified
    set(value) {
      if (value != field) {
        field = value
        dirtyTracker += DirtyFields.BackgroundColor
      }
    }

  var tints: List<HazeTint> = emptyList()
    set(value) {
      if (value != field) {
        field = value
        dirtyTracker += DirtyFields.Tints
      }
    }

  var fallbackTint: HazeTint = HazeTint.Unspecified
    set(value) {
      if (value != field) {
        field = value
        dirtyTracker += DirtyFields.FallbackTint
      }
    }

  var alpha: Float = 1f
    set(value) {
      if (value != field) {
        field = value
        dirtyTracker += DirtyFields.Alpha
      }
    }

  var progressive: HazeProgressive? = null
    set(value) {
      if (value != field) {
        field = value
        dirtyTracker += DirtyFields.Progressive
      }
    }

  var blurredEdgeTreatment: BlurredEdgeTreatment = HazeDefaults.blurredEdgeTreatment
    set(value) {
      if (value != field) {
        field = value
        dirtyTracker += DirtyFields.BlurredEdgeTreatment
      }
    }

  var compositionLocalStyle: HazeStyle = HazeStyle.Unspecified
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "LocalHazeStyle changed. Current: $field. New: $value" }
        onStyleChanged(field, value)
        field = value
      }
    }

  var style: HazeStyle = HazeStyle.Unspecified
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "style changed. Current: $field. New: $value" }
        onStyleChanged(field, value)
        field = value
      }
    }

  override fun calculateInputScaleFactor(scale: HazeInputScale): Float {
    val blurRadius = resolveBlurRadius().takeOrElse { 0.dp }
    return when (scale) {
      is HazeInputScale.None -> 1f
      is HazeInputScale.Fixed -> scale.scale
      HazeInputScale.Auto -> {
        when {
          // For small blurRadius values, input scaling is very noticeable therefore we turn it off
          blurRadius < 7.dp -> 1f
          // For progressive and masks, we need to keep enough resolution for the lowest intensity.
          // 0.5f is about right.
          progressive != null -> 0.5f
          mask != null -> 0.5f
          // Otherwise we use 1/3
          else -> 0.3334f
        }
      }
    }
  }

  override fun needInvalidation(): Boolean = dirtyTracker.any(DirtyFields.InvalidateFlags)

  private fun onStyleChanged(old: HazeStyle?, new: HazeStyle?) {
    if (old?.tints != new?.tints) dirtyTracker += DirtyFields.Tints
    if (old?.fallbackTint != new?.fallbackTint) dirtyTracker += DirtyFields.Tints
    if (old?.backgroundColor != new?.backgroundColor) dirtyTracker += DirtyFields.BackgroundColor
    if (old?.noiseFactor != new?.noiseFactor) dirtyTracker += DirtyFields.NoiseFactor
    if (old?.blurRadius != new?.blurRadius) dirtyTracker += DirtyFields.BlurRadius
  }

  internal interface Delegate {
    fun attach() = Unit
    fun DrawScope.draw()
    fun detach() = Unit
  }
}

internal expect fun BlurVisualEffect.updateDelegate(drawScope: DrawScope)

internal fun BlurVisualEffect.resolveBackgroundColor(): Color {
  return backgroundColor
    .takeOrElse { style.backgroundColor }
    .takeOrElse { compositionLocalStyle.backgroundColor }
}

internal fun BlurVisualEffect.resolveBlurRadius(): Dp {
  return blurRadius
    .takeOrElse { style.blurRadius }
    .takeOrElse { compositionLocalStyle.blurRadius }
}

internal fun BlurVisualEffect.resolveTints(): List<HazeTint> {
  return tints.takeIf { it.isNotEmpty() }
    ?: style.tints.takeIf { it.isNotEmpty() }
    ?: compositionLocalStyle.tints.takeIf { it.isNotEmpty() }
    ?: emptyList()
}

internal fun BlurVisualEffect.resolveFallbackTint(): HazeTint {
  return fallbackTint.takeIf { it.isSpecified }
    ?: style.fallbackTint.takeIf { it.isSpecified }
    ?: compositionLocalStyle.fallbackTint
}

internal fun BlurVisualEffect.resolveNoiseFactor(): Float {
  return noiseFactor
    .takeOrElse { style.noiseFactor }
    .takeOrElse { compositionLocalStyle.noiseFactor }
}

internal fun BlurVisualEffect.resolveBlurEnabled(): Boolean {
  return if (blurEnabledSet) {
    blurEnabled
  } else {
    attachedNode?.state?.blurEnabled ?: HazeDefaults.blurEnabled()
  }
}

internal expect fun createRenderEffect(
  context: PlatformContext,
  density: Density,
  params: RenderEffectParams,
): RenderEffect?
