// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.jvm.JvmInline

/**
 * Input-sampling policies used by the explicit-input [hazeEffect] overload.
 */
public sealed interface HazeSampling {
  /** Library-defined sampling defaults. */
  public companion object {
    /** Points to the library's current default sampling policy. */
    public val Default: HazeSampling = Adaptive
  }

  /**
   * Samples the effect input at full resolution.
   */
  public data object FullResolution : HazeSampling

  /**
   * Requests the configured effect's adaptive input-sampling policy.
   */
  public data object Adaptive : HazeSampling

  /**
   * Retains a fixed fraction of the full-resolution input pixels while preserving aspect ratio.
   *
   * For example, `0.5f` targets half the total input pixels by scaling each dimension by
   * `sqrt(0.5)` (approximately `0.707`). Integer raster dimensions can make the realized fraction
   * differ slightly for very small inputs.
   *
   * @param pixelFraction The total input-pixel fraction, in the range 0 < x <= 1.
   */
  @JvmInline
  public value class Fixed(public val pixelFraction: Float) : HazeSampling {
    init {
      require(pixelFraction.isFinite() && pixelFraction > 0f && pixelFraction <= 1f) {
        "pixelFraction needs to be finite and in the range 0 < x <= 1f"
      }
    }
  }
}

/**
 * Draws a typed custom effect using an explicit [input].
 *
 * [factory] is a stateless descriptor that may be shared by concurrent modifiers. Haze creates
 * one renderer for each modifier node and passes the complete current [style] to its draw and
 * layer-layout evaluations.
 *
 * @param factory The shareable descriptor that creates node-owned renderers.
 * @param input The source-backed or own-content input consumed by the renderer.
 * @param style The complete effect configuration accepted by [factory].
 * @param sampling The input-sampling policy visible to the renderer.
 * @param expandLayerBounds Whether renderer-requested layer-bound expansion is enabled.
 */
@Stable
public fun <Style> Modifier.hazeEffect(
  factory: HazeEffectFactory<Style>,
  input: HazeInput,
  style: Style,
  sampling: HazeSampling = HazeSampling.Default,
  expandLayerBounds: Boolean = true,
): Modifier = composed(
  "dev.chrisbanes.haze.hazeEffect",
  factory,
  input,
  style,
  sampling,
  expandLayerBounds,
) {
  val effect = Modifier then InputPresentationLayerElement then TypedHazeEffectNodeElement(
    factory = factory,
    input = input,
    style = style,
    sampling = sampling,
    expandLayerBounds = expandLayerBounds,
    lifecycle = LocalLifecycleOwner.current.lifecycle,
  )
  if (input === HazeInput.Content) {
    effect.graphicsLayer() then ForegroundContentInvalidationElement
  } else {
    effect
  }
}

private data object InputPresentationLayerElement : ModifierNodeElement<InputPresentationLayerNode>() {
  override fun create() = InputPresentationLayerNode()

  override fun update(node: InputPresentationLayerNode) = Unit

  override fun InspectorInfo.inspectableProperties() {
    name = "hazeInputPresentationLayer"
  }
}

/** A distinct boundary keeps effect drawing and its invalidation inside the same layer. */
internal class InputPresentationLayerNode : DelegatingNode(), TraversableNode {
  override val traverseKey: Any get() = Key
  private var owner: HazeEffectNode? = null
  private var layoutDelegate: LayoutModifierNode? = null

  fun matches(requester: HazeEffectNode, enabled: Boolean): Boolean = if (enabled) {
    owner === requester && layoutDelegate != null
  } else {
    owner !== requester || layoutDelegate == null
  }

  fun setEnabled(requester: HazeEffectNode, enabled: Boolean): Boolean {
    if (!isAttached) return false
    if (!enabled) return release(requester)
    owner = requester
    if (layoutDelegate != null) return false
    layoutDelegate = delegate(LayerNode())
    layoutDelegate?.invalidateMeasurement()
    return true
  }

  fun release(requester: HazeEffectNode): Boolean {
    if (owner !== requester) return false
    owner = null
    return removeLayer()
  }

  override fun onDetach() {
    owner = null
    removeLayer()
  }

  private fun removeLayer(): Boolean {
    val current = layoutDelegate ?: return false
    if (isAttached) current.invalidateMeasurement()
    undelegate(current)
    layoutDelegate = null
    return true
  }

  private class LayerNode : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
      val placeable = measurable.measure(constraints)
      return layout(placeable.width, placeable.height) {
        placeable.placeWithLayer(0, 0) { clip = false }
      }
    }
  }

  companion object {
    val Key = Any()
  }
}

private class TypedHazeEffectNodeElement<Style>(
  val factory: HazeEffectFactory<Style>,
  val input: HazeInput,
  val style: Style,
  val sampling: HazeSampling,
  val expandLayerBounds: Boolean,
  val lifecycle: Lifecycle,
) : ModifierNodeElement<HazeEffectNode>() {

  override fun create(): HazeEffectNode = HazeEffectNode().also { node ->
    node.explicitInput = input
    node.explicitExpandLayerBounds = expandLayerBounds
    node.updateTypedEffect(factory, style, sampling)
    node.updateLifecycle(lifecycle)
  }

  override fun update(node: HazeEffectNode) {
    node.explicitInput = input
    node.explicitExpandLayerBounds = expandLayerBounds
    node.updateTypedEffect(factory, style, sampling)
    node.updateLifecycle(lifecycle)
    node.update()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "HazeEffect"
    properties["factory"] = factory
    properties["input"] = input
    properties["style"] = style
    properties["sampling"] = sampling
    properties["expandLayerBounds"] = expandLayerBounds
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TypedHazeEffectNodeElement<*>) return false
    return factory === other.factory &&
      input == other.input &&
      style == other.style &&
      sampling == other.sampling &&
      expandLayerBounds == other.expandLayerBounds &&
      lifecycle === other.lifecycle
  }

  override fun hashCode(): Int {
    var result = 0
    result = 31 * result + input.hashCode()
    result = 31 * result + (style?.hashCode() ?: 0)
    result = 31 * result + sampling.hashCode()
    result = 31 * result + expandLayerBounds.hashCode()
    return 31 * result + lifecycle.hashCode()
  }
}

private data object ForegroundContentInvalidationElement :
  ModifierNodeElement<ForegroundContentInvalidationNode>() {
  override fun create() = ForegroundContentInvalidationNode()

  override fun update(node: ForegroundContentInvalidationNode) = Unit

  override fun InspectorInfo.inspectableProperties() {
    name = "hazeForegroundContent"
  }
}

private class ForegroundContentInvalidationNode : Modifier.Node(), DrawModifierNode {
  private var effectNode: HazeEffectNode? = null

  override fun onAttach() {
    effectNode = findNearestAncestor(HazeTraversableNodeKeys.Effect) as? HazeEffectNode
  }

  override fun onDetach() {
    effectNode = null
  }

  override fun ContentDrawScope.draw() {
    effectNode?.onForegroundContentDraw()
    drawContent()
  }
}
