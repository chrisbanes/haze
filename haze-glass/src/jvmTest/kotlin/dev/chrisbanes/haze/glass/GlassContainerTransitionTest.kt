// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.math.abs
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class GlassContainerTransitionTest : ContextTest() {
  @Test
  fun reducedTransparency_detachedGhostDrawsCapturedOverride() = runComposeUiTest {
    val settings = GlassAccessibilitySettings(true, reducedTransparencyFallbackColor = Color.Yellow)
    val fixture = fixture(
      unavailable = true,
      bTint = Color.Transparent,
      bAccessibility = settings,
      transition = GlassMemberTransition.Materialize,
    )
    assertThat(fixture.capture()[210, 100]).isEqualTo(Color.Yellow)
    fixture.removeB()
    assertThat(fixture.b.memberBindingForTest).isNull()
    assertThat(fixture.b().identity).isInstanceOf<GlassMemberGhost>()
    assertThat(fixture.b().material.fallbackBaseColor).isEqualTo(Color.Yellow)
    assertThat(fixture.capture()[210, 100]).isEqualTo(Color.Yellow)
    fixture.advance(144)
    val faded = fixture.capture()[210, 100]
    assertThat(faded.red).isGreaterThan(0.95f)
    assertThat(faded.green).isGreaterThan(0.95f)
    assertThat(faded.blue).isGreaterThan(0.3f)
    assertThat(faded.blue).isLessThan(0.7f)
    fixture.settle()
  }

  @Test
  fun fallbackBase_transitionTakesTargetPolicyImmediatelyAndGhostKeepsCapturedBase() = runComposeUiTest {
    val ordinary = mergedMember(0f, 0f, 100f, 80f)
    val reduced = GlassMergedMember(
      ordinary.rect,
      ordinary.cornerRadii,
      ordinary.tint,
      ordinary.backgroundColor,
      fallbackBaseColor = Color.Yellow,
    )
    for (fraction in listOf(0f, 0.5f, 1f)) {
      assertThat(lerpGlassMergedMember(ordinary, reduced, fraction).fallbackBaseColor).isEqualTo(Color.Yellow)
      assertThat(lerpGlassMergedMember(reduced, ordinary, fraction).fallbackBaseColor).isNull()
    }
    val fixture = fixture()
    val captured = fixture.b().copy(material = fixture.b().material.copy(fallbackBaseColor = Color.Yellow))
    val neighbour = fixture.a().copy(material = fixture.a().material.copy(fallbackBaseColor = Color.Black))
    val ghost = GlassMemberGhost(captured, fixture.aNode, checkNotNull(fixture.aNode))
    assertThat(ghost.snapshot(neighbour).material.fallbackBaseColor).isEqualTo(Color.Yellow)
    assertThat(ghost.snapshot(null).material.fallbackBaseColor).isEqualTo(Color.Yellow)
    assertThat(captured.morphedFrom(neighbour, 0f).second.fallbackBaseColor).isEqualTo(Color.Yellow)
    fixture.toggle()
    assertThat(fixture.b().identity).isInstanceOf<GlassMemberGhost>()
  }

  @Test
  fun initialMembers_doNotAnimate() = runComposeUiTest {
    val fixture = fixture()
    assertThat(fixture.b()).prop(GlassMemberSnapshot::rect).isCloseTo(Rect(150f, 60f, 250f, 140f))
    assertThat(fixture.owner().renderer.runtimeSurfaces).hasSize(1)
  }

  @Test
  fun matchedGeometry_attach_growsOutOfNeighbour() = runComposeUiTest {
    val fixture = fixture(present = false)
    fixture.toggle()
    assertThat(fixture.owner().renderer.surfaceCount).isEqualTo(1)
    assertThat(fixture.b()).prop(GlassMemberSnapshot::rect).isCloseTo(Rect(40f, 60f, 140f, 140f), tolerance = 1f)
    // Haze never animates member content.
    assertThat(fixture.capture()[155, 65]).isEqualTo(Color.Green)
    fixture.advance(150)
    // The virtual clock advances whole 16ms frames, so 150ms runs 144ms of the transition.
    assertThat(fixture.b().rect.left).isCloseTo(40f + 110f * 144f / 300f, 1f)
    fixture.settle()
    assertThat(fixture.b()).prop(GlassMemberSnapshot::rect).isCloseTo(Rect(150f, 60f, 250f, 140f))
  }

  @Test
  fun matchedGeometryAttach_doesNotReRunMemberUpdatesPerFrame() = runComposeUiTest {
    val fixture = fixture(present = false)
    fixture.toggle()
    fixture.a.updateCallsForTest = 0
    fixture.b.updateCallsForTest = 0
    val left = fixture.b().rect.left
    fixture.advance(144)
    assertThat(fixture.a.updateCallsForTest).isEqualTo(0)
    assertThat(fixture.b.updateCallsForTest).isEqualTo(0)
    assertThat(fixture.b().rect.left).isGreaterThan(left)
    fixture.settle()
  }

  @Test
  fun matchedGeometryDetach_doesNotReRunNeighbourUpdatesPerFrame() = runComposeUiTest {
    val fixture = fixture()
    fixture.toggle()
    fixture.a.updateCallsForTest = 0
    val left = fixture.b().rect.left
    fixture.advance(144)
    assertThat(fixture.a.updateCallsForTest).isEqualTo(0)
    assertThat(fixture.b().rect.left).isLessThan(left)
    fixture.settle()
  }

  @Test
  fun matchedGeometry_attachBeyondHalfSpacing_staysLinkedUntilEnd() = runComposeUiTest {
    val fixture = fixture(gap = 30, present = false)
    fixture.toggle()
    fixture.advance(250)
    assertThat(fixture.owner().model.surfaces.single().members).hasSize(2)
    fixture.settle()
    assertThat(fixture.owner().renderer.surfaceCount).isEqualTo(2)
    assertThat(fixture.owner().renderer.runtimeSurfaces).isEmpty()
  }

  @Test
  fun matchedGeometry_detach_retractsGhostIntoNeighbourThenReleases() = runComposeUiTest {
    val fixture = fixture()
    fixture.toggle()
    val field = checkNotNull(fixture.owner().renderer.runtimeSurfaces.single().mergedField)
    assertThat(field.members).hasSize(2)
    assertThat(fixture.b()).all {
      prop(GlassMemberSnapshot::identity).isInstanceOf<GlassMemberGhost>()
      prop(GlassMemberSnapshot::rect).isCloseTo(Rect(150f, 60f, 250f, 140f))
    }
    fixture.advance(150)
    assertThat(fixture.b().rect.left).isCloseTo(150f - 110f * 144f / 300f, 1f)
    fixture.settle()
    assertThat(fixture.owner().renderer.runtimeSurfaces).isEmpty()
    assertThat(fixture.owner().model.surfaces.single().members.single().identity).isEqualTo(fixture.aNode)
    assertThat(fixture.capture()[200, 100]).isEqualTo(Color.White)
  }

  @Test
  fun ghost_doesNotCountTowardsOpticalSize() = runComposeUiTest {
    val fixture = fixture(bSize = Size(200f, 160f))
    fixture.toggle()
    assertThat(fixture.b().identity).isInstanceOf<GlassMemberGhost>()
    assertThat(fixture.owner().model.opticalSize).isEqualTo(Size(80f, 80f))
    // B set the optical size it was resolved at, yet it still retracts into A's field.
    assertThat(fixture.owner().model.surfaces.single().members).hasSize(2)
    fixture.advance(150)
    assertThat(fixture.b().rect.left).isCloseTo(150f - 110f * 144f / 300f, 1f)
    assertThat(fixture.b().material.alpha).isEqualTo(1f)
    fixture.settle()
  }

  @Test
  fun containerDetach_releasesGhosts() = runComposeUiTest {
    val fixture = fixture()
    val owner = fixture.owner()
    fixture.toggle()
    fixture.advance(100)
    runOnIdle { fixture.enabled.value = false }
    fixture.advance(16)
    assertThat(owner.renderer.runtimeSurfaces).isEmpty()
    assertThat(fixture.a.opticalSizeOverride).isNull()
    runOnIdle { fixture.enabled.value = true }
    fixture.settle()
    assertThat(fixture.owner().model.surfaces.single().members.single().identity).isEqualTo(fixture.aNode)
  }

  @Test
  fun containerDetachMidFade_resetsMemberOverrides() = runComposeUiTest {
    val fixture = fixture(gap = 100, present = false, transition = GlassMemberTransition.Materialize)
    fixture.toggle()
    fixture.advance(100)
    runOnIdle { fixture.enabled.value = false }
    fixture.advance(16)
    for (effect in listOf(fixture.a, fixture.b)) {
      assertThat(effect.transitionAlpha).isEqualTo(1f)
      assertThat(effect.opticalSizeOverride).isNull()
    }
  }

  @Test
  fun noneTransition_attachesAndDetachesImmediately() = runComposeUiTest {
    val fixture = fixture(present = false, transition = GlassMemberTransition.None)
    fixture.toggle()
    assertThat(fixture.b()).prop(GlassMemberSnapshot::rect).isCloseTo(Rect(150f, 60f, 250f, 140f))
    fixture.toggle()
    assertThat(fixture.owner().model.surfaces.single().members.single().identity).isEqualTo(fixture.aNode)
    fixture.settle()
  }

  @Test
  fun reducedMotion_usesCriticallyDampedSpring() {
    val bouncy = spring<Float>(dampingRatio = 0.3f, stiffness = 200f)
    assertThat(maxRight(GlassReducedMotionPolicy.Reduced, bouncy)).isLessThanOrEqualTo(250.01f)
    // Sensitivity: the authored spring overshoots the layout rect.
    assertThat(maxRight(GlassReducedMotionPolicy.Full, bouncy)).isGreaterThan(250.01f)
  }

  @Test
  fun reducedMotion_keepsMorphWhenSystemRemovesAnimations() = runComposeUiTest(effectContext = NoMotion) {
    val fixture = fixture(present = false, policy = GlassReducedMotionPolicy.System)
    fixture.toggle()
    fixture.advance(48)
    val right = fixture.b().rect.right
    assertThat(right).isGreaterThan(141f)
    assertThat(right).isLessThan(249f)
    fixture.settle(millis = 1_000)
  }

  @Test
  fun neighbourLeavesDuringGrow_snapsMember() = runComposeUiTest {
    val fixture = fixture(present = false)
    fixture.toggle()
    fixture.advance(100)
    runOnIdle { fixture.aPresent.value = false }
    fixture.advance(16)
    // A itself may retract into B as a ghost, but B no longer grows out of A.
    assertThat(fixture.b()).prop(GlassMemberSnapshot::rect).isCloseTo(Rect(150f, 60f, 250f, 140f))
    fixture.settle()
  }

  @Test
  fun materializeGhost_drawsThroughBorrowedAnchorWithoutDisturbingIt() = runComposeUiTest {
    val fixture = fixture(gap = 100, transition = GlassMemberTransition.Materialize, bTint = Color.Red)
    val before = fixture.capture()
    fixture.toggle()
    fixture.advance(150)
    val fading = fixture.capture()
    assertThat(fading[300, 100].green).isGreaterThan(before[300, 100].green + 0.1f)
    assertThat(fading[300, 100].green).isLessThan(0.9f)
    assertThat(fading[80, 100]).isCloseTo(before[80, 100], 1f / 255f)
    fixture.settle()
    assertThat(fixture.capture()[300, 100]).isEqualTo(Color.White)
    assertThat(fixture.owner().renderer.runtimeSurfaces).isEmpty()
  }

  @Test
  fun unavailableRuntime_materializeGhostDrawsThroughBorrowedAnchorUnion() = runComposeUiTest {
    val fixture = fixture(gap = 100, transition = GlassMemberTransition.Materialize, bTint = Color.Red, unavailable = true)
    val before = fixture.capture()
    fixture.toggle()
    fixture.advance(150)
    assertThat(fixture.owner().renderer.runtimeSurfaces.single().delegate).isInstanceOf<FallbackGlassDelegate>()
    val fading = fixture.capture()
    assertThat(fading[300, 100].green).isGreaterThan(before[300, 100].green + 0.1f)
    assertThat(fading[300, 100].green).isLessThan(0.9f)
    assertThat(fading[80, 100]).isCloseTo(before[80, 100], 1f / 255f)
    fixture.settle()
    assertThat(fixture.capture()[300, 100]).isEqualTo(Color.White)
  }

  @Test
  fun anchorResolvingOtherSources_isNotBorrowed() = runComposeUiTest {
    // Equal Behind inputs resolve differently: B sits under a zIndex 1 source of the same state,
    // so it selects only the root source while A also selects that zIndex 1 source.
    val a = GlassRuntimeEffect()
    val b = GlassRuntimeEffect()
    val present = mutableStateOf(true)
    val state = HazeState()
    val input = HazeInput.Sources(state)
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(state).background(Color.White))
        Box(Modifier.size(360.dp, 240.dp).hazeGlassContainer(40.dp, transitionSpec = tween(300, easing = LinearEasing), reducedMotionPolicy = GlassReducedMotionPolicy.Full)) {
          Box(Modifier.offset(40.dp, 60.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { a }, input, containerTestStyle(Color.Red), HazePerformanceMode.Quality, true, null))
          Box(Modifier.offset(240.dp, 60.dp).size(100.dp, 80.dp).hazeSource(state, zIndex = 1f)) {
            if (present.value) {
              Box(
                Modifier.size(100.dp, 80.dp).hazeGlass(
                  HazeEffectFactory { b },
                  input,
                  containerTestStyle(Color.Blue),
                  HazePerformanceMode.Quality,
                  true,
                  null,
                  memberTransition = GlassMemberTransition.Materialize,
                ),
              )
            }
          }
        }
      }
    }
    waitForIdle()
    mainClock.autoAdvance = false
    val owner = checkNotNull(a.memberBindingForTest?.node?.container)
    val selections = owner.model.surfaces.flatMap { it.members }.map { it.sourceSelection.size }
    assertThat(selections).containsExactly(2, 1)
    runOnIdle { present.value = false }
    mainClock.advanceTimeBy(16)
    waitForIdle()
    // B's glass cannot be drawn through A's sources, so it leaves immediately.
    assertThat(owner.model.surfaces.flatMap { it.members }.map { it.identity }).containsExactly(a.memberBindingForTest?.node)
  }

  @Test
  fun neighbourResolvingOtherSources_isNotGrownFrom() = runComposeUiTest {
    // As above, B resolves only the root source while A also resolves the zIndex 1 source.
    val a = GlassRuntimeEffect()
    val b = GlassRuntimeEffect()
    val present = mutableStateOf(false)
    val state = HazeState()
    val input = HazeInput.Sources(state)
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(state).background(Color.White))
        Box(Modifier.size(360.dp, 240.dp).hazeGlassContainer(40.dp, transitionSpec = tween(300, easing = LinearEasing), reducedMotionPolicy = GlassReducedMotionPolicy.Full)) {
          Box(Modifier.offset(40.dp, 60.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { a }, input, containerTestStyle(Color.Red), HazePerformanceMode.Quality, true, null))
          Box(Modifier.offset(150.dp, 60.dp).size(100.dp, 80.dp).hazeSource(state, zIndex = 1f)) {
            if (present.value) {
              Box(Modifier.size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { b }, input, containerTestStyle(Color.Blue), HazePerformanceMode.Quality, true, null))
            }
          }
        }
      }
    }
    waitForIdle()
    mainClock.autoAdvance = false
    runOnIdle { present.value = true }
    mainClock.advanceTimeBy(16)
    waitForIdle()
    val owner = checkNotNull(a.memberBindingForTest?.node?.container)
    val bSnapshot = owner.model.surfaces.flatMap { it.members }.single { it.identity === b.memberBindingForTest?.node }
    // B cannot grow out of A's sources, so it materializes in place.
    assertThat(bSnapshot.rect).isCloseTo(Rect(150f, 60f, 250f, 140f))
    assertThat(b.transitionAlpha).isLessThan(0.1f)
    mainClock.advanceTimeBy(320)
    waitForIdle()
  }

  @Test
  fun materialize_attach_fadesInPlace() = runComposeUiTest {
    val fixture = fixture(gap = 100, present = false, transition = GlassMemberTransition.Materialize)
    fixture.toggle()
    assertThat(fixture.b.transitionAlpha).isLessThan(0.1f)
    assertThat(fixture.capture()[300, 100].blue - fixture.capture()[300, 100].red).isLessThan(0.1f)
    assertThat(fixture.b()).prop(GlassMemberSnapshot::rect).isCloseTo(Rect(240f, 60f, 340f, 140f))
    fixture.advance(150)
    assertThat(fixture.b.transitionAlpha).isCloseTo(0.5f, 0.05f)
    assertThat(fixture.b()).prop(GlassMemberSnapshot::rect).isCloseTo(Rect(240f, 60f, 340f, 140f))
    fixture.settle()
    assertThat(fixture.b.transitionAlpha).isEqualTo(1f)
    assertThat(fixture.capture()[300, 100].blue).isGreaterThan(0.9f)
  }

  @Test
  fun matchedGeometry_beyondSpacing_fallsBackToMaterialize() = runComposeUiTest {
    val fixture = fixture(gap = 50, present = false)
    fixture.toggle()
    fixture.advance(150)
    assertThat(fixture.b.transitionAlpha).isCloseTo(0.5f, 0.05f)
    assertThat(fixture.b()).prop(GlassMemberSnapshot::rect).isCloseTo(Rect(190f, 60f, 290f, 140f))
    fixture.settle()
    fixture.toggle()
    assertThat(fixture.b()).all {
      prop(GlassMemberSnapshot::identity).isInstanceOf<GlassMemberGhost>()
      prop(GlassMemberSnapshot::rect).isCloseTo(Rect(190f, 60f, 290f, 140f))
    }
    fixture.advance(150)
    assertThat(fixture.b()).all {
      prop(GlassMemberSnapshot::rect).isCloseTo(Rect(190f, 60f, 290f, 140f))
      prop(GlassMemberSnapshot::material).prop(ResolvedGlassStyle::alpha).isCloseTo(0.5f, 0.05f)
    }
    fixture.settle()
  }

  @Test
  fun matchedGeometry_incompatibleNeighbour_fallsBackToMaterialize() = runComposeUiTest {
    val fixture = fixture(present = false, bStyle = GlassStyle { contrast(0.5f) })
    fixture.toggle()
    fixture.advance(150)
    assertThat(fixture.b.transitionAlpha).isCloseTo(0.5f, 0.05f)
    assertThat(fixture.owner().model.surfaces).hasSize(2)
    fixture.settle()
    assertThat(fixture.owner().model.surfaces).hasSize(2)
  }

  @Test
  fun materialize_withinHalfSpacing_joinsClusterWhenOpaque() = runComposeUiTest {
    val fixture = fixture(present = false, transition = GlassMemberTransition.Materialize)
    fixture.toggle()
    fixture.advance(150)
    assertThat(fixture.owner().model.surfaces).hasSize(2)
    fixture.settle()
    assertThat(fixture.owner().model.surfaces.single().members).hasSize(2)
  }

  @Test
  fun memberLeavesContainerMidFade_resetsAlpha() = runComposeUiTest {
    val fixture = fixture(gap = 100, present = false, transition = GlassMemberTransition.Materialize)
    fixture.toggle()
    fixture.advance(100)
    assertThat(fixture.b.transitionAlpha).isLessThan(1f)
    val outer = fixture.owner()
    runOnIdle { fixture.nested.value = true }
    fixture.advance(16)
    assertThat(fixture.b.transitionAlpha).isEqualTo(1f)
    // B stays visible in its new container, so the old one draws no ghost of it.
    assertThat(outer.model.surfaces.single().members.single().identity).isEqualTo(fixture.aNode)
    fixture.settle()
    // Moving back into the drawn outer container shows B in place, without a transition.
    runOnIdle { fixture.nested.value = false }
    fixture.advance(16)
    assertThat(fixture.b()).prop(GlassMemberSnapshot::rect).isCloseTo(Rect(240f, 60f, 340f, 140f))
    assertThat(fixture.b.transitionAlpha).isEqualTo(1f)
    fixture.settle()
  }

  @Test
  fun membersAppearingTogether_materializeInsteadOfGrowingFromEachOther() = runComposeUiTest {
    val fixture = fixture()
    val owner = fixture.owner()
    val nodes = listOf(fixture.a, fixture.b).map { checkNotNull(it.memberBindingForTest?.node) }
    runOnIdle {
      // Both members become ready for their first snapshot in the same pass.
      nodes.forEach { owner.remove(it, leaving = false) }
      val materials = nodes.map { it.binding.material }
      nodes.forEach { it.binding.material = null }
      nodes.forEach { owner.add(it) }
      nodes.zip(materials).forEach { (node, material) -> node.binding.material = material }
      owner.memberChanged()
    }
    fixture.advance(16)
    val rects: Map<Any, Rect> = owner.model.surfaces.flatMap { it.members }.associate { it.identity to it.rect }
    // Neither member was visible yet, so neither can grow out of the other.
    assertThat(rects[nodes[0]]).isNotNull().isCloseTo(Rect(40f, 60f, 140f, 140f))
    assertThat(rects[nodes[1]]).isNotNull().isCloseTo(Rect(150f, 60f, 250f, 140f))
    assertThat(fixture.a.transitionAlpha).isLessThan(0.1f)
    assertThat(fixture.b.transitionAlpha).isLessThan(0.1f)
    fixture.settle()
  }

  @Test
  fun anchorCollapses_endsGhostWithoutCrashing() = runComposeUiTest {
    val fixture = fixture(gap = 100, transition = GlassMemberTransition.Materialize)
    fixture.toggle()
    fixture.advance(48)
    runOnIdle { fixture.aWidth.value = 0 }
    fixture.advance(16)
    assertThat(fixture.owner().model.surfaces).isEmpty()
    fixture.settle()
  }

  @Test
  fun undrawableMember_isNotBorrowedAsAnchor() = runComposeUiTest {
    // A is nearer B than C, but cannot draw when B leaves (its material is unpublished, as during
    // a binding swap), so only its last snapshot remains; C must carry the ghost.
    val fixture = fixture(gap = 100, transition = GlassMemberTransition.Materialize, cOffset = 40 to 150, cInitially = true)
    val owner = fixture.owner()
    val aBinding = checkNotNull(fixture.aNode).binding
    val bNode = checkNotNull(fixture.b.memberBindingForTest?.node)
    lateinit var ghost: GlassMemberGhost
    runOnIdle {
      val material = aBinding.material
      aBinding.material = null
      owner.remove(bNode)
      ghost = owner.model.surfaces.flatMap { it.members }.single { it.identity is GlassMemberGhost }.identity as GlassMemberGhost
      aBinding.material = material
    }
    assertThat(ghost.anchor).isEqualTo(fixture.c.memberBindingForTest?.node)
    fixture.settle()
  }

  @Test
  fun collapsedMember_isNotBorrowedAsAnchor() = runComposeUiTest {
    // A is scaled to zero height, so its bounds are non-null but empty (at the origin). They are
    // nearer B at (0, 60) than C is, but A cannot carry B's ghost; C must.
    val fixture = fixture(gap = -140, transition = GlassMemberTransition.Materialize, cOffset = 260 to 160, cInitially = true)
    runOnIdle { fixture.aScaleY.value = 0f }
    fixture.advance(16)
    fixture.toggle()
    fixture.advance(16)
    val ghost = fixture.owner().model.surfaces.flatMap { it.members }.single { it.identity is GlassMemberGhost }.identity as GlassMemberGhost
    assertThat(ghost.anchor).isEqualTo(fixture.c.memberBindingForTest?.node)
    fixture.settle()
  }

  @Test
  fun anchorChangesInput_endsGhost() = runComposeUiTest {
    val fixture = fixture(gap = 100, transition = GlassMemberTransition.Materialize)
    fixture.toggle()
    fixture.advance(48)
    // The departing glass must never draw the anchor's new input.
    runOnIdle { fixture.aState.value = HazeState() }
    fixture.advance(16)
    assertThat(fixture.owner().model.surfaces.flatMap { it.members }.map { it.identity }).containsExactly(fixture.aNode)
    fixture.settle()
  }

  @Test
  fun materializeMembersFadingTogether_neverShareAField() = runComposeUiTest {
    val fixture = fixture(present = false, withA = false, transition = GlassMemberTransition.Materialize)
    runOnIdle {
      fixture.aPresent.value = true
      fixture.present.value = true
    }
    var fadingFrames = 0
    repeat(18) {
      fixture.advance(16)
      val owner = checkNotNull(fixture.b.memberBindingForTest?.node?.container)
      if (fixture.a.transitionAlpha < 1f && fixture.b.transitionAlpha < 1f) {
        fadingFrames++
        assertThat(owner.model.surfaces.map { it.members.size }).containsExactly(1, 1)
      }
    }
    assertThat(fadingFrames).isGreaterThan(0)
    fixture.settle()
    assertThat(checkNotNull(fixture.b.memberBindingForTest?.node?.container).model.surfaces.single().members).hasSize(2)
  }

  @Test
  fun neighbourStillGrowing_isChosenAtItsCurrentShape() = runComposeUiTest {
    // C sits 10px right of B's final rect but beyond the spacing of B's current, growing shape.
    val fixture = fixture(present = false, cOffset = 260 to 60)
    fixture.toggle()
    fixture.advance(96)
    runOnIdle { fixture.cPresent.value = true }
    fixture.advance(16)
    val c = fixture.owner().model.surfaces.flatMap { it.members }.single { it.identity === fixture.c.memberBindingForTest?.node }
    assertThat(c.rect).isCloseTo(Rect(260f, 60f, 360f, 140f))
    assertThat(fixture.c.transitionAlpha).isLessThan(0.1f)
    fixture.settle()
  }

  @Test
  fun neighbourStillGrowing_morphStartsFromItsCurrentShape() = runComposeUiTest {
    // C sits below both B's current and final rects, and beyond the spacing of A.
    val fixture = fixture(present = false, cOffset = 200 to 150)
    fixture.toggle()
    fixture.advance(240)
    val bLeft = fixture.b().rect.left
    runOnIdle { fixture.cPresent.value = true }
    fixture.advance(16)
    val c = fixture.owner().model.surfaces.flatMap { it.members }.single { it.identity === fixture.c.memberBindingForTest?.node }
    // C starts on B's drawn shape, which is still left of B's final 150px edge.
    assertThat(c.rect.left).isLessThan(148f)
    assertThat(c.rect.left).isGreaterThan(bLeft)
    fixture.settle()
  }

  @Test
  fun ghostRetractingIntoGrowingNeighbour_followsItsDrawnShape() = runComposeUiTest {
    // B grows up out of C, whose final rect is beyond A's spacing; A then leaves while B's drawn
    // shape still overlaps it, so A retracts into B rather than into C.
    val fixture = fixture(gap = 50, present = false, cOffset = 90 to 150, cInitially = true)
    fixture.toggle()
    fixture.advance(96)
    val aNode = checkNotNull(fixture.aNode)
    runOnIdle { fixture.aPresent.value = false }
    fixture.advance(150)
    val ghost = fixture.owner().model.surfaces.flatMap { it.members }.single { it.identity is GlassMemberGhost }
    val identity = ghost.identity as GlassMemberGhost
    assertThat(identity.last.identity).isEqualTo(aNode)
    assertThat(identity.neighbour).isEqualTo(fixture.b.memberBindingForTest?.node)
    // B is still below and left of its final rect (190, 60). Retracting towards that rect keeps the
    // ghost's top at 60px and its left edge near 104px; following B's drawn shape moves both.
    assertThat(fixture.b().rect.top).isGreaterThan(62f)
    assertThat(ghost.rect.top).isGreaterThan(62f)
    assertThat(ghost.rect.left).isLessThan(100f)
    fixture.settle()
  }

  @Test
  fun materializeGhostsLeavingTogether_neverShareAField() = runComposeUiTest {
    val fixture = fixture(transition = GlassMemberTransition.Materialize, cOffset = 260 to 60, cTransition = GlassMemberTransition.Materialize, cInitially = true)
    assertThat(fixture.owner().model.surfaces.single().members).hasSize(3)
    runOnIdle {
      fixture.present.value = false
      fixture.cPresent.value = false
    }
    var fadingFrames = 0
    repeat(18) {
      fixture.advance(16)
      val ghosts = fixture.owner().model.surfaces.filter { cluster -> cluster.members.any { it.identity is GlassMemberGhost } }
      if (ghosts.isNotEmpty()) {
        fadingFrames++
        assertThat(ghosts.map { it.members.size }).containsExactly(1, 1)
      }
    }
    assertThat(fadingFrames).isGreaterThan(0)
    fixture.settle()
  }

  @Test
  fun growingMember_endsWhenNeighbourResolvesOtherSources() = assertGrowEnds { bSourced.value = true }

  @Test
  fun growingMember_endsWhenKeysDiverge() = assertGrowEnds { bExtra.value = GlassStyle { contrast(0.5f) } }

  @Test
  fun growingMember_endsWhenNoLongerTranslationOnly() = assertGrowEnds { bRotation.value = 10f }

  @Test
  fun growingMember_endsWhenSpacingNoLongerReachesNeighbour() = assertGrowEnds { spacing.value = 0 }

  @Test
  fun growingMember_endsWhenNeighbourLayoutMovesBeyondSpacing() = assertGrowEnds { aWidth.value = 40 }

  @Test
  fun retractingGhost_endsWhenSpacingNoLongerReachesNeighbour() = runComposeUiTest {
    val fixture = fixture()
    fixture.toggle()
    assertThat(fixture.b().identity).isInstanceOf<GlassMemberGhost>()
    runOnIdle { fixture.spacing.value = 0 }
    fixture.advance(16)
    assertThat(fixture.owner().model.surfaces.flatMap { it.members }.map { it.identity }).containsExactly(fixture.aNode)
    fixture.settle()
  }

  @Test
  fun growingMember_endsWhenNeighbourStopsBeingLive() = runComposeUiTest {
    val fixture = fixture(present = false)
    fixture.toggle()
    fixture.advance(96)
    runOnIdle { fixture.aWidth.value = 0 }
    fixture.advance(16)
    runOnIdle { fixture.aWidth.value = 100 }
    fixture.advance(16)
    // The grow ended while A was collapsed, so it does not resume from A afterwards.
    assertThat(fixture.b()).prop(GlassMemberSnapshot::rect).isCloseTo(Rect(150f, 60f, 250f, 140f))
    fixture.settle()
  }

  @Test
  fun retractingGhost_endsWhenNeighbourKeyChanges() = runComposeUiTest {
    val fixture = fixture()
    fixture.toggle()
    assertThat(fixture.b().identity).isInstanceOf<GlassMemberGhost>()
    runOnIdle { fixture.aExtra.value = GlassStyle { contrast(0.5f) } }
    fixture.advance(16)
    assertThat(fixture.owner().model.surfaces.flatMap { it.members }.map { it.identity }).containsExactly(fixture.aNode)
    fixture.settle()
  }

  @Test
  fun contentMembers_leaveWithoutGhosts() {
    // A Content ghost could only draw another member's content, so neither transition makes one.
    for ((gap, transition) in listOf(10 to GlassMemberTransition.MatchedGeometry, 100 to GlassMemberTransition.Materialize)) {
      runComposeUiTest {
        val a = GlassRuntimeEffect()
        val b = GlassRuntimeEffect()
        val present = mutableStateOf(true)
        setContent {
          Box(Modifier.size(360.dp, 240.dp).background(Color.White).hazeGlassContainer(40.dp, transitionSpec = tween(300, easing = LinearEasing), reducedMotionPolicy = GlassReducedMotionPolicy.Full)) {
            Box(Modifier.offset(40.dp, 60.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { a }, HazeInput.Content, containerTestStyle(Color.Red), HazePerformanceMode.Quality, true, null).background(Color.Yellow))
            if (present.value) {
              Box(
                Modifier.offset((140 + gap).dp, 60.dp).size(100.dp, 80.dp).hazeGlass(
                  HazeEffectFactory { b },
                  HazeInput.Content,
                  containerTestStyle(Color.Blue),
                  HazePerformanceMode.Quality,
                  true,
                  null,
                  memberTransition = transition,
                ).background(Color.Cyan),
              )
            }
          }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val owner = checkNotNull(a.memberBindingForTest?.node?.container)
        runOnIdle { present.value = false }
        mainClock.advanceTimeBy(16)
        waitForIdle()
        assertThat(owner.model.surfaces.flatMap { it.members }.map { it.identity }, "$transition").containsExactly(a.memberBindingForTest?.node)
      }
    }
  }

  @Test
  fun retractingGhost_endsWhenNeighbourStopsBeingTranslationOnly() = runComposeUiTest {
    val fixture = fixture()
    fixture.toggle()
    assertThat(fixture.b().identity).isInstanceOf<GlassMemberGhost>()
    runOnIdle { fixture.aRotation.value = 10f }
    fixture.advance(16)
    assertThat(fixture.owner().model.surfaces.flatMap { it.members }.map { it.identity }).containsExactly(fixture.aNode)
    fixture.settle()
  }

  @Test
  fun growingMember_endsWhenItsRunIsSplit() = runComposeUiTest {
    // C grows out of A through a run [A, B, C] that B bridges. B then fades out, and its ghost,
    // which never merges, splits the run.
    val fixture = fixture(transition = GlassMemberTransition.Materialize, cOffset = 40 to 150)
    runOnIdle { fixture.cPresent.value = true }
    fixture.advance(16)
    fixture.advance(96)
    val c = { fixture.owner().model.surfaces.flatMap { it.members }.single { it.identity === fixture.c.memberBindingForTest?.node } }
    assertThat(c().link).isEqualTo(fixture.aNode)
    runOnIdle { fixture.present.value = false }
    fixture.advance(16)
    val cluster = fixture.owner().model.surfaces.single { cluster -> cluster.members.any { it.identity === fixture.c.memberBindingForTest?.node } }
    // C is never drawn alone at its morphed shape: its grow ended and it is at its own rect.
    assertThat(c()).all {
      prop(GlassMemberSnapshot::link).isNull()
      prop(GlassMemberSnapshot::layoutRect).isCloseTo(Rect(40f, 150f, 140f, 230f))
    }
    assertThat(cluster.members.none { it.link != null && cluster.members.none { other -> other.identity === it.link } }).isTrue()
    fixture.settle()
  }

  @Test
  fun matchedGeometry_attachToPressedNeighbour_startsOnItsDrawnOutline() = runComposeUiTest {
    val fixture = fixture(present = false, bStyle = GlassStyle { shape(RoundedCornerShape(20.dp)) })
    fixture.pressA()
    val a = fixture.a()
    assertThat(a.rect).isCloseTo(Rect(43f, 62.4f, 137f, 137.6f), tolerance = 0.1f)
    fixture.toggle()
    // B's first frame is A as drawn: scaled by 0.94 with its corners scaled to match.
    assertThat(fixture.b()).all {
      prop(GlassMemberSnapshot::link).isEqualTo(fixture.aNode)
      prop(GlassMemberSnapshot::rect).isCloseTo(a.rect, tolerance = 0.1f)
      prop(GlassMemberSnapshot::material).prop(ResolvedGlassStyle::cornerRadii).prop(CornerRadii::topLeft).isCloseTo(18.8f, 0.1f)
    }
    fixture.settle()
  }

  @Test
  fun matchedGeometry_detachFromPressedNeighbour_retractsTowardsItsDrawnOutline() = runComposeUiTest {
    val fixture = fixture(bStyle = GlassStyle { shape(RoundedCornerShape(20.dp)) })
    fixture.pressA()
    val a = fixture.a().rect
    fixture.toggle()
    fixture.advance(240)
    val ghost = fixture.b()
    val progress = (ghost.identity as GlassMemberGhost).progress.value
    assertThat(ghost.rect).isCloseTo(lerp(a, Rect(150f, 60f, 250f, 140f), progress), tolerance = 0.1f)
    fixture.settle()
  }

  @Test
  fun matchedGeometry_detachFromPressedMember_retractsFromItsDrawnOutline() {
    val departingRects = mutableMapOf<GlassTransformPivot, Rect>()
    for (pivot in listOf(GlassTransformPivot.Center, GlassTransformPivot.Pointer)) {
      runComposeUiTest {
        val fixture = fixture(bStyle = GlassStyle { shape(RoundedCornerShape(20.dp)) }, bPivot = pivot)
        fixture.pressA()
        fixture.pressB(Offset(20f, 15f))
        val departing = fixture.b().drawnShape()
        assertThat(departing.rect).isNotEqualTo(fixture.b().layoutRect)
        assertThat(departing.cornerRadii.topLeft).isCloseTo(18.8f, 0.1f)
        fixture.removeB()
        val firstGhost = fixture.b()
        val ghost = firstGhost.identity as GlassMemberGhost
        departingRects[pivot] = departing.rect
        assertThat(ghost.progress.value).isEqualTo(1f)
        assertThat(ghost.last.drawnShape().rect).isCloseTo(departing.rect, 0.1f)
        assertThat(firstGhost.rect).isCloseTo(departing.rect, 0.1f)
        assertThat(firstGhost.material.cornerRadii.topLeft).isCloseTo(departing.cornerRadii.topLeft, 0.1f)

        fixture.advance(80)
        val currentGhost = fixture.b()
        val progress = (currentGhost.identity as GlassMemberGhost).progress.value
        val expected = lerpGlassMergedMember(fixture.a().drawnShape(), departing, progress)
        assertThat(currentGhost.rect).isCloseTo(expected.rect, 0.1f)
        assertThat(currentGhost.material.cornerRadii.topLeft).isCloseTo(expected.cornerRadii.topLeft, 0.1f)
        fixture.settle()
      }
    }
    assertThat(departingRects[GlassTransformPivot.Center]).isNotEqualTo(departingRects[GlassTransformPivot.Pointer])
  }

  @Test
  fun materialize_detachFromPressedMember_fadesItsDrawnOutlineInPlace() {
    val departingRects = mutableMapOf<GlassTransformPivot, Rect>()
    for (pivot in listOf(GlassTransformPivot.Center, GlassTransformPivot.Pointer)) {
      runComposeUiTest {
        val fixture = fixture(
          gap = 100,
          transition = GlassMemberTransition.Materialize,
          bStyle = GlassStyle { shape(RoundedCornerShape(20.dp)) },
          bPivot = pivot,
        )
        fixture.pressB(Offset(20f, 15f))
        val departing = fixture.b().drawnShape()
        assertThat(departing.rect).isNotEqualTo(fixture.b().layoutRect)
        assertThat(departing.cornerRadii.topLeft).isCloseTo(18.8f, 0.1f)
        fixture.removeB()
        val ghost = fixture.b().identity as GlassMemberGhost
        departingRects[pivot] = departing.rect
        assertThat(ghost.progress.value).isEqualTo(1f)
        assertThat(ghost.last.drawnShape().rect).isCloseTo(departing.rect, 0.1f)
        assertFadingGhostMatches(fixture.b(), departing)
        fixture.capture()
        val ghostField = checkNotNull(fixture.owner().renderer.runtimeSurfaces.single().mergedField)
        assertThat(ghostField.members.single().cornerRadii.topLeft).isCloseTo(departing.cornerRadii.topLeft, 0.1f)
        fixture.advance(80)
        assertFadingGhostMatches(fixture.b(), departing)
        assertThat(fixture.b().material.alpha).isLessThan(1f)
        fixture.settle()
      }
    }
    assertThat(departingRects[GlassTransformPivot.Center]).isNotEqualTo(departingRects[GlassTransformPivot.Pointer])
  }

  @Test
  fun materialize_detachFromCollapsedDrawnRect_skipsInvalidGhost() = runComposeUiTest {
    val fixture = fixture(transition = GlassMemberTransition.Materialize, bPivot = GlassTransformPivot.Center)
    val bNode = fixture.b().identity
    fixture.pressB(Offset(20f, 15f), scale = 1e-8f)
    val layoutBounds = checkNotNull(fixture.b.memberBindingForTest?.surface?.boundsInOwner())
    assertThat(layoutBounds).isEqualTo(Rect(150f, 60f, 250f, 140f))
    assertThat(fixture.owner().model.surfaces.flatMap { it.members }.none { it.identity === bNode }).isTrue()
    fixture.test.runOnIdle { fixture.present.value = false }
    fixture.advance(16)

    val remaining = fixture.owner().model.surfaces.flatMap { it.members }
      .filter { snapshot -> snapshot.identity === bNode || (snapshot.identity as? GlassMemberGhost)?.last?.identity === bNode }
      .map { snapshot -> snapshot.rect to snapshot.layoutRect }
    assertThat(remaining).isEmpty()
    fixture.settle()
  }

  private fun assertFadingGhostMatches(ghost: GlassMemberSnapshot, expected: GlassMergedMember) {
    assertThat(ghost.rect).isCloseTo(expected.rect, 0.1f)
    assertThat(ghost.material.cornerRadii.topLeft).isCloseTo(expected.cornerRadii.topLeft, 0.1f)
    assertThat(ghost.fading).isTrue()
  }

  @Test
  fun matchedGeometry_attachIntoSourceMixedCluster_materializes() = runComposeUiTest {
    // B resolves a zIndex 1 source as well, so A and B already form a cluster that mixes sources.
    val fixture = fixture(cOffset = 40 to 150)
    runOnIdle { fixture.bSourced.value = true }
    fixture.advance(16)
    assertThat(fixture.b().sourceSelection).isNotEqualTo(fixture.a().sourceSelection)
    runOnIdle { fixture.cPresent.value = true }
    fixture.advance(16)
    val c = fixture.owner().model.surfaces.flatMap { it.members }.single { it.identity === fixture.c.memberBindingForTest?.node }
    // Growing out of A would join that mixed cluster, which draws each member separately.
    assertThat(c).all {
      prop(GlassMemberSnapshot::link).isNull()
      prop(GlassMemberSnapshot::rect).isCloseTo(Rect(40f, 150f, 140f, 230f))
    }
    assertThat(fixture.c.transitionAlpha).isLessThan(0.1f)
    fixture.settle()
  }

  @Test
  fun matchedGeometry_detachFromSourceMixedCluster_fadesThroughAnchor() = runComposeUiTest {
    val fixture = fixture(cOffset = 40 to 150, cInitially = true)
    runOnIdle { fixture.bSourced.value = true }
    fixture.advance(16)
    runOnIdle { fixture.cPresent.value = false }
    fixture.advance(16)
    val ghost = fixture.owner().model.surfaces.flatMap { it.members }.single { it.identity is GlassMemberGhost }.identity as GlassMemberGhost
    assertThat(ghost.neighbour).isNull()
    assertThat(ghost.anchor).isEqualTo(fixture.aNode)
    fixture.settle()
  }

  @Test
  fun undrawableRegistration_doesNotBlockRetract() = runComposeUiTest {
    // B's key differs and sits between A and C in registration order. While B cannot draw (its
    // material is unpublished, as during a binding swap), its stale snapshot must not split A and C.
    val fixture = fixture(bStyle = GlassStyle { contrast(0.5f) }, cOffset = 40 to 150, cInitially = true)
    val owner = fixture.owner()
    val bBinding = checkNotNull(fixture.b.memberBindingForTest?.node).binding
    val cNode = checkNotNull(fixture.c.memberBindingForTest?.node)
    lateinit var ghost: GlassMemberGhost
    runOnIdle {
      val material = bBinding.material
      bBinding.material = null
      owner.remove(cNode)
      ghost = owner.model.surfaces.flatMap { it.members }.single { it.identity is GlassMemberGhost }.identity as GlassMemberGhost
      bBinding.material = material
    }
    assertThat(ghost.neighbour).isEqualTo(fixture.aNode)
    fixture.settle()
  }

  @Test
  fun growOutOfOvershootingNeighbour_doesNotEndWhileLayoutStaysWithinSpacing() = runComposeUiTest {
    // C's layout is 35px from B's, but B overshoots its layout while it grows out of A.
    val fixture = fixture(present = false, spec = spring(dampingRatio = 0.2f, stiffness = 200f), cOffset = 285 to 60, width = 400)
    val c = { fixture.owner().model.surfaces.flatMap { it.members }.single { it.identity === fixture.c.memberBindingForTest?.node } }
    fixture.toggle()
    // Near B's overshoot peak, so C chooses B at its drawn shape.
    repeat(13) { fixture.advance(16) }
    assertThat(fixture.b().rect.right).isGreaterThan(250f)
    runOnIdle { fixture.cPresent.value = true }
    fixture.advance(16)
    val bNode = fixture.b.memberBindingForTest?.node
    assertThat(c().link).isEqualTo(bNode)
    // B then swings back, away from C, while its layout stays within the spacing.
    repeat(20) {
      fixture.advance(16)
      assertThat(c().link).isEqualTo(bNode)
    }
    fixture.settle(3000)
  }

  @Test
  fun growSplitAtStartByMaterializingMember_fadesIn() = runComposeUiTest {
    val fixture = fixture(cOffset = 40 to 150, cTransition = GlassMemberTransition.Materialize, cInitially = true)
    val owner = fixture.owner()
    val nodes = listOf(fixture.c, fixture.b).map { checkNotNull(it.memberBindingForTest?.node) }
    runOnIdle {
      // C (fading) and B (growing out of A) start in the same pass, registered as A, C, B.
      nodes.forEach { owner.remove(it, leaving = false) }
      val materials = nodes.map { it.binding.material }
      nodes.forEach { it.binding.material = null }
      nodes.forEach { owner.add(it) }
      nodes.zip(materials).forEach { (node, material) -> node.binding.material = material }
      owner.memberChanged()
      // The pass that ends the grow already draws B transparent, before its fade's first frame.
      assertThat(fixture.b()).prop(GlassMemberSnapshot::fading).isTrue()
      assertThat(fixture.b.transitionAlpha).isEqualTo(0f)
    }
    fixture.advance(16)
    // C's fade splits B from A before B draws a morphed frame, so B fades in place.
    assertThat(fixture.b()).all {
      prop(GlassMemberSnapshot::link).isNull()
      prop(GlassMemberSnapshot::fading).isTrue()
      prop(GlassMemberSnapshot::rect).isCloseTo(Rect(150f, 60f, 250f, 140f))
    }
    assertThat(fixture.b.transitionAlpha).isLessThan(0.1f)
    fixture.settle()
    assertThat(fixture.b.transitionAlpha).isEqualTo(1f)
  }

  /** Starts B growing out of A, breaks one start condition, and expects B to snap to its own rect. */
  private fun assertGrowEnds(change: Fixture.() -> Unit) = runComposeUiTest {
    val fixture = fixture(present = false)
    fixture.toggle()
    fixture.advance(96)
    assertThat(fixture.b().link).isEqualTo(fixture.aNode)
    runOnIdle { fixture.change() }
    fixture.advance(16)
    val layout = checkNotNull(fixture.b.memberBindingForTest?.surface?.boundsInOwner())
    assertThat(fixture.b()).all {
      prop(GlassMemberSnapshot::link).isNull()
      prop(GlassMemberSnapshot::layoutRect).isCloseTo(layout)
    }
    fixture.settle()
  }

  @Test
  fun lastMemberLeaves_disappearsImmediately() = runComposeUiTest {
    val fixture = fixture(gap = 100, transition = GlassMemberTransition.Materialize, withA = false)
    fixture.toggle()
    assertThat(fixture.owner.model.surfaces).isEmpty()
    assertThat(fixture.capture()[300, 100]).isEqualTo(Color.White)
    fixture.settle()
  }

  /** The largest right edge of an attaching B, sampled every frame. */
  private fun maxRight(policy: GlassReducedMotionPolicy, spec: FiniteAnimationSpec<Float>): Float {
    var max = Float.NEGATIVE_INFINITY
    runComposeUiTest {
      val fixture = fixture(present = false, policy = policy, spec = spec)
      fixture.toggle()
      repeat(30) {
        fixture.advance(16)
        max = maxOf(max, fixture.b().rect.right)
      }
      // Detaching the container cancels the remaining spring instead of running it out.
      runOnIdle { fixture.enabled.value = false }
      fixture.advance(16)
    }
    return max
  }

  private class Fixture(
    val test: ComposeUiTest,
    val a: GlassRuntimeEffect,
    val b: GlassRuntimeEffect,
    val present: MutableState<Boolean>,
    val aPresent: MutableState<Boolean>,
    val enabled: MutableState<Boolean>,
    val nested: MutableState<Boolean>,
    val aWidth: MutableState<Int>,
    val aState: MutableState<HazeState>,
    val c: GlassRuntimeEffect,
    val cPresent: MutableState<Boolean>,
  ) {
    /** Extra style applied to B, its rotation, A's rotation, and whether B sits under a zIndex 1 source. */
    val bExtra = mutableStateOf(GlassStyle { })
    val bRotation = mutableStateOf(0f)
    val aRotation = mutableStateOf(0f)
    val aScaleY = mutableStateOf(1f)
    val aExtra = mutableStateOf(GlassStyle { })
    val bSourced = mutableStateOf(false)
    val spacing = mutableStateOf(40)

    val aSource = MutableInteractionSource()
    val bSource = MutableInteractionSource()

    val aNode: GlassMemberNode? by lazy { a.memberBindingForTest?.node }
    lateinit var owner: GlassContainerNode

    fun owner(): GlassContainerNode = (aNode?.container ?: b.memberBindingForTest?.node?.container)?.also { owner = it } ?: owner

    private var bNode: GlassMemberNode? = null

    fun a(): GlassMemberSnapshot = owner().model.surfaces.flatMap { it.members }.single { it.identity === aNode }

    /** Gives A a 20dp shape that snaps to 0.94 when pressed, then presses it. */
    fun pressA() {
      test.runOnIdle {
        aExtra.value = GlassStyle {
          shape(RoundedCornerShape(20.dp))
          pressed { animate(snap(), snap()) { scale(0.94f) } }
        }
      }
      advance(16)
      runBlocking { aSource.emit(PressInteraction.Press(Offset(50f, 40f))) }
      advance(16)
    }

    /** Gives B a 20dp shape that snaps to 0.94 when pressed, then presses at [position]. */
    fun pressB(position: Offset, scale: Float = 0.94f) {
      test.runOnIdle {
        bExtra.value = GlassStyle {
          shape(RoundedCornerShape(20.dp))
          interactionPositionAnimationSpec(snap())
          pressed { animate(snap(), snap()) { scale(scale) } }
        }
      }
      advance(16)
      runBlocking { bSource.emit(PressInteraction.Press(position)) }
      advance(16)
    }

    /** B's snapshot: the live member or its ghost. */
    fun b(): GlassMemberSnapshot {
      bNode = b.memberBindingForTest?.node ?: bNode
      return owner().model.surfaces.flatMap { it.members }.single { it.identity === bNode || (it.identity as? GlassMemberGhost)?.last?.identity === bNode }
    }

    fun capture() = test.onNodeWithTag("root").captureToImage().toPixelMap()

    /** Toggles B, then draws the transition's first frame. */
    fun toggle() {
      owner()
      if (present.value) b()
      test.runOnIdle { present.value = !present.value }
      advance(16)
    }

    fun removeB() {
      owner()
      b()
      test.runOnIdle { present.value = false }
      advance(16)
    }

    fun advance(millis: Long) {
      test.mainClock.advanceTimeBy(millis)
      test.waitForIdle()
    }

    /** Ends every transition on the virtual clock, so no frame-awaiting coroutine outlives the test. */
    fun settle(millis: Long = 320) = advance(millis)
  }

  private fun ComposeUiTest.fixture(
    gap: Int = 10,
    present: Boolean = true,
    transition: GlassMemberTransition = GlassMemberTransition.MatchedGeometry,
    spec: FiniteAnimationSpec<Float> = tween(300, easing = LinearEasing),
    policy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.Full,
    bSize: Size = Size(100f, 80f),
    bTint: Color = Color.Blue,
    bAccessibility: GlassAccessibilitySettings = GlassAccessibilitySettings(),
    bStyle: GlassStyle = GlassStyle { },
    bPivot: GlassTransformPivot = GlassTransformPivot.Pointer,
    withA: Boolean = true,
    unavailable: Boolean = false,
    cOffset: Pair<Int, Int>? = null,
    cTransition: GlassMemberTransition = GlassMemberTransition.MatchedGeometry,
    cInitially: Boolean = false,
    width: Int = 360,
  ): Fixture {
    val c = GlassRuntimeEffect()
    val cPresent = mutableStateOf(cInitially)
    val a = GlassRuntimeEffect()
    val b = GlassRuntimeEffect()
    if (unavailable) {
      listOf(a, b).forEach { effect ->
        effect.runtimeEffectFactory = GlassRuntimeEffectFactory { throw RuntimeShaderRenderEffectException(IllegalStateException("test unavailable runtime")) }
      }
    }
    val bPresent = mutableStateOf(present)
    val aPresent = mutableStateOf(withA)
    val enabled = mutableStateOf(true)
    val nested = mutableStateOf(false)
    val state = HazeState()
    val aWidth = mutableStateOf(100)
    val aState = mutableStateOf(state)
    val fixture = Fixture(this, a, b, bPresent, aPresent, enabled, nested, aWidth, aState, c, cPresent)
    setContent {
      Box(Modifier.size(width.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(width.dp, 240.dp).hazeSource(state).background(Color.White))
        Box(Modifier.size(width.dp, 240.dp).then(if (enabled.value) Modifier.hazeGlassContainer(fixture.spacing.value.dp, transitionSpec = spec, reducedMotionPolicy = policy) else Modifier)) {
          if (aPresent.value) {
            Box(
              Modifier.offset(40.dp, 60.dp).size(aWidth.value.dp, 80.dp).graphicsLayer {
                rotationZ = fixture.aRotation.value
                scaleY = fixture.aScaleY.value
              }.hazeGlass(HazeEffectFactory { a }, HazeInput.Sources(aState.value), containerTestStyle(Color.Red).then(fixture.aExtra.value), HazePerformanceMode.Quality, true, fixture.aSource),
            )
          }
          Box(
            Modifier.size(width.dp, 240.dp)
              .then(if (fixture.bSourced.value) Modifier.hazeSource(state, zIndex = 1f) else Modifier)
              .then(if (nested.value) Modifier.hazeGlassContainer(40.dp) else Modifier),
          ) {
            if (bPresent.value) {
              CompositionLocalProvider(LocalGlassAccessibilitySettings provides bAccessibility) {
                Box(
                  Modifier.offset((140 + gap).dp, 60.dp).size(bSize.width.dp, bSize.height.dp).graphicsLayer { rotationZ = fixture.bRotation.value }.hazeGlass(
                    HazeEffectFactory { b },
                    HazeInput.Sources(state),
                    containerTestStyle(bTint).then(bStyle).then(fixture.bExtra.value),
                    HazePerformanceMode.Quality,
                    true,
                    fixture.bSource,
                    interactionTransformPivot = bPivot,
                    memberTransition = transition,
                  ),
                ) {
                  Box(Modifier.size(10.dp).background(Color.Green))
                }
              }
            }
          }
          if (cOffset != null && cPresent.value) {
            Box(
              Modifier.offset(cOffset.first.dp, cOffset.second.dp).size(100.dp, 80.dp).hazeGlass(
                HazeEffectFactory { c },
                HazeInput.Sources(state),
                containerTestStyle(Color.Blue),
                HazePerformanceMode.Quality,
                true,
                null,
                memberTransition = cTransition,
              ),
            )
          }
        }
      }
    }
    waitForIdle()
    mainClock.autoAdvance = false
    fixture.aNode
    return fixture
  }

  private object NoMotion : MotionDurationScale {
    override val scaleFactor: Float = 0f
  }
}

private fun Assert<Rect>.isCloseTo(expected: Rect, tolerance: Float = 0.01f) = all {
  prop(Rect::left).isCloseTo(expected.left, tolerance)
  prop(Rect::top).isCloseTo(expected.top, tolerance)
  prop(Rect::right).isCloseTo(expected.right, tolerance)
  prop(Rect::bottom).isCloseTo(expected.bottom, tolerance)
}

private fun Assert<Color>.isCloseTo(expected: Color, tolerance: Float) = transform("channel difference from $expected") { actual ->
  maxOf(abs(actual.red - expected.red), abs(actual.green - expected.green), abs(actual.blue - expected.blue))
}.isLessThanOrEqualTo(tolerance)
