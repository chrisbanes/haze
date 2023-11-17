// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.isEmpty
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class HazeState {
  val areas = mutableStateMapOf<Any, RoundRect>()
}

internal fun HazeState.areasInLocal(boundsInRoot: Rect): List<RoundRect> {
  // The HazeState areas will be in the root coordinates. We need to translate them back to
  // local coordinates, by offsetting our bounds in root
  val rectOffset = Offset(-boundsInRoot.left, -boundsInRoot.top)

  return areas.asSequence()
    .map { it.value }
    .filterNot { it.isEmpty }
    .map { it.translate(rectOffset) }
    .toList()
}

internal val ModifierLocalHazeState = modifierLocalOf<HazeState?> { null }

/**
 * Draw content within the provided [areas] blurred in a 'glassmorphism' style.
 *
 * When running on Android 12 devicees (and newer), usage of this API renders the corresponding composable
 * into a separate graphics layer. On older Android platforms, a translucent scrim will be drawn
 * instead.
 *
 * @param backgroundColor Background color of the content. Typically you would provide
 * `MaterialTheme.colorScheme.surface` or similar.
 * @param tint Color to tint the blurred content. Should be translucent, otherwise you will not see
 * the blurred content.
 * @param blurRadius Radius of the blur.
 * @param noiseFactor Amount of noise applied to the content, in the range `0f` to `1f`.
 */
fun Modifier.haze(
  state: HazeState,
  backgroundColor: Color,
  tint: Color = HazeDefaults.tint(backgroundColor),
  blurRadius: Dp = HazeDefaults.blurRadius,
  noiseFactor: Float = HazeDefaults.noiseFactor,
): Modifier = this then HazeNodeElement(
  state = state,
  tint = tint,
  backgroundColor = backgroundColor,
  blurRadius = blurRadius,
  noiseFactor = noiseFactor,
)

/**
 * Default values for the [haze] modifiers.
 */
@Suppress("ktlint:standard:property-naming")
object HazeDefaults {
  /**
   * Default blur radius. Larger values produce a stronger blur effect.
   */
  val blurRadius: Dp = 20.dp

  /**
   * Noise factor.
   */
  const val noiseFactor = 0.15f

  /**
   * Default alpha used for the tint color. Used by the [tint] function.
   */
  val tintAlpha: Float = 0.7f

  /**
   * Default builder for the 'tint' color. Transforms the provided [color].
   */
  fun tint(color: Color): Color = color.copy(alpha = tintAlpha)
}

internal data class HazeNodeElement(
  val state: HazeState,
  val backgroundColor: Color,
  val tint: Color,
  val blurRadius: Dp,
  val noiseFactor: Float,
) : ModifierNodeElement<HazeNode>() {
  override fun create(): HazeNode = when {
    Build.VERSION.SDK_INT >= 31 -> {
      HazeNode31(
        state = state,
        backgroundColor = backgroundColor,
        tint = tint,
        blurRadius = blurRadius,
        noiseFactor = noiseFactor,
      )
    }

    else -> {
      HazeNodeBase(
        state = state,
        backgroundColor = backgroundColor,
        tint = tint,
        blurRadius = blurRadius,
        noiseFactor = noiseFactor,
      )
    }
  }

  override fun update(node: HazeNode) {
    node.state = state
    node.backgroundColor = backgroundColor
    node.tint = tint
    node.blurRadius = blurRadius
    node.noiseFactor = noiseFactor
    node.onUpdate()
    // Provide the state for child layouts
    node.provide(ModifierLocalHazeState, state)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "haze"
    properties["backgroundColor"] = backgroundColor
    properties["tint"] = tint
    properties["blurRadius"] = blurRadius
    properties["noiseFactor"] = noiseFactor
  }
}

internal abstract class HazeNode(
  var state: HazeState,
  var backgroundColor: Color,
  var tint: Color,
  var blurRadius: Dp,
  var noiseFactor: Float,
) : Modifier.Node(), ModifierLocalModifierNode {
  open fun onUpdate() {}
}
