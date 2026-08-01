// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
@file:Suppress("UNCHECKED_CAST")

package dev.chrisbanes.haze

import androidx.collection.MutableObjectLongMap
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
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
import androidx.lifecycle.Lifecycle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

internal class HazeEffectNode(
  state: HazeState? = null,
) : DelegatingNode(),
  CompositionLocalConsumerModifierNode,
  ModifierLocalModifierNode,
  GlobalPositionAwareModifierNode,
  LayoutAwareModifierNode,
  ObserverModifierNode,
  DrawModifierNode,
  TraversableNode {

  override val traverseKey: Any
    get() = HazeTraversableNodeKeys.Effect

  override val shouldAutoInvalidate: Boolean = false

  internal var dirtyTracker = Bitmask(DirtyFields.Areas)

  internal var state: HazeState? = state
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "state changed. Current: $field. New: $value" }
        clearRetainedOutput()
        dirtyTracker += DirtyFields.Areas
        field = value
      }
    }

  internal var explicitInput: HazeInput? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "explicitInput changed. Current: $field. New: $value" }
        val previous = field
        val changesRetainedContent = previous !is HazeInput.Sources ||
          value !is HazeInput.Sources ||
          previous.state !== value.state
        if (changesRetainedContent) {
          clearRetainedOutput()
        }
        sourceSelectionSnapshotObserver?.clear(sourceSelectionObservationScope)
        if ((value as? HazeInput.Sources)?.selection?.hasRefinements() != true) {
          stopSourceSelectionSnapshotObserver()
        }
        dirtyTracker += DirtyFields.Areas
        field = value
        when {
          value is HazeInput.Sources -> {
            retainOutputWhenSourceUnavailable = value.retention.keepsLastFrame()
          }
          previous is HazeInput.Sources -> retainOutputWhenSourceUnavailable = true
        }
      }
    }

  private var needsPreDrawInvalidation = false
  private var needsDirtyFieldsInvalidation = false
  private var needsVisualEffectInvalidation = false
  private var needsNextFrameVisualEffectInvalidation = false
  private var needsContentInvalidation = false
  private var hasRenderedTypedSourceOutput = false
  private var lastInputSnapshot: HazeEffectInputSnapshotImpl? = null
  private var isDrawing = false
  private var lastKnownCoordinates: LayoutCoordinates? = null
  private var sourceSelectionSnapshotObserver: SnapshotStateObserver? = null
  private var sourceSelectionObserverGeneration: Int = 0
  private val sourceSelectionObservationScope = Any()
  private val onObservedSourceSelectionChanged: (Any) -> Unit = {
    dirtyTracker += DirtyFields.Areas
    updateEffect()
  }

  private fun getOrCreateSourceSelectionSnapshotObserver(): SnapshotStateObserver {
    sourceSelectionSnapshotObserver?.let { return it }
    val observerGeneration = ++sourceSelectionObserverGeneration
    return SnapshotStateObserver { command ->
      coroutineScope.launch {
        if (isAttached && sourceSelectionObserverGeneration == observerGeneration) {
          command()
        }
      }
    }.also {
      it.start()
      sourceSelectionSnapshotObserver = it
    }
  }

  private fun stopSourceSelectionSnapshotObserver() {
    sourceSelectionObserverGeneration++
    sourceSelectionSnapshotObserver?.stop()
    sourceSelectionSnapshotObserver = null
  }

  private var _position: Offset = Offset.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "position changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.Position
        field = value
      }
    }

  internal val position: Offset get() = _position

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

  internal val size: Size get() = _size
  private var _layerSize: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "layerSize changed. Current: $field. New: $value" }
        clearRetainedOutput()
        dirtyTracker += DirtyFields.LayerSize
        field = value
      }
    }

  internal val layerSize: Size
    get() = _layerSize

  private var _layerOffset: Offset = Offset.Zero
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "layerOffset changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.LayerOffset
        field = value
      }
    }

  internal val layerOffset: Offset
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

  private val typedLifecycleScope: HazeEffectLifecycleScope by lazy(LazyThreadSafetyMode.NONE) {
    HazeEffectLifecycleScopeImpl(this)
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

  internal val areas: List<HazeArea> get() = _areas

  private val contentDrawArea by lazy { HazeArea() }

  private var typedEffectFactory: Any? = null
  private var typedEffectStyle: Any? = null
  private var typedEffectSampling: HazeSampling = HazeSampling.Default
  internal var typedEffectRenderer: HazeEffectRenderer<Any?>? = null
    private set
  private var typedRendererAttached: Boolean = false
  private var createTypedRenderer: (() -> HazeEffectRenderer<Any?>)? = null

  internal fun <Style> updateTypedEffect(
    factory: HazeEffectFactory<Style>,
    style: Style,
    sampling: HazeSampling,
  ) {
    @Suppress("UNCHECKED_CAST")
    val createRenderer = { factory.createRenderer() as HazeEffectRenderer<Any?> }
    if (typedEffectFactory !== factory) {
      val newRenderer = createRenderer()
      try {
        if (isAttached) {
          pointerInputDelegate?.cancel(
            typedEffectRenderer as? HazeEffectRendererInteraction,
          )
        }
        clearRetainedOutput()
        disposeTypedRenderer()
      } catch (throwable: Throwable) {
        runCatching { newRenderer.dispose() }
          .exceptionOrNull()
          ?.let(throwable::addSuppressed)
        throw throwable
      }
      if (isAttached) {
        attachTypedRenderer(newRenderer)
      }
      typedEffectFactory = factory
      typedEffectStyle = style
      typedEffectSampling = sampling
      typedEffectRenderer = newRenderer
      createTypedRenderer = createRenderer
      if (isAttached) {
        typedRendererAttached = true
        updateTypedRenderer()
      }
      dirtyTracker += DirtyFields.VisualEffectLayerBounds
      if (isAttached) {
        invalidateVisualEffectDraw()
      }
    } else if (typedEffectStyle != style || typedEffectSampling != sampling) {
      typedEffectStyle = style
      typedEffectSampling = sampling
      invalidateVisualEffectLayerBounds()
    } else {
      createTypedRenderer = createRenderer
    }
  }

  @OptIn(InternalHazeApi::class)
  private fun attachTypedRenderer(renderer: HazeEffectRenderer<Any?>) {
    try {
      (renderer as? HazeEffectRendererLifecycle<Any?>)?.attach(typedLifecycleScope)
    } catch (throwable: Throwable) {
      runCatching { (renderer as? HazeEffectRendererLifecycle<Any?>)?.detach() }
        .exceptionOrNull()
        ?.let(throwable::addSuppressed)
      runCatching { renderer.dispose() }
        .exceptionOrNull()
        ?.let(throwable::addSuppressed)
      throw throwable
    }
  }

  @OptIn(InternalHazeApi::class)
  private fun updateTypedRenderer() {
    val renderer = typedEffectRenderer ?: return
    (renderer as? HazeEffectRendererLifecycle<Any?>)?.update(
      scope = typedLifecycleScope,
      style = typedEffectStyle,
      sampling = typedEffectSampling,
    )
  }

  @OptIn(InternalHazeApi::class)
  private fun disposeTypedRenderer() {
    val renderer = typedEffectRenderer ?: return
    val wasAttached = typedRendererAttached
    typedEffectRenderer = null
    typedRendererAttached = false
    if (wasAttached) {
      try {
        (renderer as? HazeEffectRendererLifecycle<Any?>)?.detach()
      } finally {
        renderer.dispose()
      }
    } else {
      renderer.dispose()
    }
  }

  private var pointerInputDelegate: HazeEffectPointerInputNode? = null

  internal var expandLayerBounds: Boolean = true
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "expandLayer changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.ExpandLayer
        field = value
      }
    }

  internal var explicitExpandLayerBounds: Boolean = true
    set(value) {
      if (value != field) {
        field = value
        expandLayerBounds = value
      }
    }

  private var retainOutputWhenSourceUnavailable: Boolean = true
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

  private var lifecycle: Lifecycle? = null
  private var trimMemoryCallbackLifecycle: Lifecycle? = null
  private var trimMemoryCallbackDisposable: DisposableHandle? = null

  override fun onAttach() {
    val typedRenderer = typedEffectRenderer ?: createTypedRenderer?.invoke()?.also {
      typedEffectRenderer = it
    }
    checkNotNull(typedRenderer) { "A typed Haze renderer must be configured before attachment" }
    attachTypedRenderer(typedRenderer)
    typedRendererAttached = true
    updateTypedRenderer()
    rebindTrimMemoryCallback()
    update()
  }

  override fun onDetach() {
    stopSourceSelectionSnapshotObserver()
    trimMemoryCallbackDisposable?.dispose()
    trimMemoryCallbackDisposable = null
    trimMemoryCallbackLifecycle = null
    resetPendingInvalidations()
    _areas = emptyList()
    areaZIndexes.clear()
    areaKeys.clear()
    contentDrawArea.releaseLayer()
    clearRetainedOutput()
    pointerInputDelegate?.let { delegate ->
      val renderer = typedEffectRenderer as? HazeEffectRendererInteraction
      delegate.cancel(renderer)
      undelegate(delegate)
    }
    pointerInputDelegate = null
    lastKnownCoordinates = null
    lastInputSnapshot = null
    disposeTypedRenderer()
    typedEffectRenderer = null
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
          "Modifier.hazeEffect nodes cannot draw an ancestor Modifier.hazeSource. " +
            "Use HazeSourceSelection or correct source zIndex values to exclude it."
        }
      }

      if (this@HazeEffectNode.size.isSpecified && this@HazeEffectNode.layerSize.isSpecified) {
        val shouldPrepareDraw = shouldPrepareEffectDraw()
        if (state != null) {
          val hasDrawableSourceLayers = hasDrawableSourceLayers()
          if (!retainOutputWhenSourceUnavailable && !hasDrawableSourceLayers) {
            clearRetainedOutput()
          }

          val shouldDrawEffect = shouldPrepareDraw && if (retainOutputWhenSourceUnavailable) {
            areas.isNotEmpty() || shouldDrawRetainedOutput()
          } else {
            hasDrawableSourceLayers
          }
          if (shouldDrawEffect) {
            prepareEffectDraw()
          }
          withVisualEffectTransform {
            if (shouldDrawEffect) {
              drawEffect()
              if (typedEffectRenderer != null && hasDrawableSourceLayers) {
                hasRenderedTypedSourceOutput = true
              }
            }
            drawContentSafely()
            if (shouldDrawEffect) {
              drawEffectForeground()
            }
          }
        } else if (shouldPrepareDraw) {
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
          prepareEffectDraw()
          withVisualEffectTransform {
            if (shouldDrawContentBehindEffect()) {
              drawLayer(contentLayer)
            }
            drawEffect()
            drawEffectForeground()
          }
        } else {
          withVisualEffectTransform {
            drawContentSafely()
          }
        }
      } else {
        HazeLogger.d(TAG) { "-> State not valid, so no need to draw effect." }
        drawContentSafely()
      }
    } finally {
      isDrawing = false
      onPostDraw()
      HazeLogger.d(TAG) { "-> end draw()" }
    }
  }

  @OptIn(InternalHazeApi::class)
  private fun shouldPrepareEffectDraw(): Boolean {
    val renderer = typedEffectRenderer ?: return true
    val hooks = renderer as? HazeEffectRendererDrawHooks<Any?> ?: return true
    return hooks.shouldPrepareDraw(typedEffectStyle)
  }

  @OptIn(InternalHazeApi::class)
  private fun ContentDrawScope.prepareEffectDraw() {
    val renderer = typedEffectRenderer ?: return
    val hooks = renderer as? HazeEffectRendererDrawHooks<Any?> ?: return
    val scope = HazeEffectDrawScopeImpl(this, this@HazeEffectNode, typedEffectSampling)
    with(hooks) { scope.prepareDraw(typedEffectStyle) }
  }

  private fun ContentDrawScope.drawEffect() {
    val renderer = typedEffectRenderer ?: return
    val scope = HazeEffectDrawScopeImpl(this, this@HazeEffectNode, typedEffectSampling)
    with(renderer) { scope.draw(typedEffectStyle) }
  }

  @OptIn(InternalHazeApi::class)
  private fun ContentDrawScope.drawEffectForeground() {
    val renderer = typedEffectRenderer ?: return
    val hooks = renderer as? HazeEffectRendererDrawHooks<Any?> ?: return
    val scope = HazeEffectDrawScopeImpl(this, this@HazeEffectNode, typedEffectSampling)
    with(hooks) { scope.drawForeground(typedEffectStyle) }
  }

  @OptIn(InternalHazeApi::class)
  private fun shouldDrawContentBehindEffect(): Boolean {
    val renderer = typedEffectRenderer ?: return false
    return (renderer as? HazeEffectRendererDrawHooks<Any?>)
      ?.shouldDrawContentBehind() == true
  }

  private fun updateEffect(): Unit = trace("HazeEffectNode-updateEffect") {
    if (!isAttached) return@trace

    windowId = getWindowId()

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
        val selection = checkNotNull(explicitInput as? HazeInput.Sources).selection
        HazeLogger.d(TAG) { "Background Areas observing: $unfilteredAreas" }
        val relatedSources = unfilteredAreas.mapNotNull { area ->
          val info = HazeSourceInfo(key = area.key, zIndex = area.zIndex)
          val relationshipMatches = when (selection.baseSelection()) {
            HazeSourceSelection.All -> true
            HazeSourceSelection.Behind -> {
              ancestorSourceNode == null || info.zIndex < ancestorSourceNode.zIndex
            }
            else -> error("Unexpected base HazeSourceSelection")
          }
          if (relationshipMatches) area to info else null
        }
        val selectedSources = if (selection.hasRefinements()) {
          Snapshot.withoutReadObservation {
            lateinit var result: List<Pair<HazeArea, HazeSourceInfo>>
            getOrCreateSourceSelectionSnapshotObserver().observeReads(
              sourceSelectionObservationScope,
              onObservedSourceSelectionChanged,
            ) {
              result = relatedSources.filter { (_, info) ->
                selection.matches(info)
              }
            }
            result
          }
        } else {
          relatedSources
        }
        val filteredAreas = selectedSources
          .sortedBy { (_, info) -> info.zIndex }
          .map { (area) ->
            HazeLogger.d(TAG) { "Background Area: $area. Included=true" }
            area
          }
        _areas = filteredAreas

        if (!retainOutputWhenSourceUnavailable && !hasDrawableSourceLayers()) {
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

    // Allow the current renderer to update from CompositionLocals/state before calculating bounds,
    // so it can request a bounds refresh for values observed during this update.
    updateTypedRenderer()
    syncPointerInputDelegate()

    if (dirtyTracker.any(LayerBoundsDirtyFields)) {
      if (state != null && areas.isNotEmpty() && size.isSpecified && position.isSpecified) {
        val clippedLayerBounds = Rect(position, size)
          .letIf(shouldExpandLayer()) {
            calculateEffectLayerBounds(it, requireDensity())
          }
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
      } else if (
        state == null &&
        size.isSpecified &&
        !shouldClipEffectToNodeBounds() &&
        shouldExpandLayer()
      ) {
        val rect = size.toRect()
        val expanded = calculateEffectLayerBounds(rect, requireDensity())
        _layerSize = expanded.size
        _layerOffset = rect.topLeft - expanded.topLeft
      } else {
        _layerSize = size
        _layerOffset = Offset.Zero
      }
    }

    invalidateIfNeeded()
  }

  internal fun updateLifecycle(lifecycle: Lifecycle) {
    if (this.lifecycle !== lifecycle) {
      this.lifecycle = lifecycle
      if (isAttached) {
        rebindTrimMemoryCallback()
      }
    }
  }

  private fun calculateEffectLayerBounds(rect: Rect, density: androidx.compose.ui.unit.Density): Rect {
    val renderer = typedEffectRenderer ?: return rect
    val modifierBounds = Rect(offset = Offset.Zero, size = rect.size)
    val scope = HazeEffectLayoutScopeImpl(
      density = density,
      node = this,
      modifierBounds = modifierBounds,
    )
    val requiredBounds = with(renderer) {
      scope.calculateLayerBounds(typedEffectStyle)
    }
    return Rect(
      offset = rect.topLeft + requiredBounds.topLeft,
      size = requiredBounds.size,
    )
  }

  @OptIn(InternalHazeApi::class)
  private fun shouldClipEffectToNodeBounds(): Boolean {
    val renderer = typedEffectRenderer ?: return false
    return (renderer as? HazeEffectRendererDrawHooks<Any?>)
      ?.shouldClipToNodeBounds() == true
  }

  private fun rebindTrimMemoryCallback() {
    val lifecycle = lifecycle
    if (trimMemoryCallbackDisposable != null && trimMemoryCallbackLifecycle === lifecycle) return
    trimMemoryCallbackDisposable?.dispose()
    trimMemoryCallbackDisposable = null
    trimMemoryCallbackLifecycle = null
    trimMemoryCallbackDisposable = registerTrimMemoryCallback(
      context = requirePlatformContext(),
      lifecycle = lifecycle,
      callback = ::dispatchTrimMemory,
    )
    trimMemoryCallbackLifecycle = lifecycle
  }

  private fun dispatchTrimMemory(level: TrimMemoryLevel) {
    typedEffectRenderer?.onTrimMemory(level)
  }

  internal fun invalidateVisualEffectLayerBounds() {
    dirtyTracker += DirtyFields.VisualEffectLayerBounds
    invalidateVisualEffectDraw()
  }

  internal fun invalidateVisualEffectDraw() {
    when {
      isDrawing && !needsNextFrameVisualEffectInvalidation -> {
        needsNextFrameVisualEffectInvalidation = true
      }
      !isDrawing && !needsVisualEffectInvalidation -> {
        needsVisualEffectInvalidation = true
        invalidateHazeDraw(HazeInvalidationReason.VisualEffect)
      }
    }
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
    val invalidateNextFrame = needsNextFrameVisualEffectInvalidation
    resetPendingInvalidations()
    if (invalidateNextFrame) {
      coroutineScope.launch {
        yield()
        invalidateVisualEffectDraw()
      }
    }
  }

  private fun resetPendingInvalidations() {
    needsPreDrawInvalidation = false
    needsDirtyFieldsInvalidation = false
    needsVisualEffectInvalidation = false
    needsNextFrameVisualEffectInvalidation = false
    needsContentInvalidation = false
  }

  private fun invalidateIfNeeded() {
    val invalidateRequired =
      dirtyTracker.any(DirtyFields.InvalidateFlags)

    HazeLogger.d(TAG) {
      "invalidateRequired=$invalidateRequired. " +
        "Dirty params=${DirtyFields.stringify(dirtyTracker)}"
    }

    if (
      invalidateRequired &&
      !needsDirtyFieldsInvalidation &&
      !needsVisualEffectInvalidation &&
      !needsPreDrawInvalidation &&
      !needsContentInvalidation
    ) {
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

  @OptIn(InternalHazeApi::class)
  private fun clearRetainedOutput() {
    hasRenderedTypedSourceOutput = false
    (typedEffectRenderer as? HazeEffectRendererRetainedOutput)?.clearRetainedOutput()
  }

  @OptIn(InternalHazeApi::class)
  private fun syncPointerInputDelegate() {
    val interactive = typedEffectRenderer as? HazeEffectRendererInteraction
    if (interactive != null) {
      val required = interactive.observesPointerEvents
      when {
        required && pointerInputDelegate == null -> {
          pointerInputDelegate = delegate(
            HazeEffectPointerInputNode(
              interactiveRenderer = { typedEffectRenderer as? HazeEffectRendererInteraction },
              lifecycleScope = { typedLifecycleScope },
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
      return
    }
    pointerInputDelegate?.let { current ->
      undelegate(current)
      pointerInputDelegate = null
    }
  }

  private inline fun ContentDrawScope.withVisualEffectTransform(
    block: ContentDrawScope.() -> Unit,
  ) {
    val transform = (typedEffectRenderer as? HazeEffectRendererInteraction)
      ?.currentContentTransform()
      ?: HazeEffectContentTransform.Identity
    if (transform == HazeEffectContentTransform.Identity) {
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
      hasRenderedTypedSourceOutput &&
      (
        (typedEffectRenderer as? HazeEffectRendererRetainedOutput)
          ?.shouldDrawRetainedOutput() ?: true
        )
  }

  internal fun hasDrawableSourceLayers(): Boolean {
    return areas.any { area ->
      area.size.isSpecified &&
        area.size.minDimension.roundToInt() >= 1 &&
        area.contentLayer?.isReleased == false
    }
  }

  internal fun inputSnapshot(): HazeEffectInputSnapshot? {
    lastInputSnapshot?.takeIf { it.matches(this) }?.let { return it }
    val snapshots = ArrayList<HazeEffectInputSnapshotEntry>(areas.size)
    val effectPosition = position
    for (area in areas) {
      val layer = area.contentLayer
        ?.takeUnless { it.isReleased }
        ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 }
        ?: continue
      val sourcePosition = Snapshot.withoutReadObservation {
        area.coordinates.positionFor(resolvedPositionStrategy)
      }
      snapshots += HazeEffectInputSnapshotEntry(
        areaIdentity = area,
        layerIdentity = layer,
        contentVersion = area.contentVersion,
        position = (
          sourcePosition.takeIf(Offset::isSpecified) ?: Offset.Zero
          ) - effectPosition,
        size = area.size,
      )
    }
    return snapshots
      .takeIf { it.isNotEmpty() }
      ?.let(::HazeEffectInputSnapshotImpl)
      .also { lastInputSnapshot = it }
  }

  private companion object {
    private const val TAG = "HazeEffect"
  }
}

private class HazeEffectPointerInputNode(
  private val interactiveRenderer: () -> HazeEffectRendererInteraction?,
  private val lifecycleScope: () -> HazeEffectLifecycleScope,
) : Modifier.Node(), PointerInputModifierNode {
  private var cancellationDelivered = false

  override fun onPointerEvent(
    pointerEvent: PointerEvent,
    pass: PointerEventPass,
    bounds: IntSize,
  ) {
    if (pass == PointerEventPass.Final) {
      cancellationDelivered = false
      interactiveRenderer()?.onPointerEvent(pointerEvent, lifecycleScope())
    }
  }

  override fun onCancelPointerInput() {
    cancel(interactiveRenderer())
  }

  fun cancel(renderer: HazeEffectRendererInteraction?) {
    if (!cancellationDelivered) {
      cancellationDelivered = true
      renderer?.onCancelPointerInput(lifecycleScope())
    }
  }
}

private class HazeEffectInputSnapshotImpl(
  private val entries: List<HazeEffectInputSnapshotEntry>,
) : HazeEffectInputSnapshot {
  fun matches(node: HazeEffectNode): Boolean {
    val effectPosition = node.position
    var drawableIndex = 0
    for (area in node.areas) {
      val layer = area.contentLayer
        ?.takeUnless { it.isReleased }
        ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 }
        ?: continue
      val sourcePosition = Snapshot.withoutReadObservation {
        area.coordinates.positionFor(node.resolvedPositionStrategy)
      }
      val entry = entries.getOrNull(drawableIndex++) ?: return false
      if (
        !entry.matches(
          areaIdentity = area,
          layerIdentity = layer,
          contentVersion = area.contentVersion,
          position = (
            sourcePosition.takeIf(Offset::isSpecified) ?: Offset.Zero
            ) - effectPosition,
          size = area.size,
        )
      ) {
        return false
      }
    }
    return drawableIndex == entries.size
  }

  override fun equals(other: Any?): Boolean =
    other is HazeEffectInputSnapshotImpl && entries == other.entries

  override fun hashCode(): Int = entries.hashCode()
}

private class HazeEffectInputSnapshotEntry(
  private val areaIdentity: Any,
  private val layerIdentity: Any,
  private val contentVersion: Long,
  private val position: Offset,
  private val size: Size,
) {
  fun matches(
    areaIdentity: Any,
    layerIdentity: Any,
    contentVersion: Long,
    position: Offset,
    size: Size,
  ): Boolean =
    this.areaIdentity === areaIdentity &&
      this.layerIdentity === layerIdentity &&
      this.contentVersion == contentVersion &&
      this.position == position &&
      this.size == size

  override fun equals(other: Any?): Boolean =
    other is HazeEffectInputSnapshotEntry &&
      areaIdentity === other.areaIdentity &&
      layerIdentity === other.layerIdentity &&
      contentVersion == other.contentVersion &&
      position == other.position &&
      size == other.size

  override fun hashCode(): Int {
    var result = areaIdentity.hashCode()
    result = 31 * result + layerIdentity.hashCode()
    result = 31 * result + contentVersion.hashCode()
    result = 31 * result + position.hashCode()
    return 31 * result + size.hashCode()
  }
}

internal expect fun invalidateOnHazeAreaPreDraw(): Boolean

internal fun HazeEffectNode.shouldClipToAreaBounds(): Boolean {
  return (typedEffectRenderer as? HazeEffectRendererDrawHooks<Any?>)
    ?.shouldPreferClipToInputBounds() == true
}

internal fun HazeEffectNode.shouldExpandLayer(): Boolean {
  return expandLayerBounds
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
  if (invalidateOnHazeAreaPreDraw()) return true
  if (areas.any { it.windowId != windowId }) return true
  return false
}

@Suppress("ConstPropertyName", "ktlint:standard:property-naming")
internal object DirtyFields {
  const val Position: Int = 0b1
  const val AreaOffsets: Int = Position shl 1
  const val AreaPositionReads: Int = AreaOffsets shl 1
  const val Size: Int = AreaPositionReads shl 1
  const val Areas: Int = Size shl 1
  const val LayerSize: Int = Areas shl 1
  const val LayerOffset: Int = LayerSize shl 1
  const val ExpandLayer: Int = LayerOffset shl 1
  const val VisualEffectLayerBounds: Int = ExpandLayer shl 1

  const val InvalidateFlags: Int =
    Size or
      Position or
      AreaOffsets or
      LayerSize or
      LayerOffset or
      Areas or
      ExpandLayer or
      VisualEffectLayerBounds

  fun stringify(dirtyTracker: Bitmask): String {
    val params = buildList {
      if (Position in dirtyTracker) add("Position")
      if (AreaOffsets in dirtyTracker) add("AreaOffsets")
      if (AreaPositionReads in dirtyTracker) add("AreaPositionReads")
      if (Size in dirtyTracker) add("Size")
      if (LayerSize in dirtyTracker) add("LayerSize")
      if (LayerOffset in dirtyTracker) add("LayerOffset")
      if (Areas in dirtyTracker) add("Areas")
      if (ExpandLayer in dirtyTracker) add("ExpandLayer")
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
    DirtyFields.AreaPositionReads or
    DirtyFields.Size or
    DirtyFields.Areas or
    DirtyFields.ExpandLayer or
    DirtyFields.VisualEffectLayerBounds
