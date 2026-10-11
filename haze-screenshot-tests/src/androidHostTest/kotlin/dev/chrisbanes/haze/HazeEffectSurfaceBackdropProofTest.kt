// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class, ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.hazeGlassContainer
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

/** Native adapter, attached core boundary and public Glass container pixel qualification. */
@Config(sdk = [37])
class HazeEffectSurfaceBackdropProofTest : ScreenshotTest() {
  private var previousBackdropEnabled = false

  @Before
  fun enableBackdrop() {
    previousBackdropEnabled = HazeFeatureFlags.isPlatformBackdropEnabled
    HazeFeatureFlags.isPlatformBackdropEnabled = true
  }

  @After
  fun restoreBackdrop() {
    HazeFeatureFlags.isPlatformBackdropEnabled = previousBackdropEnabled
  }

  @Test
  fun nativeBackdrop_actualConfiguredGlassClusters_shareEarlierPixels() = assertGlassContainer()

  @Test
  fun nativeBackdrop_actualConfiguredGlassClusters_disabledLeavesSharpPixels() {
    HazeFeatureFlags.isPlatformBackdropEnabled = false
    assertGlassContainer(expectNative = false)
  }

  @Test
  @Config(sdk = [36])
  fun nativeBackdrop_actualConfiguredGlassClusters_unsupportedLeavesSharpPixels() = assertGlassContainer(expectNative = false)

  private fun assertGlassContainer(expectNative: Boolean = true) = runScreenshotTest {
    val emptyFallback = HazeState()
    fun style(tint: Color, exponent: Float) = GlassStyle {
      optics(GlassOptics(refractionStrength = 0f, refractionDisplacement = 0.dp, refractionDetailIntensity = 0f, depth = OpticalSizeValue.Fixed(1f), blurRadius = OpticalSizeValue.Fixed(14.dp)))
      tint(tint)
      backgroundColor(Color.Transparent)
      edgeShadow(Color.Transparent)
      edgeSoftness(0.dp)
      specularIntensity(0f)
      specularExponent(exponent)
      ambientResponse(0f)
      chromaticAberrationStrength(0f)
      contrast(0f)
      whitePoint(0f)
      chromaMultiplier(1f)
    }
    val first = style(Color.Red, 2f)
    val second = style(Color.Blue.copy(alpha = 0.25f), 4f)
    setContent {
      Box(Modifier.size(300.dp, 160.dp).testTag("probe")) {
        Box(
          Modifier.size(300.dp, 160.dp).drawWithContent {
            val stripe = 4.dp.toPx()
            var x = 0f
            var index = 0
            while (x < size.width) {
              drawRect(if (index++ % 2 == 0) Color.Red else Color.Blue, Offset(x, 0f), androidx.compose.ui.geometry.Size(stripe, size.height))
              x += stripe
            }
            drawContent()
          },
        )
        Box(Modifier.size(300.dp, 160.dp).hazeGlassContainer(40.dp)) {
          // Two members in each of two incompatible clusters, with overlapping material/capture.
          // There is no source anywhere, so unavailable native cannot pass the filtered assertion.
          Box(Modifier.offset(20.dp, 20.dp).size(100.dp, 100.dp).hazeGlass(HazeInput.Backdrop(emptyFallback), first, HazePerformanceMode.Quality))
          Box(Modifier.offset(120.dp, 20.dp).size(100.dp, 100.dp).hazeGlass(HazeInput.Backdrop(emptyFallback), first, HazePerformanceMode.Quality))
          Box(Modifier.offset(40.dp, 30.dp).size(100.dp, 100.dp).hazeGlass(HazeInput.Backdrop(emptyFallback), second, HazePerformanceMode.Quality))
          Box(Modifier.offset(140.dp, 30.dp).size(100.dp, 100.dp).hazeGlass(HazeInput.Backdrop(emptyFallback), second, HazePerformanceMode.Quality))
          Box(Modifier.offset(50.dp, 40.dp).size(12.dp, 100.dp).background(Color.Green))
        }
      }
    }
    composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
    waitForIdle()
    val pixels = captureRootPixels()
    val bounds = onNodeWithTag("probe").fetchSemanticsNode().boundsInRoot
    fun pixel(x: Float, y: Float) = pixels[(bounds.left + x * bounds.width / 300f).roundToInt(), (bounds.top + y * bounds.height / 160f).roundToInt()]
    val sample = pixel(100f, 80f)
    println("backdrop-proof configured-glass native=$expectNative pixel=$sample")
    if (expectNative) {
      assertThat(sample.blue).isGreaterThan(0.5f)
      assertThat(sample.red).isGreaterThan(0.1f)
      assertThat(sample.red).isLessThan(0.65f)
      assertThat(sample.green).isLessThan(0.05f)
    } else {
      assertThat(sample == Color.Red || sample == Color.Blue, "empty fallback preserves sharp earlier pixels: $sample").isTrue()
    }
    assertThat(pixel(56f, 80f).green).isGreaterThan(0.95f)
  }

  @Test
  fun nativeBackdrop_attachedBridge_preservesEarlierPixelsAndChildOrdering() = assertAttachedBridge()

  @Test
  fun nativeBackdrop_attachedBridge_overBudgetUsesEmptyFallback() = assertAttachedBridge(expectNative = false, allowGroup = false)

  @Test
  fun nativeBackdrop_attachedBridge_recoversWhenGroupBudgetBecomesViable() = assertAttachedBridge(expectNative = false, allowGroup = false, recoverGroup = true)

  @Test
  fun nativeBackdrop_attachedBridge_disabledFlagPreservesEarlierSharpPixels() {
    HazeFeatureFlags.isPlatformBackdropEnabled = false
    assertAttachedBridge(expectNative = false)
  }

  @Test
  @Config(sdk = [36])
  fun nativeBackdrop_attachedBridge_unsupportedSdkPreservesEarlierSharpPixels() = assertAttachedBridge(expectNative = false)

  @Test
  fun nativeBackdrop_singleSurface_samplesEarlierPixels() = assertCommonBackdrop(firstSurface = false)

  @Test
  fun nativeBackdrop_directSurfaces_sampleEarlierSurfaceTint() = assertCommonBackdrop(expectContamination = true)

  @Test
  fun nativeBackdrop_forcedLayers_sampleEarlierSurfaceTint() = assertCommonBackdrop(forcedLayer = true, expectContamination = true)

  @Test
  fun nativeBackdrop_saveLayers_sampleEarlierSurfaceTint() = assertCommonBackdrop(saveLayer = true, expectContamination = true)

  @Test
  fun nativeBackdrop_oneGroupLayer_shareEarlierPixels() = assertCommonBackdrop(groupLayer = true)

  @Test
  fun nativeBackdrop_groupWithSourceOutput_shareEarlierPixels() =
    assertCommonBackdrop(groupLayer = true, paintFirstSurface = true)

  @Test
  fun nativeBackdrop_sparseGroups_doNotSampleSiblingOutput() = runScreenshotTest {
    val nodes = mutableListOf<RenderNode>()
    try {
      setContent {
        Box(
          Modifier.size(360.dp, 100.dp).testTag("probe").drawWithContent {
            val canvas = drawContext.canvas.nativeCanvas
            val stripe = 4.dp.toPx()
            var x = 0f
            var index = 0
            while (x < size.width) {
              drawRect(
                if (index++ % 2 == 0) Color.Red else Color.Blue,
                androidx.compose.ui.geometry.Offset(x, 0f),
                androidx.compose.ui.geometry.Size(stripe, size.height),
              )
              x += stripe
            }
            val width = 100.dp.roundToPx()
            val height = size.height.roundToInt()
            // Only the intersecting pair receives a group layer, bounded to its actual node bounds.
            val checkpoint = canvas.saveLayer(0f, 0f, width.toFloat(), size.height, null)
            val first = backdropNode(width, height, redEffect()).also(nodes::add)
            canvas.drawRenderNode(first)
            val second = backdropNode(width, height, blueBlurEffect()).also(nodes::add)
            canvas.drawRenderNode(second)
            canvas.restoreToCount(checkpoint)
            // The distant singleton emits directly: the prior group's output cannot enter its subset.
            val third = backdropNode(width, height, blueBlurEffect(), left = 220.dp.roundToPx()).also(nodes::add)
            canvas.drawRenderNode(third)
            drawRect(Color.Green, size = androidx.compose.ui.geometry.Size(12.dp.toPx(), size.height))
            drawContent()
          },
        )
      }
      composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
      waitForIdle()
      val pixels = captureRootPixels()
      val bounds = onNodeWithTag("probe").fetchSemanticsNode().boundsInRoot
      for (x in listOf(50f, 270f)) {
        val pixel = pixels[(bounds.left + x * bounds.width / 360f).roundToInt(), bounds.center.y.roundToInt()]
        println("backdrop-proof sparse=$x pixel=$pixel")
        assertThat(pixel.blue, "bounded group and distant singleton preserve earlier pixels").isGreaterThan(0.5f)
        assertThat(pixel.red).isLessThan(0.65f)
        assertThat(pixel.green).isLessThan(0.05f)
      }
    } finally {
      composeTestRule.runOnIdle { nodes.forEach(RenderNode::discardDisplayList) }
    }
  }

  private fun assertCommonBackdrop(
    firstSurface: Boolean = true,
    forcedLayer: Boolean = false,
    saveLayer: Boolean = false,
    groupLayer: Boolean = false,
    paintFirstSurface: Boolean = false,
    expectContamination: Boolean = false,
  ) = runScreenshotTest {
    val nodes = mutableListOf<RenderNode>()
    try {
      setContent {
        Box(
          Modifier.size(200.dp, 100.dp).testTag("probe").drawWithContent {
            val canvas = drawContext.canvas.nativeCanvas
            // High-frequency red/blue background is emitted before every material surface.
            val stripe = 4.dp.toPx()
            var x = 0f
            var index = 0
            while (x < size.width) {
              drawRect(
                color = if (index++ % 2 == 0) Color.Red else Color.Blue,
                topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(stripe, size.height),
              )
              x += stripe
            }
            fun emit(node: RenderNode) {
              if (forcedLayer) {
                val parent = RenderNode("HazeBackdropIsolatedProof").apply {
                  setPosition(0, 0, size.width.roundToInt(), size.height.roundToInt())
                  setUseCompositingLayer(true, null)
                  beginRecording().drawRenderNode(node)
                  endRecording()
                }
                nodes += parent
                canvas.drawRenderNode(parent)
              } else if (saveLayer) {
                val checkpoint = canvas.saveLayer(0f, 0f, size.width, size.height, null)
                canvas.drawRenderNode(node)
                canvas.restoreToCount(checkpoint)
              } else {
                canvas.drawRenderNode(node)
              }
            }
            val groupCheckpoint = if (groupLayer) canvas.saveLayer(0f, 0f, size.width, size.height, null) else null
            if (paintFirstSurface) {
              // Source/Content material outputs must participate in native interference groups too.
              drawRect(Color.Red)
            } else if (firstSurface) {
              val first = backdropNode(size.width.roundToInt(), size.height.roundToInt(), redEffect())
              nodes += first
              emit(first)
            }
            val second = backdropNode(size.width.roundToInt(), size.height.roundToInt(), blueBlurEffect())
            nodes += second
            emit(second)
            if (groupCheckpoint != null) canvas.restoreToCount(groupCheckpoint)
            // Child emission follows all surfaces; this must never contaminate material input.
            drawRect(Color.Green, size = androidx.compose.ui.geometry.Size(12.dp.toPx(), size.height))
            drawContent()
          },
        )
      }
      composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
      waitForIdle()
      val pixels = captureRootPixels()
      val bounds = onNodeWithTag("probe").fetchSemanticsNode().boundsInRoot
      val pixel = pixels[bounds.center.x.roundToInt(), bounds.center.y.roundToInt()]
      println("backdrop-proof first=$firstSurface forced=$forcedLayer save=$saveLayer group=$groupLayer pixel=$pixel")
      val child = pixels[(bounds.left + 6.dp.value * bounds.width / 200f).roundToInt(), bounds.center.y.roundToInt()]
      assertThat(child.green, "opaque child is replayed above material").isGreaterThan(0.95f)
      assertThat(pixel.green, "children must follow material output").isLessThan(0.05f)
      if (expectContamination) {
        assertThat(pixel.blue).isLessThan(0.3f)
        assertThat(pixel.red).isGreaterThan(0.7f)
      } else {
        assertThat(pixel.blue, "second surface samples earlier red/blue backdrop, not first red tint: $pixel").isGreaterThan(0.5f)
        assertThat(pixel.red, "red tint must not enter the second surface input: $pixel").isLessThan(0.65f)
      }
    } finally {
      composeTestRule.runOnIdle { nodes.forEach(RenderNode::discardDisplayList) }
    }
  }

  private fun assertAttachedBridge(expectNative: Boolean = true, allowGroup: Boolean = true, recoverGroup: Boolean = false) = runScreenshotTest {
    val owner = BridgeOwner(allowGroup)
    // No hazeSource exists. The empty fallback cannot satisfy any positive filtered-pixel assertion.
    val emptyFallback = HazeState()
    val first = BridgeFactory(owner, ::redEffect)
    val second = BridgeFactory(owner, ::blueBlurEffect)
    setContent {
      Box(Modifier.size(200.dp, 100.dp).testTag("probe")) {
        Box(
          Modifier.size(200.dp, 100.dp).drawWithContent {
            val stripe = 4.dp.toPx()
            var x = 0f
            var index = 0
            while (x < size.width) {
              drawRect(if (index++ % 2 == 0) Color.Red else Color.Blue, Offset(x, 0f), androidx.compose.ui.geometry.Size(stripe, size.height))
              x += stripe
            }
            drawContent()
          },
        )
        Box(Modifier.size(200.dp, 100.dp).then(BridgeOwnerElement(owner))) {
          Box(Modifier.size(200.dp, 100.dp).hazeEffect(first, HazeInput.Backdrop(emptyFallback), Unit))
          Box(Modifier.size(200.dp, 100.dp).hazeEffect(second, HazeInput.Backdrop(emptyFallback), Unit))
          Box(
            Modifier.size(12.dp, 100.dp).drawWithContent {
              drawRect(Color.Green)
              drawContent()
            },
          )
        }
      }
    }
    composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
    waitForIdle()
    val pixels = captureRootPixels()
    val bounds = onNodeWithTag("probe").fetchSemanticsNode().boundsInRoot
    val pixel = pixels[bounds.center.x.roundToInt(), bounds.center.y.roundToInt()]
    println("backdrop-proof attached native=$expectNative pixel=$pixel groups=${owner.groups}")
    if (expectNative) {
      assertThat(pixel.blue).isGreaterThan(0.5f)
      assertThat(pixel.red).isGreaterThan(0.1f)
      assertThat(pixel.red).isLessThan(0.65f)
      assertThat(pixel.green).isLessThan(0.05f)
      assertThat(owner.groups).isGreaterThan(0)
    } else {
      assertThat(pixel == Color.Red || pixel == Color.Blue, "ineligible native output leaves earlier stripe sharp: $pixel").isTrue()
      if (allowGroup) assertThat(owner.groups).isEqualTo(0) else assertThat(owner.groups).isGreaterThan(0)
    }
    assertThat(pixels[(bounds.left + bounds.width * 6f / 200f).roundToInt(), bounds.center.y.roundToInt()].green).isGreaterThan(0.95f)
    if (recoverGroup) {
      composeTestRule.runOnIdle {
        owner.allowGroup = true
        owner.invalidateDraw()
      }
      waitForIdle()
      composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
      waitForIdle()
      val recovered = captureRootPixels()[bounds.center.x.roundToInt(), bounds.center.y.roundToInt()]
      println("backdrop-proof attached recovery=$recovered")
      assertThat(recovered.blue).isGreaterThan(0.5f)
      assertThat(recovered.red).isGreaterThan(0.1f)
      assertThat(recovered.red).isLessThan(0.65f)
    }
  }

  private class BridgeFactory(private val owner: BridgeOwner, private val effect: () -> RenderEffect) : HazeEffectFactory<Unit> {
    override fun createRenderer(): HazeEffectRenderer<Unit> = object : HazeEffectRenderer<Unit>, HazeEffectRendererLifecycle<Unit>, HazeEffectRendererBackdrop<Unit> {
      override fun attach(scope: HazeEffectLifecycleScope) {
        scope.surface.relocateTo(owner)
      }
      override fun HazeEffectDrawScope.draw(style: Unit) {
        drawInput()
      }
      override fun HazeEffectRuntimeDrawScope.backdropEffect(style: Unit): HazeEffectBackdrop = HazeEffectBackdrop(effect())
    }
  }

  private class BridgeOwner(var allowGroup: Boolean = true) : Modifier.Node(), HazeEffectSurfaceOwner, DrawModifierNode, LayoutAwareModifierNode {
    override var surfaceCoordinates: LayoutCoordinates? = null
    val surfaces = mutableListOf<HazeEffectSurface>()
    lateinit var children: GraphicsLayer
    var groups = 0
    override fun onAttach() {
      children = requireGraphicsContext().createGraphicsLayer()
    }
    override fun onDetach() {
      surfaces.toList().forEach { it.relocateTo(null) }
      requireGraphicsContext().releaseGraphicsLayer(children)
    }
    override fun onPlaced(coordinates: LayoutCoordinates) {
      surfaceCoordinates = coordinates
    }
    override fun onSurfaceChanged(surface: HazeEffectSurface) {
      if (surface !in surfaces) surfaces += surface
      if (isAttached) invalidateDraw()
    }
    override fun onSurfaceReleased(surface: HazeEffectSurface) {
      surfaces -= surface
      if (isAttached) invalidateDraw()
    }
    override fun canComposeNativeSurfaces(bounds: Rect): Boolean {
      groups++
      return allowGroup
    }
    override fun ContentDrawScope.draw() = drawHazeEffectSurfaces(this@BridgeOwner, surfaces.toList(), children)
  }

  private data class BridgeOwnerElement(val owner: BridgeOwner) : ModifierNodeElement<BridgeOwner>() {
    override fun create(): BridgeOwner = owner
    override fun update(node: BridgeOwner) = Unit
    override fun InspectorInfo.inspectableProperties() {
      name = "BackdropBridgeOwner"
    }
  }

  private fun backdropNode(width: Int, height: Int, effect: RenderEffect, left: Int = 0): RenderNode =
    RenderNode("HazeBackdropProof").apply {
      setPosition(left, 0, left + width, height)
      RenderNode::class.java.getMethod("setBackdropRenderEffect", RenderEffect::class.java).invoke(this, effect)
      beginRecording(width, height).drawColor(android.graphics.Color.TRANSPARENT)
      endRecording()
    }

  private fun redEffect(): RenderEffect = RenderEffect.createColorFilterEffect(
    ColorMatrixColorFilter(
      ColorMatrix(
        floatArrayOf(
          0f, 0f, 0f, 0f, 255f,
          0f, 0f, 0f, 0f, 0f,
          0f, 0f, 0f, 0f, 0f,
          0f, 0f, 0f, 1f, 0f,
        ),
      ),
    ),
  )

  private fun blueBlurEffect(): RenderEffect = RenderEffect.createColorFilterEffect(
    ColorMatrixColorFilter(
      ColorMatrix(
        floatArrayOf(
          0.75f, 0f, 0f, 0f, 0f,
          0f, 0.75f, 0f, 0f, 0f,
          0f, 0f, 0.75f, 0f, 63.75f,
          0f, 0f, 0f, 1f, 0f,
        ),
      ),
    ),
    RenderEffect.createBlurEffect(14f, 14f, Shader.TileMode.CLAMP),
  )
}
