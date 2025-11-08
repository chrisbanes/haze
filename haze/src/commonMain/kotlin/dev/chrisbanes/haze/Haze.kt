// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
public fun rememberHazeState(blurEnabled: Boolean = HazeDefaults.blurEnabled()): HazeState {
  return remember {
    HazeState(initialBlurEnabled = blurEnabled)
  }.apply {
    this.blurEnabled = blurEnabled
  }
}

@Stable
public class HazeState public constructor(initialBlurEnabled: Boolean) {
  private val _areas = mutableStateListOf<HazeArea>()
  public val areas: List<HazeArea> get() = _areas

  public constructor() : this(initialBlurEnabled = HazeDefaults.blurEnabled())

  /**
   * Whether blurring is enabled or not. This can be overridden on each [hazeEffect]
   * via the [HazeEffectScope.blurEnabled] property.
   */
  public var blurEnabled: Boolean by mutableStateOf(initialBlurEnabled)

  internal fun addArea(area: HazeArea) {
    _areas += area
  }

  internal fun removeArea(area: HazeArea) {
    _areas -= area
  }

  @Deprecated("Inspect areas instead")
  public var positionOnScreen: Offset
    get() = areas.firstOrNull()?.positionOnScreen ?: Offset.Unspecified
    set(value) {
      areas.firstOrNull()?.apply {
        positionOnScreen = value
      }
    }

  @Deprecated("Inspect areas instead")
  public var contentLayer: GraphicsLayer?
    get() = areas.firstOrNull()?.contentLayer
    set(value) {
      areas.firstOrNull()?.apply {
        contentLayer = value
      }
    }
}

@Stable
public class HazeArea {

  public var positionOnScreen: Offset by mutableStateOf(Offset.Unspecified)
    internal set

  public var size: Size by mutableStateOf(Size.Unspecified)
    internal set

  public var zIndex: Float by mutableFloatStateOf(0f)
    internal set

  public var key: Any? = null
    internal set

  public var windowId: Any? = null
    internal set

  internal val preDrawListeners = mutableStateSetOf<OnPreDrawListener>()

  /**
   * The content [GraphicsLayer].
   */
  public var contentLayer: GraphicsLayer? by mutableStateOf(null)
    internal set

  internal val bounds: Rect?
    get() = when {
      size.isSpecified && positionOnScreen.isSpecified -> Rect(positionOnScreen, size)
      else -> null
    }

  internal var contentDrawing = false

  public override fun toString(): String = buildString {
    append("HazeArea(")
    append("positionOnScreen=$positionOnScreen, ")
    append("size=$size, ")
    append("zIndex=$zIndex, ")
    append("contentLayer=$contentLayer, ")
    append("contentDrawing=$contentDrawing")
    append(")")
  }
}

internal fun interface OnPreDrawListener {
  operator fun invoke()
}

@Deprecated(
  message = "Renamed to Modifier.hazeSource()",
  replaceWith = ReplaceWith("hazeSource(state)", "dev.chrisbanes.haze.hazeSource"),
)
@Stable
public fun Modifier.haze(state: HazeState): Modifier = hazeSource(state)

/**
 * Captures background content for [hazeEffect] child nodes, which will be drawn with a blur
 * in a 'glassmorphism' style.
 *
 * When running on Android 12 devices (and newer), usage of this API renders the corresponding composable
 * into a separate graphics layer. On older Android platforms, a translucent scrim will be drawn
 * instead.
 */
@Stable
public fun Modifier.hazeSource(
  state: HazeState,
  zIndex: Float = 0f,
  key: Any? = null,
): Modifier = this then HazeSourceElement(state, zIndex, key)

/**
 * Default values for the [hazeSource] and [hazeEffect] modifiers.
 */
@Suppress("ktlint:standard:property-naming")
public object HazeDefaults {
  /**
   * Default blur radius. Larger values produce a stronger blur effect.
   */
  public val blurRadius: Dp = 20.dp

  /**
   * Noise factor.
   */
  public const val noiseFactor: Float = 0.15f

  /**
   * Default alpha used for the tint color. Used by the [tint] function.
   */
  public const val tintAlpha: Float = 0.7f

  /**
   * Default value for [HazeEffectScope.blurredEdgeTreatment]
   */
  public val blurredEdgeTreatment: BlurredEdgeTreatment = BlurredEdgeTreatment.Rectangle

  /**
   * Default value for [HazeEffectScope.drawContentBehind]
   */
  public const val drawContentBehind: Boolean = false

  /**
   * Default builder for the 'tint' color. Transforms the provided [color].
   */
  public fun tint(color: Color): HazeTint = HazeTint(
    color = when {
      color.isSpecified -> color.copy(alpha = color.alpha * tintAlpha)
      else -> color
    },
  )

  /**
   * Default [HazeStyle] for usage with [Modifier.hazeSource].
   *
   * @param backgroundColor Color to draw behind the blurred content. Ideally should be opaque
   * so that the original content is not visible behind. Typically this would be
   * `MaterialTheme.colorScheme.surface` or similar.
   * @param tint Default color to tint the blurred content. Should be translucent, otherwise you
   * will not see the blurred content.
   * @param blurRadius Radius of the blur.
   * @param noiseFactor Amount of noise applied to the content, in the range `0f` to `1f`.
   * Anything outside of that range will be clamped.
   */
  public fun style(
    backgroundColor: Color,
    tint: HazeTint = tint(backgroundColor),
    blurRadius: Dp = this.blurRadius,
    noiseFactor: Float = this.noiseFactor,
  ): HazeStyle = HazeStyle(backgroundColor, tint, blurRadius, noiseFactor)

  /**
   * Default values for [HazeEffectScope.blurEnabled]. This function only returns `true` on
   * platforms where we know blurring works reliably.
   *
   * This is not the same as everywhere where it technically works. The key omission here
   * is Android SDK Level 31, which is known to have some issues with
   * RenderNode invalidation.
   *
   * The devices excluded by this function may change in the future.
   */
  public fun blurEnabled(): Boolean = isBlurEnabledByDefault()
}

internal data class HazeSourceElement(
  public val state: HazeState,
  public val zIndex: Float = 0f,
  public val key: Any? = null,
) : ModifierNodeElement<HazeSourceNode>() {

  override fun create(): HazeSourceNode = HazeSourceNode(state = state, zIndex = zIndex, key = key)

  override fun update(node: HazeSourceNode) {
    node.state = state
    node.zIndex = zIndex
    node.key = key
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "hazeSource"
    properties["zIndex"] = zIndex
    properties["key"] = key
  }
}
