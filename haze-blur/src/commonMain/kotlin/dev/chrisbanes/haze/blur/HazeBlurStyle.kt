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
 * An opaque, stateless, and shareable program of Blur Style writes.
 *
 * A Style never owns renderer or platform resources. Calling [then] creates a new Style whose
 * writes replay after this one, so the last write to each property wins.
 */
@Immutable
public sealed interface HazeBlurStyle {
  /** Returns a Style that replays [other] after this Style. */
  public fun then(other: HazeBlurStyle): HazeBlurStyle = combineHazeBlurStyles(this, other)

  /** Returns a Style that replays [block] after this Style. */
  public fun then(block: HazeBlurStyleScope.() -> Unit): HazeBlurStyle =
    then(HazeBlurStyle(block))

  public companion object : HazeBlurStyle {
    /** Compatibility name for the empty Style. */
    @Deprecated("Use HazeBlurStyle")
    public val Unspecified: HazeBlurStyle get() = this
  }
}

@Immutable
private class RecordedHazeBlurStyle(
  private val writes: List<HazeBlurStyleScope.() -> Unit>,
) : HazeBlurStyle {
  fun replay(scope: HazeBlurStyleScope) {
    for (write in writes) {
      scope.write()
    }
  }

  fun then(other: RecordedHazeBlurStyle): HazeBlurStyle =
    RecordedHazeBlurStyle(writes + other.writes)
}

/** Creates an opaque, replayable Blur Style from [block]. */
public fun HazeBlurStyle(block: HazeBlurStyleScope.() -> Unit): HazeBlurStyle =
  RecordedHazeBlurStyle(recordWrites(block))

/**
 * Compatibility adapter for the pre-2.0 value Style.
 *
 * Sentinel interpretation is deliberately isolated here. New code should use the Style block.
 */
@Deprecated("Use HazeBlurStyle { ... }")
public fun HazeBlurStyle(
  backgroundColor: Color = Color.Unspecified,
  colorEffects: List<HazeColorEffect>? = null,
  blurRadius: Dp = Dp.Unspecified,
  noiseFactor: Float = -1f,
  fallbackColorEffect: HazeColorEffect = HazeColorEffect.Unspecified,
): HazeBlurStyle = HazeBlurStyle {
  if (backgroundColor.isSpecified) backgroundColor(backgroundColor)
  if (colorEffects != null) colorEffects(colorEffects)
  if (blurRadius.isSpecified) blurRadius(blurRadius)
  if (noiseFactor >= 0f) noiseFactor(noiseFactor)
  if (fallbackColorEffect.isSpecified) fallbackColorEffect(fallbackColorEffect)
}

/** Compatibility adapter for the singular pre-2.0 color effect constructor. */
@Deprecated("Use HazeBlurStyle { ... }")
@Suppress("DEPRECATION")
public fun HazeBlurStyle(
  backgroundColor: Color = Color.Unspecified,
  colorEffect: HazeColorEffect?,
  blurRadius: Dp = Dp.Unspecified,
  noiseFactor: Float = -1f,
  fallbackColorEffect: HazeColorEffect = HazeColorEffect.Unspecified,
): HazeBlurStyle = HazeBlurStyle(
  backgroundColor = backgroundColor,
  colorEffects = colorEffect?.let(::listOf),
  blurRadius = blurRadius,
  noiseFactor = noiseFactor,
  fallbackColorEffect = fallbackColorEffect,
)

private fun combineHazeBlurStyles(
  first: HazeBlurStyle,
  second: HazeBlurStyle,
): HazeBlurStyle = when (first) {
  HazeBlurStyle -> second
  is RecordedHazeBlurStyle -> when (second) {
    HazeBlurStyle -> first
    is RecordedHazeBlurStyle -> first.then(second)
  }
}

internal fun HazeBlurStyle.replay(scope: HazeBlurStyleScope) {
  when (this) {
    HazeBlurStyle -> Unit
    is RecordedHazeBlurStyle -> replay(scope)
  }
}

/**
 * Blur-specific property functions available while constructing a [HazeBlurStyle].
 */
public sealed interface HazeBlurStyleScope {
  public fun blurEnabled(enabled: Boolean)
  public fun blurRadius(radius: Dp)
  public fun noiseFactor(factor: Float)
  public fun backgroundColor(color: Color)
  public fun colorEffects(effects: List<HazeColorEffect>)
  public fun fallbackColorEffect(effect: HazeColorEffect)
  public fun alpha(alpha: Float)
  public fun mask(mask: Brush?)
  public fun progressive(progressive: HazeProgressive?)
  public fun blurredEdgeTreatment(treatment: BlurredEdgeTreatment)
}

private fun recordWrites(
  block: HazeBlurStyleScope.() -> Unit,
): List<HazeBlurStyleScope.() -> Unit> = buildList {
  RecordingHazeBlurStyleScope(this).block()
}

private class RecordingHazeBlurStyleScope(
  private val writes: MutableList<HazeBlurStyleScope.() -> Unit>,
) : HazeBlurStyleScope {
  override fun blurEnabled(enabled: Boolean) {
    writes += { blurEnabled(enabled) }
  }

  override fun blurRadius(radius: Dp) {
    writes += { blurRadius(radius) }
  }

  override fun noiseFactor(factor: Float) {
    writes += { noiseFactor(factor) }
  }

  override fun backgroundColor(color: Color) {
    writes += { backgroundColor(color) }
  }

  override fun colorEffects(effects: List<HazeColorEffect>) {
    val snapshot = effects.toList()
    writes += { colorEffects(snapshot) }
  }

  override fun fallbackColorEffect(effect: HazeColorEffect) {
    writes += { fallbackColorEffect(effect) }
  }

  override fun alpha(alpha: Float) {
    writes += { alpha(alpha) }
  }

  override fun mask(mask: Brush?) {
    writes += { mask(mask) }
  }

  override fun progressive(progressive: HazeProgressive?) {
    writes += { progressive(progressive) }
  }

  override fun blurredEdgeTreatment(treatment: BlurredEdgeTreatment) {
    writes += { blurredEdgeTreatment(treatment) }
  }
}

@Poko
internal class ResolvedHazeBlurStyle(
  val blurEnabled: Boolean,
  val blurRadius: Dp,
  val noiseFactor: Float,
  val backgroundColor: Color,
  val colorEffects: List<HazeColorEffect>,
  val fallbackColorEffect: HazeColorEffect,
  val alpha: Float,
  val mask: Brush?,
  val progressive: HazeProgressive?,
  val blurredEdgeTreatment: BlurredEdgeTreatment,
)

private class HazeBlurStyleAccumulator : HazeBlurStyleScope {
  private var blurEnabled: Boolean = HazeBlurDefaults.blurEnabled()
  private var blurRadius: Dp = HazeBlurDefaults.blurRadius
  private var noiseFactor: Float = HazeBlurDefaults.noiseFactor
  private var backgroundColor: Color = Color.Transparent
  private var colorEffects: List<HazeColorEffect> = emptyList()
  private var fallbackColorEffect: HazeColorEffect = HazeColorEffect.Unspecified
  private var alpha: Float = 1f
  private var mask: Brush? = null
  private var progressive: HazeProgressive? = null
  private var blurredEdgeTreatment: BlurredEdgeTreatment = HazeBlurDefaults.blurredEdgeTreatment

  override fun blurEnabled(enabled: Boolean) {
    blurEnabled = enabled
  }

  override fun blurRadius(radius: Dp) {
    require(radius.isSpecified && radius >= 0.dp) { "blurRadius must be specified and non-negative" }
    blurRadius = radius
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

  override fun fallbackColorEffect(effect: HazeColorEffect) {
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
 * This is a sealed interface with concrete implementations for color filters and tints.
 * Follows the Compose UI model where ColorFilter is a top-level effect.
 */
@Stable
public sealed interface HazeColorEffect {
  /**
   * The blend mode to use when applying the effect.
   */
  public val blendMode: BlendMode

  /**
   * Whether this effect is specified (not [Unspecified]).
   */
  public val isSpecified: Boolean

  /**
   * A color filter effect.
   */
  @Immutable
  public data class ColorFilter(
    public val colorFilter: androidx.compose.ui.graphics.ColorFilter,
    override val blendMode: BlendMode = DefaultBlendMode,
  ) : HazeColorEffect {
    override val isSpecified: Boolean get() = true
  }

  /**
   * A color-based tint effect.
   */
  @Immutable
  public data class TintColor(
    public val color: Color,
    override val blendMode: BlendMode = DefaultBlendMode,
  ) : HazeColorEffect {
    override val isSpecified: Boolean get() = color.isSpecified
  }

  /**
   * A brush-based tint effect.
   */
  @Immutable
  public data class TintBrush(
    public val brush: Brush,
    override val blendMode: BlendMode = DefaultBlendMode,
  ) : HazeColorEffect {
    override val isSpecified: Boolean = true
  }

  /**
   * An unspecified color effect. When used, no effect will be applied.
   */
  public object Unspecified : HazeColorEffect {
    override val blendMode: BlendMode = BlendMode.SrcOver
    override val isSpecified: Boolean = false
  }

  @Suppress("NOTHING_TO_INLINE")
  public companion object {
    /**
     * Default blend mode for effects.
     */
    public val DefaultBlendMode: BlendMode = BlendMode.SrcOver

    /**
     * Creates a color filter effect.
     */
    public inline fun colorFilter(
      colorFilter: androidx.compose.ui.graphics.ColorFilter,
      blendMode: BlendMode = DefaultBlendMode,
    ): HazeColorEffect = ColorFilter(colorFilter, blendMode)

    /**
     * Creates a color-based tint effect.
     */
    public inline fun tint(
      color: Color,
      blendMode: BlendMode = DefaultBlendMode,
    ): HazeColorEffect = TintColor(color, blendMode)

    /**
     * Creates a brush-based tint effect.
     */
    public inline fun tint(
      brush: Brush,
      blendMode: BlendMode = DefaultBlendMode,
    ): HazeColorEffect = TintBrush(brush, blendMode)
  }
}
