// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import dev.chrisbanes.haze.effect.BlurEffectConfig
import dev.chrisbanes.haze.effect.BlurVisualEffect
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
   * The visual effect implementation used by this node. Clients can replace or configure
   * the effect directly.
   *
   * Use [blurEffect] to easily configure a [BlurVisualEffect]:
   * ```
   * Modifier.hazeEffect(state) {
   *   blurEffect {
   *     blurRadius = 20.dp
   *     tints = listOf(HazeTint(Color.Black.copy(alpha = 0.5f)))
   *   }
   * }
   * ```
   */
  public var visualEffect: dev.chrisbanes.haze.effect.VisualEffect

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
   * Whether the layer should be expanded on all edges. Defaults to enabled.
   *
   * This might sound strange, but when enabled it allows effects to be more
   * consistent and realistic on the edges, by being able to capture more nearby content.
   * You may wish to disable this if you find that the effect is drawn in unwanted areas.
   */
  public var expandLayerBounds: Boolean?

  /**
   * Force draw invalidation from pre-draw events of contributing [HazeArea]s.
   *
   * When enabled, Haze will register a pre-draw listener and invalidate this effect node
   * whenever the source areas are about to be drawn. This helps ensure the effect stays in sync
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
 * Draw the 'haze' effect behind the attached node using a pre-configured [VisualEffect].
 *
 * This version allows you to provide a custom [VisualEffect] implementation directly:
 *
 * ```
 * val effect = BlurVisualEffect().apply {
 *   blurRadius = 20.dp
 *   tints = listOf(HazeTint(Color.Black.copy(alpha = 0.5f)))
 * }
 * Modifier.hazeEffect(state, effect = effect)
 * ```
 *
 * @param state The [HazeState] to observe for background content.
 * @param effect The [VisualEffect] to use. Defaults to a new [BlurVisualEffect].
 * @param style The [HazeStyle] to use on this content.
 * @param block Optional configuration block for additional effect scope properties.
 */
@Stable
public fun Modifier.hazeEffect(
  state: HazeState?,
  effect: dev.chrisbanes.haze.effect.VisualEffect = BlurVisualEffect(),
  style: HazeStyle = HazeStyle.Unspecified,
  block: (HazeEffectScope.() -> Unit)? = null,
): Modifier = this then HazeEffectNodeElement(
  state = state,
  style = style,
  effect = effect,
  block = block,
)

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
): Modifier = hazeEffect(
  state = state,
  effect = BlurVisualEffect(),
  style = style,
  block = block,
)

/**
 * Draw the 'haze' effect, using this node's content as the source, with a pre-configured [VisualEffect].
 *
 * This version allows you to provide a custom [VisualEffect] implementation directly.
 *
 * @param effect The [VisualEffect] to use. Defaults to a new [BlurVisualEffect].
 * @param style The [HazeStyle] to use on this content.
 * @param block Optional configuration block for additional effect scope properties.
 */
@Stable
public fun Modifier.hazeEffect(
  effect: dev.chrisbanes.haze.effect.VisualEffect = BlurVisualEffect(),
  style: HazeStyle = HazeStyle.Unspecified,
  block: (HazeEffectScope.() -> Unit)? = null,
): Modifier = this then HazeEffectNodeElement(
  state = null,
  style = style,
  effect = effect,
  block = block,
)

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
@Deprecated(
  message = "Use overload with explicit effect parameter for better type safety",
  replaceWith = ReplaceWith(
    "hazeEffect(effect = BlurVisualEffect(), style = style, block = block)",
    "dev.chrisbanes.haze.effect.BlurVisualEffect",
  ),
  level = DeprecationLevel.HIDDEN,
)
@Stable
public fun Modifier.hazeEffect(
  style: HazeStyle = HazeStyle.Unspecified,
  block: (HazeEffectScope.() -> Unit)? = null,
): Modifier = hazeEffect(
  effect = BlurVisualEffect(),
  style = style,
  block = block,
)

private data class HazeEffectNodeElement(
  public val state: HazeState?,
  public val style: HazeStyle = HazeStyle.Unspecified,
  public val effect: dev.chrisbanes.haze.effect.VisualEffect = BlurVisualEffect(),
  public val block: (HazeEffectScope.() -> Unit)? = null,
) : ModifierNodeElement<HazeEffectNode>() {

  override fun create(): HazeEffectNode = HazeEffectNode(
    state = state,
    block = block,
  ).apply {
    visualEffect = effect
    // Apply style to BlurVisualEffect if applicable
    (effect as? dev.chrisbanes.haze.effect.BlurEffectConfig)?.style = style
  }

  override fun update(node: HazeEffectNode) {
    node.state = state
    node.visualEffect = effect
    // Apply style to BlurVisualEffect if applicable
    (effect as? dev.chrisbanes.haze.effect.BlurEffectConfig)?.style = style
    node.block = block
    node.update()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "HazeEffect"
  }
}

/**
 * Configures the [BlurVisualEffect] for this effect scope, allowing direct access to blur-specific
 * properties without casting.
 *
 * This extension function simplifies configuration when you know you're working with a blur effect:
 *
 * ```
 * Modifier.hazeEffect(state) {
 *   blurEffect {
 *     blurRadius = 20.dp
 *     tints = listOf(HazeTint(Color.Black.copy(alpha = 0.5f)))
 *     noiseFactor = 0.15f
 *   }
 * }
 * ```
 *
 * If the current [visualEffect] is not a [BlurVisualEffect], this
 * function will replace it with a new instance and then configure it.
 *
 * @param block Configuration block that receives the [BlurEffectConfig]
 */
public inline fun HazeEffectScope.blurEffect(
  block: BlurEffectConfig.() -> Unit,
) {
  val effect = visualEffect as? BlurEffectConfig
    ?: BlurVisualEffect().also { visualEffect = it }
  effect.block()
}
