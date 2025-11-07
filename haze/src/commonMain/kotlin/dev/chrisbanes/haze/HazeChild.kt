// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import kotlin.jvm.JvmInline

@Deprecated(
  message = "Renamed to HazeEffectScope",
  replaceWith = ReplaceWith(
    "HazeEffectScope",
    "dev.chrisbanes.haze.HazeEffectScope",
  ),
)
public interface HazeChildScope : HazeEffectScope

public interface HazeEffectScope {

  /**
   * Whether the blur effect is enabled or not, when running on platforms which support blurring.
   *
   * When set to `false` a scrim effect will be used. When set to `true`, and running on a platform
   * which does not support blurring, a scrim effect will be used.
   *
   * Defaults to [HazeDefaults.blurEnabled].
   */
  public var blurEnabled: Boolean

  /**
   * The opacity that the overall effect will drawn with, in the range of 0..1.
   */
  public var alpha: Float

  /**
   * Style set on this specific [HazeEffectNode].
   *
   * There are precedence rules to how each styling property is applied. The order of precedence
   * for each property are as follows:
   *
   *  - Property value set in [HazeEffectScope], if specified.
   *  - Value set here in [HazeEffectScope.style], if specified.
   *  - Value set in the [LocalHazeStyle] composition local.
   */
  public var style: HazeStyle

  /**
   * Optional alpha mask which allows effects such as fading via a
   * [Brush.verticalGradient] or similar. This is only applied when [progressive] is null.
   *
   * An alpha mask provides a similar effect as that provided as [HazeProgressive], in a more
   * performant way, but may provide a less pleasing visual result.
   */
  public var mask: Brush?

  /**
   * Color to draw behind the blurred content. Ideally should be opaque
   * so that the original content is not visible behind. Typically this would be
   * `MaterialTheme.colorScheme.surface` or similar.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [HazeStyle.backgroundColor] value set in [style], if specified.
   *  - [HazeStyle.backgroundColor] value set in the [LocalHazeStyle] composition local.
   */
  public var backgroundColor: Color

  /**
   * The [HazeTint]s to apply to the blurred content.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if not empty.
   *  - [HazeStyle.tints] value set in [style], if not empty.
   *  - [HazeStyle.tints] value set in the [LocalHazeStyle] composition local.
   */
  public var tints: List<HazeTint>

  /**
   * Radius of the blur.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [HazeStyle.blurRadius] value set in [style], if specified.
   *  - [HazeStyle.blurRadius] value set in the [LocalHazeStyle] composition local.
   */
  public var blurRadius: Dp

  /**
   * Amount of noise applied to the content, in the range `0f` to `1f`.
   * Anything outside of that range will be clamped.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if in the range 0f..1f.
   *  - [HazeStyle.noiseFactor] value set in [style], if in the range 0f..1f.
   *  - [HazeStyle.noiseFactor] value set in the [LocalHazeStyle] composition local.
   */
  public var noiseFactor: Float

  /**
   * The [HazeTint] to use when Haze uses the fallback scrim functionality.
   *
   * The scrim used whenever [blurEnabled] is resolved to false, either because the host
   * platform does not support blurring, or it has been manually disabled.
   *
   * When the fallback tint is used, the tints provided in [tints] are ignored.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified
   *  - [HazeStyle.fallbackTint] value set in [style], if specified.
   *  - [HazeStyle.fallbackTint] value set in the [LocalHazeStyle] composition local.
   */
  public var fallbackTint: HazeTint

  /**
   * Parameters for enabling a progressive (or gradient) blur effect, or null for a uniform
   * blurring effect. Defaults to null.
   *
   * Please note: progressive blurring effects can be expensive, so you should test on a variety
   * of devices to verify that performance is acceptable for your use case. An alternative and
   * more performant way to achieve this effect is via the [mask] parameter, at the cost of
   * visual finesse.
   */
  public var progressive: HazeProgressive?

  /**
   * The input scale factor, which needs to be in the range 0 < x <= 1.
   * Defaults to `1.0`, resulting in no scaling.
   *
   * The content will be scaled by this value in both the x and y dimensions, allowing the blur
   * effect to be potentially applied over scaled-down content, before being scaled back up
   * and drawn at the original size.
   *
   * Using a value less than 1.0 **may** improve performance, at the sacrifice of
   * quality and crispness. As always, run your own benchmarks as to whether this
   * compromise is worth it.
   *
   * If you're looking for a good value to experiment with, `0.8` results in a reduction in
   * total resolution of ~35%, while being visually imperceptible to most people (probably).
   *
   * The minimum value I would realistically use is somewhere in the region of
   * `0.5`, which results in the total pixel count being only 25% of the original content. This
   * will likely be visually perceptible different to no scaling, but depending on the styling
   * parameters will still look pleasing to the user.
   *
   * This feature is experimental as it's unclear how much gain it provides. It may be removed
   * some point in the future.
   */
  @ExperimentalHazeApi
  public var inputScale: HazeInputScale

  /**
   * A block which controls whether this [hazeEffect] should draw the given [HazeArea].
   *
   * When null, the default behavior is that this effect will only draw areas with a
   * [HazeArea.zIndex] the nearest ancestor [HazeSourceNode].
   */
  @ExperimentalHazeApi
  public var canDrawArea: ((HazeArea) -> Boolean)?

  /**
   * The [BlurredEdgeTreatment] to use when blurring content.
   *
   * Defaults to [BlurredEdgeTreatment.Rectangle] (via [HazeDefaults.blurredEdgeTreatment]), which
   * is nearly always the correct value for when performing background blurring. If you're
   * performing content (foreground) blurring, it depends on the effect which you're looking for.
   *
   * Please note: some platforms do not support all of the treatments available. This value is a
   * best-effort attempt.
   */
  public var blurredEdgeTreatment: BlurredEdgeTreatment

  /**
   * Whether to draw the content behind the blurred effect for foreground blurring. This is
   * sometimes useful when you're using a mask or progressive effect.
   *
   * Defaults to `false` via [HazeDefaults.drawContentBehind].
   *
   * This flag has no effect when used with background blurring.
   */
  public var drawContentBehind: Boolean

  /**
   * Whether the drawn effect should be clipped to the total bounds which cover all of the
   * areas provided via the [HazeState.areas].
   *
   * This defaults to `null` which means that Haze will decide whether to clip or not depending
   * on other conditions.
   */
  public var clipToAreasBounds: Boolean?

  /**
   * Whether the layer should be expanded by the [blurRadius] on all edges. Defaults to enabled.
   *
   * This might sound strange, but when enabled it allows the blurred effect to be more
   * consistent and realistic on the edges, by being able to capturing more nearby content.
   * You may wish to disable this if you find that the blurred effect is drawn in unwanted
   * areas.
   */
  public var expandLayerBounds: Boolean?

  /**
   * Force draw invalidation from pre-draw events of contributing [HazeArea]s.
   *
   * When enabled, Haze will register a pre-draw listener and invalidate this effect node
   * whenever the source areas are about to be drawn. This helps ensure the blur stays in sync
   * with rapidly changing or externally-invalidated content.
   *
   * Haze automatically enables this for scenarios where it knows we need it:
   * - The source content is drawn in a different window than this effect (e.g. Dialogs/Popups),
   *   so it's outside of this node's normal invalidation scope.
   * - On some older Android versions where invalidation propagation is less reliable.
   *
   * However, there may be other use cases where invalidation does not work as expected, and
   * the [hazeEffect] looks like it is 'stuck' or out of sync. By setting this flag to `true`,
   * we use the pre-draw listener to force invalidations, and thus should fix the majority
   * of issues.
   *
   * Notes:
   * - Only has an effect when blurring is enabled.
   * - May have a performance cost due to additional invalidations from the pre-draw listener.
   */
  public var forceInvalidateOnPreDraw: Boolean
}

/**
 * Value classes used for [HazeEffectScope.inputScale].
 */
@ExperimentalHazeApi
public sealed interface HazeInputScale {
  /**
   * No input scaling. This is functionally the same as `Fixed(1.0f)`
   */
  public data object None : HazeInputScale

  /**
   * Automatic input scaling. Haze will attempt to use an appropriate input scale depending on
   * the other settings which have been set. The values used underneath may change in the future.
   */
  public data object Auto : HazeInputScale

  /**
   * An input scale which uses a fixed scale factor.
   *
   * @param scale The scale factor, in the range 0 < x <= 1.
   */
  @JvmInline
  public value class Fixed(public val scale: Float) : HazeInputScale {
    init {
      require(scale > 0f && scale <= 1f) {
        "scale needs to be in the range 0 < x <= 1f"
      }
    }
  }

  public companion object {
    /**
     * The default [HazeInputScale] value. Currently this resolves to [HazeInputScale.None] but
     * this may change in the future, probably to [HazeInputScale.Auto].
     */
    @ExperimentalHazeApi
    public val Default: HazeInputScale get() = None
  }
}

@Deprecated(
  message = "Renamed to Modifier.hazeEffect()",
  replaceWith = ReplaceWith("hazeEffect(state, style, block)", "dev.chrisbanes.haze.hazeEffect"),
)
@Stable
public fun Modifier.hazeChild(
  state: HazeState,
  style: HazeStyle = HazeStyle.Unspecified,
  block: (HazeEffectScope.() -> Unit)? = null,
): Modifier = hazeEffect(state, style, block)

/**
 * Draw the 'haze' effect behind the attached node.
 *
 * This version of the modifier is the primary entry for 'background blurring', where the
 * modifier node will read the attached [HazeArea]s in the given [state], and then draw
 * those (blurred) as a background. This layout's content will be drawn on top.
 *
 * @param state The [HazeState] to observe for background content.
 * @param style The [HazeStyle] to use on this content. Any specified values in the given
 * style will override that value from the default style, provided to [hazeSource].
 * @param block block on HazeChildScope where you define the styling and visual properties.
 */
@Stable
public fun Modifier.hazeEffect(
  state: HazeState?,
  style: HazeStyle = HazeStyle.Unspecified,
  block: (HazeEffectScope.() -> Unit)? = null,
): Modifier = this then HazeEffectNodeElement(state = state, style = style, block = block)

/**
 * Draw the 'haze' effect, using this node's content as the source.
 *
 * This version of the modifier is the entry point for 'content blurring', where the
 * modifier node will blurred any content drawn into **this** layout node. It is
 * similar to the `Modifier.blur` modifier available in Compose Foundation, but you get all of
 * the styling and features which provides on top.
 *
 * @param style The [HazeStyle] to use on this content. Any specified values in the given
 * style will override that value from the default style, provided to [hazeSource].
 * @param block block on HazeChildScope where you define the styling and visual properties.
 */
@Stable
public fun Modifier.hazeEffect(
  style: HazeStyle = HazeStyle.Unspecified,
  block: (HazeEffectScope.() -> Unit)? = null,
): Modifier = this then HazeEffectNodeElement(state = null, style = style, block = block)

private data class HazeEffectNodeElement(
  public val state: HazeState?,
  public val style: HazeStyle = HazeStyle.Unspecified,
  public val block: (HazeEffectScope.() -> Unit)? = null,
) : ModifierNodeElement<HazeEffectNode>() {

  override fun create(): HazeEffectNode = HazeEffectNode(state, style, block)

  override fun update(node: HazeEffectNode) {
    node.state = state
    node.style = style
    node.block = block
    node.update()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "HazeEffect"
  }
}
