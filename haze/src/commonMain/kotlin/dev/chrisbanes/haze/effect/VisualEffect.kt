// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.effect

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.toIntSize
import dev.chrisbanes.haze.HazeEffectNode
import dev.chrisbanes.haze.HazeEffectNode.Companion.TAG
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.orZero
import dev.chrisbanes.haze.translate
import dev.chrisbanes.haze.withGraphicsLayer
import kotlin.math.max

public interface VisualEffect {
  /**
   * Draws the effect.
   */
  public fun DrawScope.drawEffect(node: HazeEffectNode): Unit

  /**
   * Attaches this effect to the given node.
   */
  public fun attach(node: HazeEffectNode): Unit = Unit

  public fun update(): Unit = Unit

  /**
   * Detaches this effect from its node.
   */
  public fun detach(): Unit = Unit

  public fun shouldClip(): Boolean = false

  public fun calculateInputScaleFactor(scale: HazeInputScale): Float = when (scale) {
    is HazeInputScale.None -> 1f
    is HazeInputScale.Fixed -> scale.scale
    HazeInputScale.Auto -> 1f
  }

  public fun needInvalidation(): Boolean = false

  public fun preferClipToAreaBounds(): Boolean = false

  /**
   * The resulting rect should be in the same coordinate system of the passed in rect. i.e. the
   * content at [x,y] of [rect] should be the same content of the resulting rect.
   */
  public fun expandLayerRect(rect: Rect): Rect = rect
}

/**
 * Configuration interface for effects that support blur-related properties.
 *
 * This interface exposes all the blur-specific configuration options that can be set
 * on a visual effect. Effects that implement this interface can be configured through
 * the properties defined here.
 *
 * Currently, [BlurVisualEffect] implements this interface.
 */
public interface BlurEffectConfig {
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
   * The opacity that the overall effect will drawn with, in the range of 0..1.
   */
  public var alpha: Float

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
   * Style set on this specific blur effect.
   *
   * There are precedence rules to how each styling property is applied. The order of precedence
   * for each property are as follows:
   *
   *  - Property value set directly on this [BlurEffectConfig], if specified.
   *  - Value set here in [style], if specified.
   *  - Value set in the [LocalHazeStyle] composition local.
   */
  public var style: HazeStyle

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
}

internal fun DrawScope.drawScrim(
  tint: HazeTint,
  node: CompositionLocalConsumerModifierNode,
  offset: Offset = Offset.Zero,
  expandedSize: Size = this.size,
  mask: Brush? = null,
) {
  if (tint.brush != null) {
    if (mask != null) {
      node.withGraphicsLayer { layer ->
        layer.compositingStrategy = CompositingStrategy.Offscreen
        layer.record(size = size.toIntSize()) {
          drawRect(brush = tint.brush, blendMode = tint.blendMode)
          drawRect(brush = mask, blendMode = BlendMode.DstIn)
        }
        translate(offset) {
          drawLayer(layer)
        }
      }
    } else {
      drawRect(
        brush = tint.brush,
        topLeft = offset,
        size = size,
        blendMode = tint.blendMode,
      )
    }
  } else {
    if (mask != null) {
      drawRect(
        brush = mask,
        topLeft = offset,
        size = size,
        colorFilter = ColorFilter.tint(tint.color),
      )
    } else {
      drawRect(color = tint.color, size = expandedSize, blendMode = tint.blendMode)
    }
  }
}

internal fun DrawScope.createAndDrawScaledContentLayer(
  node: HazeEffectNode,
  releaseLayerOnExit: Boolean = true,
  block: DrawScope.(GraphicsLayer) -> Unit,
) {
  val graphicsContext = node.currentValueOf(LocalGraphicsContext)

  val effect = node.visualEffect
  val scaleFactor = effect.calculateInputScaleFactor(node.inputScale)
  val clip = effect.shouldClip()

  val layer = createScaledContentLayer(
    node = node,
    scaleFactor = scaleFactor,
    layerSize = node.layerSize,
    layerOffset = node.layerOffset,
    backgroundColor = (effect as? BlurEffectConfig)?.backgroundColor ?: Color.Transparent,
  )

  if (layer != null) {
    layer.clip = clip

    drawScaledContent(
      offset = -node.layerOffset,
      scaledSize = size * scaleFactor,
      clip = clip,
    ) {
      block(layer)
    }

    if (releaseLayerOnExit) {
      graphicsContext.releaseGraphicsLayer(layer)
    }
  }
}

internal fun DrawScope.createScaledContentLayer(
  node: HazeEffectNode,
  backgroundColor: Color,
  scaleFactor: Float,
  layerSize: Size,
  layerOffset: Offset,
): GraphicsLayer? {
  val scaledLayerSize = (layerSize * scaleFactor).roundToIntSize()

  if (scaledLayerSize.width <= 0 || scaledLayerSize.height <= 0) {
    // If we have a 0px dimension we can't do anything so just return
    return null
  }

  // Now we need to draw `contentNode` into each of an 'effect' graphic layers.
  // The RenderEffect applied will provide the blurring effect.
  val graphicsContext = node.currentValueOf(LocalGraphicsContext)
  val layer = graphicsContext.createGraphicsLayer()

  layer.record(size = scaledLayerSize) {
    if (backgroundColor.isSpecified) {
      drawRect(backgroundColor)
    }

    scale(scale = scaleFactor, pivot = Offset.Zero) {
      translate(layerOffset - node.positionOnScreen) {
        for (area in node.areas) {
          require(!area.contentDrawing) {
            "Modifier.haze nodes can not draw Modifier.hazeChild nodes. " +
              "This should not happen if you are providing correct values for zIndex on Modifier.haze. " +
              "Alternatively you can use can `canDrawArea` to to filter out parent areas."
          }

          val position = Snapshot.withoutReadObservation { area.positionOnScreen.orZero }
          translate(position) {
            // Draw the content into our effect layer. We do want to observe this via snapshot
            // state
            val areaLayer = area.contentLayer
              ?.takeUnless { it.isReleased }
              ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 }

            if (areaLayer != null) {
              HazeLogger.d(TAG) { "Drawing HazeArea GraphicsLayer: $areaLayer" }
              drawLayer(areaLayer)
            } else {
              HazeLogger.d(TAG) { "HazeArea GraphicsLayer is not valid" }
            }
          }
        }
      }
    }
  }

  return layer
}

internal fun DrawScope.drawScaledContent(
  offset: Offset,
  scaledSize: Size,
  clip: Boolean = true,
  block: DrawScope.() -> Unit,
) {
  val scaleFactor = max(size.width / scaledSize.width, size.height / scaledSize.height)
  optionalClipRect(enabled = clip) {
    translate(offset) {
      scale(scale = scaleFactor, pivot = Offset.Zero) {
        block()
      }
    }
  }
}

private inline fun DrawScope.optionalClipRect(
  enabled: Boolean,
  left: Float = 0.0f,
  top: Float = 0.0f,
  right: Float = size.width,
  bottom: Float = size.height,
  clipOp: ClipOp = ClipOp.Intersect,
  block: DrawScope.() -> Unit,
) = withTransform(
  transformBlock = {
    if (enabled) {
      clipRect(left, top, right, bottom, clipOp)
    }
  },
  drawBlock = block,
)
