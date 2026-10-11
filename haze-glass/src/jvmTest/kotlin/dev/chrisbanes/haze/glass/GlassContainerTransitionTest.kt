// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNull
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

class GlassContainerTransitionTest : ContextTest() {
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
    runOnIdle { fixture.nested.value = true }
    fixture.advance(16)
    assertThat(fixture.b.transitionAlpha).isEqualTo(1f)
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
  ) {
    val aNode: GlassMemberNode? = a.memberBindingForTest?.node
    lateinit var owner: GlassContainerNode

    fun owner(): GlassContainerNode = (aNode?.container ?: b.memberBindingForTest?.node?.container)?.also { owner = it } ?: owner

    private var bNode: GlassMemberNode? = null

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
    bStyle: GlassStyle = GlassStyle { },
    withA: Boolean = true,
    unavailable: Boolean = false,
  ): Fixture {
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
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(state).background(Color.White))
        Box(Modifier.size(360.dp, 240.dp).then(if (enabled.value) Modifier.hazeGlassContainer(40.dp, spec, policy) else Modifier)) {
          if (aPresent.value) {
            Box(Modifier.offset(40.dp, 60.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { a }, HazeInput.Sources(state), containerTestStyle(Color.Red), HazePerformanceMode.Quality, true, null))
          }
          Box(Modifier.size(360.dp, 240.dp).then(if (nested.value) Modifier.hazeGlassContainer(40.dp) else Modifier)) {
            if (bPresent.value) {
              Box(
                Modifier.offset((140 + gap).dp, 60.dp).size(bSize.width.dp, bSize.height.dp).hazeGlass(
                  HazeEffectFactory { b },
                  HazeInput.Sources(state),
                  containerTestStyle(bTint).then(bStyle),
                  HazePerformanceMode.Quality,
                  true,
                  null,
                  memberTransition = transition,
                ),
              ) {
                Box(Modifier.size(10.dp).background(Color.Green))
              }
            }
          }
        }
      }
    }
    waitForIdle()
    mainClock.autoAdvance = false
    return Fixture(this, a, b, bPresent, aPresent, enabled, nested)
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
