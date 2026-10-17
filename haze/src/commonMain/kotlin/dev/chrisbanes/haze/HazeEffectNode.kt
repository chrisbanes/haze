// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
@file:Suppress("UNCHECKED_CAST")

package dev.chrisbanes.haze

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
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
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

internal class HazeEffectNode(
  private val createBackdropRenderer: () -> HazeBackdropRenderer? = ::createHazeBackdropRenderer,
) :
  DelegatingNode(),
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

  internal var state: HazeState? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "state changed. Current: $field. New: $value" }
        clearRetainedOutput()
        dirtyTracker += DirtyFields.Areas
        field = value
        reconcileSourceDemand()
      }
    }

  internal var explicitInput: HazeInput? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "explicitInput changed. Current: $field. New: $value" }
        val previousInput = field
        val previousSources = resolvedSourcesInput()
        field = value
        if (previousInput is HazeInput.Backdrop && value !is HazeInput.Backdrop) {
          releaseBackdropRenderer()
        }
        // The content layer is only drawn for Content; free it now rather than at detach.
        if (isAttached && previousInput === HazeInput.Content) {
          contentDrawArea.releaseContentLayer(requireGraphicsContext())
        }
        refreshResolvedSourcesInput(previousSources)
      }
    }

  private val backdropBackendState = HazeBackdropBackendState()
  private val configuredSurfaces = mutableSetOf<HazeConfiguredSurfaceDraw>()
  private var backdropEnabledAtAttach = false
  private var backdropRenderer: HazeBackdropRenderer? = null
  private val captureConsumer = Any()
  private var captureDemandAreas: List<HazeArea> = emptyList()

  private fun resolvedSourcesInput(): HazeInput.Sources? = when (val input = explicitInput) {
    is HazeInput.Sources -> input
    is HazeInput.Backdrop -> if (backdropBackendState.usesFallback) {
      input.fallback
    } else {
      null
    }
    HazeInput.Content,
    null,
    -> null
  }

  private fun refreshResolvedSourcesInput(previous: HazeInput.Sources?) {
    val current = resolvedSourcesInput()
    if (previous == null || current == null || previous.state !== current.state) {
      clearRetainedOutput()
    }
    sourceSelectionSnapshotObserver?.clear(sourceSelectionObservationScope)
    if (current?.selection?.hasRefinements() != true) {
      stopSourceSelectionSnapshotObserver()
    }
    retainOutputWhenSourceUnavailable = current?.retention?.keepsLastFrame() ?: true
    state = current?.state
    dirtyTracker += DirtyFields.Areas
    syncCaptureDemand()
  }

  private var needsPreDrawInvalidation = false
  private var needsDirtyFieldsInvalidation = false
  private var needsVisualEffectInvalidation = false
  private var needsNextFrameVisualEffectInvalidation = false
  private var needsNextFrameLayerBounds = false
  private var layerBoundsRefreshPending = false
  private var needsContentInvalidation = false
  private val sourceDemandKey = Any()
  private var sourceDemandState: HazeState? = null
  private var hasRenderedTypedSourceOutput = false
  private var lastInputSnapshot: HazeEffectInputSnapshotImpl? = null
  private var inputCaptureGeneration = 0L
  private var isDrawing = false
  private var lastKnownCoordinates: LayoutCoordinates? = null
  internal val surface = HazeEffectSurface(this)
  private var preparedSurfaceBackdrop: HazeEffectBackdrop? = null
  private var surfaceBudgetFallback = false

  // False means a read refresh is pending: the last configuration pass, or its geometry pass, did not finish.
  private var observedUpdateCompleted = false

  // Source movement re-runs only the geometry pass, through its own read scope (#1456).
  private val geometryObserver = delegate(
    HazeEffectGeometryObserverNode {
      dirtyTracker += DirtyFields.AreaPositionReads
      refreshGeometry(fromObserver = true)
    },
  )

  // The member's geometry in its surface owner when the owner was last notified.
  private var notifiedSurfaceOwner: HazeEffectSurfaceOwner? = null
  private var notifiedSurfaceTransform: FloatArray? = null
  private var notifiedSurfaceSize: Size = Size.Unspecified
  private var notifiedLayerSize: Size = Size.Unspecified
  private var notifiedLayerOffset: Offset = Offset.Unspecified
  private val surfaceTransformScratch = Matrix()
  private var screenToEffectTransform: Matrix? = null
  private var sourceSelectionSnapshotObserver: SnapshotStateObserver? = null
  private var sourceSelectionObserverGeneration: Int = 0
  private val sourceSelectionObservationScope = Any()
  private val onObservedSourceSelectionChanged: (Any) -> Unit = {
    dirtyTracker += DirtyFields.Areas
    update()
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
      sourceSelectionSnapshotObserver = it
      it.start()
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

  private val areaTransforms = mutableMapOf<HazeArea, Matrix>()

  // Scratch for updateAreaTransforms only; retained transforms are always copies.
  private val areaTransformScratch = Matrix()

  private var _size: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "size changed. Current: $field. New: $value" }
        invalidateRetainedOutput()
        dirtyTracker += DirtyFields.Size
        field = value
      }
    }

  internal val size: Size get() = _size
  private var _layerSize: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "layerSize changed. Current: $field. New: $value" }
        invalidateRetainedOutput()
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
        invalidateRetainedOutput()
        dirtyTracker += DirtyFields.LayerOffset
        field = value
      }
    }

  internal val layerOffset: Offset
    get() = _layerOffset

  // The on-screen part of the layer, in effect space. An ancestor layer moving the effect with its
  // sources changes only this, so it is the only geometry that asks for the redraw (#1534).
  private var visibleLayerBounds: Rect = Rect.Zero
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "visibleLayerBounds changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.VisibleLayerBounds
        field = value
      }
    }

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

  internal var lastSeenStateAreas: List<HazeArea> = emptyList()
    private set
  private var lastSeenSelectionVersions = IntArray(0)

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
        syncCaptureDemand()
      }
    }

  internal val areas: List<HazeArea> get() = _areas

  private fun syncCaptureDemand() {
    val desiredAreas = if (resolvedSourcesInput() != null) _areas else emptyList()
    for (area in captureDemandAreas) {
      if (area !in desiredAreas) area.removeCaptureConsumer(captureConsumer)
    }
    for (area in desiredAreas) {
      if (area !in captureDemandAreas) area.addCaptureConsumer(captureConsumer)
    }
    captureDemandAreas = desiredAreas
  }

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
      layerBoundsRefreshPending = true
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

  private var retainOutputWhenSourceUnavailable: Boolean = true
  private val areaPreDrawListener by lazy(LazyThreadSafetyMode.NONE) {
    OnPreDrawListener(
      effectWindowId = { windowId },
      onPreDraw = { invalidateInputCapture ->
        if (invalidateInputCapture) inputCaptureGeneration++
        if (!needsPreDrawInvalidation) {
          needsPreDrawInvalidation = true
          invalidateHazeDraw(HazeInvalidationReason.PreDraw)
          invalidateSurfaceOwnerDraw()
        }
      },
    )
  }

  internal fun update() {
    if (!isAttached) return

    onObservedReadsChanged()
  }

  private var lifecycle: Lifecycle? = null
  private var trimMemoryCallbackLifecycle: Lifecycle? = null
  private var trimMemoryCallbackDisposable: DisposableHandle? = null

  override fun onAttach() {
    val previousSources = resolvedSourcesInput()
    backdropEnabledAtAttach = HazeFeatureFlags.isPlatformBackdropEnabled
    backdropBackendState.attach(backdropEnabledAtAttach)
    refreshResolvedSourcesInput(previousSources)
    val typedRenderer = typedEffectRenderer ?: createTypedRenderer?.invoke()?.also {
      typedEffectRenderer = it
    }
    checkNotNull(typedRenderer) { "A typed Haze renderer must be configured before attachment" }
    attachTypedRenderer(typedRenderer)
    typedRendererAttached = true
    updateTypedRenderer()
    rebindTrimMemoryCallback()
    update()
    reconcileSourceDemand()

    // Reactivation with an unchanged layout is not placed again (#1473); a first attach is unmeasured.
    val coordinates = requireLayoutCoordinates()
    if (coordinates.isAttached && coordinates.size != IntSize.Zero) {
      onPositioned(coordinates, fromPlacement = false)
    }
  }

  override fun onDetach() {
    surface.release()
    unregisterSourceDemand()
    stopSourceSelectionSnapshotObserver()
    trimMemoryCallbackDisposable?.dispose()
    trimMemoryCallbackDisposable = null
    trimMemoryCallbackLifecycle = null
    resetPendingInvalidations()
    _areas = emptyList()
    areaTransforms.clear()
    lastSeenStateAreas = emptyList()
    contentDrawArea.releaseContentLayer(requireGraphicsContext())
    clearRetainedOutput()
    pointerInputDelegate?.let { delegate ->
      val renderer = typedEffectRenderer as? HazeEffectRendererInteraction
      delegate.cancel(renderer)
      undelegate(delegate)
    }
    pointerInputDelegate = null
    lastKnownCoordinates = null
    observedUpdateCompleted = false
    notifiedSurfaceOwner = null
    notifiedSurfaceTransform = null
    notifiedSurfaceSize = Size.Unspecified
    notifiedLayerSize = Size.Unspecified
    notifiedLayerOffset = Offset.Unspecified
    visibleLayerBounds = Rect.Zero
    screenToEffectTransform = null
    lastInputSnapshot = null
    releaseBackdropRenderer()
    backdropBackendState.reset()
    disposeTypedRenderer()
    typedEffectRenderer = null
  }

  private fun reconcileSourceDemand() {
    val demandState = resolvedSourcesInput()?.state
    if (!isAttached) return
    if (sourceDemandState !== demandState) {
      sourceDemandState?.removeSourceDemand(sourceDemandKey)
      demandState?.addSourceDemand(sourceDemandKey)
      sourceDemandState = demandState
    }
  }

  private fun unregisterSourceDemand() {
    sourceDemandState?.removeSourceDemand(sourceDemandKey)
    sourceDemandState = null
  }

  override fun onObservedReadsChanged() {
    dirtyTracker += DirtyFields.AreaPositionReads
    refreshObservedReads()
  }

  /**
   * Runs the observed configuration pass, which ends with the geometry pass. Draw-time refreshes
   * pass [notifySurface] false: the owner is not notified while it draws, and the next pass that
   * completes outside drawing reports any change.
   */
  private fun refreshObservedReads(notifySurface: Boolean = true) {
    // Cleared first so an early exit leaves a read refresh pending.
    observedUpdateCompleted = false
    observeReads(::updateConfiguration)
    if (notifySurface) notifySurfaceIfGeometryChanged()
  }

  private fun refreshGeometry(fromObserver: Boolean = false) {
    if (!isAttached) return
    // Never place a stale selection: a source joining or leaving (even in this layout pass, #1437)
    // notifies both scopes in no fixed order, so membership changes run the configuration pass.
    val membershipChanged = Snapshot.withoutReadObservation {
      val stateAreas = state?.areas
      stateAreas != null && !stateAreasMatchLastSeen(stateAreas)
    }
    if (membershipChanged) {
      // The configuration scope reads the same list and versions, so an observer notification for
      // it is already pending; running it here as well would update the renderer twice.
      if (!fromObserver) refreshObservedReads()
      return
    }
    if (!observeGeometry()) observedUpdateCompleted = false
    notifySurfaceIfGeometryChanged()
  }

  /** Runs [updateGeometry] in its own read scope, outside any enclosing observation. */
  private fun observeGeometry(): Boolean {
    var completed = false
    Snapshot.withoutReadObservation {
      geometryObserver.observe { completed = updateGeometry() }
    }
    return completed
  }

  // Outside both read scopes: the owner reclusters synchronously.
  private fun notifySurfaceIfGeometryChanged() {
    val owner = surface.owner ?: run {
      notifiedSurfaceOwner = null
      return
    }
    // A new owner has not been told about this member's geometry yet.
    var changed = owner !== notifiedSurfaceOwner
    notifiedSurfaceOwner = owner
    if (notifiedLayerSize != layerSize || notifiedLayerOffset != layerOffset) {
      notifiedLayerSize = layerSize
      notifiedLayerOffset = layerOffset
      changed = true
    }
    val member = lastKnownCoordinates
    val ownerCoordinates = owner.surfaceCoordinates
    if (member != null && member.isAttached && ownerCoordinates?.isAttached == true) {
      surfaceTransformScratch.reset()
      ownerCoordinates.transformFrom(member, surfaceTransformScratch)
      val values = surfaceTransformScratch.values
      if (notifiedSurfaceTransform?.contentEquals(values) != true || notifiedSurfaceSize != size) {
        notifiedSurfaceTransform = values.copyOf()
        notifiedSurfaceSize = size
        changed = true
      }
    }
    if (changed) surface.changed()
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
        onPositioned(coordinates, fromPlacement = true)
      }
    }
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    onPositioned(coordinates, fromPlacement = false)
  }

  private fun onPositioned(coordinates: LayoutCoordinates, fromPlacement: Boolean) {
    if (!isAttached) {
      // This shouldn't happen, but it does...
      // https://github.com/chrisbanes/haze/issues/665
      return
    }

    lastKnownCoordinates = coordinates
    val previousSize = _size
    updatePositionGeometry(
      coordinates = coordinates,
      source = if (fromPlacement) "onPlaced" else "onGloballyPositioned",
    )
    // Sources placed earlier in this pass have not notified observers yet (#1437), so the geometry
    // pass always re-reads them.
    dirtyTracker += DirtyFields.AreaPositionReads
    // Glass resolves its style against the effect size (ADR-0011). A pending refresh also needs the
    // configuration reads that an early exit dropped.
    if (!observedUpdateCompleted || _size != previousSize) {
      refreshObservedReads()
    } else {
      refreshGeometry()
    }
  }

  private fun updatePositionGeometry(
    coordinates: LayoutCoordinates,
    source: String,
  ) {
    // Use node-local resolvedPositionStrategy instead of shared state
    _position = coordinates.positionForHaze(resolvedPositionStrategy)
    _size = coordinates.size.toSize()
    windowId = getWindowId()
    screenToEffectTransform = if (resolvedPositionStrategy == HazePositionStrategy.Screen) {
      coordinates.safeTransformToScreen()?.also { it.invert() }
    } else {
      null
    }

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
    if (surface.owner != null) {
      if (layerBoundsRefreshPending) refreshObservedReads(notifySurface = false)
      isDrawing = true
      try {
        if (explicitInput === HazeInput.Content && size.minDimension > 0f) {
          recordSurfaceContent()
          withVisualEffectTransform { contentDrawArea.contentLayer?.let { drawLayer(it) } }
        } else {
          withVisualEffectTransform { drawContentSafely() }
        }
      } finally {
        isDrawing = false
      }
      return
    }
    try {
      HazeLogger.d(TAG) { "-> start draw()" }

      if (!isAttached) {
        // This shouldn't happen, but it does...
        // https://github.com/chrisbanes/haze/issues/665
        return
      }

      if (layerBoundsRefreshPending) {
        refreshObservedReads(notifySurface = false)
      }
      // Bounds requested during the refresh are consumed by that update's calculation.
      // Only requests after this boundary need to survive until another frame.
      isDrawing = true
      for (area in areas) {
        require(!area.isContentDrawing) {
          "Modifier.hazeEffect nodes cannot draw an ancestor Modifier.hazeSource. " +
            "Use HazeSourceSelection or correct source zIndex values to exclude it."
        }
      }

      if (this@HazeEffectNode.size.isSpecified && this@HazeEffectNode.layerSize.isSpecified) {
        val shouldPrepareDraw = shouldPrepareEffectDraw()
        when {
          state != null -> drawSourceBackedEffect(shouldPrepareDraw)
          explicitInput is HazeInput.Backdrop -> drawBackdropEffect(shouldPrepareDraw)
          explicitInput === HazeInput.Content && shouldPrepareDraw -> drawContentEffect()
          else -> withVisualEffectTransform { drawContentSafely() }
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

  private fun ContentDrawScope.drawSourceBackedEffect(shouldPrepareDraw: Boolean) {
    val hasDrawableSourceLayers = hasDrawableSourceLayers()
    if (!retainOutputWhenSourceUnavailable && !hasDrawableSourceLayers) {
      clearRetainedOutput()
    }

    val shouldDrawEffect = shouldPrepareDraw && if (retainOutputWhenSourceUnavailable) {
      areas.isNotEmpty() || shouldDrawRetainedOutput()
    } else {
      hasDrawableSourceLayers
    }
    val scope = if (shouldDrawEffect) effectDrawScope() else null
    if (scope != null) {
      prepareEffectDraw(scope)
    }
    withVisualEffectTransform {
      if (scope != null) {
        drawEffect(scope)
        if (typedEffectRenderer != null && hasDrawableSourceLayers) {
          hasRenderedTypedSourceOutput = true
        }
      }
      drawContentSafely()
      if (scope != null) {
        drawEffectForeground(scope)
      }
    }
  }

  private fun ContentDrawScope.drawContentEffect() {
    recordSurfaceContent()
    val contentLayer = checkNotNull(contentDrawArea.contentLayer)
    val scope = effectDrawScope()
    prepareEffectDraw(scope)
    withVisualEffectTransform {
      if (shouldDrawContentBehindEffect()) {
        drawLayer(contentLayer)
      }
      drawEffect(scope)
      drawEffectForeground(scope)
    }
  }

  private fun ContentDrawScope.recordSurfaceContent() {
    val contentLayer = contentDrawArea.contentLayer
      ?.takeUnless { it.isReleased }
      ?: requireGraphicsContext().createGraphicsLayer().also {
        contentDrawArea.contentLayer = it
        HazeLogger.d(TAG) { "Updated contentLayer in content HazeArea" }
      }
    contentLayer.record(size.toIntSize()) {
      this@recordSurfaceContent.drawContentSafely()
    }
    val effectOnlyDraw = needsVisualEffectInvalidation &&
      !needsPreDrawInvalidation &&
      !needsDirtyFieldsInvalidation &&
      !needsContentInvalidation
    if (!effectOnlyDraw) {
      contentDrawArea.contentVersion++
    }
  }

  @OptIn(InternalHazeApi::class)
  private fun ContentDrawScope.drawBackdropEffect(shouldPrepareDraw: Boolean) {
    if (!shouldPrepareDraw) {
      withVisualEffectTransform { drawContentSafely() }
      return
    }
    if (backdropBackendState.usesFallback && resolvedSourcesInput() == null) {
      withVisualEffectTransform { drawContentSafely() }
      return
    }

    val capability = typedEffectRenderer as? HazeEffectRendererBackdrop<Any?>
    if (capability == null) {
      activateBackdropFallback(failed = false)
      withVisualEffectTransform { drawContentSafely() }
      return
    }

    val renderer = backdropRenderer ?: try {
      createBackdropRenderer()?.also {
        backdropRenderer = it
      }
    } catch (exception: Exception) {
      HazeLogger.d(TAG, exception) { "Backdrop renderer creation failed" }
      activateBackdropFallback(failed = true)
      null
    }
    if (renderer == null) {
      if (!backdropBackendState.usesFallback) {
        activateBackdropFallback(failed = false)
      }
      withVisualEffectTransform { drawContentSafely() }
      return
    }
    val supported = try {
      renderer.isSupported(drawContext.canvas)
    } catch (exception: Exception) {
      HazeLogger.d(TAG, exception) { "Backdrop renderer support check failed" }
      activateBackdropFallback(failed = true)
      false
    }
    if (!supported) {
      if (!backdropBackendState.usesFallback) {
        activateBackdropFallback(failed = false)
      }
      withVisualEffectTransform { drawContentSafely() }
      return
    }

    val scope = effectDrawScope()
    val backdrop = try {
      preparedSurfaceBackdrop ?: with(capability) { scope.backdropEffect(typedEffectStyle) }
    } catch (exception: Exception) {
      HazeLogger.d(TAG, exception) { "Backdrop effect preparation failed" }
      activateBackdropFallback(failed = true)
      withVisualEffectTransform { drawContentSafely() }
      return
    }
    if (backdrop == null) {
      // A renderer can decline single frames, such as Glass beyond its layer budget or a disabled
      // Blur pass. Only an undecided node with a source fallback switches to it; any other node
      // draws its content for this frame and retries native rendering on the next.
      if (
        backdropBackendState.selection == HazeBackdropBackendSelection.Undecided &&
        (explicitInput as? HazeInput.Backdrop)?.fallback != null
      ) {
        activateBackdropFallback(failed = false)
      }
      withVisualEffectTransform { drawContentSafely() }
      return
    }

    backdropBackendState.resolve(nativeAvailable = true)
    var backdropDrawn = false
    withVisualEffectTransform {
      try {
        withVisualEffectTransform(transform = backdrop.materialTransform) {
          val bounds = Rect(
            offset = Offset.Zero - layerOffset,
            size = layerSize,
          )
          val clip = Rect(Offset.Zero, size).takeIf { shouldClipEffectToNodeBounds() }
          backdropDrawn = trace("HazeBackdrop.draw") {
            renderer.configure(
              bounds = bounds,
              clip = clip,
              effect = backdrop.getPlatformEffect(),
              alpha = backdrop.alpha,
            ) && renderer.draw(drawContext.canvas)
          }
        }
      } catch (exception: Exception) {
        HazeLogger.d(TAG, exception) { "Backdrop renderer draw failed" }
      }
      if (!backdropDrawn) {
        if (layerSize.width > 0f && layerSize.height > 0f) {
          activateBackdropFallback(failed = true)
        } else {
          HazeLogger.d(TAG) { "Backdrop layer is empty; retrying native rendering next frame" }
        }
      }
      drawContentSafely()
      if (backdropDrawn) {
        drawEffectForeground(scope)
      }
    }
  }

  private fun activateBackdropFallback(failed: Boolean) {
    val previousSources = resolvedSourcesInput()
    if (failed) {
      backdropBackendState.fail()
    } else {
      backdropBackendState.resolve(nativeAvailable = false)
    }
    HazeLogger.d(TAG) {
      "Backdrop selected ${backdropBackendState.selection}; " +
        if (resolvedSourcesInput() == null) "drawing content unchanged" else "using source fallback"
    }
    releaseBackdropRenderer()
    refreshResolvedSourcesInput(previousSources)
    coroutineScope.launch {
      yield()
      if (isAttached && resolvedSourcesInput() != null) {
        dirtyTracker += DirtyFields.Areas
        invalidateVisualEffectLayerBounds()
        update()
      }
    }
  }

  private fun releaseBackdropRenderer() {
    runCatching { backdropRenderer?.release() }
      .onFailure { HazeLogger.d(TAG, it) { "Failed to release backdrop renderer" } }
    backdropRenderer = null
  }

  @OptIn(InternalHazeApi::class)
  private fun shouldPrepareEffectDraw(): Boolean {
    val renderer = typedEffectRenderer ?: return true
    val hooks = renderer as? HazeEffectRendererDrawHooks<Any?> ?: return true
    return hooks.shouldPrepareDraw(typedEffectStyle)
  }

  @OptIn(InternalHazeApi::class)
  private fun prepareEffectDraw(scope: HazeEffectDrawScopeImpl) {
    val renderer = typedEffectRenderer ?: return
    val hooks = renderer as? HazeEffectRendererDrawHooks<Any?> ?: return
    with(hooks) { scope.prepareDraw(typedEffectStyle) }
  }

  // One scope per draw: every hook on a path runs on the same ContentDrawScope receiver.
  private fun ContentDrawScope.effectDrawScope() =
    HazeEffectDrawScopeImpl(this, this@HazeEffectNode, typedEffectSampling)

  private fun drawEffect(scope: HazeEffectDrawScopeImpl) {
    val renderer = typedEffectRenderer ?: return
    with(renderer) { scope.draw(typedEffectStyle) }
  }

  @OptIn(InternalHazeApi::class)
  private fun drawEffectForeground(scope: HazeEffectDrawScopeImpl) {
    val renderer = typedEffectRenderer ?: return
    val hooks = renderer as? HazeEffectRendererDrawHooks<Any?> ?: return
    with(hooks) { scope.drawForeground(typedEffectStyle) }
  }

  @OptIn(InternalHazeApi::class)
  private fun shouldDrawContentBehindEffect(): Boolean {
    val renderer = typedEffectRenderer ?: return false
    return (renderer as? HazeEffectRendererDrawHooks<Any?>)
      ?.shouldDrawContentBehind() == true
  }

  /**
   * Configuration pass, observed by this node: source membership and selection, the renderer's
   * update and pointer input, then the geometry pass. Placement alone never runs it.
   */
  private fun updateConfiguration(): Unit = trace("HazeEffectNode-updateConfiguration") {
    if (!isAttached) return@trace

    windowId = getWindowId()

    val state = this.state
    if (state != null) {
      // Background blur: only recompute areas when relevant dirty flags are set.
      // Always read state.areas to maintain snapshot observation on HazeState._areas
      // (mutableStateListOf), even when we skip recomputation below.
      val stateAreas = state.areas

      // Detect membership, key and z-index changes in the observed state list. The
      // snapshot observation fires when items are added or removed, or when an area's
      // selectionVersion changes, but we gate the _areas rebuild behind DirtyFields.Areas.
      // If anything changed, mark ourselves dirty so the filtered / sorted _areas is
      // refreshed. We copy toList() only then, because stateAreas is a SnapshotStateList
      // reference and would otherwise mutate lastSeenStateAreas in place.
      if (!stateAreasMatchLastSeen(stateAreas)) {
        lastSeenStateAreas = stateAreas.toList()
        if (lastSeenSelectionVersions.size < stateAreas.size) {
          lastSeenSelectionVersions = IntArray(stateAreas.size)
        }
        for (i in stateAreas.indices) {
          lastSeenSelectionVersions[i] = stateAreas[i].selectionVersion
        }
        dirtyTracker += DirtyFields.Areas
      }

      if (DirtyFields.Areas in dirtyTracker) {
        _areas.forEach { area ->
          // Remove our pre draw listener from the current areas
          area.preDrawListeners -= areaPreDrawListener
        }

        val unfilteredAreas = stateAreas.orEmpty()
        val selection = checkNotNull(resolvedSourcesInput()).selection
        val ancestorSourceNode = if (selection.baseSelection() == HazeSourceSelection.Behind) {
          var result: HazeSourceNode? = null
          traverseAncestors(HazeTraversableNodeKeys.Source) { node ->
            val sourceNode = node as? HazeSourceNode
            if (sourceNode?.state === state) {
              result = sourceNode
              false
            } else {
              true
            }
          }
          result
        } else {
          null
        }

        HazeLogger.d(TAG) { "Background Areas observing: $unfilteredAreas" }
        val relatedSources = unfilteredAreas.mapNotNull { area ->
          val metadata = HazeSourceMetadata(key = area.key, zIndex = area.zIndex)
          val relationshipMatches = when (selection.baseSelection()) {
            HazeSourceSelection.All -> true
            HazeSourceSelection.Behind -> {
              ancestorSourceNode == null || metadata.zIndex < ancestorSourceNode.zIndex
            }
            else -> error("Unexpected base HazeSourceSelection")
          }
          if (relationshipMatches) area to metadata else null
        }
        val selectedSources = if (selection.hasRefinements()) {
          Snapshot.withoutReadObservation {
            lateinit var result: List<Pair<HazeArea, HazeSourceMetadata>>
            getOrCreateSourceSelectionSnapshotObserver().observeReads(
              sourceSelectionObservationScope,
              onObservedSourceSelectionChanged,
            ) {
              result = relatedSources.filter { (_, metadata) ->
                selection.matches(metadata)
              }
            }
            result
          }
        } else {
          relatedSources
        }
        val filteredAreas = selectedSources
          .sortedBy { (_, metadata) -> metadata.zIndex }
          .map { (area) ->
            HazeLogger.d(TAG) { "Background Area: $area. Included=true" }
            area
          }
        _areas = filteredAreas

        // Source sizes and layers are geometry: reading them here would re-run the renderer update.
        if (!retainOutputWhenSourceUnavailable && !Snapshot.withoutReadObservation { hasDrawableSourceLayers() }) {
          clearRetainedOutput()
        }
      }
    } else if (explicitInput === HazeInput.Content) {
      lastSeenStateAreas = emptyList()
      _areas = listOf(contentDrawArea)
    } else {
      lastSeenStateAreas = emptyList()
      _areas = emptyList()
    }

    // Allow the current renderer to update from CompositionLocals/state before calculating bounds,
    // so it can request a bounds refresh for values observed during this update.
    updateTypedRenderer()
    syncPointerInputDelegate()

    observedUpdateCompleted = observeGeometry()
  }

  /**
   * Geometry pass, observed separately: position strategy, area transforms, pre-draw listeners,
   * layer bounds and invalidation. Returns false when it exits early without coordinates.
   */
  private fun updateGeometry(): Boolean = trace("HazeEffectNode-updateGeometry") {
    if (!isAttached) return@trace true

    if (explicitInput === HazeInput.Content) {
      // Foreground (content) blur: always update contentDrawArea since its size,
      // position, and windowId may change every frame with no dirty flag.
      contentDrawArea.size = size
      contentDrawArea.coordinates.localPosition = position
      contentDrawArea.coordinates.screenPosition = position
      contentDrawArea.windowId = windowId
    }
    val state = this.state

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
          return@trace false
        }
        updatePositionGeometry(coordinates, "positionStrategyChanged")
      }
    }

    // Resolved lazily on first use; layout cannot re-root the effect during an update.
    // The configuration pass's updateTypedRenderer can dirty the layer bounds alone, so that block
    // resolves it if needed.
    var effectRoot: LayoutCoordinates? = null
    if (dirtyTracker.any(AreaTransformsDirtyFields)) {
      effectRoot = localEffectRoot()
      updateAreaTransforms(effectRoot)
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

    if (dirtyTracker.any(LayerBoundsDirtyFields)) {
      // Requests made before this point are consumed by the calculation below.
      layerBoundsRefreshPending = false
      if (effectRoot == null) effectRoot = localEffectRoot()
      val rootInEffect = rootBoundsInEffect(effectRoot)
      if (state != null && areas.isNotEmpty() && size.isSpecified && position.isSpecified) {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (area in areas) {
          val bounds = areaBoundsInEffect(area) ?: continue
          left = min(left, bounds.left)
          top = min(top, bounds.top)
          right = max(right, bounds.right)
          bottom = max(bottom, bounds.bottom)
        }
        val clippedLayerBounds = Rect(Offset.Zero, size)
          .letIf(shouldExpandLayer()) {
            calculateEffectLayerBounds(it, requireDensity())
          }
          .letIf(shouldClipToAreaBounds()) { rect ->
            rect.intersect(left, top, right, bottom)
          }
          .intersect(rootInEffect.extendedToAreas(left, top, right, bottom))

        _layerSize = Size(
          width = clippedLayerBounds.width.coerceAtLeast(0f),
          height = clippedLayerBounds.height.coerceAtLeast(0f),
        )
        _layerOffset = Offset.Zero - clippedLayerBounds.topLeft
      } else if (shouldDrawRetainedOutput()) {
        // Keep the previous layer bounds for source transition gaps. Recomputing
        // bounds with no areas collapses to the node size and clears the retained layer.
      } else if (
        explicitInput is HazeInput.Backdrop &&
        size.isSpecified
      ) {
        val rect = size.toRect()
        val expanded = rect.letIf(shouldExpandLayer()) {
          calculateEffectLayerBounds(it, requireDensity())
        }
        val clippedLayerBounds = expanded.intersect(rootInEffect)
        _layerSize = Size(
          width = clippedLayerBounds.width.coerceAtLeast(0f),
          height = clippedLayerBounds.height.coerceAtLeast(0f),
        )
        _layerOffset = rect.topLeft - clippedLayerBounds.topLeft
      } else if (
        explicitInput === HazeInput.Content &&
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
      if (_layerSize.isSpecified) {
        val visible = Rect(Offset.Zero - _layerOffset, _layerSize).intersect(rootInEffect)
        visibleLayerBounds = if (visible.isEmpty) Rect.Zero else visible
      }
    }

    invalidateIfNeeded()
    true
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
    configuredSurfaces.toList().forEach { it.onTrimMemory(level) }
  }

  internal fun invalidateVisualEffectLayerBounds() {
    if (isDrawing) needsNextFrameLayerBounds = true else layerBoundsRefreshPending = true
    dirtyTracker += DirtyFields.VisualEffectLayerBounds
    invalidateVisualEffectDraw()
  }

  internal fun invalidateVisualEffectDraw() {
    surface.changed()
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
    surface.changed()
    if (!needsContentInvalidation) {
      needsContentInvalidation = true
      if (!isDrawing) {
        invalidateHazeDraw(HazeInvalidationReason.Content)
      }
    }
  }

  private fun onPostDraw() {
    val carryLayerBounds = needsNextFrameLayerBounds
    dirtyTracker = if (carryLayerBounds) {
      Bitmask(DirtyFields.VisualEffectLayerBounds)
    } else {
      Bitmask()
    }
    val invalidateNextFrame = needsNextFrameVisualEffectInvalidation
    resetPendingInvalidations()
    layerBoundsRefreshPending = carryLayerBounds
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
    needsNextFrameLayerBounds = false
    layerBoundsRefreshPending = false
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

  private fun updateAreaTransforms(effectRoot: LayoutCoordinates?) {
    var haveAreaTransformsChanged = areaTransforms.keys.removeAll { it !in areas }

    val scratch = areaTransformScratch
    for (area in areas) {
      calculateSourceTransformInEffect(area, effectRoot, scratch)
      if (areaTransforms[area]?.hasSameValues(scratch) != true) {
        areaTransforms[area] = Matrix(scratch.values.copyOf())
        haveAreaTransformsChanged = true
      }
    }

    if (haveAreaTransformsChanged) {
      HazeLogger.d(TAG) { "areaTransforms changed" }
      dirtyTracker += DirtyFields.AreaTransforms
      invalidateSurfaceOwnerDraw()
    }
  }

  /**
   * A relocated member's output is drawn by its owner, whose recorded draw this node's own
   * invalidation does not reach when a layer separates them. Moving sources and pre-draw refreshes
   * need a redraw only, so this does not notify the owner, which would recluster its members.
   */
  private fun invalidateSurfaceOwnerDraw() {
    val owner = surface.owner as? DrawModifierNode ?: return
    if (owner.node.isAttached) owner.invalidateDraw()
  }

  internal fun sourceTransformInEffect(area: HazeArea): Matrix =
    areaTransforms[area] ?: Matrix().also { calculateSourceTransformInEffect(area, localEffectRoot(), it) }

  /** The attached effect's root for the Local strategy, which is the only strategy that uses it. */
  private fun localEffectRoot(): LayoutCoordinates? {
    if (resolvedPositionStrategy != HazePositionStrategy.Local) return null
    return lastKnownCoordinates?.takeIf { it.isAttached }?.findRootCoordinates()
  }

  /** Writes [area]'s transform into [transform]; [effectRoot] is the attached effect's root. */
  private fun calculateSourceTransformInEffect(
    area: HazeArea,
    effectRoot: LayoutCoordinates?,
    transform: Matrix,
  ) {
    val effectCoordinates = lastKnownCoordinates
    val sourceCoordinates = area.observedLayoutCoordinates
    if (effectCoordinates != null && sourceCoordinates != null) {
      if (
        resolvedPositionStrategy == HazePositionStrategy.Local &&
        effectRoot != null &&
        sourceCoordinates.isAttached &&
        sourceCoordinates.findRootCoordinates() === effectRoot
      ) {
        transform.reset()
        effectCoordinates.transformFrom(sourceCoordinates, transform)
        return
      }

      if (resolvedPositionStrategy == HazePositionStrategy.Screen) {
        val screenToEffect = screenToEffectTransform
        if (screenToEffect != null && sourceCoordinates.safeTransformToScreen(transform)) {
          transform *= screenToEffect
          return
        }
      }
    }

    transform.reset()
    val sourcePosition = area.coordinates.positionFor(resolvedPositionStrategy)
    if (sourcePosition.isSpecified && position.isSpecified) {
      val relativePosition = sourcePosition - position
      transform.translate(relativePosition.x, relativePosition.y)
    }
  }

  private fun areaBoundsInEffect(area: HazeArea): Rect? {
    return if (area.size.isSpecified) {
      sourceTransformInEffect(area).map(Rect(Offset.Zero, area.size))
    } else {
      null
    }
  }

  private fun rootBoundsInEffect(effectRoot: LayoutCoordinates?): Rect {
    val effectCoordinates = lastKnownCoordinates
    if (
      resolvedPositionStrategy == HazePositionStrategy.Local &&
      effectCoordinates != null &&
      effectRoot?.isAttached == true
    ) {
      return effectCoordinates.localBoundingBoxOf(effectRoot, clipBounds = false)
    }

    if (resolvedPositionStrategy == HazePositionStrategy.Screen) {
      screenToEffectTransform?.let { return it.map(rootBounds) }
    }

    return if (position.isSpecified) {
      Rect(offset = rootBounds.topLeft - position, size = rootBounds.size)
    } else {
      Rect.Zero
    }
  }

  // Reads every area's version, even after a mismatch, so the configuration pass observes all of them.
  private fun stateAreasMatchLastSeen(stateAreas: List<HazeArea>): Boolean {
    var matches = stateAreas.size == lastSeenStateAreas.size
    for (i in stateAreas.indices) {
      val area = stateAreas[i]
      val version = area.selectionVersion
      if (matches && (area !== lastSeenStateAreas[i] || version != lastSeenSelectionVersions[i])) {
        matches = false
      }
    }
    return matches
  }

  @OptIn(InternalHazeApi::class)
  private fun clearRetainedOutput() {
    hasRenderedTypedSourceOutput = false
    (typedEffectRenderer as? HazeEffectRendererRetainedOutput)?.clearRetainedOutput()
  }

  /** [clearRetainedOutput] for a size change, which lets the renderer keep its resources. */
  @OptIn(InternalHazeApi::class)
  private fun invalidateRetainedOutput() {
    hasRenderedTypedSourceOutput = false
    (typedEffectRenderer as? HazeEffectRendererRetainedOutput)?.invalidateRetainedOutput()
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
    transform: HazeEffectContentTransform =
      (typedEffectRenderer as? HazeEffectRendererInteraction)
        ?.currentContentTransform()
        ?: HazeEffectContentTransform.Identity,
    block: ContentDrawScope.() -> Unit,
  ) {
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

  internal fun hasDrawableInput(): Boolean =
    (explicitInput is HazeInput.Backdrop && !backdropBackendState.usesFallback) ||
      hasDrawableSourceLayers()

  internal fun inputSnapshot(): HazeEffectInputSnapshot? {
    lastInputSnapshot
      ?.takeIf { it.matches(this, inputCaptureGeneration) }
      ?.let { return it }
    val snapshots = ArrayList<HazeEffectInputSnapshotEntry>(areas.size)
    for (area in areas) {
      val layer = area.drawableContentLayer ?: continue
      val sourceTransform = Snapshot.withoutReadObservation { sourceTransformInEffect(area) }
      snapshots += HazeEffectInputSnapshotEntry(
        areaIdentity = area,
        layerIdentity = layer,
        contentVersion = area.contentVersion,
        transform = sourceTransform,
        size = area.size,
      )
    }
    return snapshots
      .takeIf { it.isNotEmpty() }
      ?.let { HazeEffectInputSnapshotImpl(it, inputCaptureGeneration) }
      .also { lastInputSnapshot = it }
  }

  internal fun surfaceBounds(owner: LayoutCoordinates?): Rect? {
    val member = lastKnownCoordinates ?: return null
    if (!isAttached || !member.isAttached || owner?.isAttached != true || size.minDimension <= 0f) return null
    return owner.localBoundingBoxOf(member, clipBounds = false)
  }

  internal fun surfaceLifecycleScope(): HazeEffectLifecycleScope = HazeEffectLifecycleScopeImpl(this)
  internal fun registerConfiguredSurface(surface: HazeConfiguredSurfaceDraw) {
    configuredSurfaces += surface
  }
  internal fun unregisterConfiguredSurface(surface: HazeConfiguredSurfaceDraw) {
    configuredSurfaces -= surface
  }
  internal fun completeSurfaceDraw() = onPostDraw()
  internal fun surfaceNativeEligible(): Boolean = !backdropBackendState.usesFallback || surfaceBudgetFallback

  internal fun surfaceInputEntries(owner: LayoutCoordinates?): List<HazeSurfaceInputEntry> {
    val transform = surfaceTransform(owner) ?: return emptyList()
    return areas.mapNotNull { area ->
      val layer = area.drawableContentLayer ?: return@mapNotNull null
      require(!area.isContentDrawing) { "A relocated effect cannot sample its drawing ancestor source" }
      val source = Snapshot.withoutReadObservation { sourceTransformInEffect(area) }
      // Apply source-to-member followed by member-to-owner, keeping both matrices unchanged.
      HazeSurfaceInputEntry(area, layer, Matrix(source.values.copyOf()).also { it *= transform })
    }
  }

  private fun surfaceTransform(owner: LayoutCoordinates?): Matrix? {
    if (surfaceBounds(owner) == null) return null
    return Matrix().also { checkNotNull(owner).transformFrom(checkNotNull(lastKnownCoordinates), it) }
  }

  private fun <T> withSurfaceScope(scope: DrawScope, owner: LayoutCoordinates?, block: ContentDrawScope.() -> T): T? {
    val matrix = surfaceTransform(owner) ?: return null
    var result: T? = null
    scope.withTransform({ transform(matrix) }) {
      val previousSize = drawContext.size
      drawContext.size = this@HazeEffectNode.size
      try {
        val relocated = object : ContentDrawScope, DrawScope by this {
          override fun drawContent() = Unit
        }
        result = relocated.block()
      } finally {
        drawContext.size = previousSize
      }
    }
    return result
  }

  internal fun surfaceFootprint(scope: DrawScope, owner: LayoutCoordinates?): HazeSurfaceFootprint? {
    val matrix = surfaceTransform(owner) ?: return null
    if (layerBoundsRefreshPending) refreshObservedReads(notifySurface = false)
    if (!layerSize.isSpecified || layerSize.minDimension <= 0f) return null
    val interaction = (typedEffectRenderer as? HazeEffectRendererInteraction)?.currentContentTransform()
      ?: HazeEffectContentTransform.Identity
    // The Android adapter floors/ceils these bounds before native sampling and output.
    val layer = Rect(floor(-layerOffset.x), floor(-layerOffset.y), ceil(layerSize.width - layerOffset.x), ceil(layerSize.height - layerOffset.y))
    var material = layer
    // A temporary composition-budget rejection can recover without detaching the member.
    // Real native setup/draw failures keep the existing attachment-sticky fallback policy.
    var native = explicitInput is HazeInput.Backdrop && (!backdropBackendState.usesFallback || surfaceBudgetFallback)
    if (native) {
      try {
        val renderer = backdropRenderer ?: createBackdropRenderer()?.also { backdropRenderer = it }
        if (renderer?.isSupported(scope.drawContext.canvas) != true || typedEffectRenderer !is HazeEffectRendererBackdrop<*>) {
          surfaceBudgetFallback = false
          activateBackdropFallback(failed = false)
          native = false
        }
      } catch (exception: Exception) {
        surfaceBudgetFallback = false
        HazeLogger.d(TAG, exception) { "Relocated backdrop support check failed" }
        activateBackdropFallback(failed = true)
        native = false
      }
    }
    if (native) {
      preparedSurfaceBackdrop = try {
        withSurfaceScope(scope, owner) {
          val capability = typedEffectRenderer as? HazeEffectRendererBackdrop<Any?>
          capability?.let { with(it) { HazeEffectDrawScopeImpl(this@withSurfaceScope, this@HazeEffectNode, typedEffectSampling).backdropEffect(typedEffectStyle) } }
        }
      } catch (exception: Exception) {
        surfaceBudgetFallback = false
        HazeLogger.d(TAG, exception) { "Relocated backdrop preparation failed" }
        activateBackdropFallback(failed = true)
        native = false
        null
      }
      preparedSurfaceBackdrop?.let { material = material.transformedBy(it.materialTransform) }
    }
    if (!native && shouldPrepareEffectDraw()) {
      withSurfaceScope(scope, owner) { prepareEffectDraw(effectDrawScope()) }
    }
    val output = matrix.map(layer.transformedBy(interaction))
    val capture = if (native) {
      // HWUI snapshots the transformed node subset with roundOut(), then filters only that image.
      // Convert that pixel-rounded root footprint back to owner coordinates, including affine scale.
      val inRoot = checkNotNull(lastKnownCoordinates).transformToRoot().map(material.transformedBy(interaction))
      val rounded = Rect(floor(inRoot.left), floor(inRoot.top), ceil(inRoot.right), ceil(inRoot.bottom))
      checkNotNull(owner).transformToRoot().also { it.invert() }.map(rounded)
    } else {
      null
    }
    return HazeSurfaceFootprint(
      output = if (capture != null) Rect(minOf(output.left, capture.left), minOf(output.top, capture.top), maxOf(output.right, capture.right), maxOf(output.bottom, capture.bottom)) else output,
      nativeCapture = capture,
    )
  }

  private fun Rect.transformedBy(transform: HazeEffectContentTransform): Rect = Rect(
    transform.pivot.x + (left - transform.pivot.x) * transform.scaleX,
    transform.pivot.y + (top - transform.pivot.y) * transform.scaleY,
    transform.pivot.x + (right - transform.pivot.x) * transform.scaleX,
    transform.pivot.y + (bottom - transform.pivot.y) * transform.scaleY,
  )

  internal fun usePortableSurfaceInput(failed: Boolean = false, temporary: Boolean = true) {
    if (explicitInput is HazeInput.Backdrop) {
      // A group may include already failed/unavailable neighbours. Budget rejection cannot make
      // their attachment-sticky fallback recoverable.
      val recoverable = temporary && !failed && (surfaceBudgetFallback || !backdropBackendState.usesFallback)
      if (failed || !surfaceBudgetFallback) activateBackdropFallback(failed = failed)
      surfaceBudgetFallback = recoverable
    }
    preparedSurfaceBackdrop = null
  }

  internal fun restoreSurfaceInput() {
    if (!isAttached || explicitInput !is HazeInput.Backdrop || !surfaceBudgetFallback) return
    surfaceBudgetFallback = false
    val previousSources = resolvedSourcesInput()
    releaseBackdropRenderer()
    backdropBackendState.attach(backdropEnabledAtAttach)
    refreshResolvedSourcesInput(previousSources)
    // Reached while the owner draws its surfaces, so it is not notified here.
    dirtyTracker += DirtyFields.AreaPositionReads
    refreshObservedReads(notifySurface = false)
  }

  internal fun drawRelocatedSurface(scope: DrawScope, owner: LayoutCoordinates?) {
    try {
      isDrawing = true
      withSurfaceScope(scope, owner) {
        for (area in areas) {
          require(!area.isContentDrawing) {
            "Modifier.hazeEffect nodes cannot draw an ancestor Modifier.hazeSource. " +
              "Use HazeSourceSelection or correct source zIndex values to exclude it."
          }
        }
        val prepare = shouldPrepareEffectDraw()
        when {
          state != null -> drawSourceBackedEffect(prepare)
          explicitInput is HazeInput.Backdrop -> drawBackdropEffect(prepare)
          explicitInput === HazeInput.Content && prepare -> {
            val effectScope = effectDrawScope()
            prepareEffectDraw(effectScope)
            withVisualEffectTransform {
              drawEffect(effectScope)
              drawEffectForeground(effectScope)
            }
          }
        }
      }
    } finally {
      preparedSurfaceBackdrop = null
      isDrawing = false
    }
  }

  private companion object {
    private const val TAG = "HazeEffect"
  }
}

/** A read scope of its own, so geometry reads re-run only the geometry pass. */
private class HazeEffectGeometryObserverNode(
  private val onChanged: () -> Unit,
) : Modifier.Node(), ObserverModifierNode {
  fun observe(block: () -> Unit) = observeReads(block)

  override fun onObservedReadsChanged() = onChanged()
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
  private val captureGeneration: Long,
) : HazeEffectInputSnapshot {
  fun matches(node: HazeEffectNode, captureGeneration: Long): Boolean {
    if (this.captureGeneration != captureGeneration) return false

    var drawableIndex = 0
    for (area in node.areas) {
      val layer = area.drawableContentLayer ?: continue
      val sourceTransform = Snapshot.withoutReadObservation { node.sourceTransformInEffect(area) }
      val entry = entries.getOrNull(drawableIndex++) ?: return false
      if (
        !entry.matches(
          areaIdentity = area,
          layerIdentity = layer,
          contentVersion = area.contentVersion,
          transform = sourceTransform,
          size = area.size,
        )
      ) {
        return false
      }
    }
    return drawableIndex == entries.size
  }

  override fun equals(other: Any?): Boolean =
    other is HazeEffectInputSnapshotImpl &&
      captureGeneration == other.captureGeneration &&
      entries == other.entries

  override fun hashCode(): Int = 31 * entries.hashCode() + captureGeneration.hashCode()
}

private class HazeEffectInputSnapshotEntry(
  private val areaIdentity: Any,
  private val layerIdentity: Any,
  private val contentVersion: Long,
  transform: Matrix,
  private val size: Size,
) {
  private val transformValues: FloatArray = transform.values.copyOf()

  fun matches(
    areaIdentity: Any,
    layerIdentity: Any,
    contentVersion: Long,
    transform: Matrix,
    size: Size,
  ): Boolean =
    this.areaIdentity === areaIdentity &&
      this.layerIdentity === layerIdentity &&
      this.contentVersion == contentVersion &&
      transformValues.contentEquals(transform.values) &&
      this.size == size

  override fun equals(other: Any?): Boolean =
    other is HazeEffectInputSnapshotEntry &&
      areaIdentity === other.areaIdentity &&
      layerIdentity === other.layerIdentity &&
      contentVersion == other.contentVersion &&
      transformValues.contentEquals(other.transformValues) &&
      size == other.size

  override fun hashCode(): Int {
    var result = areaIdentity.hashCode()
    result = 31 * result + layerIdentity.hashCode()
    result = 31 * result + contentVersion.hashCode()
    result = 31 * result + transformValues.contentHashCode()
    return 31 * result + size.hashCode()
  }
}

private fun Matrix.hasSameValues(other: Matrix): Boolean = values.contentEquals(other.values)

/**
 * Returns these root bounds with each side that the source bounds reach or cross moved out to the
 * source edge, and each side the sources stop short of left open. This clips like the root bounds
 * while the sources fill the root, but stays unchanged while an effect and its sources move
 * together across the root edge.
 */
private fun Rect.extendedToAreas(left: Float, top: Float, right: Float, bottom: Float): Rect {
  if (left > right || top > bottom) return this
  return Rect(
    left = if (left <= this.left) left else Float.NEGATIVE_INFINITY,
    top = if (top <= this.top) top else Float.NEGATIVE_INFINITY,
    right = if (right >= this.right) right else Float.POSITIVE_INFINITY,
    bottom = if (bottom >= this.bottom) bottom else Float.POSITIVE_INFINITY,
  )
}

private val HazeArea.observedLayoutCoordinates: LayoutCoordinates?
  get() {
    // Observe callbacks whose origin and size are unchanged but whose local transform changed.
    coordinateVersion
    return layoutCoordinates
  }

internal expect fun invalidateOnHazeAreaPreDraw(): Boolean

/**
 * Whether effects in the same window as their source also invalidate their input capture when a
 * snapshot is applied, as cross-window effects always do.
 *
 * API 31 keeps showing old RenderEffect output when a source's children move without the source
 * redrawing, such as items in a scrolling lazy list. Invalidating the capture gives the effect a
 * fresh RenderEffect. The observer sees every snapshot apply in the app, so on API 31 any running
 * animation recaptures attached effects once per frame.
 */
internal expect fun observesSameWindowSourceSnapshots(): Boolean

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
  const val AreaTransforms: Int = Position shl 1
  const val AreaPositionReads: Int = AreaTransforms shl 1
  const val Size: Int = AreaPositionReads shl 1
  const val Areas: Int = Size shl 1
  const val LayerSize: Int = Areas shl 1
  const val LayerOffset: Int = LayerSize shl 1
  const val ExpandLayer: Int = LayerOffset shl 1
  const val VisualEffectLayerBounds: Int = ExpandLayer shl 1
  const val VisibleLayerBounds: Int = VisualEffectLayerBounds shl 1

  const val InvalidateFlags: Int =
    Size or
      AreaTransforms or
      LayerSize or
      LayerOffset or
      Areas or
      ExpandLayer or
      VisualEffectLayerBounds or
      VisibleLayerBounds

  fun stringify(dirtyTracker: Bitmask): String {
    val params = buildList {
      if (Position in dirtyTracker) add("Position")
      if (AreaTransforms in dirtyTracker) add("AreaTransforms")
      if (AreaPositionReads in dirtyTracker) add("AreaPositionReads")
      if (Size in dirtyTracker) add("Size")
      if (LayerSize in dirtyTracker) add("LayerSize")
      if (LayerOffset in dirtyTracker) add("LayerOffset")
      if (Areas in dirtyTracker) add("Areas")
      if (ExpandLayer in dirtyTracker) add("ExpandLayer")
      if (VisualEffectLayerBounds in dirtyTracker) add("VisualEffectLayerBounds")
      if (VisibleLayerBounds in dirtyTracker) add("VisibleLayerBounds")
    }
    return params.joinToString(separator = ", ", prefix = "[", postfix = "]")
  }
}

/** Dirty fields that warrant recomputing area transforms. */
internal val AreaTransformsDirtyFields: Int =
  DirtyFields.Position or
    DirtyFields.Areas or
    DirtyFields.AreaPositionReads

/** Dirty fields that warrant recomputing layer bounds. */
internal val LayerBoundsDirtyFields: Int =
  DirtyFields.Position or
    DirtyFields.AreaTransforms or
    DirtyFields.AreaPositionReads or
    DirtyFields.Size or
    DirtyFields.Areas or
    DirtyFields.ExpandLayer or
    DirtyFields.VisualEffectLayerBounds
