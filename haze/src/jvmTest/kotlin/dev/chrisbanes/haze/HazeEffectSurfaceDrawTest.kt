// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import kotlin.test.Test

class HazeEffectSurfaceDrawTest {
  @Test
  fun relocatedSources_drawBehindChildren_andRestoreOnRelease() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val factory = SurfaceFactory(owner)
    val state = HazeState()
    setContent {
      Box(Modifier.size(160.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(160.dp).hazeSource(state).background(Color.Red))
        Box(Modifier.fillMaxSize().background(Color.White))
        Box(Modifier.offset(20.dp, 10.dp).size(120.dp).then(OwnerElement(owner))) {
          Box(Modifier.offset(10.dp, 15.dp).size(60.dp).hazeEffect(factory, HazeInput.Sources(state), Unit)) {
            Box(Modifier.size(10.dp).background(Color.Blue))
          }
        }
      }
    }
    var pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    assertThat(pixels[55, 50]).isEqualTo(Color.Red)
    assertThat(pixels[35, 30]).isEqualTo(Color.Blue)
    assertThat(factory.surface.boundsInOwner()).isEqualTo(Rect(10f, 15f, 70f, 75f))
    factory.surface.relocateTo(null)
    waitForIdle()
    pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    assertThat(pixels[55, 50]).isEqualTo(Color.Red)
    assertThat(owner.releases).isEqualTo(1)
  }

  @Test
  fun relocatedContent_capturesEachMembersOwnPixels_andReplaysChildrenOnce() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val red = SurfaceFactory(owner, spill = true)
    val blue = SurfaceFactory(owner, spill = true)
    var redChildDraws = 0
    var blueChildDraws = 0
    var redBefore = 0
    var blueBefore = 0
    val recordedCounts = mutableListOf<Pair<Int, Int>>()
    owner.beforeRecord = {
      redBefore = redChildDraws
      blueBefore = blueChildDraws
    }
    owner.afterRecord = { recordedCounts += (redChildDraws - redBefore) to (blueChildDraws - blueBefore) }
    setContent {
      Box(Modifier.size(160.dp).testTag("root").background(Color.White).then(OwnerElement(owner))) {
        Box(
          Modifier.offset(10.dp, 10.dp).size(20.dp).hazeEffect(red, HazeInput.Content, Unit).drawWithContent {
            redChildDraws++
            drawContent()
          }.background(Color.Red),
        )
        Box(
          Modifier.offset(10.dp, 70.dp).size(20.dp).hazeEffect(blue, HazeInput.Content, Unit).drawWithContent {
            blueChildDraws++
            drawContent()
          }.background(Color.Blue),
        )
      }
    }
    val pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    // Material copies each own-content capture beside the member; children replay at original bounds.
    assertThat(pixels[20, 20]).isEqualTo(Color.Red)
    assertThat(pixels[60, 20]).isEqualTo(Color.Red)
    assertThat(pixels[20, 80]).isEqualTo(Color.Blue)
    assertThat(pixels[60, 80]).isEqualTo(Color.Blue)
    assertThat(red.draws).isGreaterThan(0)
    assertThat(blue.draws).isGreaterThan(0)
    assertThat(redChildDraws).isGreaterThan(0)
    assertThat(blueChildDraws).isGreaterThan(0)
    for ((redCount, blueCount) in recordedCounts) {
      assertThat(redCount, "a member's own Content records at most once per owner frame").isLessThan(2)
      assertThat(blueCount, "replay does not execute member content again").isLessThan(2)
    }
  }

  @Test
  fun relocatedSources_movedSelectedSourceUpdatesPixelsWithoutSelectingSibling() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val factory = SurfaceFactory(owner)
    val state = HazeState()
    val offset = mutableStateOf(0.dp)
    val selection = HazeSourceSelection.All.where { it.key == "picked" }
    setContent {
      Box(Modifier.size(160.dp).testTag("root").background(Color.White)) {
        Box(Modifier.offset(offset.value, 0.dp).size(60.dp).hazeSource(state, key = "picked").background(Color.Red))
        Box(Modifier.fillMaxSize().hazeSource(state, key = "ignored").background(Color.Blue))
        Box(Modifier.fillMaxSize().background(Color.White))
        Box(Modifier.offset(20.dp, 10.dp).size(120.dp).then(OwnerElement(owner))) {
          Box(Modifier.offset(10.dp, 15.dp).size(60.dp).hazeEffect(factory, HazeInput.Sources(state, selection), Unit))
        }
      }
    }
    var pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    assertThat(pixels[40, 40]).isEqualTo(Color.Red)
    assertThat(pixels[70, 40]).isEqualTo(Color.White)
    offset.value = 60.dp
    waitForIdle()
    pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    assertThat(pixels[40, 40]).isEqualTo(Color.White)
    assertThat(pixels[70, 40]).isEqualTo(Color.Red)
  }

  @Test
  fun relocatedSources_keepLastFrameRetainsOutput_andMemberDetachReleasesOwner() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val factory = SurfaceFactory(owner, retainedColor = Color.Blue)
    val state = HazeState()
    val source = mutableStateOf(true)
    val member = mutableStateOf(true)
    setContent {
      Box(Modifier.size(100.dp).testTag("root").background(Color.White)) {
        if (source.value) Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
        Box(Modifier.fillMaxSize().background(Color.White))
        Box(Modifier.fillMaxSize().then(OwnerElement(owner))) {
          if (member.value) Box(Modifier.fillMaxSize().hazeEffect(factory, HazeInput.Sources(state), Unit))
        }
      }
    }
    assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[50, 50]).isEqualTo(Color.Blue)
    source.value = false
    waitForIdle()
    assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[50, 50]).isEqualTo(Color.Blue)
    member.value = false
    waitForIdle()
    assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[50, 50]).isEqualTo(Color.White)
    assertThat(owner.releases).isEqualTo(1)
    assertThat(owner.surfaces.size).isEqualTo(0)
  }

  @Test
  fun relocatedSources_respectsUnavailableSourceRetention() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val factory = SurfaceFactory(owner)
    val state = HazeState()
    val present = mutableStateOf(true)
    setContent {
      Box(Modifier.size(100.dp).testTag("root").background(Color.White)) {
        if (present.value) Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
        Box(Modifier.fillMaxSize().background(Color.White))
        Box(Modifier.fillMaxSize().then(OwnerElement(owner))) {
          Box(Modifier.fillMaxSize().hazeEffect(factory, HazeInput.Sources(state, retention = HazeSourceRetention.ClearWhenUnavailable), Unit))
        }
      }
    }
    assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[50, 50]).isEqualTo(Color.Red)
    present.value = false
    waitForIdle()
    assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[50, 50]).isEqualTo(Color.White)
  }

  private class SurfaceFactory(val owner: SurfaceOwnerNode, val spill: Boolean = false, val retainedColor: Color? = null) : HazeEffectFactory<Unit> {
    lateinit var surface: HazeEffectSurface
    var draws = 0
    override fun createRenderer(): HazeEffectRenderer<Unit> = object : HazeEffectRenderer<Unit>, HazeEffectRendererLifecycle<Unit>, HazeEffectRendererRetainedOutput {
      var retained = false
      override fun canDrawRetainedOutput(): Boolean = retained
      override fun clearRetainedOutput() {
        retained = false
      }
      override fun attach(scope: HazeEffectLifecycleScope) {
        surface = scope.surface
        surface.relocateTo(owner)
      }
      override fun HazeEffectDrawScope.draw(style: Unit) {
        draws++
        if (retainedColor != null) {
          retained = true
          drawRect(retainedColor)
        } else if (spill) translate(left = 40f) { this@draw.drawInput() } else drawInput()
      }
      override fun HazeEffectLayoutScope.calculateLayerBounds(style: Unit): Rect =
        if (spill) Rect(0f, 0f, modifierBounds.right + 40f, modifierBounds.bottom) else modifierBounds
    }
  }

  private class SurfaceOwnerNode : Modifier.Node(), HazeEffectSurfaceOwner, DrawModifierNode, LayoutAwareModifierNode {
    override var surfaceCoordinates: LayoutCoordinates? = null
    val surfaces = mutableListOf<HazeEffectSurface>()
    lateinit var children: GraphicsLayer
    var releases = 0
    var beforeRecord: () -> Unit = {}
    var afterRecord: () -> Unit = {}
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
      releases++
      if (isAttached) invalidateDraw()
    }
    override fun canComposeNativeSurfaces(bounds: Rect): Boolean = true
    override fun ContentDrawScope.draw() {
      beforeRecord()
      drawHazeEffectSurfaces(this@SurfaceOwnerNode, surfaces.toList(), children)
      afterRecord()
    }
  }

  private data class OwnerElement(val owner: SurfaceOwnerNode) : ModifierNodeElement<SurfaceOwnerNode>() {
    override fun create(): SurfaceOwnerNode = owner
    override fun update(node: SurfaceOwnerNode) = Unit
    override fun InspectorInfo.inspectableProperties() {
      name = "TestSurfaceOwner"
    }
  }
}
