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
interface HazeChildScope : HazeEffectScope

interface HazeEffectScope {

  /**
   * Whether the blur effect is enabled or not, when running on platforms which support blurring.
   *
   * When set to `false` a scrim effect will be used. When set to `true`, and running on a platform
   * which does not support blurring, a scrim effect will be used.
   *
   * Defaults to [HazeDefaults.blurEnabled].
   */
  var blurEnabled: Boolean

  /**
   * The opacity that the overall effect will drawn with, in the range of 0..1.
   */
  var alpha: Float

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
  var style: HazeStyle

  /**
   * Optional alpha mask which allows effects such as fading via a
   * [Brush.verticalGradient] or similar. This is only applied when [progressive] is null.
   *
   * An alpha mask provides a similar effect as that provided as [HazeProgressive], in a more
   * performant way, but may provide a less pleasing visual result.
   */
  var mask: Brush?

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
  var backgroundColor: Color

  /**
   * The [HazeTint]s to apply to the blurred content.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if not empty.
   *  - [HazeStyle.tints] value set in [style], if not empty.
   *  - [HazeStyle.tints] value set in the [LocalHazeStyle] composition local.
   */
  var tints: List<HazeTint>

  /**
   * Radius of the blur.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [HazeStyle.blurRadius] value set in [style], if specified.
   *  - [HazeStyle.blurRadius] value set in the [LocalHazeStyle] composition local.
   */
  var blurRadius: Dp

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
  var noiseFactor: Float

  /**
   * The [HazeTint] to use when Haze uses the fallback scrim functionality.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified
   *  - [HazeStyle.fallbackTint] value set in [style], if specified.
   *  - [HazeStyle.fallbackTint] value set in the [LocalHazeStyle] composition local.
   */
  var fallbackTint: HazeTint

  /**
   * Parameters for enabling a progressive (or gradient) blur effect, or null for a uniform
   * blurring effect. Defaults to null.
   *
   * Please note: progressive blurring effects can be expensive, so you should test on a variety
   * of devices to verify that performance is acceptable for your use case. An alternative and
   * more performant way to achieve this effect is via the [mask] parameter, at the cost of
   * visual finesse.
   */
  var progressive: HazeProgressive?

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
  var inputScale: HazeInputScale

  /**
   * A block which controls whether this [hazeEffect] should draw the given [HazeArea].
   *
   * When null, the default behavior is that this effect will only draw areas with a
   * [HazeArea.zIndex] the nearest ancestor [HazeSourceNode].
   */
  @ExperimentalHazeApi
  var canDrawArea: ((HazeArea) -> Boolean)?

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
  var blurredEdgeTreatment: BlurredEdgeTreatment

  /**
   * Whether to draw the content behind the blurred effect for foreground blurring. This is
   * sometimes useful when you're using a mask or progressive effect.
   *
   * Defaults to `false` via [HazeDefaults.drawContentBehind].
   *
   * This flag has no effect when used with background blurring.
   */
  var drawContentBehind: Boolean
}

/**
 * Value classes used for [HazeEffectScope.inputScale].
 */
@ExperimentalHazeApi
sealed interface HazeInputScale {
  /**
   * No input scaling. This is functionally the same as `Fixed(1.0f)`
   */
  data object None : HazeInputScale

  /**
   * Automatic input scaling. Haze will attempt to use an appropriate input scale depending on
   * the other settings which have been set. The values used underneath may change in the future.
   */
  data object Auto : HazeInputScale

  /**
   * An input scale which uses a fixed scale factor.
   *
   * @param scale The scale factor, in the range 0 < x <= 1.
   */
  @JvmInline
  value class Fixed(val scale: Float) : HazeInputScale {
    init {
      require(scale > 0f && scale <= 1f) {
        "scale needs to be in the range 0 < x <= 1f"
      }
    }
  }

  companion object {
    /**
     * The default [HazeInputScale] value. Currently this resolves to [HazeInputScale.None] but
     * this may change in the future, probably to [HazeInputScale.Auto].
     */
    @ExperimentalHazeApi
    val Default: HazeInputScale get() = None
  }
}

@Deprecated(
  message = "Renamed to Modifier.hazeEffect()",
  replaceWith = ReplaceWith("hazeEffect(state, style, block)", "dev.chrisbanes.haze.hazeEffect"),
)
@Stable
fun Modifier.hazeChild(
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
fun Modifier.hazeEffect(
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
fun Modifier.hazeEffect(
  style: HazeStyle = HazeStyle.Unspecified,
  block: (HazeEffectScope.() -> Unit)? = null,
): Modifier = this then HazeEffectNodeElement(state = null, style = style, block = block)

private data class HazeEffectNodeElement(
  val state: HazeState?,
  val style: HazeStyle = HazeStyle.Unspecified,
  val block: (HazeEffectScope.() -> Unit)? = null,
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
