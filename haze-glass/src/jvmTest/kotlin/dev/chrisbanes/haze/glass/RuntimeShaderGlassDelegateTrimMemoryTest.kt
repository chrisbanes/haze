// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.VisualEffectContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import sun.misc.Unsafe

@OptIn(ExperimentalHazeApi::class, InternalComposeUiApi::class)
class RuntimeShaderGlassDelegateTrimMemoryTest {

  @Test
  fun releaseBlurred_releasesAtMostThreeBlurLayersAndSize() {
    val layers = GlassLayers()
    val graphicsContext = TestGraphicsContext()
    layers.populate(graphicsContext)
    val blurLayers = listOfNotNull(
      layers.blurPrefiltered,
      layers.blurHorizontal,
      layers.blurred,
    )
    layers.blurWorkingSize = IntSize(540, 960)

    layers.releaseBlurred(graphicsContext)

    assertThat(graphicsContext.releasedLayers).containsExactly(*blurLayers.toTypedArray())
    assertThat(blurLayers.size).isEqualTo(3)
    assertThat(layers.blurWorkingSize).isEqualTo(null)
    assertThat(layers.hasBlurHorizontal).isEqualTo(false)
    assertThat(layers.hasBlurPrefiltered).isEqualTo(false)
    assertThat(layers.hasBlurred).isEqualTo(false)
    assertThat(layers.hasSource).isTrue()
    assertThat(layers.hasRefractionDetail).isTrue()
  }

  @Test
  fun releaseBlurPrefiltered_keepsSeparableWorkingLayers() {
    val layers = GlassLayers()
    val graphicsContext = TestGraphicsContext()
    layers.populate(graphicsContext)
    val prefiltered = checkNotNull(layers.blurPrefiltered)

    layers.releaseBlurPrefiltered(graphicsContext)

    assertThat(graphicsContext.releasedLayers).containsExactly(prefiltered)
    assertThat(layers.hasBlurPrefiltered).isEqualTo(false)
    assertThat(layers.hasBlurHorizontal).isTrue()
    assertThat(layers.hasBlurred).isTrue()
    assertThat(layers.hasRefractionDetail).isTrue()
  }

  @Test
  fun updateBlurWorkingSize_recreatesBlurGraphWhenScaleSelectionChanges() {
    val layers = GlassLayers()
    val graphicsContext = TestGraphicsContext()
    layers.populate(graphicsContext)
    layers.blurWorkingSize = IntSize(540, 960)
    val oldBlurLayers = listOfNotNull(
      layers.blurPrefiltered,
      layers.blurHorizontal,
      layers.blurred,
    )

    layers.updateBlurWorkingSize(IntSize(1080, 1920), graphicsContext)

    assertThat(graphicsContext.releasedLayers).containsExactly(*oldBlurLayers.toTypedArray())
    assertThat(layers.blurWorkingSize).isEqualTo(IntSize(1080, 1920))
    assertThat(layers.hasBlurPrefiltered).isEqualTo(false)
    assertThat(layers.hasBlurHorizontal).isEqualTo(false)
    assertThat(layers.hasBlurred).isEqualTo(false)
    assertThat(layers.hasRefractionDetail).isTrue()
  }

  @Test
  fun releaseRefractionDetail_releasesOnlyDetailLayer() {
    val layers = GlassLayers()
    val graphicsContext = TestGraphicsContext()
    layers.populate(graphicsContext)
    val detail = checkNotNull(layers.refractionDetail)

    layers.releaseRefractionDetail(graphicsContext)

    assertThat(graphicsContext.releasedLayers).containsExactly(detail)
    assertThat(layers.hasRefractionDetail).isFalse()
    assertThat(layers.hasOptical).isTrue()
  }

  @Test
  fun onTrimMemory_backgroundKeepsRetainedOutputAvailability() {
    val delegate = RuntimeShaderGlassDelegate(GlassVisualEffect())
    val context = RecordingVisualEffectContext()
    delegate.layers.populate(context.graphicsContext)

    delegate.onTrimMemory(context, TrimMemoryLevel.BACKGROUND)

    assertThat(delegate.layers.hasSource).isTrue()
    assertThat(delegate.layers.hasBlurPrefiltered).isTrue()
    assertThat(delegate.layers.hasBlurHorizontal).isTrue()
    assertThat(delegate.layers.hasBlurred).isTrue()
    assertThat(delegate.layers.hasDepthMixed).isTrue()
    assertThat(delegate.layers.hasOptical).isTrue()
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.layers.hasRim).isTrue()
    assertThat(context.invalidateDrawCalls).isEqualTo(0)
  }

  @Test
  fun onTrimMemory_moderateClearsRetainedOutputAvailabilityAndInvalidatesDraw() {
    val delegate = RuntimeShaderGlassDelegate(GlassVisualEffect())
    val context = RecordingVisualEffectContext()
    delegate.layers.populate(context.graphicsContext)
    delegate.seedSuccessfulCacheMetadata()
    val retainedLayers = listOfNotNull(
      delegate.layers.source,
      delegate.layers.blurPrefiltered,
      delegate.layers.blurHorizontal,
      delegate.layers.blurred,
      delegate.layers.depthMixed,
      delegate.layers.optical,
      delegate.layers.refractionDetail,
      delegate.layers.rim,
    )

    assertThat(delegate.layers.hasSource).isTrue()
    assertThat(delegate.layers.hasBlurPrefiltered).isTrue()
    assertThat(delegate.layers.hasBlurHorizontal).isTrue()
    assertThat(delegate.layers.hasBlurred).isTrue()
    assertThat(delegate.layers.hasDepthMixed).isTrue()
    assertThat(delegate.layers.hasOptical).isTrue()
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.layers.hasRim).isTrue()

    delegate.onTrimMemory(context, TrimMemoryLevel.MODERATE)

    assertThat(context.graphicsContext.releasedLayers).containsExactly(*retainedLayers.toTypedArray())
    assertThat(delegate.layers.isEmpty).isTrue()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNull()
    assertThat(delegate.lastSuccessfulStageInputs).isNull()
    assertThat(context.invalidateDrawCalls).isEqualTo(1)
  }

  @Test
  fun detach_clearsSuccessfulCacheMetadata() {
    val delegate = RuntimeShaderGlassDelegate(GlassVisualEffect())
    val context = RecordingVisualEffectContext()
    delegate.layers.populate(context.graphicsContext)
    val retainedLayers = delegate.layers.allLayers()
    delegate.setGraphicsContextForTest(context.graphicsContext)
    delegate.seedSuccessfulCacheMetadata()

    delegate.detach()

    assertThat(context.graphicsContext.releasedLayers).containsExactly(*retainedLayers.toTypedArray())
    assertThat(delegate.layers.isEmpty).isTrue()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNull()
    assertThat(delegate.lastSuccessfulStageInputs).isNull()
  }

  @Test
  fun clearRetainedOutput_releasesAllPrivateLayersIncludingDetail() {
    val delegate = RuntimeShaderGlassDelegate(GlassVisualEffect())
    val context = RecordingVisualEffectContext()
    delegate.layers.populate(context.graphicsContext)
    val retainedLayers = delegate.layers.allLayers()
    delegate.setGraphicsContextForTest(context.graphicsContext)
    delegate.seedSuccessfulCacheMetadata()
    delegate.seedRetainedOutputAvailable()

    delegate.clearRetainedOutput()

    assertThat(context.graphicsContext.releasedLayers).containsExactly(*retainedLayers.toTypedArray())
    assertThat(delegate.layers.isEmpty).isTrue()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNull()
    assertThat(delegate.lastSuccessfulStageInputs).isNull()
    assertThat(delegate.canDrawRetainedOutput()).isFalse()
  }

  @Test
  fun canDrawRetainedOutput_activeDetailRequiresAvailableDetailLayer() {
    val delegate = RuntimeShaderGlassDelegate(GlassVisualEffect())
    val context = RecordingVisualEffectContext()
    delegate.layers.populate(context.graphicsContext)
    delegate.seedSuccessfulCacheMetadata(detail = Any())
    delegate.seedRetainedOutputAvailable()

    delegate.layers.releaseRefractionDetail(context.graphicsContext)

    assertThat(delegate.canDrawRetainedOutput()).isFalse()
  }

  @Test
  fun canDrawRetainedOutput_inactiveDetailDoesNotRequireDetailLayer() {
    val delegate = RuntimeShaderGlassDelegate(GlassVisualEffect())
    val context = RecordingVisualEffectContext()
    delegate.layers.populate(context.graphicsContext)
    delegate.seedSuccessfulCacheMetadata(detail = null)
    delegate.seedRetainedOutputAvailable()

    delegate.layers.releaseRefractionDetail(context.graphicsContext)

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun prepareRefractionDetail_activeAllocatesLayerBeforeDraw() {
    val layers = GlassLayers()
    val graphicsContext = TestGraphicsContext()

    layers.prepareRefractionDetail(required = true, graphicsContext)

    assertThat(layers.hasRefractionDetail).isTrue()
  }

  @Test
  fun prepareRefractionDetail_inactiveReleasesLayer() {
    val layers = GlassLayers()
    val graphicsContext = TestGraphicsContext()
    layers.refractionDetail = graphicsContext.createGraphicsLayer()

    layers.prepareRefractionDetail(required = false, graphicsContext)

    assertThat(layers.hasRefractionDetail).isFalse()
  }

  @Test
  fun prepareDraw_invalidSizeReleasesResourcesBeforeGeometryCalibration() {
    listOf(
      RecordingVisualEffectContext(size = Size(0f, 100f), layerSize = Size(100f, 100f)),
      RecordingVisualEffectContext(size = Size(100f, 0f), layerSize = Size(100f, 100f)),
      RecordingVisualEffectContext(
        size = Size(100f, 100f),
        layerSize = Size(Float.POSITIVE_INFINITY, 100f),
        failIfBuildRenderParamsReached = true,
      ),
      RecordingVisualEffectContext(
        size = Size(100f, 100f),
        layerSize = Size(100f, Float.POSITIVE_INFINITY),
        failIfBuildRenderParamsReached = true,
      ),
    ).forEach { context ->
      val delegate = RuntimeShaderGlassDelegate(GlassVisualEffect())
      val retainedLayers = delegate.prepareDrawWithRetainedLayers(context)

      assertThat(context.graphicsContext.releasedLayers)
        .containsExactly(*retainedLayers.toTypedArray())
      assertThat(delegate.layers.isEmpty).isTrue()
    }
  }
}

@OptIn(InternalComposeUiApi::class)
private fun GlassLayers.populate(graphicsContext: GraphicsContext) {
  source = graphicsContext.createGraphicsLayer()
  blurPrefiltered = graphicsContext.createGraphicsLayer()
  blurHorizontal = graphicsContext.createGraphicsLayer()
  blurred = graphicsContext.createGraphicsLayer()
  depthMixed = graphicsContext.createGraphicsLayer()
  optical = graphicsContext.createGraphicsLayer()
  refractionDetail = graphicsContext.createGraphicsLayer()
  rim = graphicsContext.createGraphicsLayer()
}

private fun GlassLayers.allLayers(): List<GraphicsLayer> = listOfNotNull(
  source,
  blurPrefiltered,
  blurHorizontal,
  blurred,
  depthMixed,
  optical,
  refractionDetail,
  rim,
)

private fun RuntimeShaderGlassDelegate.prepareDrawWithRetainedLayers(
  context: RecordingVisualEffectContext,
): List<GraphicsLayer> {
  layers.populate(context.graphicsContext)
  val retainedLayers = listOfNotNull(
    layers.source,
    layers.blurPrefiltered,
    layers.blurHorizontal,
    layers.blurred,
    layers.depthMixed,
    layers.optical,
    layers.refractionDetail,
    layers.rim,
  )
  setGraphicsContextForTest(context.graphicsContext)

  with(CanvasDrawScope()) {
    with(this@prepareDrawWithRetainedLayers) { prepareDraw(context) }
  }
  return retainedLayers
}

private fun RuntimeShaderGlassDelegate.setGraphicsContextForTest(
  graphicsContext: GraphicsContext,
) {
  javaClass.getDeclaredField("graphicsContext").apply {
    isAccessible = true
    set(this@setGraphicsContextForTest, graphicsContext)
  }
}

private fun RuntimeShaderGlassDelegate.seedSuccessfulCacheMetadata(
  detail: Any? = Any(),
) {
  javaClass.getDeclaredField("lastSuccessfulSourceSnapshot").apply {
    isAccessible = true
    set(
      this@seedSuccessfulCacheMetadata,
      GlassSourceSnapshot(1f, Size(1f, 1f), Offset.Zero, emptyList()),
    )
  }
  javaClass.getDeclaredField("lastSuccessfulStageInputs").apply {
    isAccessible = true
    set(
      this@seedSuccessfulCacheMetadata,
      GlassStageInputs(
        blur = Any(),
        depth = .5f,
        optical = Any(),
        detail = detail,
        rim = Any(),
      ),
    )
  }
}

private fun RuntimeShaderGlassDelegate.seedRetainedOutputAvailable() {
  javaClass.getDeclaredField("retainedOutputAvailable").apply {
    isAccessible = true
    set(this@seedRetainedOutputAvailable, true)
  }
}

@OptIn(InternalComposeUiApi::class)
private class RecordingVisualEffectContext(
  override val size: Size = Size.Zero,
  override val layerSize: Size = Size.Zero,
  private val failIfBuildRenderParamsReached: Boolean = false,
) : VisualEffectContext {
  val graphicsContext = TestGraphicsContext()

  var invalidateDrawCalls = 0
    private set

  override val position: Offset = Offset.Zero
  override val layerOffset: Offset = Offset.Zero
  override val rootBounds: Rect = Rect.Zero
  override val inputScale: HazeInputScale = HazeInputScale.None
  override val windowId: Any? = null
  override val areas: List<HazeArea> = emptyList()
  override val state: HazeState? = null
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun positionOf(area: HazeArea): Offset = area.coordinates.localPosition
  override fun boundsOf(area: HazeArea): Rect? {
    val position = area.coordinates.localPosition
    return if (position.isSpecified && area.size.isSpecified) Rect(position, area.size) else null
  }

  override fun requirePlatformContext(): PlatformContext = error("Unused in trim-memory tests")
  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T {
    if (failIfBuildRenderParamsReached) {
      error("buildRenderParams reached for invalid raw sample size")
    }
    return LayoutDirection.Ltr as T
  }
  override fun requireGraphicsContext(): GraphicsContext = graphicsContext

  override fun invalidateDraw() {
    invalidateDrawCalls++
  }
}

@OptIn(InternalComposeUiApi::class)
private class TestGraphicsContext : GraphicsContext {
  val releasedLayers = mutableListOf<GraphicsLayer>()

  override fun createGraphicsLayer(): GraphicsLayer =
    unsafe.allocateInstance(GraphicsLayer::class.java) as GraphicsLayer

  override fun releaseGraphicsLayer(layer: GraphicsLayer) {
    releasedLayers += layer
  }
}

private val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
  isAccessible = true
  get(null) as Unsafe
}
