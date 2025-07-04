// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

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
import dev.chrisbanes.haze.VisualEffect
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

  override fun DrawScope.onPreDrawEffect(node: HazeEffectNode) {
    updateDelegate(this)
  }

  override fun DrawScope.drawEffect(node: HazeEffectNode) {
    try {
      with(delegate) { draw() }
    } finally {
      resetDirtyTracker()
    }
  }

  override fun detach() {
    attachedNode = null
  }

  override fun shouldClip(): Boolean = blurredEdgeTreatment.shape != null

  internal fun requireNode(): HazeEffectNode = attachedNode ?: error("VisualEffect is not attached")

  var dirtyTracker = Bitmask()
    private set

  private fun resetDirtyTracker() {
    dirtyTracker = Bitmask()
  }

  private var _blurEnabled: Boolean? = null
  var blurEnabled: Boolean
    get() = _blurEnabled ?: attachedNode?.state?.blurEnabled ?: HazeDefaults.blurEnabled()
    set(value) {
      if (_blurEnabled == null || value != _blurEnabled) {
        HazeLogger.d(TAG) { "blurEnabled changed. Current: $_blurEnabled. New: $value" }
        _blurEnabled = value
        dirtyTracker += DirtyFields.BlurEnabled
      }
    }

  var blurRadius: Dp = Dp.Unspecified
    get() {
      return field
        .takeOrElse { style.blurRadius }
        .takeOrElse { compositionLocalStyle.blurRadius }
    }
    set(value) {
      if (value != field) {
        field = value
        dirtyTracker += DirtyFields.BlurRadius
      }
    }

  var noiseFactor: Float = -1f
    get() {
      return field
        .takeOrElse { style.noiseFactor }
        .takeOrElse { compositionLocalStyle.noiseFactor }
    }
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
    get() {
      return field
        .takeOrElse { style.backgroundColor }
        .takeOrElse { compositionLocalStyle.backgroundColor }
    }
    set(value) {
      if (value != field) {
        field = value
        dirtyTracker += DirtyFields.BackgroundColor
      }
    }

  var tints: List<HazeTint> = emptyList()
    get() {
      return field.takeIf { it.isNotEmpty() }
        ?: style.tints.takeIf { it.isNotEmpty() }
        ?: compositionLocalStyle.tints.takeIf { it.isNotEmpty() }
        ?: emptyList()
    }
    set(value) {
      if (value != field) {
        field = value
        dirtyTracker += DirtyFields.Tints
      }
    }

  var fallbackTint: HazeTint = HazeTint.Unspecified
    get() {
      return field.takeIf { it.isSpecified }
        ?: style.fallbackTint.takeIf { it.isSpecified }
        ?: compositionLocalStyle.fallbackTint
    }
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
    val blurRadius = blurRadius.takeOrElse { 0.dp }
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

  override fun needContentDrawBehind(): Boolean = delegate is ScrimBlurVisualEffectDelegate

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

internal expect fun createBlurRenderEffect(
  context: PlatformContext,
  density: Density,
  params: RenderEffectParams,
): RenderEffect?
