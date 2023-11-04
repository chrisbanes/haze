// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("NOTHING_TO_INLINE")

package dev.chrisbanes.haze

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draw content within the provided [area]s blurred in a 'glassmorphism' style.
 *
 * When running on Android 12 devicees (and newer), usage of this API renders the corresponding composable
 * into a separate graphics layer. On older Android platforms, a translucent scrim will be drawn
 * instead.
 *
 * @param area The areas of the content which should have the blur effect applied to.
 * @param backgroundColor Background color of the content. Typically you would provide
 * `MaterialTheme.colorScheme.surface` or similar.
 * @param tint Color to tint the blurred content. Should be translucent, otherwise you will not see
 * the blurred content.
 * @param blurRadius Radius of the blur.
 * @param noiseFactor Amount of noise applied to the content, in the range `0f` to `1f`.
 */
inline fun Modifier.haze(
  vararg area: Rect,
  backgroundColor: Color,
  tint: Color = HazeDefaults.tint(backgroundColor),
  blurRadius: Dp = HazeDefaults.blurRadius,
  noiseFactor: Float = HazeDefaults.noiseFactor,
): Modifier = haze(area.map(::RoundRect), backgroundColor, tint, blurRadius, noiseFactor)

/**
 * Draw content within the provided [area]s blurred in a 'glassmorphism' style.
 *
 * When running on Android 12 devicees (and newer), usage of this API renders the corresponding composable
 * into a separate graphics layer. On older Android platforms, a translucent scrim will be drawn
 * instead.
 *
 * @param area The areas of the content which should have the blur effect applied to.
 * @param backgroundColor Background color of the content. Typically you would provide
 * `MaterialTheme.colorScheme.surface` or similar.
 * @param tint Color to tint the blurred content. Should be translucent, otherwise you will not see
 * the blurred content.
 * @param blurRadius Radius of the blur.
 * @param noiseFactor Amount of noise applied to the content, in the range `0f` to `1f`.
 */
inline fun Modifier.haze(
  vararg area: RoundRect,
  backgroundColor: Color,
  tint: Color = HazeDefaults.tint(backgroundColor),
  blurRadius: Dp = HazeDefaults.blurRadius,
  noiseFactor: Float = HazeDefaults.noiseFactor,
): Modifier = haze(area.toList(), backgroundColor, tint, blurRadius, noiseFactor)

/**
 * Draw content within the provided [areas] blurred in a 'glassmorphism' style.
 *
 * When running on Android 12 devicees (and newer), usage of this API renders the corresponding composable
 * into a separate graphics layer. On older Android platforms, a translucent scrim will be drawn
 * instead.
 *
 * @param areas The areas of the content which should have the blur effect applied to.
 * @param backgroundColor Background color of the content. Typically you would provide
 * `MaterialTheme.colorScheme.surface` or similar.
 * @param tint Color to tint the blurred content. Should be translucent, otherwise you will not see
 * the blurred content.
 * @param blurRadius Radius of the blur.
 * @param noiseFactor Amount of noise applied to the content, in the range `0f` to `1f`.
 */
fun Modifier.haze(
  areas: List<RoundRect>,
  backgroundColor: Color,
  tint: Color = HazeDefaults.tint(backgroundColor),
  blurRadius: Dp = HazeDefaults.blurRadius,
  noiseFactor: Float = HazeDefaults.noiseFactor,
): Modifier = this then HazeNodeElement(
  areas = areas,
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
  val areas: List<RoundRect>,
  val backgroundColor: Color,
  val tint: Color,
  val blurRadius: Dp,
  val noiseFactor: Float,
) : ModifierNodeElement<HazeNode>() {
  override fun create(): HazeNode = when {
    Build.VERSION.SDK_INT >= 31 -> {
      HazeNode31(
        areas = areas,
        backgroundColor = backgroundColor,
        tint = tint,
        blurRadius = blurRadius,
        noiseFactor = noiseFactor,
      )
    }

    else -> {
      HazeNodeBase(
        areas = areas,
        backgroundColor = backgroundColor,
        tint = tint,
        blurRadius = blurRadius,
        noiseFactor = noiseFactor,
      )
    }
  }

  override fun update(node: HazeNode) {
    node.areas = areas
    node.backgroundColor = backgroundColor
    node.tint = tint
    node.blurRadius = blurRadius
    node.noiseFactor = noiseFactor
    node.onUpdate()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "haze"
    properties["areas"] = areas
    properties["backgroundColor"] = backgroundColor
    properties["tint"] = tint
    properties["blurRadius"] = blurRadius
    properties["noiseFactor"] = noiseFactor
  }
}

internal abstract class HazeNode(
  var areas: List<RoundRect>,
  var backgroundColor: Color,
  var tint: Color,
  var blurRadius: Dp,
  var noiseFactor: Float,
) : Modifier.Node() {
  open fun onUpdate() {}
}
