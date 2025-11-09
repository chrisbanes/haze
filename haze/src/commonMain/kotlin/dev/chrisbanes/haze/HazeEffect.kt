// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
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
   */
  public var visualEffect: VisualEffect

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
   * sometimes useful when you're using a mask or progressive effect. Defaults to `false`.
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

/**
 * Draw the 'haze' effect behind the attached node using a pre-configured [VisualEffect].
 *
 * ```
 * Modifier.hazeEffect(state, effect = effect) {
 *   visualEffect = BlurVisualEffect().apply {
 *     blurRadius = 20.dp
 *     tints = listOf(HazeTint(Color.Black.copy(alpha = 0.5f)))
 *   }
 * }
 * ```
 *
 * @param state The [HazeState] to observe for background content.
 * @param block Optional configuration block for additional effect scope properties.
 */
@Stable
public fun Modifier.hazeEffect(
  state: HazeState?,
  block: (HazeEffectScope.() -> Unit)? = null,
): Modifier = this then HazeEffectNodeElement(state = state, block = block)

/**
 * Draw the 'haze' effect, using this node's content as the source, with a pre-configured [VisualEffect].
 *
 * @param block Optional configuration block for additional effect scope properties.
 */
@Stable
public fun Modifier.hazeEffect(
  block: (HazeEffectScope.() -> Unit)? = null,
): Modifier = this then HazeEffectNodeElement(state = null, block = block)

private data class HazeEffectNodeElement(
  val state: HazeState?,
  val block: (HazeEffectScope.() -> Unit)? = null,
) : ModifierNodeElement<HazeEffectNode>() {

  override fun create(): HazeEffectNode = HazeEffectNode(
    state = state,
    block = block,
  )

  override fun update(node: HazeEffectNode) {
    node.state = state
    node.block = block
    node.update()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "HazeEffect"
  }
}
