// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.hazeEffect
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import sun.misc.Unsafe

@OptIn(ExperimentalTestApi::class)
class FallbackGlassDelegateTest {

  @Test
  fun boundedFractionalAlpha_preparesAndReusesGroupLayer() {
    val effect = GlassVisualEffect().apply { alpha = 0.5f }
    val delegate = FallbackGlassDelegate(effect)
    val context = FallbackRecordingContext(size = Size(100f, 100f))

    delegate.prepare(context)
    val first = checkNotNull(delegate.groupAlphaForTest().layer)
    delegate.prepare(context)

    assertSame(first, delegate.groupAlphaForTest().layer)
    assertThat(context.graphicsContext.createdLayers).isEqualTo(listOf(first))
  }

  @Test
  fun alphaZero_releasesAndOwnsNoFallbackGroupLayer() {
    val effect = GlassVisualEffect().apply { alpha = 0.5f }
    val delegate = FallbackGlassDelegate(effect)
    val context = FallbackRecordingContext(size = Size(100f, 100f))
    delegate.prepare(context)
    val groupLayer = checkNotNull(delegate.groupAlphaForTest().layer)

    effect.alpha = 0f
    delegate.prepare(context)

    assertThat(delegate.groupAlphaForTest().layer).isNull()
    assertThat(groupLayer in context.graphicsContext.releasedLayers).isTrue()
  }

  @Test
  fun fallbackGroup_exceedingDimension_usesDirectAlphaWithoutAllocation() {
    val effect = GlassVisualEffect().apply { alpha = 0.5f }
    val delegate = FallbackGlassDelegate(effect)
    val context = FallbackRecordingContext(size = Size(4097f, 1f))

    delegate.prepare(context)

    assertThat(delegate.groupAlphaForTest().isAvailable).isFalse()
    assertThat(context.graphicsContext.createdLayers).isEqualTo(emptyList())
  }

  @Test
  fun fallbackGroup_atExactPixelBoundary_isAllowed() {
    val effect = GlassVisualEffect().apply { alpha = 0.5f }
    val delegate = FallbackGlassDelegate(effect)
    val context = FallbackRecordingContext(size = Size(4096f, 4096f))

    delegate.prepare(context)

    assertThat(delegate.groupAlphaForTest().isAvailable).isTrue()
  }

  @Test
  fun fallbackGroup_exceedingDimensionAndPixelLimit_usesDirectAlphaWithoutAllocation() {
    val effect = GlassVisualEffect().apply { alpha = 0.5f }
    val delegate = FallbackGlassDelegate(effect)
    val context = FallbackRecordingContext(size = Size(4096f, 4097f))

    delegate.prepare(context)

    assertThat(delegate.groupAlphaForTest().isAvailable).isFalse()
    assertThat(context.graphicsContext.createdLayers).isEqualTo(emptyList())
  }

  @Test
  fun directAlphaDegradation_scalesEveryFallbackContribution() = runComposeUiTest {
    fun assertDirectAlphaHalvesContribution(
      effect: GlassVisualEffect,
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
      assertTrue(opaque.alpha > 0.01f)
      assertTrue(abs(direct.alpha - opaque.alpha / 2f) < 0.03f)
    }

    assertDirectAlphaHalvesContribution(
      effect = GlassVisualEffect().apply {
        tint = Color.Red
        specularIntensity = 0f
        edgeSoftness = 0.dp
      },
      sample = Offset(60f, 60f),
    )
    assertDirectAlphaHalvesContribution(
      effect = GlassVisualEffect().apply {
        tint = Color.Transparent
        specularIntensity = 1f
        edgeSoftness = 0.dp
        lightPosition = Offset(60f, 60f)
      },
      sample = Offset(60f, 60f),
    )
    assertDirectAlphaHalvesContribution(
      effect = GlassVisualEffect().apply {
        tint = Color.Transparent
        specularIntensity = 0f
        ambientResponse = 1f
        edgeSoftness = 8.dp
      },
      sample = Offset(2f, 60f),
    )
    assertDirectAlphaHalvesContribution(
      effect = GlassVisualEffect().apply {
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
    val effect = GlassVisualEffect().apply {
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

    assertTrue(image.toPixelMap()[60, 60].red > 0.01f)
  }

  @Test
  fun stablePrepare_reusesResourcesUntilTheirSemanticInputsChange() {
    val effect = GlassVisualEffect().apply {
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

    assertSame(first.prepared, stable.prepared)
    assertSame(first.style, stable.style)
    assertSame(first.shapePath, stable.shapePath)
    assertSame(first.highlightBrush, stable.highlightBrush)
    assertSame(first.edgeBrush, stable.edgeBrush)
    assertSame(first.edgeStroke, stable.edgeStroke)

    effect.lightPosition = Offset(24f, 36f)
    delegate.prepare(context)
    val movedLight = delegate.preparedResourcesForTest()

    assertNotSame(stable.prepared, movedLight.prepared)
    assertNotSame(stable.highlightBrush, movedLight.highlightBrush)
    assertSame(stable.shapePath, movedLight.shapePath)
    assertSame(stable.edgeBrush, movedLight.edgeBrush)
    assertSame(stable.edgeStroke, movedLight.edgeStroke)

    effect.ambientResponse = 0.5f
    delegate.prepare(context)
    val changedEdge = delegate.preparedResourcesForTest()

    assertSame(movedLight.highlightBrush, changedEdge.highlightBrush)
    assertNotSame(movedLight.edgeBrush, changedEdge.edgeBrush)
    assertSame(movedLight.edgeStroke, changedEdge.edgeStroke)
    assertSame(movedLight.shapePath, changedEdge.shapePath)

    effect.edgeSoftness = 0.dp
    delegate.prepare(context)
    val noEdge = delegate.preparedResourcesForTest()

    assertNull(noEdge.edgeBrush)
    assertNull(noEdge.edgeDirectBrush)
    assertNull(noEdge.edgeStroke)

    effect.edgeSoftness = 8.dp
    delegate.prepare(context)
    val restoredEdge = delegate.preparedResourcesForTest()

    assertNotNull(restoredEdge.edgeBrush)
    assertNotNull(restoredEdge.edgeDirectBrush)
    assertNotNull(restoredEdge.edgeStroke)
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
    Box(
      Modifier
        .size(120.dp)
        .testTag(FALLBACK_TAG)
        .hazeEffect { visualEffect = fallbackVisualEffect },
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
  private val glass: GlassVisualEffect,
  private val fallback: FallbackGlassDelegate,
) : VisualEffect {
  private var prepareGroup = true

  fun disableGroupPreparation() {
    prepareGroup = false
    fallback.onTrimMemory(checkNotNull(glass.attachedContextForTest), TrimMemoryLevel.UI_HIDDEN)
  }

  override fun androidx.compose.ui.graphics.drawscope.DrawScope.prepareDraw(context: VisualEffectContext) {
    with(fallback) { prepareDraw(context) }
    if (!prepareGroup) {
      fallback.onTrimMemory(context, TrimMemoryLevel.UI_HIDDEN)
    }
  }

  override fun androidx.compose.ui.graphics.drawscope.DrawScope.draw(context: VisualEffectContext) {
    with(fallback) { draw(context) }
  }

  override fun androidx.compose.ui.graphics.drawscope.DrawScope.drawForeground(
    context: VisualEffectContext,
  ) {
    with(fallback) { drawForeground(context) }
  }

  override fun attach(context: VisualEffectContext) {
    glass.attach(context)
    fallback.attach()
  }

  override fun update(context: VisualEffectContext) {
    glass.update(context)
  }

  override fun detach(context: VisualEffectContext) {
    fallback.detach()
    glass.detach(context)
  }
}

private class FallbackRecordingContext(
  override val size: Size,
) : VisualEffectContext {
  override val layerSize: Size = size
  val graphicsContext = FallbackTestGraphicsContext()
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
  override fun boundsOf(area: HazeArea): Rect? = null
  override fun requirePlatformContext(): PlatformContext = error("Unused in fallback tests")
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: androidx.compose.runtime.CompositionLocal<T>): T {
    @Suppress("UNCHECKED_CAST")
    return LayoutDirection.Ltr as T
  }
  override fun requireGraphicsContext(): GraphicsContext = graphicsContext
  override fun invalidateDraw() = Unit
}

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
