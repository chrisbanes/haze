// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.collection.MutableObjectLongMap
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.toSize
import kotlin.math.max
import kotlin.math.min

/**
 * The [Modifier.Node] implementation used by [Modifier.hazeEffect].
 *
 * This is public API in order to aid custom extensible modifiers, _but_ we reserve the right
 * to be able to change the API in the future, hence why it is marked as experimental forever.
 */
@ExperimentalHazeApi
public class HazeEffectNode(
  public var state: HazeState? = null,
  public var block: (HazeEffectScope.() -> Unit)? = null,
) : Modifier.Node(),
  CompositionLocalConsumerModifierNode,
  GlobalPositionAwareModifierNode,
  LayoutAwareModifierNode,
  ObserverModifierNode,
  DrawModifierNode,
  TraversableNode,
  HazeEffectScope {

  override val traverseKey: Any
    get() = HazeTraversableNodeKeys.Effect

  override val shouldAutoInvalidate: Boolean = false

  internal var dirtyTracker = Bitmask()

  override var inputScale: HazeInputScale = HazeInputScale.Default
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "inputScale changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += DirtyFields.InputScale
      }
    }

  internal var positionOnScreen: Offset = Offset.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "positionOnScreen changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.ScreenPosition
        field = value
      }
    }

  internal var rootBoundsOnScreen: Rect = Rect.Zero
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "rootBoundsOnScreen changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.ScreenPosition
        field = value
      }
    }

  private val areaOffsets = MutableObjectLongMap<HazeArea>()

  internal var size: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "size changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.Size
        field = value
      }
    }

  internal var layerSize: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "layerSize changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.LayerSize
        field = value
      }
    }

  internal var layerOffset: Offset = Offset.Zero
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "layerOffset changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.LayerOffset
        field = value
      }
    }

  internal var windowId: Any? = null

  internal var areas: List<HazeArea> = emptyList()
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "backgroundAreas changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.Areas

        // Remove the pre-draw listener from the current areas
        for (area in field) {
          area.preDrawListeners -= areaPreDrawListener
        }
        // Add the pre-draw listener to all of the new areas
        for (area in value) {
          area.preDrawListeners += areaPreDrawListener
        }
        field = value
      }
    }

  private val contentDrawArea by lazy { HazeArea() }

  override var canDrawArea: ((HazeArea) -> Boolean)? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "canDrawArea changed. Current $field. New: $value" }
        field = value
      }
    }

  public override var visualEffect: dev.chrisbanes.haze.effect.VisualEffect =
    dev.chrisbanes.haze.effect.BlurVisualEffect()
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "visualEffect changed. Current $field. New: $value" }
        // attach new VisualEffect
        value.attach(this)
        // detach old VisualEffect
        field.detach()
        field = value
      }
    }

  override var drawContentBehind: Boolean = HazeDefaults.drawContentBehind
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "drawContentBehind changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.DrawContentBehind
        field = value
      }
    }

  override var clipToAreasBounds: Boolean? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "clipToAreasBounds changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.ClipToAreas
        field = value
      }
    }

  override var expandLayerBounds: Boolean? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "expandLayer changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.ExpandLayer
        field = value
      }
    }

  override var forceInvalidateOnPreDraw: Boolean = false
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "forceInvalidateOnPreDraw changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.ForcePreDraw
        field = value
      }
    }
  private val areaPreDrawListener by unsynchronizedLazy { OnPreDrawListener(::invalidateDraw) }

  internal fun update() {
    onObservedReadsChanged()
  }

  override fun onAttach() {
    visualEffect.attach(this)
    update()
  }

  override fun onDetach() {
    visualEffect.detach()
  }

  override fun onObservedReadsChanged() {
    observeReads(::updateEffect)
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    // If the positionOnScreen has not been placed yet, we use the value on onPlaced,
    // otherwise we ignore it. This primarily fixes screenshot tests which only run tests
    // up to the first draw. We need onGloballyPositioned which tends to happen after
    // the first pass
    Snapshot.withoutReadObservation {
      if (positionOnScreen.isUnspecified) {
        onPositioned(coordinates, "onPlaced")
      }
    }
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    onPositioned(coordinates, "onGloballyPositioned")
  }

  private fun onPositioned(coordinates: LayoutCoordinates, source: String) {
    if (!isAttached) {
      // This shouldn't happen, but it does...
      // https://github.com/chrisbanes/haze/issues/665
      return
    }

    positionOnScreen = coordinates.positionForHaze()
    size = coordinates.size.toSize()
    windowId = getWindowId()

    val rootLayoutCoords = coordinates.findRootCoordinates()
    rootBoundsOnScreen = Rect(
      offset = rootLayoutCoords.positionForHaze(),
      size = rootLayoutCoords.size.toSize(),
    )

    HazeLogger.d(TAG) {
      "$source: positionOnScreen=$positionOnScreen, size=$size"
    }

    updateEffect()
  }

  override fun ContentDrawScope.draw() {
    try {
      HazeLogger.d(TAG) { "-> start draw()" }

      if (!isAttached) {
        // This shouldn't happen, but it does...
        // https://github.com/chrisbanes/haze/issues/665
        return
      }

      if (size.isSpecified && layerSize.isSpecified) {
        if (state != null) {
          if (areas.isNotEmpty()) {
            // If the state is not null and we have some areas, let's perform background blurring
            with(visualEffect) {
              drawEffect(this@HazeEffectNode)
            }
          }
          // Finally we draw the content over the background
          drawContentSafely()
        } else {
          // Else we're doing content (foreground) blurring, so we need to use our
          // contentDrawArea
          val contentLayer = contentDrawArea.contentLayer
            ?.takeUnless { it.isReleased }
            ?: requireGraphicsContext().createGraphicsLayer().also {
              contentDrawArea.contentLayer = it
              HazeLogger.d(TAG) { "Updated contentLayer in content HazeArea" }
            }
          // Record the this node's content into the layer
          contentLayer.record(size.toIntSize()) {
            this@draw.drawContentSafely()
          }
          if (drawContentBehind) {
            // We need to draw the content for scrims
            drawLayer(contentLayer)
          }
          with(visualEffect) {
            drawEffect(this@HazeEffectNode)
          }
        }
      } else {
        HazeLogger.d(TAG) { "-> State not valid, so no need to draw effect." }
        drawContentSafely()
      }
    } finally {
      onPostDraw()
      HazeLogger.d(TAG) { "-> end draw()" }
    }
  }

  private fun updateEffect(): Unit = trace("HazeEffectNode-updateEffect") {
    // Allow the current VisualEffect to update from CompositionLocals/state
    visualEffect.update()
    windowId = getWindowId()

    // Invalidate if any of the effects triggered an invalidation, or we now have zero
    // effects but were previously showing some
    block?.invoke(this)

    val backgroundBlurring = state != null

    areas.forEach { area ->
      // Remove our pre draw listener from the current areas
      area.preDrawListeners -= areaPreDrawListener
    }

    areas = if (backgroundBlurring) {
      val ancestorSourceNode =
        (findNearestAncestor(HazeTraversableNodeKeys.Source) as? HazeSourceNode)
          ?.takeIf { it.state == this.state }

      state?.areas.orEmpty()
        .also {
          HazeLogger.d(TAG) { "Background Areas observing: $it" }
        }
        .asSequence()
        .filter { area ->
          val filter = canDrawArea
          when {
            filter != null -> filter(area)
            ancestorSourceNode != null -> area.zIndex < ancestorSourceNode.zIndex
            else -> true
          }.also { included ->
            HazeLogger.d(TAG) { "Background Area: $area. Included=$included" }
          }
        }
        .toMutableList()
        .apply { sortBy(HazeArea::zIndex) }
    } else {
      contentDrawArea.size = size
      contentDrawArea.positionOnScreen = positionOnScreen
      contentDrawArea.windowId = windowId
      listOf(contentDrawArea)
    }

    updateAreaOffsets()

    if (shouldUsePreDrawListener()) {
      for (area in areas) {
        area.preDrawListeners += areaPreDrawListener
      }
    }

    if (backgroundBlurring && areas.isNotEmpty() && size.isSpecified && positionOnScreen.isSpecified) {
      // Now we clip the expanded layer bounds, to remove anything areas which
      // don't overlap any areas, and the window bounds
      val clippedLayerBounds = Rect(positionOnScreen, size)
        .letIf(shouldExpandLayer()) { visualEffect.expandLayerRect(it) }
        .letIf(shouldClipToAreaBounds()) { rect ->
          // Calculate the dimensions which covers all areas...
          var left = Float.POSITIVE_INFINITY
          var top = Float.POSITIVE_INFINITY
          var right = Float.NEGATIVE_INFINITY
          var bottom = Float.NEGATIVE_INFINITY
          for (area in areas) {
            val bounds = area.bounds ?: continue
            left = min(left, bounds.left)
            top = min(top, bounds.top)
            right = max(right, bounds.right)
            bottom = max(bottom, bounds.bottom)
          }
          rect.intersect(left, top, right, bottom)
        }
        .intersect(rootBoundsOnScreen)

      layerSize = Size(
        width = clippedLayerBounds.width.coerceAtLeast(0f),
        height = clippedLayerBounds.height.coerceAtLeast(0f),
      )
      layerOffset = positionOnScreen - clippedLayerBounds.topLeft
    } else if (!backgroundBlurring && size.isSpecified && !visualEffect.shouldClip()) {
      if (shouldExpandLayer()) {
        val rect = size.toRect()
        val expanded = visualEffect.expandLayerRect(rect)
        layerSize = expanded.size
        layerOffset = rect.topLeft - expanded.topLeft
      } else {
        layerSize = size
        layerOffset = Offset.Zero
      }
    }

    invalidateIfNeeded()
  }

  private fun onPostDraw() {
    dirtyTracker = Bitmask()
  }

  private fun invalidateIfNeeded() {
    val invalidateRequired =
      dirtyTracker.any(DirtyFields.InvalidateFlags) ||
        visualEffect.needInvalidation()

    HazeLogger.d(TAG) {
      "invalidateRequired=$invalidateRequired. " +
        "Dirty params=${DirtyFields.stringify(dirtyTracker)}"
    }

    if (invalidateRequired) {
      invalidateDraw()
    }
  }

  private fun updateAreaOffsets() {
    // Calculate new offsets and detect changes for diff tracking
    val hasAreaOffsetsChanged = when {
      areaOffsets.size != areas.size -> true
      else -> {
        areas.any { area ->
          val newOffset = positionOnScreen - area.positionOnScreen
          !areaOffsets.contains(area) || areaOffsets[area] != newOffset.packedValue
        }
      }
    }

    if (hasAreaOffsetsChanged) {
      HazeLogger.d(TAG) { "areaOffsets changed" }
      dirtyTracker += DirtyFields.AreaOffsets

      areaOffsets.clear()
      areas.forEach { area ->
        val offset = positionOnScreen - area.positionOnScreen
        areaOffsets[area] = offset.packedValue
      }
    }
  }

  internal companion object {
    const val TAG = "HazeEffect"
  }
}

internal expect fun invalidateOnHazeAreaPreDraw(): Boolean

internal fun HazeEffectNode.shouldClipToAreaBounds(): Boolean {
  clipToAreasBounds?.let { return it }
  return visualEffect.preferClipToAreaBounds()
}

internal fun HazeEffectNode.shouldExpandLayer(): Boolean {
  expandLayerBounds?.let { return it }
  return true
}

/**
 * We need to use the area pre draw listener in a few situations when blurring is enabled:
 *
 * - Globally, if [invalidateOnHazeAreaPreDraw] is set to true. This is mostly for older
 *   Android versions.
 * - The source haze node is drawn in a different window to us. In this instance, we won't be
 *   in the same invalidation scope, so need to force invalidation. This handles cases
 *   like Dialogs.
 */
internal fun HazeEffectNode.shouldUsePreDrawListener(): Boolean {
  if (forceInvalidateOnPreDraw) return true
  if (invalidateOnHazeAreaPreDraw()) return true
  if (areas.any { it.windowId != windowId }) return true
  return false
}

@Suppress("ConstPropertyName", "ktlint:standard:property-naming")
internal object DirtyFields {
  const val BlurEnabled: Int = 0b1
  const val InputScale = BlurEnabled shl 1
  const val ScreenPosition = InputScale shl 1
  const val AreaOffsets = ScreenPosition shl 1
  const val Size = AreaOffsets shl 1
  const val BlurRadius = Size shl 1
  const val NoiseFactor = BlurRadius shl 1
  const val Mask = NoiseFactor shl 1
  const val BackgroundColor = Mask shl 1
  const val Tints = BackgroundColor shl 1
  const val FallbackTint = Tints shl 1
  const val Alpha = FallbackTint shl 1
  const val Progressive = Alpha shl 1
  const val Areas = Progressive shl 1
  const val LayerSize = Areas shl 1
  const val LayerOffset = LayerSize shl 1
  const val BlurredEdgeTreatment = LayerOffset shl 1
  const val DrawContentBehind = BlurredEdgeTreatment shl 1
  const val ClipToAreas = DrawContentBehind shl 1
  const val ExpandLayer = ClipToAreas shl 1
  const val ForcePreDraw = ExpandLayer shl 1

  const val RenderEffectAffectingFlags =
    BlurEnabled or
      InputScale or
      AreaOffsets or
      Size or
      LayerSize or
      LayerOffset or
      BlurRadius or
      NoiseFactor or
      Mask or
      Tints or
      FallbackTint or
      Progressive or
      BlurredEdgeTreatment or
      ClipToAreas or
      ExpandLayer

  const val InvalidateFlags =
    RenderEffectAffectingFlags or // Eventually we'll move this out of invalidation
      BlurEnabled or
      InputScale or
      Size or
      ScreenPosition or
      LayerSize or
      LayerOffset or
      BackgroundColor or
      Progressive or
      Areas or
      Alpha or
      BlurredEdgeTreatment or
      DrawContentBehind or
      ClipToAreas or
      ExpandLayer or
      ForcePreDraw

  fun stringify(dirtyTracker: Bitmask): String {
    val params = buildList {
      if (BlurEnabled in dirtyTracker) add("BlurEnabled")
      if (InputScale in dirtyTracker) add("InputScale")
      if (ScreenPosition in dirtyTracker) add("ScreenPosition")
      if (AreaOffsets in dirtyTracker) add("RelativePosition")
      if (Size in dirtyTracker) add("Size")
      if (LayerSize in dirtyTracker) add("LayerSize")
      if (LayerOffset in dirtyTracker) add("LayerOffset")
      if (BlurRadius in dirtyTracker) add("BlurRadius")
      if (NoiseFactor in dirtyTracker) add("NoiseFactor")
      if (Mask in dirtyTracker) add("Mask")
      if (BackgroundColor in dirtyTracker) add("BackgroundColor")
      if (Tints in dirtyTracker) add("Tints")
      if (FallbackTint in dirtyTracker) add("FallbackTint")
      if (Alpha in dirtyTracker) add("Alpha")
      if (Progressive in dirtyTracker) add("Progressive")
      if (Areas in dirtyTracker) add("Areas")
      if (ExpandLayer in dirtyTracker) add("ExpandLayer")
      if (ForcePreDraw in dirtyTracker) add("ForcePreDraw")
    }
    return params.joinToString(separator = ", ", prefix = "[", postfix = "]")
  }
}
