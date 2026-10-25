// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class, androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isSameInstanceAs
import java.awt.Frame
import kotlin.test.Test

class HazeEffectSurfaceDrawTest {
  @Test
  fun configuredContent_combinesDistinctRecordingsAtNonzeroOffsetsOnce() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val red = SurfaceFactory(owner)
    val blue = SurfaceFactory(owner)
    var configured: HazeConfiguredEffectSurface<Unit>? = null
    var created = 0
    var disposed = 0
    val factory = HazeEffectFactory<Unit> {
      created++
      object : HazeEffectRenderer<Unit> {
        override fun HazeEffectDrawScope.draw(style: Unit) {
          translate(left = 60f) { this@draw.drawInput() }
        }
        override fun dispose() {
          disposed++
        }
      }
    }
    owner.resolveSurfaces = {
      configured = configured ?: red.surface.createConfiguredSurface(factory, Unit, Rect(10f, 15f, 30f, 95f), listOf(red.surface, blue.surface))
      listOf(checkNotNull(configured).surface)
    }
    setContent {
      Box(Modifier.size(180.dp).testTag("root").background(Color.White)) {
        Box(Modifier.offset(20.dp, 10.dp).size(160.dp).then(OwnerElement(owner))) {
          Box(Modifier.offset(10.dp, 15.dp).size(20.dp).hazeEffect(red, HazeInput.Content, Unit).background(Color.Red))
          Box(Modifier.offset(10.dp, 75.dp).size(20.dp).hazeEffect(blue, HazeInput.Content, Unit).background(Color.Blue))
        }
      }
    }
    val pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    assertThat(pixels[95, 30]).isEqualTo(Color.Red)
    assertThat(pixels[95, 90]).isEqualTo(Color.Blue)
    assertThat(pixels[95, 60]).isEqualTo(Color.White)
    assertThat(created).isEqualTo(1)
    runOnIdle {
      configured?.dispose()
      configured?.dispose()
      owner.resolveSurfaces = null
    }
    assertThat(disposed).isEqualTo(1)
  }

  @Test
  fun configuredSurface_lifecycleScope_doesNotExposeTheAnchorSurface() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val anchor = SurfaceFactory(owner)
    setContent {
      Box(Modifier.size(100.dp).then(OwnerElement(owner))) {
        Box(Modifier.size(20.dp).hazeEffect(anchor, HazeInput.Content, Unit))
      }
    }
    waitForIdle()
    val factory = HazeEffectFactory<Unit> {
      object : HazeEffectRenderer<Unit>, HazeEffectRendererLifecycle<Unit> {
        override fun attach(scope: HazeEffectLifecycleScope) {
          scope.surface
        }
        override fun HazeEffectDrawScope.draw(style: Unit) = Unit
      }
    }

    // The anchor member owns its own surface; a configured surface's scope must not hand it out.
    assertFailure {
      anchor.surface.createConfiguredSurface(factory, Unit, Rect(0f, 0f, 20f, 20f), listOf(anchor.surface))
    }.isInstanceOf<IllegalStateException>()
  }

  @Test
  fun nativeToPortableSwitch_drawsEffectWithinOneFrame() = runComposeUiTest {
    val previousFlag = HazeFeatureFlags.isPlatformBackdropEnabled
    HazeFeatureFlags.isPlatformBackdropEnabled = true
    try {
      val owner = SurfaceOwnerNode()
      val member = SurfaceFactory(owner)
      val state = HazeState()
      var configured: HazeConfiguredEffectSurface<Unit>? = null
      val ownerDrawsAtMemberDraw = mutableListOf<Int>()
      owner.resolveSurfaces = {
        configured = configured ?: member.surface.createConfiguredSurface(
          HazeEffectFactory {
            object : HazeEffectRenderer<Unit> {
              override fun HazeEffectDrawScope.draw(style: Unit) {
                ownerDrawsAtMemberDraw += owner.draws
                drawInput()
              }
            }
          },
          Unit, Rect(10f, 15f, 70f, 75f), listOf(member.surface),
        )
        listOf(checkNotNull(configured).surface)
      }
      setContent {
        Box(Modifier.size(160.dp).testTag("root").background(Color.White)) {
          Box(Modifier.size(160.dp).hazeSource(state).background(Color.Red))
          Box(Modifier.offset(20.dp, 10.dp).size(120.dp).then(OwnerElement(owner))) {
            Box(Modifier.offset(10.dp, 15.dp).size(60.dp).hazeEffect(member, HazeInput.Backdrop(state), Unit))
          }
        }
      }
      assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[60, 60]).isEqualTo(Color.Red)
      // The source records no layer while the Backdrop is native, so the first owner draw after the
      // switch has no input. The accepted bound is that the effect draws by the second owner draw.
      assertThat(ownerDrawsAtMemberDraw).isNotEmpty()
      assertThat(ownerDrawsAtMemberDraw.first()).isLessThanOrEqualTo(2)
      runOnIdle {
        configured?.dispose()
        owner.resolveSurfaces = null
      }
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = previousFlag
    }
  }

  @Test
  fun configuredSources_deduplicatesSharedSelectedCaptureAlpha() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val first = SurfaceFactory(owner)
    val second = SurfaceFactory(owner)
    val state = HazeState()
    val input = HazeInput.Sources(state, HazeSourceSelection.All.where { it.key == "selected" })
    var configured: HazeConfiguredEffectSurface<Unit>? = null
    owner.resolveSurfaces = {
      configured = configured ?: first.surface.createConfiguredSurface(
        HazeEffectFactory {
          object : HazeEffectRenderer<Unit> {
            override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()
          }
        },
        Unit, Rect(10f, 15f, 80f, 45f), listOf(first.surface, second.surface),
      )
      listOf(checkNotNull(configured).surface)
    }
    setContent {
      Box(Modifier.size(160.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(160.dp).hazeSource(state, key = "selected").background(Color.Red.copy(alpha = 0.5f)))
        Box(Modifier.size(160.dp).hazeSource(state, key = "ignored").background(Color.Blue))
        Box(Modifier.size(160.dp).background(Color.White))
        Box(Modifier.offset(20.dp, 10.dp).size(120.dp).then(OwnerElement(owner))) {
          Box(Modifier.offset(10.dp, 15.dp).size(30.dp).hazeEffect(first, input, Unit))
          Box(Modifier.offset(50.dp, 15.dp).size(30.dp).hazeEffect(second, input, Unit))
        }
      }
    }
    val pixel = onNodeWithTag("root").captureToImage().toPixelMap()[65, 35]
    assertThat(pixel.red).isGreaterThan(0.95f)
    assertThat(pixel.green).isGreaterThan(0.45f)
    assertThat(pixel.green).isLessThan(0.55f)
    assertThat(pixel.blue).isGreaterThan(0.45f)
    runOnIdle {
      configured?.dispose()
      owner.resolveSurfaces = null
    }
  }

  @Test
  fun configuredSources_differentResolvedSelectionsDrawMembersSeparately() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val first = SurfaceFactory(owner)
    val second = SurfaceFactory(owner)
    val state = HazeState()
    // Equal Behind inputs select different sources under differently ordered ancestors.
    val input = HazeInput.Sources(state)
    var configured: HazeConfiguredEffectSurface<Unit>? = null
    var clusterDraws = 0
    owner.resolveSurfaces = {
      configured = configured ?: first.surface.createConfiguredSurface(
        HazeEffectFactory {
          object : HazeEffectRenderer<Unit> {
            override fun HazeEffectDrawScope.draw(style: Unit) {
              clusterDraws++
              drawInput()
            }
          }
        },
        Unit, Rect(0f, 0f, 110f, 30f), listOf(first.surface, second.surface),
      )
      listOf(checkNotNull(configured).surface)
    }
    setContent {
      Box(Modifier.size(160.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(160.dp).hazeSource(state, zIndex = 0f).background(Color.Red))
        Box(Modifier.size(160.dp).then(OwnerElement(owner))) {
          Box(Modifier.size(80.dp).hazeSource(state, zIndex = 1f)) {
            Box(Modifier.size(30.dp).hazeEffect(first, input, Unit))
          }
          Box(Modifier.offset(x = 80.dp).size(80.dp).hazeSource(state, zIndex = 2f)) {
            Box(Modifier.size(30.dp).hazeEffect(second, input, Unit))
          }
        }
      }
    }
    waitForIdle()
    assertThat(clusterDraws).isEqualTo(0)
    assertThat(first.draws).isGreaterThan(0)
    assertThat(second.draws).isGreaterThan(0)
    runOnIdle {
      configured?.dispose()
      owner.resolveSurfaces = null
    }
  }

  @Test
  fun configuredSurface_respectsMembersDisabledLayerExpansion() {
    for (expand in listOf(true, false)) {
      assertThat(configuredLayerOrigin(expand), "expandLayerBounds=$expand")
        .isEqualTo(if (expand) Offset(-10f, -10f) else Offset.Zero)
    }
  }

  private fun configuredLayerOrigin(expand: Boolean): Offset? {
    var origin: Offset? = null
    runComposeUiTest {
      val owner = SurfaceOwnerNode()
      val first = SurfaceFactory(owner)
      val second = SurfaceFactory(owner)
      var configured: HazeConfiguredEffectSurface<Unit>? = null
      owner.resolveSurfaces = {
        configured = configured ?: first.surface.createConfiguredSurface(
          HazeEffectFactory {
            object : HazeEffectRenderer<Unit> {
              override fun HazeEffectLayoutScope.calculateLayerBounds(style: Unit): Rect = modifierBounds.inflate(10f)
              override fun HazeEffectDrawScope.draw(style: Unit) {
                // The runtime scope reports the layer origin relative to the configured bounds.
                origin = Offset.Zero - modifierBounds.topLeft
              }
            }
          },
          Unit, Rect(0f, 0f, 60f, 30f), listOf(first.surface, second.surface),
        )
        listOf(checkNotNull(configured).surface)
      }
      setContent {
        Box(Modifier.size(100.dp).then(OwnerElement(owner))) {
          Box(Modifier.size(30.dp).hazeEffect(first, HazeInput.Content, Unit, expandLayerBounds = expand))
          Box(Modifier.offset(x = 30.dp).size(30.dp).hazeEffect(second, HazeInput.Content, Unit, expandLayerBounds = expand))
        }
      }
      waitForIdle()
      runOnIdle {
        configured?.dispose()
        owner.resolveSurfaces = null
      }
    }
    return origin
  }

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
  fun relocatedMember_notifiesOwnerOnlyWhenItsSurfaceGeometryChanges() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val factory = SurfaceFactory(owner)
    val state = HazeState()
    val groupX = mutableStateOf(0)
    val memberX = mutableStateOf(10)
    setContent {
      Box(Modifier.size(200.dp).background(Color.White)) {
        Box(Modifier.size(200.dp).hazeSource(state).background(Color.Red))
        Box(Modifier.offset { IntOffset(groupX.value, 10) }.size(120.dp).then(OwnerElement(owner))) {
          Box(
            Modifier.offset { IntOffset(memberX.value, 15) }.size(60.dp)
              .hazeEffect(factory, HazeInput.Sources(state), Unit),
          )
        }
      }
    }
    waitForIdle()

    // The owner and its member move together: the member is re-placed, but its bounds in the owner
    // and its layer bounds are unchanged.
    var changes = owner.changes
    groupX.value = 30
    waitForIdle()
    assertThat(owner.changes).isEqualTo(changes)

    changes = owner.changes
    memberX.value = 20
    waitForIdle()
    assertThat(owner.changes).isGreaterThan(changes)
    assertThat(factory.surface.boundsInOwner()?.left).isEqualTo(20f)
  }

  @Test
  fun relocatedMember_layeredGroupMovedOverStaticSourceUpdatesPixels() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val factory = SurfaceFactory(owner)
    val state = HazeState()
    val groupX = mutableStateOf(0)
    setContent {
      Box(Modifier.size(160.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(60.dp).hazeSource(state).background(Color.Red))
        Box(Modifier.fillMaxSize().background(Color.White))
        // Layers keep the owner's recorded draw when the group moves: only the member is placed again
        // relative to the source, while its transform in the owner is unchanged.
        Box(Modifier.offset { IntOffset(groupX.value, 0) }.graphicsLayer {}.size(120.dp)) {
          Box(Modifier.size(120.dp).then(OwnerElement(owner))) {
            Box(Modifier.graphicsLayer {}.size(60.dp).hazeEffect(factory, HazeInput.Sources(state), Unit))
          }
        }
      }
    }
    assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[30, 30]).isEqualTo(Color.Red)

    groupX.value = 60
    waitForIdle()

    assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[90, 30]).isEqualTo(Color.White)
  }

  @Test
  fun relocatedMember_layeredCrossWindowSourceChangeRedrawsOwner() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val factory = SurfaceFactory(owner)
    val state = HazeState()
    val sourceColor = mutableStateOf(Color.Red)
    val sourceWindow = createTestWindow("SourceWindow")
    val effectWindow = createTestWindow("EffectWindow")
    try {
      setContent {
        Box(Modifier.size(160.dp).testTag("root").background(Color.White)) {
          CompositionLocalProvider(LocalAwtWindow provides sourceWindow) {
            Box(Modifier.size(60.dp).hazeSource(state).background(sourceColor.value))
          }
          // Hides the source so only the owner's recorded member output shows its color.
          Box(Modifier.fillMaxSize().background(Color.White))
          CompositionLocalProvider(LocalAwtWindow provides effectWindow) {
            Box(Modifier.size(120.dp).then(OwnerElement(owner))) {
              // The layer stands in for AnimatedVisibility: the member can redraw
              // without redrawing the owner.
              Box(Modifier.graphicsLayer {}.size(60.dp).hazeEffect(factory, HazeInput.Sources(state), Unit))
            }
          }
        }
      }
      waitForIdle()

      assertThat(state.areas.single().preDrawListeners).isNotEmpty()
      assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[30, 30]).isEqualTo(Color.Red)
      val before = owner.draws

      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeByFrame()
      waitForIdle()
      assertThat(owner.draws).isEqualTo(before)

      sourceColor.value = Color.Blue
      waitForIdle()

      assertThat(owner.draws).isGreaterThan(before)
      assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[30, 30]).isEqualTo(Color.Blue)
    } finally {
      (sourceWindow as? Frame)?.dispose()
      (effectWindow as? Frame)?.dispose()
    }
  }

  @Test
  fun configuredSurface_unchangedMembersReuseTheInputSnapshotAcrossDraws() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val first = SurfaceFactory(owner)
    val second = SurfaceFactory(owner)
    val state = HazeState()
    val snapshots = mutableListOf<HazeEffectInputSnapshot>()
    var configured: HazeConfiguredEffectSurface<Unit>? = null
    owner.resolveSurfaces = {
      configured = configured ?: first.surface.createConfiguredSurface(
        HazeEffectFactory {
          object : HazeEffectRenderer<Unit> {
            override fun HazeEffectDrawScope.draw(style: Unit) {
              snapshots.add(checkNotNull((this as HazeEffectRuntimeDrawScope).inputSnapshot))
              drawInput()
            }
          }
        },
        Unit, Rect(0f, 0f, 110f, 30f), listOf(first.surface, second.surface),
      )
      listOf(checkNotNull(configured).surface)
    }
    setContent {
      Box(Modifier.size(160.dp).background(Color.White)) {
        Box(Modifier.size(60.dp).hazeSource(state).background(Color.Red))
        Box(Modifier.size(160.dp).then(OwnerElement(owner))) {
          Box(Modifier.size(30.dp).hazeEffect(first, HazeInput.Sources(state), Unit))
          Box(Modifier.offset(x = 80.dp).size(30.dp).hazeEffect(second, HazeInput.Sources(state), Unit))
        }
      }
    }
    waitForIdle()
    val drawsBefore = snapshots.size

    // Nothing the cluster reads has changed, so the owner's next draws find the same inputs.
    repeat(2) {
      runOnIdle { owner.redraw() }
      waitForIdle()
    }

    assertThat(snapshots.size, "cluster draws").isGreaterThan(drawsBefore + 1)
    assertThat(snapshots.last()).isSameInstanceAs(snapshots[snapshots.size - 2])
    assertThat(snapshots.last()).isSameInstanceAs(snapshots[drawsBefore - 1])
    runOnIdle {
      configured?.dispose()
      owner.resolveSurfaces = null
    }
  }

  @Test
  fun configuredSurface_inputSnapshotChangesWhenCrossWindowCaptureGenerationAdvances() = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val first = SurfaceFactory(owner)
    val second = SurfaceFactory(owner)
    val state = HazeState()
    val layerAlpha = mutableFloatStateOf(1f)
    val snapshots = mutableListOf<HazeEffectInputSnapshot>()
    var configured: HazeConfiguredEffectSurface<Unit>? = null
    owner.resolveSurfaces = {
      configured = configured ?: first.surface.createConfiguredSurface(
        HazeEffectFactory {
          object : HazeEffectRenderer<Unit> {
            override fun HazeEffectDrawScope.draw(style: Unit) {
              snapshots.add(checkNotNull((this as HazeEffectRuntimeDrawScope).inputSnapshot))
              drawInput()
            }
          }
        },
        Unit, Rect(0f, 0f, 110f, 30f), listOf(first.surface, second.surface),
      )
      listOf(checkNotNull(configured).surface)
    }
    val sourceWindow = createTestWindow("SourceWindow")
    val effectWindow = createTestWindow("EffectWindow")
    try {
      setContent {
        Box(Modifier.size(160.dp).background(Color.White)) {
          CompositionLocalProvider(LocalAwtWindow provides sourceWindow) {
            Box(Modifier.size(60.dp).hazeSource(state)) {
              // Changing this layer leaves the source's contentVersion unchanged.
              Box(Modifier.fillMaxSize().graphicsLayer { alpha = layerAlpha.floatValue }.background(Color.Red))
            }
          }
          CompositionLocalProvider(LocalAwtWindow provides effectWindow) {
            Box(Modifier.size(160.dp).then(OwnerElement(owner))) {
              Box(Modifier.size(30.dp).hazeEffect(first, HazeInput.Sources(state), Unit))
              Box(Modifier.offset(x = 80.dp).size(30.dp).hazeEffect(second, HazeInput.Sources(state), Unit))
            }
          }
        }
      }
      waitForIdle()

      val area = state.areas.single()
      val before = snapshots.last()
      val versionBefore = area.contentVersion
      val sizeBefore = area.size
      val drawsBefore = snapshots.size

      layerAlpha.floatValue = 0f
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeByFrame()
      waitForIdle()

      assertThat(area.contentVersion, "contentVersion").isEqualTo(versionBefore)
      assertThat(area.size, "size").isEqualTo(sizeBefore)
      assertThat(snapshots.size, "cluster draws").isGreaterThan(drawsBefore)
      assertThat(snapshots.last()).isNotEqualTo(before)
    } finally {
      runOnIdle {
        configured?.dispose()
        owner.resolveSurfaces = null
      }
      (sourceWindow as? Frame)?.dispose()
      (effectWindow as? Frame)?.dispose()
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

  @Test
  fun configuredSources_translationOrder_matchesDirectCoordinatesAndPixels() = configuredSourceOrderProof(HazePositionStrategy.Local, transformed = false)

  @Test
  fun configuredSources_noncommutingLocalOrder_matchesDirectCoordinates() = configuredSourceOrderProof(HazePositionStrategy.Local, transformed = true)

  @Test
  fun configuredSources_noncommutingScreenOrder_matchesDirectCoordinates() = configuredSourceOrderProof(HazePositionStrategy.Screen, transformed = true)

  @Test
  fun configuredSources_noncommutingLocalOrder_drawsFeaturesAtDirectPositions() = configuredSourceOrderProof(HazePositionStrategy.Local, transformed = true, verifyCoordinates = false)

  @Test
  fun configuredSources_noncommutingScreenOrder_drawsFeaturesAtDirectPositions() = configuredSourceOrderProof(HazePositionStrategy.Screen, transformed = true, verifyCoordinates = false)

  private fun configuredSourceOrderProof(strategy: HazePositionStrategy, transformed: Boolean, verifyCoordinates: Boolean = true) = runComposeUiTest {
    val owner = SurfaceOwnerNode()
    val first = SurfaceFactory(owner)
    val second = SurfaceFactory(owner)
    val standalone = SurfaceFactory(owner, relocate = false)
    val state = HazeState().also { it.positionStrategy = strategy }
    val input = HazeInput.Sources(state, HazeSourceSelection.All.where { it.key == "selected" })
    var sourceCoordinates: LayoutCoordinates? = null
    var standaloneCoordinates: LayoutCoordinates? = null
    var configured: HazeConfiguredEffectSurface<Unit>? = null
    val bounds = Rect(10f, 15f, 600f, 420f)
    val features = listOf(Rect(280f, 210f, 320f, 250f), Rect(370f, 270f, 390f, 330f), Rect(720f, 190f, 740f, 220f))
    owner.resolveSurfaces = {
      configured = configured ?: first.surface.createConfiguredSurface(
        HazeEffectFactory {
          object : HazeEffectRenderer<Unit> {
            override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()
          }
        },
        Unit, bounds, listOf(first.surface, second.surface),
      )
      listOf(checkNotNull(configured).surface)
    }
    setContent {
      Box(Modifier.size(900.dp, 600.dp).testTag("root").background(Color.White)) {
        Box(
          Modifier.size(900.dp, 600.dp).onGloballyPositioned { sourceCoordinates = it }.hazeSource(state, key = "selected").drawBehind {
            drawRect(Color.Black)
            features.forEach { drawRect(Color.Red, it.topLeft, it.size) }
          },
        )
        Box(Modifier.fillMaxSize().background(Color.White))
        Box(
          Modifier.offset(100.dp, 70.dp).size(650.dp, 450.dp).graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            if (transformed) {
              scaleX = .75f
              scaleY = .9f
              rotationZ = 12f
            }
          }.then(OwnerElement(owner)),
        ) {
          Box(
            Modifier.offset(50.dp, 40.dp).size(400.dp, 300.dp).graphicsLayer {
              transformOrigin = TransformOrigin(0f, 0f)
              if (transformed) {
                scaleX = .8f
                scaleY = .65f
                rotationZ = -17f
              }
            }.hazeEffect(first, input, Unit),
          )
          Box(Modifier.offset(80.dp, 60.dp).size(400.dp, 300.dp).hazeEffect(second, input, Unit))
        }
        Box(Modifier.offset(680.dp, 150.dp).size(160.dp, 180.dp).clipToBounds().onGloballyPositioned { standaloneCoordinates = it }.hazeEffect(standalone, input, Unit))
      }
    }
    val actualPixels = onNodeWithTag("root").captureToImage().toPixelMap()
    val source = checkNotNull(sourceCoordinates)
    val ownerCoordinates = checkNotNull(owner.surfaceCoordinates)
    val direct = Matrix().also { ownerCoordinates.transformFrom(source, it) }
    val points = features.take(2).flatMap { listOf(it.topLeft, it.topRight, it.bottomLeft, it.bottomRight, it.center) }
    var maximumError = 0f
    for (factory in listOf(first, second)) {
      val node = factory.surface.node
      val sourceToMember = node.sourceTransformInEffect(node.areas.single())
      val retained = sourceToMember.values.copyOf()
      val entry = node.surfaceInputEntries(ownerCoordinates).single()
      for (point in points) {
        val expected = direct.map(point)
        val error = (entry.transform.map(point) - expected).getDistance()
        maximumError = maxOf(maximumError, error)
      }
      assertThat(sourceToMember.values.toList(), "entry resolution retains cached source transform").isEqualTo(retained.toList())
      val repeated = node.surfaceInputEntries(ownerCoordinates).single()
      assertThat(repeated.transform.values.toList(), "consecutive entry resolution retains composed transform").isEqualTo(entry.transform.values.toList())
      assertThat(sourceToMember.values.toList(), "consecutive entry resolution retains source").isEqualTo(retained.toList())
    }
    val standaloneNode = standalone.surface.node
    val area = standaloneNode.areas.single()
    val standaloneOracle = Matrix().also { checkNotNull(standaloneCoordinates).transformFrom(source, it) }
    for (point in points) {
      assertThat((standaloneNode.sourceTransformInEffect(area).map(point) - standaloneOracle.map(point)).getDistance(), "standalone transform").isBetween(0f, .01f)
    }
    assertFeaturePixels(actualPixels, features.last(), "standalone")
    val actualRed = features.take(2).sumOf { redFeatureCount(actualPixels, it) }
    println("ORDER strategy=$strategy transformed=$transformed actualMaxError=$maximumError actualFeaturePixels=$actualRed direct=${direct.values.toList()}")
    if (verifyCoordinates) assertThat(maximumError, "$strategy actual source-to-owner coordinate error").isBetween(0f, .01f)
    features.take(2).forEach { assertFeaturePixels(actualPixels, it, "$strategy actual configured drawInput") }
    runOnIdle {
      configured?.dispose()
      owner.resolveSurfaces = null
    }
  }

  private fun assertFeaturePixels(pixels: PixelMap, feature: Rect, label: String) {
    // Root-space source coordinates are independent of both owner and member transforms. The direct
    // source-to-owner oracle above maps these same corners; drawing through the owner cancels it.
    for (point in listOf(feature.topLeft + Offset(2f, 2f), feature.topRight + Offset(-3f, 2f), feature.bottomLeft + Offset(2f, -3f), feature.bottomRight - Offset(3f, 3f), feature.center)) {
      val pixel = pixels[point.x.toInt(), point.y.toInt()]
      assertThat(pixel.red - pixel.green, "$label interior $point").isGreaterThan(.8f)
    }
    for (point in listOf(feature.center.copy(x = feature.left - 2f), feature.center.copy(x = feature.right + 1f), feature.center.copy(y = feature.top - 2f), feature.center.copy(y = feature.bottom + 1f))) {
      val pixel = pixels[point.x.toInt(), point.y.toInt()]
      assertThat(pixel.red - pixel.green, "$label exterior $point").isLessThan(.1f)
    }
    assertThat(redFeatureCount(pixels, feature), "$label feature area").isBetween((feature.width * feature.height).toInt() - 4, (feature.width * feature.height).toInt() + 4)
  }

  private fun redFeatureCount(pixels: PixelMap, feature: Rect): Int {
    var count = 0
    for (y in feature.top.toInt() - 2 until feature.bottom.toInt() + 2) {
      for (x in feature.left.toInt() - 2 until feature.right.toInt() + 2) {
        if (pixels[x, y].red - pixels[x, y].green > .5f) count++
      }
    }
    return count
  }

  private class SurfaceFactory(val owner: SurfaceOwnerNode, val spill: Boolean = false, val retainedColor: Color? = null, val relocate: Boolean = true) : HazeEffectFactory<Unit> {
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
        if (relocate) surface.relocateTo(owner)
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
    var resolveSurfaces: (() -> List<HazeEffectSurface>)? = null
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
    var changes = 0
    var draws = 0
    fun redraw() = invalidateDraw()
    override fun onSurfaceChanged(surface: HazeEffectSurface) {
      changes++
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
      draws++
      beforeRecord()
      drawHazeEffectSurfaces(this@SurfaceOwnerNode, { resolveSurfaces?.invoke() ?: surfaces.toList() }, children)
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
