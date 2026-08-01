// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRendererDrawHooks
import dev.chrisbanes.haze.HazeEffectRendererLifecycle
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.hazeEffect
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import sun.misc.Unsafe

@OptIn(ExperimentalTestApi::class, InternalHazeApi::class)
class FallbackGlassDelegateTest {

  @Test
  fun boundedFractionalAlpha_preparesAndReusesGroupLayer() {
    val effect = GlassRuntimeEffect().apply { alpha = 0.5f }
    val delegate = FallbackGlassDelegate(effect)
    val context = FallbackRecordingContext(size = Size(100f, 100f))

    delegate.prepare(context)
    val first = checkNotNull(delegate.groupAlphaForTest().layer)
    delegate.prepare(context)

    assertThat(delegate.groupAlphaForTest().layer).isSameInstanceAs(first)
    assertThat(context.graphicsContext.createdLayers).isEqualTo(listOf(first))
  }

  @Test
  fun alphaZero_skipsFirstPreparationAndRetainsExistingFallbackGroupLayer() {
    val effect = GlassRuntimeEffect().apply { alpha = 0f }
    val delegate = FallbackGlassDelegate(effect)
    val context = FallbackRecordingContext(size = Size(100f, 100f))

    delegate.prepare(context)

    assertThat(delegate.preparedDrawForTest()).isNull()
    assertThat(context.graphicsContext.createdLayers).isEqualTo(emptyList())

    effect.alpha = 0.5f
    delegate.prepare(context)
    val groupLayer = checkNotNull(delegate.groupAlphaForTest().layer)
    val prepared = checkNotNull(delegate.preparedDrawForTest())

    effect.alpha = 0f
    delegate.prepare(context)

    assertThat(delegate.preparedDrawForTest()).isSameInstanceAs(prepared)
    assertThat(delegate.groupAlphaForTest().layer).isSameInstanceAs(groupLayer)
    assertThat(context.graphicsContext.createdLayers).isEqualTo(listOf(groupLayer))
    assertThat(context.graphicsContext.releasedLayers).isEqualTo(emptyList())
  }

  @Test
  fun fallbackGroup_exceedingDimension_usesDirectAlphaWithoutAllocation() {
    val effect = GlassRuntimeEffect().apply { alpha = 0.5f }
    val delegate = FallbackGlassDelegate(effect)
    val context = FallbackRecordingContext(size = Size(4097f, 1f))

    delegate.prepare(context)

    assertThat(delegate.groupAlphaForTest().isAvailable).isFalse()
    assertThat(context.graphicsContext.createdLayers).isEqualTo(emptyList())
  }

  @Test
  fun fallbackGroup_atExactPixelBoundary_isAllowed() {
    val effect = GlassRuntimeEffect().apply { alpha = 0.5f }
    val delegate = FallbackGlassDelegate(effect)
    val context = FallbackRecordingContext(size = Size(4096f, 4096f))

    delegate.prepare(context)

    assertThat(delegate.groupAlphaForTest().isAvailable).isTrue()
  }

  @Test
  fun fallbackGroup_exceedingDimensionAndPixelLimit_usesDirectAlphaWithoutAllocation() {
    val effect = GlassRuntimeEffect().apply { alpha = 0.5f }
    val delegate = FallbackGlassDelegate(effect)
    val context = FallbackRecordingContext(size = Size(4096f, 4097f))

    delegate.prepare(context)

    assertThat(delegate.groupAlphaForTest().isAvailable).isFalse()
    assertThat(context.graphicsContext.createdLayers).isEqualTo(emptyList())
  }

  @Test
  fun directAlphaDegradation_scalesEveryFallbackContribution() = runComposeUiTest {
    fun assertDirectAlphaHalvesContribution(
      effect: GlassRuntimeEffect,
      sample: Offset,
      activateInteraction: Boolean = false,
    ) {
      val fallback = FallbackGlassDelegate(effect)
      val visualEffect = FallbackOnlyVisualEffect(effect, fallback)
      setContent { FallbackTestContent(visualEffect) }
      waitForIdle()
      if (activateInteraction) {
        effect.setPressedForTest(sample)
        waitForIdle()
      }
      val opaque = onNodeWithTag(FALLBACK_TAG).captureToImage().toPixelMap()[
        sample.x.toInt(),
        sample.y.toInt(),
      ]

      effect.alpha = 0.5f
      visualEffect.disableGroupPreparation()
      waitForIdle()
      val direct = onNodeWithTag(FALLBACK_TAG).captureToImage().toPixelMap()[
        sample.x.toInt(),
        sample.y.toInt(),
      ]

      assertThat(fallback.groupAlphaForTest().isAvailable).isFalse()
      assertThat(opaque.alpha).isGreaterThan(0.01f)
      assertThat(abs(direct.alpha - opaque.alpha / 2f)).isLessThan(0.03f)
    }

    assertDirectAlphaHalvesContribution(
      effect = GlassRuntimeEffect().apply {
        tint = Color.Red
        specularIntensity = 0f
        edgeSoftness = 0.dp
      },
      sample = Offset(60f, 60f),
    )
    assertDirectAlphaHalvesContribution(
      effect = GlassRuntimeEffect().apply {
        tint = Color.Transparent
        specularIntensity = 1f
        edgeSoftness = 0.dp
        lightPosition = Offset(60f, 60f)
      },
      sample = Offset(60f, 60f),
    )
    assertDirectAlphaHalvesContribution(
      effect = GlassRuntimeEffect().apply {
        tint = Color.Transparent
        specularIntensity = 0f
        ambientResponse = 1f
        edgeSoftness = 8.dp
      },
      sample = Offset(2f, 60f),
    )
    assertDirectAlphaHalvesContribution(
      effect = GlassRuntimeEffect().apply {
        tint = Color.Transparent
        specularIntensity = 0f
        edgeSoftness = 0.dp
        pressed { lightingIntensity(1f) }
      },
      sample = Offset(60f, 60f),
      activateInteraction = true,
    )
  }

  @Test
  fun foregroundLighting_drawsOverOpaqueContent() {
    val effect = GlassRuntimeEffect().apply {
      tint = Color.Transparent
      specularIntensity = 1f
      ambientResponse = 0f
      edgeSoftness = 0.dp
      lightPosition = Offset(60f, 60f)
    }
    val fallback = FallbackGlassDelegate(effect)
    val context = FallbackRecordingContext(size = Size(120f, 120f))
    val image = ImageBitmap(120, 120)
    CanvasDrawScope().draw(
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
      canvas = Canvas(image),
      size = context.size,
    ) {
      with(fallback) {
        prepareDraw(context)
        draw(context)
      }
      drawRect(Color.Black)
      with(fallback) { drawForeground(context) }
    }

    assertThat(image.toPixelMap()[60, 60].red).isGreaterThan(0.01f)
  }

  @Test
  fun stablePrepare_reusesResourcesUntilTheirSemanticInputsChange() {
    val effect = GlassRuntimeEffect().apply {
      tint = Color.Red
      specularIntensity = 1f
      ambientResponse = 1f
      edgeSoftness = 8.dp
      shape = RoundedCornerShape(12.dp)
    }
    val delegate = FallbackGlassDelegate(effect)
    val context = FallbackRecordingContext(size = Size(100f, 100f))

    delegate.prepare(context)
    val first = delegate.preparedResourcesForTest()
    delegate.prepare(context)
    val stable = delegate.preparedResourcesForTest()

    assertThat(stable.prepared).isSameInstanceAs(first.prepared)
    assertThat(stable.style).isSameInstanceAs(first.style)
    assertThat(stable.shapePath).isSameInstanceAs(first.shapePath)
    assertThat(stable.highlightBrush).isSameInstanceAs(first.highlightBrush)
    assertThat(stable.edgeBrush).isSameInstanceAs(first.edgeBrush)
    assertThat(stable.edgeStroke).isSameInstanceAs(first.edgeStroke)

    effect.lightPosition = Offset(24f, 36f)
    delegate.prepare(context)
    val movedLight = delegate.preparedResourcesForTest()

    assertThat(movedLight.prepared).isNotSameInstanceAs(stable.prepared)
    assertThat(movedLight.highlightBrush).isNotSameInstanceAs(stable.highlightBrush)
    assertThat(movedLight.shapePath).isSameInstanceAs(stable.shapePath)
    assertThat(movedLight.edgeBrush).isSameInstanceAs(stable.edgeBrush)
    assertThat(movedLight.edgeStroke).isSameInstanceAs(stable.edgeStroke)

    effect.ambientResponse = 0.5f
    delegate.prepare(context)
    val changedEdge = delegate.preparedResourcesForTest()

    assertThat(changedEdge.highlightBrush).isSameInstanceAs(movedLight.highlightBrush)
    assertThat(changedEdge.edgeBrush).isNotSameInstanceAs(movedLight.edgeBrush)
    assertThat(changedEdge.edgeStroke).isSameInstanceAs(movedLight.edgeStroke)
    assertThat(changedEdge.shapePath).isSameInstanceAs(movedLight.shapePath)

    effect.edgeSoftness = 0.dp
    delegate.prepare(context)
    val noEdge = delegate.preparedResourcesForTest()

    assertThat(noEdge.edgeBrush).isNull()
    assertThat(noEdge.edgeDirectBrush).isNull()
    assertThat(noEdge.edgeStroke).isNull()

    effect.edgeSoftness = 8.dp
    delegate.prepare(context)
    val restoredEdge = delegate.preparedResourcesForTest()

    assertThat(restoredEdge.edgeBrush).isNotNull()
    assertThat(restoredEdge.edgeDirectBrush).isNotNull()
    assertThat(restoredEdge.edgeStroke).isNotNull()
  }

  private fun FallbackGlassDelegate.prepare(context: FallbackRecordingContext) {
    CanvasDrawScope().draw(
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
      canvas = Canvas(ImageBitmap(1, 1)),
      size = context.size,
    ) {
      with(this@prepare) { prepareDraw(context) }
    }
  }

  private fun FallbackGlassDelegate.groupAlphaForTest(): RetainedGlassGroupAlphaLayer =
    javaClass.getDeclaredField("groupAlpha").run {
      isAccessible = true
      get(this@groupAlphaForTest) as RetainedGlassGroupAlphaLayer
    }

  private fun FallbackGlassDelegate.preparedDrawForTest(): Any? =
    javaClass.getDeclaredField("preparedDraw").run {
      isAccessible = true
      get(this@preparedDrawForTest)
    }

  private fun FallbackGlassDelegate.preparedResourcesForTest(): PreparedResourcesForTest {
    val prepared = javaClass.getDeclaredField("preparedDraw").run {
      isAccessible = true
      get(this@preparedResourcesForTest)
    }
    checkNotNull(prepared)
    fun field(name: String): Any? = prepared.javaClass.getDeclaredField(name).run {
      isAccessible = true
      get(prepared)
    }
    return PreparedResourcesForTest(
      prepared = prepared,
      style = field("style"),
      shapePath = field("shapePath"),
      highlightBrush = field("highlightBrush"),
      edgeBrush = field("edgeBrush"),
      edgeDirectBrush = field("edgeDirectBrush"),
      edgeStroke = field("edgeStroke"),
    )
  }

  @Composable
  private fun FallbackTestContent(fallbackVisualEffect: FallbackOnlyVisualEffect) {
    val factory = remember(fallbackVisualEffect) {
      HazeEffectFactory<Unit> { fallbackVisualEffect }
    }
    Box(
      Modifier
        .size(120.dp)
        .testTag(FALLBACK_TAG)
        .hazeEffect(
          factory = factory,
          input = HazeInput.Content,
          style = Unit,
          sampling = HazeSampling.FullResolution,
        ),
    )
  }
}

private data class PreparedResourcesForTest(
  val prepared: Any,
  val style: Any?,
  val shapePath: Any?,
  val highlightBrush: Any?,
  val edgeBrush: Any?,
  val edgeDirectBrush: Any?,
  val edgeStroke: Any?,
)

private const val FALLBACK_TAG = "fallback"

private class FallbackOnlyVisualEffect(
  private val glass: GlassRuntimeEffect,
  private val fallback: FallbackGlassDelegate,
) :
  HazeEffectRenderer<Unit>,
  HazeEffectRendererLifecycle<Unit>,
  HazeEffectRendererDrawHooks<Unit> {
  private var prepareGroup = true

  fun disableGroupPreparation() {
    prepareGroup = false
    fallback.onTrimMemory(checkNotNull(glass.attachedContextForTest), TrimMemoryLevel.UI_HIDDEN)
  }

  override fun HazeEffectRuntimeDrawScope.prepareDraw(style: Unit) {
    val context = this
    with(fallback) { prepareDraw(context) }
    if (!prepareGroup) {
      fallback.releaseGroupWithoutInvalidation(context)
    }
  }

  override fun HazeEffectDrawScope.draw(style: Unit) {
    val context = this as HazeEffectRuntimeDrawScope
    with(fallback) { draw(context) }
  }

  override fun HazeEffectRuntimeDrawScope.drawForeground(style: Unit) {
    val context = this
    with(fallback) { drawForeground(context) }
  }

  override fun attach(scope: HazeEffectLifecycleScope) {
    glass.attach(scope)
    fallback.attach()
  }

  override fun update(
    scope: HazeEffectLifecycleScope,
    style: Unit,
    sampling: HazeSampling,
  ) {
    glass.update(
      scope,
      GlassNodeConfiguration(
        style = glass.style,
        interactionSource = glass.interactionSource,
      ),
      sampling,
    )
  }

  override fun detach() {
    fallback.detach()
    glass.detach()
  }

  private fun FallbackGlassDelegate.releaseGroupWithoutInvalidation(
    context: HazeEffectRuntimeDrawScope,
  ) {
    val groupAlpha = javaClass.getDeclaredField("groupAlpha").run {
      isAccessible = true
      get(this@releaseGroupWithoutInvalidation) as RetainedGlassGroupAlphaLayer
    }
    groupAlpha.release(context.requireGraphicsContext())
  }
}

@OptIn(InternalHazeApi::class)
private class FallbackRecordingContext(
  override val size: Size,
) :
  HazeEffectRuntimeDrawScope,
  HazeEffectLifecycleScope,
  androidx.compose.ui.graphics.drawscope.DrawScope by CanvasDrawScope() {
  override val modifierSize: Size get() = size
  override val modifierBounds: Rect get() = Rect(Offset.Zero, size)
  override val sampling: HazeSampling = HazeSampling.FullResolution
  override val layerSize: Size = size
  val graphicsContext = FallbackTestGraphicsContext()
  override val layerOffset: Offset = Offset.Zero
  override val hasDrawableInput: Boolean = true
  override val inputSnapshot: HazeEffectInputSnapshot = FallbackInputSnapshot
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun requirePlatformContext(): PlatformContext = error("Unused in fallback tests")
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: androidx.compose.runtime.CompositionLocal<T>): T {
    @Suppress("UNCHECKED_CAST")
    return LayoutDirection.Ltr as T
  }
  override fun requireGraphicsContext(): GraphicsContext = graphicsContext
  override fun drawInput() = Unit
  override fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInput() = Unit
  override fun invalidateDraw() = Unit
  override fun invalidateLayerBounds() = Unit
}

@OptIn(InternalHazeApi::class)
private object FallbackInputSnapshot : HazeEffectInputSnapshot

private class FallbackTestGraphicsContext : GraphicsContext {
  val createdLayers = mutableListOf<GraphicsLayer>()
  val releasedLayers = mutableListOf<GraphicsLayer>()

  override fun createGraphicsLayer(): GraphicsLayer =
    (fallbackUnsafe.allocateInstance(GraphicsLayer::class.java) as GraphicsLayer).also {
      createdLayers += it
    }

  override fun releaseGraphicsLayer(layer: GraphicsLayer) {
    releasedLayers += layer
  }
}

private val fallbackUnsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
  isAccessible = true
  get(null) as Unsafe
}
