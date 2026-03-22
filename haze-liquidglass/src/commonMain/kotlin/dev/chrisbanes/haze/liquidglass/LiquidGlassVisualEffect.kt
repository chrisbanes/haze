// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext

/**
 * A [VisualEffect] implementation that simulates the iOS-style liquid glass look:
 * refraction, depth layering, specular highlights, and soft tinted glass.
 */
@OptIn(ExperimentalHazeApi::class)
public class LiquidGlassVisualEffect : VisualEffect {

  internal var dirtyTracker: Bitmask by mutableStateOf(Bitmask())
    private set

  internal var delegate: Delegate = FallbackLiquidGlassDelegate(this)
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "delegate changed. Current $field. New: $value" }
        value.attach()
        field.detach()
        field = value
      }
    }

  override fun update(context: VisualEffectContext) {
    if (dirtyTracker.any(LiquidGlassDirtyFields.InvalidateFlags)) {
      context.invalidateDraw()
    }
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    updateDelegate(context, this)

    try {
      with(delegate) { draw(context) }
    } finally {
      resetDirtyTracker()
    }
  }

  override fun shouldClip(): Boolean = edgeSoftness > 0.dp || !shape.hasZeroCornerRadii()

  override fun calculateInputScaleFactor(scale: HazeInputScale): Float = when (scale) {
    is HazeInputScale.None -> 1f
    is HazeInputScale.Fixed -> scale.scale
    HazeInputScale.Auto -> 0.75f
  }

  override fun requireInvalidation(): Boolean = dirtyTracker.any(LiquidGlassDirtyFields.InvalidateFlags)

  override fun calculateLayerBounds(rect: Rect, density: Density): Rect {
    val softnessPx = with(density) { edgeSoftness.toPx() }
    return if (softnessPx > 0f) rect.inflate(softnessPx) else rect
  }

  // ======== Properties ========

  private var _refractionStrength: Float = Float.NaN
  private var _specularIntensity: Float = Float.NaN
  private var _depth: Float = Float.NaN
  private var _ambientResponse: Float = Float.NaN
  private var _edgeSoftness: Dp = Dp.Unspecified
  private var _lightPosition: Offset = Offset.Unspecified
  private var _tint: Color = Color.Unspecified
  private var _blurRadius: Dp = Dp.Unspecified
  private var _refractionHeight: Float = Float.NaN
  private var _chromaticAberrationStrength: Float = Float.NaN
  private var _shape: RoundedCornerShape? = null

  /**
   * Strength of refractive distortion, in the range `0f..1f`.
   */
  public var refractionStrength: Float
    get() = _refractionStrength.takeOrElse { style.refractionStrength }.takeOrElse {
      LiquidGlassDefaults.refractionStrength
    }
    set(value) {
      if (value != _refractionStrength) {
        _refractionStrength = value
        dirtyTracker += LiquidGlassDirtyFields.RefractionStrength
      }
    }

  /**
   * Intensity of specular highlights, in the range `0f..1f`.
   */
  public var specularIntensity: Float
    get() = _specularIntensity.takeOrElse { style.specularIntensity }
      .takeOrElse { LiquidGlassDefaults.specularIntensity }
    set(value) {
      if (value != _specularIntensity) {
        _specularIntensity = value
        dirtyTracker += LiquidGlassDirtyFields.SpecularIntensity
      }
    }

  /**
   * Depth perception factor (0 = flat, 1 = deep layered glass).
   */
  public var depth: Float
    get() = _depth.takeOrElse { style.depth }.takeOrElse { LiquidGlassDefaults.depth }
    set(value) {
      if (value != _depth) {
        _depth = value
        dirtyTracker += LiquidGlassDirtyFields.Depth
      }
    }

  /**
   * Strength of ambient lighting response and Fresnel accent.
   */
  public var ambientResponse: Float
    get() = _ambientResponse.takeOrElse { style.ambientResponse }.takeOrElse {
      LiquidGlassDefaults.ambientResponse
    }
    set(value) {
      if (value != _ambientResponse) {
        _ambientResponse = value
        dirtyTracker += LiquidGlassDirtyFields.AmbientResponse
      }
    }

  /**
   * Glass tint applied to the refracted content.
   */
  public var tint: Color
    get() = _tint.takeOrElse { style.tint }.takeOrElse { LiquidGlassDefaults.tint }
    set(value) {
      if (value != _tint) {
        _tint = value
        dirtyTracker += LiquidGlassDirtyFields.Tint
      }
    }

  /**
   * Softening distance for glass edges.
   */
  public var edgeSoftness: Dp
    get() = _edgeSoftness.takeOrElse { style.edgeSoftness }.takeOrElse {
      LiquidGlassDefaults.edgeSoftness
    }
    set(value) {
      if (value != _edgeSoftness) {
        _edgeSoftness = value
        dirtyTracker += LiquidGlassDirtyFields.EdgeSoftness
      }
    }

  /**
   * Position of the virtual light source. When unspecified, the center of the layer is used.
   */
  public var lightPosition: Offset
    get() = _lightPosition.takeOrElse { style.lightPosition }.takeOrElse { Offset.Unspecified }
    set(value) {
      if (value != _lightPosition) {
        _lightPosition = value
        dirtyTracker += LiquidGlassDirtyFields.LightPosition
      }
    }

  /**
   * Radius of the blur applied to create depth effect.
   */
  public var blurRadius: Dp
    get() = _blurRadius.takeOrElse { style.blurRadius }.takeOrElse {
      LiquidGlassDefaults.blurRadius
    }
    set(value) {
      if (value != _blurRadius) {
        _blurRadius = value
        dirtyTracker += LiquidGlassDirtyFields.BlurRadius
      }
    }

  /**
   * Height of the refraction zone expressed as a fraction of the smallest dimension (0f..1f).
   */
  public var refractionHeight: Float
    get() = _refractionHeight.takeOrElse { style.refractionHeight }.takeOrElse {
      LiquidGlassDefaults.refractionHeight
    }
    set(value) {
      if (value != _refractionHeight) {
        _refractionHeight = value
        dirtyTracker += LiquidGlassDirtyFields.RefractionHeight
      }
    }

  /**
   * Strength of chromatic aberration. TODO: expand to configurable channel/spread controls.
   */
  public var chromaticAberrationStrength: Float
    get() = _chromaticAberrationStrength.takeOrElse { style.chromaticAberrationStrength }
      .takeOrElse { LiquidGlassDefaults.chromaticAberrationStrength }
    set(value) {
      if (value != _chromaticAberrationStrength) {
        _chromaticAberrationStrength = value
        dirtyTracker += LiquidGlassDirtyFields.ChromaticAberration
      }
    }

  /**
   * Shape applied to the glass. Defaults to a rectangle (all radii zero).
   */
  public var shape: RoundedCornerShape
    get() = _shape ?: style.shape ?: LiquidGlassDefaults.shape
    set(value) {
      if (value != _shape) {
        _shape = value
        dirtyTracker += LiquidGlassDirtyFields.Shape
      }
    }

  /**
   * Overall opacity for the effect.
   */
  public var alpha: Float = 1f
    set(value) {
      if (value != field) {
        field = value
        dirtyTracker += LiquidGlassDirtyFields.Alpha
      }
    }

  /**
   * Optional style container that can set multiple parameters at once.
   */
  public var style: LiquidGlassStyle = LiquidGlassStyle.Unspecified
    set(value) {
      if (field != value) {
        onStyleChanged(old = field, new = value)
        field = value
        dirtyTracker += LiquidGlassDirtyFields.Style
      }
    }

  override fun preferClipToAreaBounds(): Boolean = edgeSoftness <= 0.dp && shape.hasZeroCornerRadii()

  internal interface Delegate {
    fun attach() = Unit
    fun DrawScope.draw(context: VisualEffectContext)
    fun detach() = Unit
  }

  private fun resetDirtyTracker() {
    dirtyTracker = Bitmask()
  }

  private fun onStyleChanged(old: LiquidGlassStyle, new: LiquidGlassStyle) {
    if (old.refractionStrength != new.refractionStrength) {
      dirtyTracker += LiquidGlassDirtyFields.RefractionStrength
    }
    if (old.specularIntensity != new.specularIntensity) {
      dirtyTracker += LiquidGlassDirtyFields.SpecularIntensity
    }
    if (old.depth != new.depth) {
      dirtyTracker += LiquidGlassDirtyFields.Depth
    }
    if (old.ambientResponse != new.ambientResponse) {
      dirtyTracker += LiquidGlassDirtyFields.AmbientResponse
    }
    if (old.tint != new.tint) {
      dirtyTracker += LiquidGlassDirtyFields.Tint
    }
    if (old.edgeSoftness != new.edgeSoftness) {
      dirtyTracker += LiquidGlassDirtyFields.EdgeSoftness
    }
    if (old.lightPosition != new.lightPosition) {
      dirtyTracker += LiquidGlassDirtyFields.LightPosition
    }
    if (old.blurRadius != new.blurRadius) {
      dirtyTracker += LiquidGlassDirtyFields.BlurRadius
    }
    if (old.refractionHeight != new.refractionHeight) {
      dirtyTracker += LiquidGlassDirtyFields.RefractionHeight
    }
    if (old.chromaticAberrationStrength != new.chromaticAberrationStrength) {
      dirtyTracker += LiquidGlassDirtyFields.ChromaticAberration
    }
    if (old.shape != new.shape) {
      dirtyTracker += LiquidGlassDirtyFields.Shape
    }
  }

  internal companion object {
    const val TAG = "LiquidGlassVisualEffect"
  }
}

internal expect fun LiquidGlassVisualEffect.updateDelegate(
  context: VisualEffectContext,
  drawScope: DrawScope,
)

private fun RoundedCornerShape.hasZeroCornerRadii(): Boolean = this == LiquidGlassDefaults.shape

private inline fun Float.takeOrElse(default: () -> Float): Float {
  return if (this.isNaN()) default() else this
}
