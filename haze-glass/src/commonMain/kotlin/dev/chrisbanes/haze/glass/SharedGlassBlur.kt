// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazePositionStrategy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.MutableRuntimeShaderRenderEffect
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.asComposeRenderEffect
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Shares the expensive source-space blur between compatible effects observing the same source.
 *
 * The registry is main-thread confined: effect lifecycle and draw callbacks are dispatched from
 * Compose's UI thread.
 */
@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal object SharedGlassBlurRegistry {
  private val ownerGroups = mutableMapOf<RuntimeShaderGlassDelegate, SharedGlassBlurGroup>()
  private val groups = mutableMapOf<SharedGlassBlurKey, SharedGlassBlurGroup>()

  fun update(
    owner: RuntimeShaderGlassDelegate,
    context: VisualEffectContext,
    params: GlassRenderParams,
    opticalKey: GlassOpticalEffectKey?,
    detailKey: GlassRefractionDetailEffectKey?,
    graphicsContext: GraphicsContext,
  ): SharedGlassBlurGroup? {
    val key = sharedBlurKey(context, params, graphicsContext)
    val previous = ownerGroups[owner]
    if (key == null) {
      remove(owner)
      return null
    }

    val group = if (previous?.key == key) {
      previous
    } else {
      remove(owner)
      groups.getOrPut(key) { SharedGlassBlurGroup(key) }.also {
        ownerGroups[owner] = it
      }
    }
    group.update(
      owner = owner,
      context = context,
      opticalKey = opticalKey,
      opticalDepth = params.depth,
      detailKey = detailKey,
    )
    return group.takeIf { it.memberCount > 1 }
  }

  fun remove(owner: RuntimeShaderGlassDelegate) {
    val group = ownerGroups.remove(owner) ?: return
    group.remove(owner)
    when (group.memberCount) {
      0 -> {
        groups.remove(group.key)
        group.destroy()
      }
      1 -> group.releaseLayers()
    }
  }

  fun releaseFor(owner: RuntimeShaderGlassDelegate) {
    val group = ownerGroups[owner] ?: return
    group.releaseLayers()
    group.invalidateMembers()
  }

  private fun sharedBlurKey(
    context: VisualEffectContext,
    params: GlassRenderParams,
    graphicsContext: GraphicsContext,
  ): SharedGlassBlurKey? {
    if (
      !supportsSharedGlassBlur ||
      context.state == null ||
      params.progressive != null ||
      params.depth <= 0f ||
      params.blurRadiusPx <= 0f ||
      !context.position.isSpecified ||
      !context.layerSize.isSpecified ||
      context.layerSize.width <= 0f ||
      context.layerSize.height <= 0f
    ) {
      return null
    }
    return SharedGlassBlurKey(
      state = context.state ?: return null,
      areas = context.areas.toList(),
      positionStrategy = context.positionStrategy,
      windowId = context.windowId,
      rootBounds = context.rootBounds,
      graphicsContext = graphicsContext,
      captureScale = params.coordinates.scaleFactor,
      effectiveRadiusPx = params.blurRadiusPx,
      sigmaPx = params.blurSigmaPx,
    )
  }
}

@OptIn(ExperimentalHazeApi::class)
internal data class SharedGlassBlurKey(
  val state: HazeState,
  val areas: List<HazeArea>,
  val positionStrategy: HazePositionStrategy,
  val windowId: Any?,
  val rootBounds: Rect,
  val graphicsContext: GraphicsContext,
  val captureScale: Float,
  val effectiveRadiusPx: Float,
  val sigmaPx: Float,
)

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class SharedGlassBlurGroup(
  val key: SharedGlassBlurKey,
) {
  private val members = mutableMapOf<RuntimeShaderGlassDelegate, SharedGlassBlurMember>()

  private var source: GraphicsLayer? = null
  private var prefiltered: GraphicsLayer? = null
  private var horizontal: GraphicsLayer? = null
  internal var blurred: GraphicsLayer? = null
    private set
  private var opticalAtlasInput: GraphicsLayer? = null
  internal var opticalAtlas: GraphicsLayer? = null
    private set
  private var refractionDetailAtlasSource: GraphicsLayer? = null
  internal var refractionDetailAtlas: GraphicsLayer? = null
    private set

  private var horizontalShader: MutableRuntimeShaderRenderEffect? = null
  private var verticalShader: MutableRuntimeShaderRenderEffect? = null
  private var prefilterShader: MutableRuntimeShaderRenderEffect? = null
  private var opticalAtlasShader: MutableRuntimeShaderRenderEffect? = null
  private var refractionDetailAtlasShader: MutableRuntimeShaderRenderEffect? = null
  private var effectsKey: GlassBlurEffectKey? = null
  private var effects: SharedGlassBlurEffects? = null
  private var opticalAtlasKey: SharedGlassOpticalAtlasKey? = null
  private var refractionDetailAtlasKey: SharedGlassRefractionDetailAtlasKey? = null
  private var lastSourceSnapshot: GlassSourceSnapshot? = null
  private var lastBounds: Rect? = null

  val memberCount: Int get() = members.size

  fun update(
    owner: RuntimeShaderGlassDelegate,
    context: VisualEffectContext,
    opticalKey: GlassOpticalEffectKey?,
    opticalDepth: Float,
    detailKey: GlassRefractionDetailEffectKey?,
  ) {
    val topLeft = context.position - context.layerOffset
    members[owner] = SharedGlassBlurMember(
      context = context,
      bounds = Rect(topLeft, context.layerSize),
      opticalKey = opticalKey,
      opticalDepth = opticalDepth,
      detailKey = detailKey,
    )
  }

  fun remove(owner: RuntimeShaderGlassDelegate) {
    members.remove(owner)
  }

  fun invalidateMembers() {
    members.values.forEach { it.context.invalidateDraw() }
  }

  fun DrawScope.obtain(context: VisualEffectContext): SharedGlassBlurOutput? {
    if (memberCount <= 1) return null
    val bounds = unionBounds() ?: return null
    val sampleSize = IntSize(
      width = (bounds.width * key.captureScale).roundToInt().coerceAtLeast(1),
      height = (bounds.height * key.captureScale).roundToInt().coerceAtLeast(1),
    )
    val plan = SemanticBlurPlan.createForSigma(
      sampleWidth = sampleSize.width,
      sampleHeight = sampleSize.height,
      effectiveRadiusPx = key.effectiveRadiusPx,
      sigmaPx = key.sigmaPx,
      allowMultiscale = true,
    )
    val blurKey = GlassBlurEffectKey(
      plan = plan,
      progressive = null,
      maskOrigin = Offset.Zero,
      maskSize = Size.Zero,
      maskCoordinateScale = 1f,
    )
    val currentEffects = updateEffects(blurKey)
    ensureLayers(plan)

    val layerOffset = context.position - bounds.topLeft
    val sourceState = context.resolveGlassSourceState(
      captureScale = key.captureScale,
      layerSize = bounds.size,
      layerOffset = layerOffset,
      previousSnapshot = lastSourceSnapshot,
    )
    if (!sourceState.hasDrawableSource) return null

    val sourceChanged = sourceState.snapshot == null ||
      sourceState.snapshot != lastSourceSnapshot ||
      bounds != lastBounds ||
      source?.isReleased != false
    if (sourceChanged) {
      source = createScaledContentLayer(
        context = context,
        scaleFactor = key.captureScale,
        layerSize = bounds.size,
        layerOffset = layerOffset,
        existingLayer = source,
        backgroundColor = Color.Transparent,
      ) ?: return null
    }

    val blurChanged = sourceChanged ||
      effectsKey != blurKey ||
      blurred?.isReleased != false
    if (blurChanged) {
      recordBlur(plan, currentEffects) ?: return null
    }

    effectsKey = blurKey
    lastSourceSnapshot = sourceState.snapshot
    lastBounds = bounds
    return SharedGlassBlurOutput(
      layer = blurred?.takeUnless { it.isReleased } ?: return null,
      bounds = bounds,
      captureScale = key.captureScale,
    )
  }

  fun DrawScope.obtainOptical(
    owner: RuntimeShaderGlassDelegate,
    opticalKey: GlassOpticalEffectKey,
    depth: Float,
  ): SharedGlassOpticalOutput? {
    val ownerMember = members[owner]
    if (
      memberCount <= 1 ||
      ownerMember?.opticalKey != opticalKey ||
      ownerMember.opticalDepth != depth
    ) {
      releaseOpticalAtlasLayers()
      return null
    }
    val compatibleGroups = members.entries
      .filter { (_, member) -> member.opticalKey != null }
      .groupBy { (_, member) ->
        SharedGlassOpticalStyleKey(
          opticalStyleKey = checkNotNull(member.opticalKey).atlasStyleKey(),
          depth = member.opticalDepth,
        )
      }
    val selectedGroup = compatibleGroups.values.maxByOrNull { entries -> entries.size }
    if (selectedGroup == null || selectedGroup.size <= 1) {
      releaseOpticalAtlasLayers()
      return null
    }
    val selectedMembers = selectedGroup.take(GlassShaders.SHARED_GLASS_ATLAS_TILE_CAPACITY)
    val opticalStyleKey = SharedGlassOpticalStyleKey(
      opticalStyleKey = checkNotNull(selectedMembers.first().value.opticalKey).atlasStyleKey(),
      depth = selectedMembers.first().value.opticalDepth,
    )
    if (selectedMembers.none { (memberOwner, _) -> memberOwner === owner }) {
      if (opticalAtlasKey?.opticalStyleKey != opticalStyleKey) {
        releaseOpticalAtlasLayers()
      }
      return null
    }
    val source = source?.takeUnless { it.isReleased } ?: run {
      releaseOpticalAtlasLayers()
      return null
    }
    val blurred = blurred?.takeUnless { it.isReleased } ?: run {
      releaseOpticalAtlasLayers()
      return null
    }
    val sourceBounds = lastBounds ?: run {
      releaseOpticalAtlasLayers()
      return null
    }
    val sampleSizes = selectedMembers.map { (_, member) ->
      checkNotNull(member.opticalKey).coordinates.sampleSize.roundToIntSize()
    }
    val layout = createSharedGlassAtlasLayout(sampleSizes) ?: run {
      releaseOpticalAtlasLayers()
      return null
    }
    val placements = selectedMembers.mapIndexed { index, (memberOwner, member) ->
      val memberOpticalKey = checkNotNull(member.opticalKey)
      SharedGlassAtlasPlacement(
        owner = memberOwner,
        tileOrigin = Offset(
          x = (index % layout.columns * layout.tileSize.width).toFloat(),
          y = (index / layout.columns * layout.tileSize.height).toFloat(),
        ),
        sampleOffset = (member.bounds.topLeft - sourceBounds.topLeft) * key.captureScale,
        sampleSize = memberOpticalKey.coordinates.sampleSize.roundToIntSize(),
        effectKey = memberOpticalKey,
      )
    }
    val atlasKey = SharedGlassOpticalAtlasKey(
      source = source,
      blurred = blurred,
      opticalStyleKey = opticalStyleKey,
      layout = layout,
      placements = placements,
    )
    val atlasInput = ensureLayer(opticalAtlasInput, key.graphicsContext).also {
      opticalAtlasInput = it
    }
    val atlas = ensureLayer(opticalAtlas, key.graphicsContext).also {
      opticalAtlas = it
    }
    if (opticalAtlasKey != atlasKey) {
      source.alpha = 1f
      source.blendMode = BlendMode.SrcOver
      blurred.alpha = 1f
      blurred.blendMode = BlendMode.SrcOver
      atlasInput.alpha = 1f
      atlasInput.blendMode = BlendMode.SrcOver
      atlasInput.scaleX = 1f
      atlasInput.scaleY = 1f
      atlasInput.pivotOffset = Offset.Zero
      atlasInput.renderEffect = null
      recordDepthMix(
        layer = atlasInput,
        size = layout.atlasSize,
        depth = opticalStyleKey.depth,
        drawSource = { drawAtlasTiles(placements, source) },
        drawBlurred = { drawAtlasTiles(placements, blurred) },
      )

      val shader = opticalAtlasShader
        ?: createSharedGlassOpticalAtlasRenderEffect().also {
          opticalAtlasShader = it
        }
      val effect = shader.updateUniforms {
        setOpticalAtlasUniforms(
          key = placements.first().effectKey,
          tileSize = layout.tileSize,
          columns = layout.columns,
          tileKeys = placements.map { it.effectKey },
        )
      }
      atlas.alpha = 1f
      atlas.blendMode = BlendMode.SrcOver
      atlas.scaleX = 1f
      atlas.scaleY = 1f
      atlas.pivotOffset = Offset.Zero
      atlas.renderEffect = effect.asComposeRenderEffect()
      atlas.record(layout.atlasSize) {
        drawLayer(atlasInput)
      }
      opticalAtlasKey = atlasKey
    }

    val placement = placements.firstOrNull { placement -> placement.owner === owner } ?: return null
    return SharedGlassOpticalOutput(
      layer = atlas,
      tileOrigin = placement.tileOrigin,
      tileSize = placement.sampleSize,
    )
  }

  fun DrawScope.obtainRefractionDetail(
    owner: RuntimeShaderGlassDelegate,
    detailKey: GlassRefractionDetailEffectKey,
  ): SharedGlassRefractionDetailOutput? {
    if (memberCount <= 1 || members[owner]?.detailKey != detailKey) {
      releaseRefractionDetailAtlasLayers()
      return null
    }
    val compatibleGroups = members.entries
      .filter { (_, member) -> member.detailKey != null }
      .groupBy { (_, member) -> checkNotNull(member.detailKey).atlasStyleKey() }
    val selectedGroup = compatibleGroups.values.maxByOrNull { entries -> entries.size }
    if (selectedGroup == null || selectedGroup.size <= 1) {
      releaseRefractionDetailAtlasLayers()
      return null
    }
    val selectedMembers = selectedGroup.take(GlassShaders.SHARED_GLASS_ATLAS_TILE_CAPACITY)
    val detailStyleKey = checkNotNull(selectedMembers.first().value.detailKey).atlasStyleKey()
    if (selectedMembers.none { (memberOwner, _) -> memberOwner === owner }) {
      if (refractionDetailAtlasKey?.detailStyleKey != detailStyleKey) {
        releaseRefractionDetailAtlasLayers()
      }
      return null
    }
    val source = source?.takeUnless { it.isReleased } ?: run {
      releaseRefractionDetailAtlasLayers()
      return null
    }
    val sourceBounds = lastBounds ?: run {
      releaseRefractionDetailAtlasLayers()
      return null
    }
    val sampleSizes = selectedMembers.map { (_, member) ->
      checkNotNull(member.detailKey).sampleSize.roundToIntSize()
    }
    val layout = createSharedGlassAtlasLayout(sampleSizes) ?: run {
      releaseRefractionDetailAtlasLayers()
      return null
    }
    val placements = selectedMembers.mapIndexed { index, (memberOwner, member) ->
      val memberDetailKey = checkNotNull(member.detailKey)
      SharedGlassAtlasPlacement(
        owner = memberOwner,
        tileOrigin = Offset(
          x = (index % layout.columns * layout.tileSize.width).toFloat(),
          y = (index / layout.columns * layout.tileSize.height).toFloat(),
        ),
        sampleOffset = (member.bounds.topLeft - sourceBounds.topLeft) * key.captureScale,
        sampleSize = memberDetailKey.sampleSize.roundToIntSize(),
        effectKey = memberDetailKey,
      )
    }
    val atlasKey = SharedGlassRefractionDetailAtlasKey(
      source = source,
      detailStyleKey = detailStyleKey,
      layout = layout,
      placements = placements,
    )
    val atlasSource = ensureLayer(refractionDetailAtlasSource, key.graphicsContext).also {
      refractionDetailAtlasSource = it
    }
    val atlas = ensureLayer(refractionDetailAtlas, key.graphicsContext).also {
      refractionDetailAtlas = it
    }
    if (
      refractionDetailAtlasKey != atlasKey ||
      atlasSource.isReleased ||
      atlas.isReleased
    ) {
      source.alpha = 1f
      source.blendMode = BlendMode.SrcOver
      source.scaleX = 1f
      source.scaleY = 1f
      source.pivotOffset = Offset.Zero

      atlasSource.alpha = 1f
      atlasSource.blendMode = BlendMode.SrcOver
      atlasSource.scaleX = 1f
      atlasSource.scaleY = 1f
      atlasSource.pivotOffset = Offset.Zero
      atlasSource.renderEffect = null
      atlasSource.record(layout.atlasSize) {
        drawAtlasTiles(placements, source)
      }

      val shader = refractionDetailAtlasShader
        ?: createSharedRefractionDetailAtlasRenderEffect().also {
          refractionDetailAtlasShader = it
        }
      val effect = shader.updateUniforms {
        setRefractionDetailAtlasUniforms(
          key = placements.first().effectKey,
          tileSize = layout.tileSize,
          columns = layout.columns,
          tileKeys = placements.map { it.effectKey },
        )
      }
      atlas.alpha = 1f
      atlas.blendMode = BlendMode.SrcOver
      atlas.scaleX = 1f
      atlas.scaleY = 1f
      atlas.pivotOffset = Offset.Zero
      atlas.renderEffect = effect.asComposeRenderEffect()
      atlas.record(layout.atlasSize) {
        drawLayer(atlasSource)
      }
      refractionDetailAtlasKey = atlasKey
    }

    val placement = placements.firstOrNull { placement -> placement.owner === owner } ?: return null
    return SharedGlassRefractionDetailOutput(
      layer = atlas,
      tileOrigin = placement.tileOrigin,
      tileSize = placement.sampleSize,
    )
  }

  private fun <T> DrawScope.drawAtlasTiles(
    placements: List<SharedGlassAtlasPlacement<T>>,
    input: GraphicsLayer,
  ) {
    placements.forEach { placement ->
      clipRect(
        left = placement.tileOrigin.x,
        top = placement.tileOrigin.y,
        right = placement.tileOrigin.x + placement.sampleSize.width,
        bottom = placement.tileOrigin.y + placement.sampleSize.height,
      ) {
        translate(placement.tileOrigin - placement.sampleOffset) {
          drawLayer(input)
        }
      }
    }
  }

  private fun DrawScope.recordBlur(
    plan: SemanticBlurPlan,
    effects: SharedGlassBlurEffects,
  ): GraphicsLayer? {
    val source = source?.takeUnless { it.isReleased } ?: return null
    source.alpha = 1f
    source.blendMode = BlendMode.SrcOver
    val horizontalInput = if (plan.requiresPrefilter) {
      val layer = prefiltered?.takeUnless { it.isReleased } ?: return null
      val effect = effects.prefilter ?: return null
      layer.scaleX = 1f
      layer.scaleY = 1f
      layer.pivotOffset = Offset.Zero
      layer.renderEffect = effect.asComposeRenderEffect()
      layer.record(plan.sampleSize) { drawLayer(source) }
      layer
    } else {
      source
    }

    val horizontal = horizontal?.takeUnless { it.isReleased } ?: return null
    horizontal.scaleX = 1f
    horizontal.scaleY = 1f
    horizontal.pivotOffset = Offset.Zero
    horizontal.renderEffect = effects.horizontal.asComposeRenderEffect()
    horizontal.record(plan.workingSize) {
      scale(plan.scaleFactor, pivot = Offset.Zero) { drawLayer(horizontalInput) }
    }

    return blurred?.takeUnless { it.isReleased }?.also { blurred ->
      blurred.scaleX = 1f / plan.scaleFactor
      blurred.scaleY = 1f / plan.scaleFactor
      blurred.pivotOffset = Offset.Zero
      blurred.renderEffect = effects.vertical.asComposeRenderEffect()
      blurred.record(plan.workingSize) { drawLayer(horizontal) }
    }
  }

  private fun updateEffects(key: GlassBlurEffectKey): SharedGlassBlurEffects {
    effects?.takeIf { effectsKey == key }?.let { return it }
    val horizontalShader = horizontalShader ?: createGlassBlurRenderEffect(
      horizontal = true,
      progressive = false,
    ).also { horizontalShader = it }
    val verticalShader = verticalShader ?: createGlassBlurRenderEffect(
      horizontal = false,
      progressive = false,
    ).also { verticalShader = it }
    val horizontal = horizontalShader.updateUniforms {
      setGlassBlurUniforms(
        key = key,
        kernel = key.plan.horizontalKernel,
        sampleWidth = key.plan.workingSize.width,
        sampleHeight = key.plan.workingSize.height,
      )
    }
    val vertical = verticalShader.updateUniforms {
      setGlassBlurUniforms(
        key = key,
        kernel = key.plan.verticalKernel,
        sampleWidth = key.plan.workingSize.width,
        sampleHeight = key.plan.workingSize.height,
      )
    }
    val prefilter = key.plan.takeIf { it.requiresPrefilter }?.let { plan ->
      val shader = prefilterShader ?: createGlassBlurPrefilterRenderEffect().also {
        prefilterShader = it
      }
      shader.updateUniforms {
        setFloatUniform(
          "sampleSize",
          plan.sampleSize.width.toFloat(),
          plan.sampleSize.height.toFloat(),
        )
      }
    }
    return SharedGlassBlurEffects(
      prefilter = prefilter,
      horizontal = horizontal,
      vertical = vertical,
    ).also {
      effectsKey = key
      effects = it
    }
  }

  private fun ensureLayers(plan: SemanticBlurPlan) {
    if (
      source?.size != plan.sampleSize ||
      horizontal?.size != plan.workingSize ||
      blurred?.size != plan.workingSize
    ) {
      releaseLayers()
    }
    source = ensureLayer(source, key.graphicsContext)
    if (plan.requiresPrefilter) {
      prefiltered = ensureLayer(prefiltered, key.graphicsContext)
    } else {
      releaseLayer(prefiltered, key.graphicsContext)
      prefiltered = null
    }
    horizontal = ensureLayer(horizontal, key.graphicsContext)
    blurred = ensureLayer(blurred, key.graphicsContext)
  }

  fun releaseLayers() {
    releaseLayer(source, key.graphicsContext)
    releaseLayer(prefiltered, key.graphicsContext)
    releaseLayer(horizontal, key.graphicsContext)
    releaseLayer(blurred, key.graphicsContext)
    releaseOpticalAtlasLayers()
    releaseRefractionDetailAtlasLayers()
    source = null
    prefiltered = null
    horizontal = null
    blurred = null
    lastSourceSnapshot = null
    lastBounds = null
  }

  fun destroy() {
    releaseLayers()
    horizontalShader = null
    verticalShader = null
    prefilterShader = null
    opticalAtlasShader = null
    refractionDetailAtlasShader = null
    effectsKey = null
    effects = null
  }

  private fun releaseOpticalAtlasLayers() {
    releaseLayer(opticalAtlasInput, key.graphicsContext)
    releaseLayer(opticalAtlas, key.graphicsContext)
    opticalAtlasInput = null
    opticalAtlas = null
    opticalAtlasKey = null
  }

  private fun releaseRefractionDetailAtlasLayers() {
    releaseLayer(refractionDetailAtlasSource, key.graphicsContext)
    releaseLayer(refractionDetailAtlas, key.graphicsContext)
    refractionDetailAtlasSource = null
    refractionDetailAtlas = null
    refractionDetailAtlasKey = null
  }

  private fun unionBounds(): Rect? {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    for (member in members.values) {
      val bounds = member.bounds
      if (bounds.width <= 0f || bounds.height <= 0f) continue
      left = min(left, bounds.left)
      top = min(top, bounds.top)
      right = max(right, bounds.right)
      bottom = max(bottom, bounds.bottom)
    }
    return if (left.isFinite() && top.isFinite() && right > left && bottom > top) {
      Rect(left, top, right, bottom)
    } else {
      null
    }
  }
}

@OptIn(ExperimentalHazeApi::class)
private data class SharedGlassBlurMember(
  val context: VisualEffectContext,
  val bounds: Rect,
  val opticalKey: GlassOpticalEffectKey?,
  val opticalDepth: Float,
  val detailKey: GlassRefractionDetailEffectKey?,
)

internal data class SharedGlassBlurOutput(
  val layer: GraphicsLayer,
  val bounds: Rect,
  val captureScale: Float,
)

private data class SharedGlassAtlasPlacement<T>(
  val owner: RuntimeShaderGlassDelegate,
  val tileOrigin: Offset,
  val sampleOffset: Offset,
  val sampleSize: IntSize,
  val effectKey: T,
)

private data class SharedGlassOpticalStyleKey(
  val opticalStyleKey: GlassOpticalEffectKey,
  val depth: Float,
)

private data class SharedGlassOpticalAtlasKey(
  val source: GraphicsLayer,
  val blurred: GraphicsLayer,
  val opticalStyleKey: SharedGlassOpticalStyleKey,
  val layout: SharedGlassAtlasLayout,
  val placements: List<SharedGlassAtlasPlacement<GlassOpticalEffectKey>>,
)

internal data class SharedGlassOpticalOutput(
  val layer: GraphicsLayer,
  val tileOrigin: Offset,
  val tileSize: IntSize,
)

private data class SharedGlassRefractionDetailAtlasKey(
  val source: GraphicsLayer,
  val detailStyleKey: GlassRefractionDetailEffectKey,
  val layout: SharedGlassAtlasLayout,
  val placements: List<SharedGlassAtlasPlacement<GlassRefractionDetailEffectKey>>,
)

internal data class SharedGlassRefractionDetailOutput(
  val layer: GraphicsLayer,
  val tileOrigin: Offset,
  val tileSize: IntSize,
)

@OptIn(InternalHazeApi::class)
private data class SharedGlassBlurEffects(
  val prefilter: PlatformRenderEffect?,
  val horizontal: PlatformRenderEffect,
  val vertical: PlatformRenderEffect,
)

private data class SharedGlassAtlasLayout(
  val tileSize: IntSize,
  val columns: Int,
  val atlasSize: IntSize,
)

internal expect val supportsSharedGlassBlur: Boolean

private const val MAX_SHARED_GLASS_ATLAS_DIMENSION = 4096L

private fun createSharedGlassAtlasLayout(sampleSizes: List<IntSize>): SharedGlassAtlasLayout? {
  if (sampleSizes.isEmpty() || sampleSizes.any { size -> size.width <= 0 || size.height <= 0 }) {
    return null
  }
  val tileSize = IntSize(
    width = sampleSizes.maxOf(IntSize::width),
    height = sampleSizes.maxOf(IntSize::height),
  )
  val columns = ceil(sqrt(sampleSizes.size.toDouble())).toInt().coerceAtLeast(1)
  val rows = (sampleSizes.size + columns - 1) / columns
  val atlasWidth = tileSize.width.toLong() * columns
  val atlasHeight = tileSize.height.toLong() * rows
  if (
    atlasWidth > MAX_SHARED_GLASS_ATLAS_DIMENSION ||
    atlasHeight > MAX_SHARED_GLASS_ATLAS_DIMENSION
  ) {
    return null
  }
  return SharedGlassAtlasLayout(
    tileSize = tileSize,
    columns = columns,
    atlasSize = IntSize(atlasWidth.toInt(), atlasHeight.toInt()),
  )
}

private fun GlassOpticalEffectKey.atlasStyleKey(): GlassOpticalEffectKey = copy(
  coordinates = coordinates.copy(
    sampleSize = Size.Zero,
    materialOrigin = Offset.Zero,
  ),
)

private fun GlassRefractionDetailEffectKey.atlasStyleKey(): GlassRefractionDetailEffectKey = copy(
  sampleSize = Size.Zero,
  materialOrigin = Offset.Zero,
)
