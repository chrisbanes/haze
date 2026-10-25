// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.Poko

/**
 * A [ProvidableCompositionLocal] which provides Blur Style writes inherited by all
 * [hazeBlur] modifiers in its content.
 *
 * Resolution applies [HazeBlurDefaults.style], this Style, and the modifier's explicit Style in
 * order. Later writes replace earlier writes.
 */
public val LocalHazeBlurStyle: ProvidableCompositionLocal<HazeBlurStyle> =
  compositionLocalOf { HazeBlurStyle }

/**
 * An opaque, shareable sequence of Blur Style writes.
 *
 * The block passed to [HazeBlurStyle] runs each time a [hazeBlur] node resolves its style, into a
 * fresh node-owned accumulator. It may run on any frame and must not have side effects. Calling
 * [then] creates a new Style whose writes apply after this one, so the last write to each property
 * wins.
 *
 * A block may read snapshot state, such as an animated value. Each consuming node observes those
 * reads; when the state changes the node resolves the Style again and redraws on the next frame
 * without recomposition. Styles created with [HazeBlurStyle] compare by identity, so `remember` or
 * hoist them. Built-in material presets compare by value. A Style never owns renderer or
 * platform resources. Lists passed to [HazeBlurStyleScope.colorEffects] are snapshotted each time
 * the block runs.
 */
@Immutable
public sealed interface HazeBlurStyle {
  /** Returns a Style that applies [other] after this Style. */
  public fun then(other: HazeBlurStyle): HazeBlurStyle = combineHazeBlurStyles(this, other)

  /** Returns a Style that runs [block] after this Style. */
  public fun then(block: HazeBlurStyleScope.() -> Unit): HazeBlurStyle =
    then(HazeBlurStyle(block))

  /** The empty Blur Style, which performs no writes. */
  public companion object : HazeBlurStyle
}

/**
 * A Style that runs one block. With [inputs] it compares by value, so equal inputs are equal;
 * without them it compares by identity.
 */
@Immutable
private class LeafHazeBlurStyle(
  private val inputs: List<Any?>?,
  private val block: HazeBlurStyleScope.() -> Unit,
) : HazeBlurStyle {
  fun replay(scope: HazeBlurStyleScope) {
    scope.block()
  }

  override fun equals(other: Any?): Boolean =
    this === other || (inputs != null && other is LeafHazeBlurStyle && inputs == other.inputs)

  override fun hashCode(): Int = inputs?.hashCode() ?: super.hashCode()
}

@Immutable
private class CombinedHazeBlurStyle(
  val styles: List<HazeBlurStyle>,
) : HazeBlurStyle {
  override fun equals(other: Any?): Boolean =
    this === other || (other is CombinedHazeBlurStyle && styles == other.styles)

  override fun hashCode(): Int = styles.hashCode()

  fun replay(scope: HazeBlurStyleScope) {
    for (style in styles) {
      style.replay(scope)
    }
  }
}

/**
 * Creates a [HazeBlurStyle] which runs [block] whenever a node resolves its style.
 *
 * [block] does not run during this call. It may run on any frame, so it must not have side
 * effects. It may read snapshot state: each consuming node observes those reads and applies a
 * change on the next frame without recomposition. Styles created here compare by identity, so
 * `remember` or hoist a Style rather than constructing one in each composition. Invalid values throw
 * [IllegalArgumentException] when [block] runs.
 */
public fun HazeBlurStyle(block: HazeBlurStyleScope.() -> Unit): HazeBlurStyle =
  LeafHazeBlurStyle(inputs = null, block = block)

/**
 * Creates a [HazeBlurStyle] which compares by value: two Styles are equal when their [inputs] are
 * equal. [inputs] must contain every value [block] captures, plus an identifier of the block
 * itself. Otherwise behaves like [HazeBlurStyle].
 */
@InternalHazeApi
public fun valueBackedHazeBlurStyle(
  vararg inputs: Any?,
  block: HazeBlurStyleScope.() -> Unit,
): HazeBlurStyle = LeafHazeBlurStyle(inputs.toList(), block)

private fun combineHazeBlurStyles(
  first: HazeBlurStyle,
  second: HazeBlurStyle,
): HazeBlurStyle {
  if (first === HazeBlurStyle) return second
  if (second === HazeBlurStyle) return first
  val firstStyles = if (first is CombinedHazeBlurStyle) first.styles else listOf(first)
  val secondStyles = if (second is CombinedHazeBlurStyle) second.styles else listOf(second)
  return CombinedHazeBlurStyle(firstStyles + secondStyles)
}

internal fun HazeBlurStyle.replay(scope: HazeBlurStyleScope) {
  when (this) {
    HazeBlurStyle -> Unit
    is LeafHazeBlurStyle -> replay(scope)
    is CombinedHazeBlurStyle -> replay(scope)
  }
}

/**
 * Blur-specific property functions available while building a [HazeBlurStyle].
 *
 * Property functions validate values when the Style's block runs. Invalid values throw
 * [IllegalArgumentException], except that [blurRadius], [noiseFactor] and [alpha] are clamped into
 * their valid ranges, so animation overshoot never throws.
 */
public sealed interface HazeBlurStyleScope {
  /** Enables or disables the blur pass. */
  public fun blurEnabled(enabled: Boolean)

  /** Sets the blur [radius]. A negative radius, such as animation overshoot, is treated as zero. */
  public fun blurRadius(radius: Dp)

  /** Sets the noise opacity, coerced to the range `0f..1f`. */
  public fun noiseFactor(factor: Float)

  /** Sets the background color composited behind the blurred input. */
  public fun backgroundColor(color: Color)

  /** Replaces the ordered color effects applied to the blurred input. */
  public fun colorEffects(effects: List<HazeColorEffect>)

  /** Sets the effect used when blur rendering is unavailable. */
  public fun fallbackColorEffect(effect: HazeColorEffect?)

  /** Sets the overall effect opacity, coerced to the range `0f..1f`. */
  public fun alpha(alpha: Float)

  /**
   * Sets the optional alpha [mask] applied to the complete effect.
   *
   * When [progressive] is also set, the mask is applied after the progressive intensity, and the
   * two compose: fully transparent mask regions show no effect.
   */
  public fun mask(mask: Brush?)

  /**
   * Sets the optional progressive effect intensity.
   *
   * It can be combined with [mask], which then fades the whole progressive result.
   */
  public fun progressive(progressive: HazeProgressive?)

  /** Sets how content outside the input bounds contributes to the blur. */
  public fun blurredEdgeTreatment(treatment: BlurredEdgeTreatment)
}

@Poko
internal class ResolvedHazeBlurStyle(
  val blurEnabled: Boolean,
  val blurRadius: Dp,
  val noiseFactor: Float,
  val backgroundColor: Color,
  val colorEffects: List<HazeColorEffect>,
  val fallbackColorEffect: HazeColorEffect?,
  val alpha: Float,
  val mask: Brush?,
  val progressive: HazeProgressive?,
  val blurredEdgeTreatment: BlurredEdgeTreatment,
)

private class HazeBlurStyleAccumulator : HazeBlurStyleScope {
  private var blurEnabled: Boolean = HazeBlurDefaults.isBlurEnabledByDefault()
  private var blurRadius: Dp = HazeBlurDefaults.blurRadius
  private var noiseFactor: Float = HazeBlurDefaults.noiseFactor
  private var backgroundColor: Color = Color.Transparent
  private var colorEffects: List<HazeColorEffect> = emptyList()
  private var fallbackColorEffect: HazeColorEffect? = null
  private var alpha: Float = 1f
  private var mask: Brush? = null
  private var progressive: HazeProgressive? = null
  private var blurredEdgeTreatment: BlurredEdgeTreatment = HazeBlurDefaults.blurredEdgeTreatment

  override fun blurEnabled(enabled: Boolean) {
    blurEnabled = enabled
  }

  override fun blurRadius(radius: Dp) {
    require(radius.isSpecified) { "blurRadius must be specified" }
    blurRadius = if (radius < 0.dp) 0.dp else radius
  }

  override fun noiseFactor(factor: Float) {
    require(!factor.isNaN()) { "noiseFactor must not be NaN" }
    noiseFactor = factor.coerceIn(0f, 1f)
  }

  override fun backgroundColor(color: Color) {
    require(color.isSpecified) { "backgroundColor must be specified" }
    backgroundColor = color
  }

  override fun colorEffects(effects: List<HazeColorEffect>) {
    colorEffects = effects.toList()
  }

  override fun fallbackColorEffect(effect: HazeColorEffect?) {
    fallbackColorEffect = effect
  }

  override fun alpha(alpha: Float) {
    require(!alpha.isNaN()) { "alpha must not be NaN" }
    this.alpha = alpha.coerceIn(0f, 1f)
  }

  override fun mask(mask: Brush?) {
    this.mask = mask
  }

  override fun progressive(progressive: HazeProgressive?) {
    this.progressive = progressive
  }

  override fun blurredEdgeTreatment(treatment: BlurredEdgeTreatment) {
    blurredEdgeTreatment = treatment
  }

  fun snapshot(): ResolvedHazeBlurStyle = ResolvedHazeBlurStyle(
    blurEnabled = blurEnabled,
    blurRadius = blurRadius,
    noiseFactor = noiseFactor,
    backgroundColor = backgroundColor,
    colorEffects = colorEffects.toList(),
    fallbackColorEffect = fallbackColorEffect,
    alpha = alpha,
    mask = mask,
    progressive = progressive,
    blurredEdgeTreatment = blurredEdgeTreatment,
  )
}

internal fun resolveHazeBlurStyle(
  localStyle: HazeBlurStyle,
  explicitStyle: HazeBlurStyle,
): ResolvedHazeBlurStyle = HazeBlurStyleAccumulator().also { accumulator ->
  HazeBlurDefaults.style.replay(accumulator)
  localStyle.replay(accumulator)
  explicitStyle.replay(accumulator)
}.snapshot()

/**
 * Describes a color effect applied by the haze effect.
 *
 * Create effects with [colorFilter] or [tint].
 */
@Stable
public sealed interface HazeColorEffect {
  /** Factories for [HazeColorEffect] values. */
  public companion object {
    /**
     * Creates a color filter effect.
     */
    public fun colorFilter(
      colorFilter: androidx.compose.ui.graphics.ColorFilter,
      blendMode: BlendMode = BlendMode.SrcOver,
    ): HazeColorEffect = ColorFilterHazeColorEffect(colorFilter, blendMode)

    /**
     * Creates a color-based tint effect.
     */
    public fun tint(
      color: Color,
      blendMode: BlendMode = BlendMode.SrcOver,
    ): HazeColorEffect {
      require(color.isSpecified) { "color must be specified" }
      return TintColorHazeColorEffect(color, blendMode)
    }

    /**
     * Creates a brush-based tint effect.
     */
    public fun tint(
      brush: Brush,
      blendMode: BlendMode = BlendMode.SrcOver,
    ): HazeColorEffect = TintBrushHazeColorEffect(brush, blendMode)
  }
}

@Immutable
@Poko
internal class ColorFilterHazeColorEffect(
  val colorFilter: androidx.compose.ui.graphics.ColorFilter,
  val blendMode: BlendMode,
) : HazeColorEffect

@Immutable
@Poko
internal class TintColorHazeColorEffect(
  val color: Color,
  val blendMode: BlendMode,
) : HazeColorEffect

@Immutable
@Poko
internal class TintBrushHazeColorEffect(
  val brush: Brush,
  val blendMode: BlendMode,
) : HazeColorEffect
