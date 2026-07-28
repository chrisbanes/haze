// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)

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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.toSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.DisposableHandle

/**
 * The [Modifier.Node] implementation used by [Modifier.hazeEffect].
 *
 * This is public API in order to aid custom extensible modifiers, _but_ we reserve the right
 * to be able to change the API in the future, hence why it is marked as experimental forever.
 */
@ExperimentalHazeApi
public class HazeEffectNode(
  state: HazeState? = null,
  public var block: (HazeEffectScope.() -> Unit)? = null,
) : DelegatingNode(),
  CompositionLocalConsumerModifierNode,
  ModifierLocalModifierNode,
  GlobalPositionAwareModifierNode,
  LayoutAwareModifierNode,
  ObserverModifierNode,
  DrawModifierNode,
  TraversableNode,
  HazeEffectScope {

  @Deprecated(
    message = "For binary compatibility only. Use the hazeEffect modifier APIs.",
    level = DeprecationLevel.HIDDEN,
  )
  public constructor() : this(state = null, block = null)

  override val traverseKey: Any
    get() = HazeTraversableNodeKeys.Effect

  override val shouldAutoInvalidate: Boolean = false

  internal var dirtyTracker = Bitmask(DirtyFields.Areas)

  public var state: HazeState? = state
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "state changed. Current: $field. New: $value" }
        clearRetainedOutput()
        dirtyTracker += DirtyFields.Areas
        field = value
      }
    }

  private var needsPreDrawInvalidation = false
  private var needsDirtyFieldsInvalidation = false
  private var needsVisualEffectInvalidation = false
  private var needsContentInvalidation = false
  private var isDrawing = false
  private var lastKnownCoordinates: LayoutCoordinates? = null

  override var inputScale: HazeInputScale = HazeInputScale.Default
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "inputScale changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += DirtyFields.InputScale
      }
    }

  private var _position: Offset = Offset.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "position changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.Position
        field = value
      }
    }

  public val position: Offset get() = _position

  internal var rootBounds: Rect = Rect.Zero
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "rootBounds changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.Position
        field = value
      }
    }

  private val areaOffsets = MutableObjectLongMap<HazeArea>()
  private val areaZIndexes = MutableObjectLongMap<HazeArea>()
  private val areaKeys = mutableMapOf<HazeArea, Any?>()

  private var _size: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "size changed. Current: $field. New: $value" }
        clearRetainedOutput()
        dirtyTracker += DirtyFields.Size
        field = value
      }
    }

  public val size: Size get() = _size
  private var _layerSize: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "layerSize changed. Current: $field. New: $value" }
        clearRetainedOutput()
        dirtyTracker += DirtyFields.LayerSize
        field = value
      }
    }

  public val layerSize: Size
    get() = _layerSize

  private var _layerOffset: Offset = Offset.Zero
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "layerOffset changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.LayerOffset
        field = value
      }
    }

  public val layerOffset: Offset
    get() = _layerOffset

  internal var windowId: Any? = null

  /**
   * Node-local resolved position strategy. This is computed from the configured strategy
   * and the areas this effect observes. Unlike the previous shared HazeState.resolvedStrategy,
   * this is per-node to prevent oscillation when effects in different windows disagree.
   */
  internal var resolvedPositionStrategy: HazePositionStrategy = HazePositionStrategy.Local
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "resolvedPositionStrategy changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.Position
        field = value
      }
    }

  internal val visualEffectContext: VisualEffectContext by lazy(LazyThreadSafetyMode.NONE) {
    HazeEffectNodeVisualEffectContext(this)
  }

  private var lastSeenStateAreas: List<HazeArea> = emptyList()

  private var _areas: List<HazeArea> = emptyList()
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

  public val areas: List<HazeArea> get() = _areas

  private val contentDrawArea by lazy { HazeArea() }

  override var canDrawArea: ((HazeArea) -> Boolean)? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "canDrawArea changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.Areas
        field = value
      }
    }

  public override var visualEffect: VisualEffect = VisualEffect.Empty
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "visualEffect changed. Current $field. New: $value" }
        val oldEffect = field
        pointerInputDelegate?.cancel(oldEffect as? InteractiveVisualEffect)
        if (isAttached) {
          attachVisualEffect(value)
          try {
            clearRetainedOutput()
          } catch (throwable: Throwable) {
            runCatching { detachVisualEffect(value) }
              .exceptionOrNull()
              ?.let(throwable::addSuppressed)
            throw throwable
          }
          runCatching { detachVisualEffect(oldEffect) }
        } else {
          clearRetainedOutput()
        }
        field = value
        dirtyTracker += DirtyFields.VisualEffectLayerBounds
        syncPointerInputDelegate()
        if (isAttached) {
          invalidateVisualEffectDraw()
        }
      }
    }

  private var pointerInputDelegate: HazeEffectPointerInputNode? = null

  override var drawContentBehind: Boolean = false
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

  override var retainOutputWhenSourceUnavailable: Boolean = true
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) {
          "retainOutputWhenSourceUnavailable changed. Current $field. New: $value"
        }
        if (!value) {
          clearRetainedOutput()
        }
        dirtyTracker += DirtyFields.RetainOutput
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
  private val areaPreDrawListener by lazy(LazyThreadSafetyMode.NONE) {
    OnPreDrawListener {
      if (!needsPreDrawInvalidation) {
        needsPreDrawInvalidation = true
        invalidateHazeDraw(HazeInvalidationReason.PreDraw)
      }
    }
  }

  internal fun update() {
    if (!isAttached) return

    onObservedReadsChanged()
  }

  private var trimMemoryCallbackDisposable: DisposableHandle? = null

  override fun onAttach() {
    attachVisualEffect(visualEffect)
    trimMemoryCallbackDisposable = registerTrimMemoryCallback(
      requirePlatformContext(),
    ) { level -> visualEffect.onTrimMemory(visualEffectContext, level) }
    update()
  }

  override fun onDetach() {
    trimMemoryCallbackDisposable?.dispose()
    trimMemoryCallbackDisposable = null
    resetPendingInvalidations()
    _areas = emptyList()
    areaZIndexes.clear()
    areaKeys.clear()
    contentDrawArea.releaseLayer()
    clearRetainedOutput()
    pointerInputDelegate?.let { delegate ->
      delegate.cancel(visualEffect as? InteractiveVisualEffect)
      undelegate(delegate)
    }
    pointerInputDelegate = null
    lastKnownCoordinates = null
    detachVisualEffect(visualEffect)
  }

  private fun HazeArea.releaseLayer() {
    contentLayer?.let { layer ->
      HazeLogger.d(TAG) { "Releasing content layer: $layer" }
      requireGraphicsContext().releaseGraphicsLayer(layer)
    }
    contentLayer = null
  }

  override fun onObservedReadsChanged() {
    dirtyTracker += DirtyFields.AreaPositionReads
    observeReads(::updateEffect)
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    Snapshot.withoutReadObservation {
      lastKnownCoordinates = coordinates
      // onPlaced is needed before first draw because onGloballyPositioned can arrive
      // after screenshot tests capture the first frame (#433).
      //
      // Lazy-list scroll can update local/root positions every placement while
      // onGloballyPositioned is too sparse for sticky haze headers (#994). Keep
      // screen coordinates guarded/authoritative via onGloballyPositioned, but
      // allow local coordinates to refresh from placement.
      if (resolvedPositionStrategy != HazePositionStrategy.Screen || position.isUnspecified) {
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

    lastKnownCoordinates = coordinates
    updatePositionGeometry(coordinates, source)
    updateEffect()
  }

  private fun updatePositionGeometry(coordinates: LayoutCoordinates, source: String) {
    // Use node-local resolvedPositionStrategy instead of shared state
    _position = coordinates.positionForHaze(resolvedPositionStrategy)
    _size = coordinates.size.toSize()
    windowId = getWindowId()

    val rootLayoutCoords = coordinates.findRootCoordinates()
    rootBounds = Rect(
      offset = rootLayoutCoords.positionForHaze(resolvedPositionStrategy),
      size = rootLayoutCoords.size.toSize(),
    )

    HazeLogger.d(TAG) {
      "$source: position=$position, size=$size"
    }
  }

  override fun ContentDrawScope.draw() {
    isDrawing = true
    try {
      HazeLogger.d(TAG) { "-> start draw()" }

      if (!isAttached) {
        // This shouldn't happen, but it does...
        // https://github.com/chrisbanes/haze/issues/665
        return
      }

      for (area in areas) {
        require(!area.isContentDrawing) {
          "Modifier.haze nodes can not draw Modifier.hazeChild nodes. " +
            "This should not happen if you are providing correct values for zIndex on Modifier.haze. " +
            "Alternatively you can use can `canDrawArea` to to filter out parent areas."
        }
      }

      if (this@HazeEffectNode.size.isSpecified && this@HazeEffectNode.layerSize.isSpecified) {
        if (state != null) {
          val hasDrawableSourceLayers = hasDrawableSourceLayers()
          if (!retainOutputWhenSourceUnavailable && !hasDrawableSourceLayers) {
            clearRetainedOutput()
          }

          val shouldDrawEffect = if (retainOutputWhenSourceUnavailable) {
            areas.isNotEmpty() || shouldDrawRetainedOutput()
          } else {
            hasDrawableSourceLayers
          }
          if (shouldDrawEffect) {
            with(visualEffect) {
              prepareDraw(visualEffectContext)
            }
          }
          withVisualEffectTransform {
            if (shouldDrawEffect) {
              with(visualEffect) {
                draw(visualEffectContext)
              }
            }
            drawContentSafely()
            if (shouldDrawEffect) {
              with(visualEffect) {
                drawForeground(visualEffectContext)
              }
            }
          }
        } else if (visualEffect === EmptyVisualEffect) {
          contentDrawArea.releaseLayer()
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
          val effectOnlyDraw = needsVisualEffectInvalidation &&
            !needsPreDrawInvalidation &&
            !needsDirtyFieldsInvalidation &&
            !needsContentInvalidation
          if (!effectOnlyDraw) {
            contentDrawArea.contentVersion++
          }
          with(visualEffect) {
            prepareDraw(visualEffectContext)
          }
          withVisualEffectTransform {
            if (drawContentBehind || visualEffect.shouldDrawContentBehind(visualEffectContext)) {
              drawLayer(contentLayer)
            }
            with(visualEffect) {
              draw(visualEffectContext)
              drawForeground(visualEffectContext)
            }
          }
        }
      } else {
        HazeLogger.d(TAG) { "-> State not valid, so no need to draw effect." }
        drawContentSafely()
      }
    } finally {
      onPostDraw()
      isDrawing = false
      HazeLogger.d(TAG) { "-> end draw()" }
    }
  }

  private fun updateEffect(): Unit = trace("HazeEffectNode-updateEffect") {
    if (!isAttached) return@trace

    windowId = getWindowId()

    // Read positionStrategy to establish snapshot observation.
    // When the user changes positionStrategy, this triggers updateEffect() to re-run
    // via onObservedReadsChanged().
    state?.positionStrategy

    // Invalidate if any of the effects triggered an invalidation, or we now have zero
    // effects but were previously showing some
    block?.invoke(this)

    val state = this.state
    if (state != null) {
      // Background blur: only recompute areas when relevant dirty flags are set.
      // Always read state.areas to maintain snapshot observation on HazeState._areas
      // (mutableStateListOf), even when we skip recomputation below.
      val stateAreas = state.areas

      // Detect membership changes in the observed state list. The snapshot
      // observation fires when items are added or removed, but we gate the
      // _areas rebuild behind DirtyFields.Areas. If the raw list has changed,
      // mark ourselves dirty so the filtered / sorted _areas is refreshed.
      // We copy toList() because stateAreas is a SnapshotStateList reference
      // and would otherwise mutate lastSeenStateAreas in place.
      val currentStateAreas = stateAreas.toList()
      if (
        currentStateAreas != lastSeenStateAreas ||
        haveAreaZIndexesChanged(currentStateAreas) ||
        haveAreaKeysChanged(currentStateAreas)
      ) {
        lastSeenStateAreas = currentStateAreas
        dirtyTracker += DirtyFields.Areas
      }

      if (DirtyFields.Areas in dirtyTracker) {
        _areas.forEach { area ->
          // Remove our pre draw listener from the current areas
          area.preDrawListeners -= areaPreDrawListener
        }

        val ancestorSourceNode =
          (findNearestAncestor(HazeTraversableNodeKeys.Source) as? HazeSourceNode)
            ?.takeIf { it.state == this.state }

        val unfilteredAreas = stateAreas.orEmpty()
        val filteredAreas = unfilteredAreas
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
        _areas = filteredAreas

        if (!retainOutputWhenSourceUnavailable && !hasDrawableSourceLayers()) {
          clearRetainedOutput()
        } else if (unfilteredAreas.isNotEmpty() && filteredAreas.isEmpty()) {
          clearRetainedOutput()
        }
        updateAreaZIndexes(currentStateAreas)
        updateAreaKeys(currentStateAreas)
      }
    } else {
      areaZIndexes.clear()
      areaKeys.clear()
      // Foreground (content) blur: always update contentDrawArea since its size,
      // position, and windowId may change every frame with no dirty flag.
      contentDrawArea.size = size
      contentDrawArea.coordinates.localPosition = position
      contentDrawArea.coordinates.screenPosition = position
      contentDrawArea.windowId = windowId
      _areas = listOf(contentDrawArea)
    }

    // Auto-promote position strategy when cross-window is detected
    // This is now node-local to prevent oscillation between effects in different windows
    state?.let { hazeState ->
      val newResolved = resolvePositionStrategy(
        configured = hazeState.positionStrategy,
        areas = _areas,
        windowId = windowId,
      )
      if (resolvedPositionStrategy != newResolved) {
        resolvedPositionStrategy = newResolved
        val coordinates = lastKnownCoordinates
        if (coordinates == null || !coordinates.isAttached) {
          // Without attached coordinates we cannot atomically enter the new coordinate space.
          invalidateIfNeeded()
          return@trace
        }
        updatePositionGeometry(coordinates, "positionStrategyChanged")
      }
    }

    if (dirtyTracker.any(AreaOffsetsDirtyFields)) {
      updateAreaOffsets()
    }

    if (shouldUsePreDrawListener()) {
      for (area in areas) {
        area.preDrawListeners += areaPreDrawListener
      }
    } else {
      // Always remove the listener when it should no longer be active,
      // even if DirtyFields.Areas was not set this frame.
      for (area in areas) {
        area.preDrawListeners -= areaPreDrawListener
      }
    }

    // Allow the current VisualEffect to update from CompositionLocals/state before calculating
    // bounds, so it can request a bounds refresh for values observed during this update.
    visualEffect.update(visualEffectContext)
    syncPointerInputDelegate()

    if (dirtyTracker.any(LayerBoundsDirtyFields)) {
      if (state != null && areas.isNotEmpty() && size.isSpecified && position.isSpecified) {
        val clippedLayerBounds = Rect(position, size)
          .letIf(shouldExpandLayer()) { visualEffect.calculateLayerBounds(it, requireDensity()) }
          .letIf(shouldClipToAreaBounds()) { rect ->
            var left = Float.POSITIVE_INFINITY
            var top = Float.POSITIVE_INFINITY
            var right = Float.NEGATIVE_INFINITY
            var bottom = Float.NEGATIVE_INFINITY
            for (area in areas) {
              val bounds = area.coordinates.boundsFor(resolvedPositionStrategy, area.size) ?: continue
              left = min(left, bounds.left)
              top = min(top, bounds.top)
              right = max(right, bounds.right)
              bottom = max(bottom, bounds.bottom)
            }
            rect.intersect(left, top, right, bottom)
          }
          .intersect(rootBounds)

        _layerSize = Size(
          width = clippedLayerBounds.width.coerceAtLeast(0f),
          height = clippedLayerBounds.height.coerceAtLeast(0f),
        )
        _layerOffset = position - clippedLayerBounds.topLeft
      } else if (shouldDrawRetainedOutput()) {
        // Keep the previous layer bounds for source transition gaps. Recomputing
        // bounds with no areas collapses to the node size and clears the retained layer.
      } else if (state == null && size.isSpecified && !visualEffect.shouldClipToNodeBounds() && shouldExpandLayer()) {
        val rect = size.toRect()
        val expanded = visualEffect.calculateLayerBounds(rect, requireDensity())
        _layerSize = expanded.size
        _layerOffset = rect.topLeft - expanded.topLeft
      } else {
        _layerSize = size
        _layerOffset = Offset.Zero
      }
    }

    invalidateIfNeeded()
  }

  internal fun invalidateVisualEffectLayerBounds() {
    dirtyTracker += DirtyFields.VisualEffectLayerBounds
    invalidateHazeDraw(HazeInvalidationReason.VisualEffect)
  }

  internal fun invalidateVisualEffectDraw() {
    needsVisualEffectInvalidation = true
    invalidateHazeDraw(HazeInvalidationReason.VisualEffect)
  }

  internal fun onForegroundContentDraw() {
    if (!needsContentInvalidation) {
      needsContentInvalidation = true
      if (!isDrawing) {
        invalidateHazeDraw(HazeInvalidationReason.Content)
      }
    }
  }

  private fun onPostDraw() {
    dirtyTracker = Bitmask()
    resetPendingInvalidations()
  }

  private fun resetPendingInvalidations() {
    needsPreDrawInvalidation = false
    needsDirtyFieldsInvalidation = false
    needsVisualEffectInvalidation = false
    needsContentInvalidation = false
  }

  private fun invalidateIfNeeded() {
    val invalidateRequired =
      dirtyTracker.any(DirtyFields.InvalidateFlags)

    HazeLogger.d(TAG) {
      "invalidateRequired=$invalidateRequired. " +
        "Dirty params=${DirtyFields.stringify(dirtyTracker)}"
    }

    if (invalidateRequired && !needsDirtyFieldsInvalidation) {
      needsDirtyFieldsInvalidation = true
      invalidateHazeDraw(HazeInvalidationReason.DirtyFields)
    }
  }

  private fun updateAreaOffsets() {
    // Calculate new offsets and detect changes for diff tracking
    val hasAreaOffsetsChanged = when {
      areaOffsets.size != areas.size -> true
      else -> {
        areas.any { area ->
          val areaPosition = area.coordinates.positionFor(resolvedPositionStrategy)
          val newOffset = position - areaPosition
          !areaOffsets.contains(area) || areaOffsets[area] != newOffset.packedValue
        }
      }
    }

    if (hasAreaOffsetsChanged) {
      HazeLogger.d(TAG) { "areaOffsets changed" }
      dirtyTracker += DirtyFields.AreaOffsets

      areaOffsets.clear()
      areas.forEach { area ->
        val areaPosition = area.coordinates.positionFor(resolvedPositionStrategy)
        val offset = position - areaPosition
        areaOffsets[area] = offset.packedValue
      }
    }
  }

  private fun haveAreaZIndexesChanged(stateAreas: List<HazeArea>): Boolean {
    if (areaZIndexes.size != stateAreas.size) return true
    return stateAreas.any { area ->
      !areaZIndexes.contains(area) || areaZIndexes[area] != area.zIndex.toRawBits().toLong()
    }
  }

  private fun updateAreaZIndexes(stateAreas: List<HazeArea>) {
    areaZIndexes.clear()
    stateAreas.forEach { area ->
      areaZIndexes[area] = area.zIndex.toRawBits().toLong()
    }
  }

  private fun haveAreaKeysChanged(stateAreas: List<HazeArea>): Boolean {
    if (areaKeys.size != stateAreas.size) return true
    return stateAreas.any { area ->
      !areaKeys.containsKey(area) || areaKeys[area] != area.key
    }
  }

  private fun updateAreaKeys(stateAreas: List<HazeArea>) {
    areaKeys.clear()
    stateAreas.forEach { area ->
      areaKeys[area] = area.key
    }
  }

  private fun clearRetainedOutput() {
    (visualEffect as? RetainedOutputVisualEffect)?.clearRetainedOutput()
  }

  private fun syncPointerInputDelegate() {
    val interactive = visualEffect as? InteractiveVisualEffect
    val required = interactive?.observesPointerEvents == true
    when {
      required && pointerInputDelegate == null -> {
        pointerInputDelegate = delegate(
          HazeEffectPointerInputNode(
            interactiveEffect = { visualEffect as? InteractiveVisualEffect },
            context = { visualEffectContext },
          ),
        )
      }
      !required && pointerInputDelegate != null -> {
        val current = pointerInputDelegate ?: return
        current.cancel(interactive)
        undelegate(current)
        pointerInputDelegate = null
      }
    }
  }

  private inline fun ContentDrawScope.withVisualEffectTransform(
    block: ContentDrawScope.() -> Unit,
  ) {
    val transform = (visualEffect as? InteractiveVisualEffect)
      ?.currentContentTransform(visualEffectContext)
      ?: VisualEffectTransform.Identity
    if (transform == VisualEffectTransform.Identity) {
      block()
    } else {
      scale(
        scaleX = transform.scaleX,
        scaleY = transform.scaleY,
        pivot = transform.pivot,
        block = { block(this@withVisualEffectTransform) },
      )
    }
  }

  private fun shouldDrawRetainedOutput(): Boolean {
    return retainOutputWhenSourceUnavailable &&
      (visualEffect as? RetainedOutputVisualEffect)?.shouldDrawRetainedOutput(visualEffectContext) == true
  }

  private fun hasDrawableSourceLayers(): Boolean {
    return areas.any { area ->
      area.size.isSpecified &&
        area.size.minDimension.roundToInt() >= 1 &&
        area.contentLayer?.isReleased == false
    }
  }

  private companion object {
    private const val TAG = "HazeEffect"
  }
}

private class HazeEffectPointerInputNode(
  private val interactiveEffect: () -> InteractiveVisualEffect?,
  private val context: () -> VisualEffectContext,
) : Modifier.Node(), PointerInputModifierNode {
  private var cancellationDelivered = false

  override fun onPointerEvent(
    pointerEvent: PointerEvent,
    pass: PointerEventPass,
    bounds: IntSize,
  ) {
    if (pass == PointerEventPass.Final) {
      cancellationDelivered = false
      interactiveEffect()?.onPointerEvent(pointerEvent, context())
    }
  }

  override fun onCancelPointerInput() {
    cancel(interactiveEffect())
  }

  fun cancel(effect: InteractiveVisualEffect?) {
    if (!cancellationDelivered) {
      cancellationDelivered = true
      effect?.onCancelPointerInput(context())
    }
  }
}

internal expect fun invalidateOnHazeAreaPreDraw(): Boolean

internal fun HazeEffectNode.shouldClipToAreaBounds(): Boolean {
  clipToAreasBounds?.let { return it }
  return visualEffect.shouldPreferClipToAreaBounds()
}

// Tracks currently attached effect instances across all nodes.
// Multiple entries are expected (different effect instances on different nodes),
// but a single effect instance must never be owned by more than one node.
// Confined to main-thread access; Compose modifier node callbacks run exclusively on main.
// Entries are removed in detachVisualEffect(), called from onDetach() which Compose
// guarantees before the node becomes unreachable.
private val attachedEffectOwners = mutableListOf<Pair<VisualEffect, HazeEffectNode>>()

internal fun HazeEffectNode.attachVisualEffect(effect: VisualEffect) {
  if (effect === EmptyVisualEffect) return // No-op singleton; no ownership needed

  val current = attachedEffectOwners
    .firstOrNull { (attachedEffect, _) -> attachedEffect === effect }
    ?.second

  check(current == null || current === this) {
    "VisualEffect instances are single-owner and cannot be shared across multiple hazeEffect nodes."
  }

  if (current === this) return // Already attached to this node; no-op

  attachedEffectOwners += effect to this

  runCatching {
    effect.attach(visualEffectContext)
  }.onFailure {
    attachedEffectOwners.removeAll { (attachedEffect, attachedNode) ->
      attachedEffect === effect && attachedNode === this
    }
  }.getOrThrow()
}

internal fun HazeEffectNode.detachVisualEffect(effect: VisualEffect) {
  if (effect === EmptyVisualEffect) return // No-op singleton; no ownership needed

  try {
    effect.detach(visualEffectContext)
  } finally {
    attachedEffectOwners.removeAll { (attachedEffect, attachedNode) ->
      attachedEffect === effect && attachedNode === this
    }
  }
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
  const val InputScale: Int = 0b1
  const val Position: Int = InputScale shl 1
  const val AreaOffsets: Int = Position shl 1
  const val AreaPositionReads: Int = AreaOffsets shl 1
  const val Size: Int = AreaPositionReads shl 1
  const val Areas: Int = Size shl 1
  const val LayerSize: Int = Areas shl 1
  const val LayerOffset: Int = LayerSize shl 1
  const val DrawContentBehind: Int = LayerOffset shl 1
  const val ClipToAreas: Int = DrawContentBehind shl 1
  const val ExpandLayer: Int = ClipToAreas shl 1
  const val RetainOutput: Int = ExpandLayer shl 1
  const val ForcePreDraw: Int = RetainOutput shl 1
  const val VisualEffectLayerBounds: Int = ForcePreDraw shl 1

  const val InvalidateFlags: Int =
    InputScale or
      Size or
      Position or
      AreaOffsets or
      LayerSize or
      LayerOffset or
      Areas or
      DrawContentBehind or
      ClipToAreas or
      ExpandLayer or
      RetainOutput or
      ForcePreDraw or
      VisualEffectLayerBounds

  fun stringify(dirtyTracker: Bitmask): String {
    val params = buildList {
      if (InputScale in dirtyTracker) add("InputScale")
      if (Position in dirtyTracker) add("Position")
      if (AreaOffsets in dirtyTracker) add("AreaOffsets")
      if (AreaPositionReads in dirtyTracker) add("AreaPositionReads")
      if (Size in dirtyTracker) add("Size")
      if (LayerSize in dirtyTracker) add("LayerSize")
      if (LayerOffset in dirtyTracker) add("LayerOffset")
      if (Areas in dirtyTracker) add("Areas")
      if (DrawContentBehind in dirtyTracker) add("DrawContentBehind")
      if (ClipToAreas in dirtyTracker) add("ClipToAreas")
      if (ExpandLayer in dirtyTracker) add("ExpandLayer")
      if (RetainOutput in dirtyTracker) add("RetainOutput")
      if (ForcePreDraw in dirtyTracker) add("ForcePreDraw")
      if (VisualEffectLayerBounds in dirtyTracker) add("VisualEffectLayerBounds")
    }
    return params.joinToString(separator = ", ", prefix = "[", postfix = "]")
  }
}

/** Dirty fields that warrant recomputing area offsets. */
internal val AreaOffsetsDirtyFields: Int =
  DirtyFields.Position or
    DirtyFields.Areas or
    DirtyFields.AreaPositionReads

/** Dirty fields that warrant recomputing layer bounds. */
internal val LayerBoundsDirtyFields: Int =
  DirtyFields.Position or
    DirtyFields.AreaOffsets or
    DirtyFields.Size or
    DirtyFields.Areas or
    DirtyFields.ExpandLayer or
    DirtyFields.ClipToAreas or
    DirtyFields.RetainOutput or
    DirtyFields.VisualEffectLayerBounds
