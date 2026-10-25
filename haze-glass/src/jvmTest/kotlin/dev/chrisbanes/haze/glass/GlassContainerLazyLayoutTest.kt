// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.matchesPredicate
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TRANSITION_MILLIS = 150

/**
 * Characterises what a [hazeGlassContainer] does with the members of a lazy layout, to back the
 * guidance in `docs/effects/glass/containers.md` ("Lazy layouts") and the [GlassMemberTransition]
 * KDoc.
 *
 * A `LazyRow` of 80dp members, 8dp apart, scrolls 6px per 16ms frame on the test's manual clock.
 * The container is on the `LazyRow` itself. Items 0 to 4 compose before the first draw, so they
 * never animate; item 5 is the first item to scroll in, with a settled neighbour (item 4), and
 * item 0 is the first to scroll out. The tests read the container's drawn state (`model`), so a
 * member is "visible" when its glass rect overlaps the 360x80 viewport. They do not read pixels.
 *
 * Pixel values assume a density of 1, as in the desktop test environment, and a positive
 * `dispatchRawDelta` scrolls towards later items.
 *
 * The existing tests use 6px per frame because that rate keeps item 5's grow running for its whole
 * duration. The 8px regression test reaches the merge distance transiently during partial
 * placement. Material changes every three items keep the morph fixtures below the four-member
 * cluster cap. These tests characterise the first item only: later items depend on whether their
 * neighbour is still animating. The `withScrollState` tests scroll inside `LazyListState.scroll`,
 * as drags and flings do, because `dispatchRawDelta` does not report a scroll in progress.
 *
 * The container animates over [TRANSITION_MILLIS] milliseconds, about 10 frames. Each frame
 * draws the glass on the CPU, which takes a few hundred milliseconds in the desktop tests.
 * `runComposeUiTest` fails a body that takes more than 60 seconds of wall-clock time, so
 * [LazyFixture.settle] runs only as many frames as the animations need.
 *
 * `PrefetchScheduler` cannot be driven from `jvmTest`, so the prefetch test uses the API that lazy
 * prefetch is built on, [SubcomposeLayoutState.precompose] and `premeasure`. It shows the same
 * registration path, not the foundation scheduler's timing on a device.
 */
class GlassContainerLazyLayoutTest : ContextTest() {
  @Test
  fun lazyRowGroup_scrolledBack_drawsAsOneGroup() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.None, withScrollState = true, splitClusters = false, groupKey = { index -> "row".takeIf { index <= 2 } })
    // Items 0 and 1 leave, item 2 stays; scrolling back re-registers 0 and 1 after the ungrouped items.
    fixture.frames(3, scrollPerFrame = 130f / 3)
    fixture.frames(2)
    assertThat(fixture.node(0)?.isAttached ?: false).isFalse()
    fixture.frames(3, scrollPerFrame = -130f / 3)
    fixture.frames(2)
    val units = fixture.owner().model.surfaces.flatMap { it.groups }
    val group = units.single { unit -> unit.any { it.identity === fixture.node(0) } }
    assertThat(group.map { it.identity }).containsOnly(fixture.node(0), fixture.node(1), fixture.node(2))
  }

  /**
   * Pins the 2.1.0 limitation behind the `Modifier.clipToBounds()` guidance in
   * `docs/effects/glass/containers.md`: the container draws its members' glass on its own canvas,
   * so a viewport clip applied after it does not cut that glass. A fix to the draw path has to
   * update this test.
   */
  @Test
  fun insetLazyRow_withoutClipToBounds_glassDrawsPastListBounds() = runComposeUiTest {
    val image = insetLazyRow(clipToBounds = false)

    // Inside the list, glass is drawing over the half-scrolled items.
    assertThat(image[60, 120]).isEqualTo(Color.Red)
    assertThat(image[300, 120]).isEqualTo(Color.Red)
    // Outside the list, the glass of items scrolled half out is not clipped.
    assertThat(image[20, 120]).isEqualTo(Color.Red)
    assertThat(image[330, 120]).isEqualTo(Color.Red)
  }

  /** Pins the recipe documented in `docs/effects/glass/containers.md`: `clipToBounds()` before the container cuts its glass at the list. */
  @Test
  fun insetLazyRow_withClipToBoundsBeforeContainer_glassStaysInsideListBounds() = runComposeUiTest {
    val image = insetLazyRow(clipToBounds = true)

    // Glass still draws inside the list.
    assertThat(image[60, 120]).isEqualTo(Color.Red)
    assertThat(image[300, 120]).isEqualTo(Color.Red)
    // Nothing is drawn outside it.
    assertThat(image[20, 120]).isEqualTo(Color.White)
    assertThat(image[330, 120]).isEqualTo(Color.White)
  }

  @Test
  fun lazyItem_matchedGeometry_atClusterCapacity_materializes() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.MatchedGeometry, splitClusters = false)

    fixture.scroll(frames = 6)

    val entering = checkNotNull(fixture.member(5))
    assertThat(entering.link).isNull()
    assertThat(entering.fading).isTrue()
    assertThat(entering.material.alpha).isLessThan(1f)
    assertThat(entering.rect).isEqualTo(fixture.contentBounds(5))
    fixture.settle()
    fixture.assertAppearedWithoutAnimating(5)
  }

  @Test
  fun lazyItem_matchedGeometry_growsOutOfNeighbourWhileVisible() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.MatchedGeometry)

    fixture.scroll(frames = 6)

    // Item 5 has just scrolled in. Its glass starts on its neighbour, not on its content.
    val entering = checkNotNull(fixture.member(5))
    assertThat(entering.link).isNotNull()
    assertThat(entering.rect.overlaps(fixture.viewport)).isTrue()
    assertThat(entering.rect.left).isLessThan(fixture.contentBounds(5).left)

    // Item 4 composed before the first draw, so it never animated.
    val settled = checkNotNull(fixture.member(4))
    assertThat(settled.link).isNull()
    assertThat(settled.fading).isFalse()

    // It stays visible and lagging while item 0, which scrolled out, leaves a ghost.
    fixture.frames(3)
    assertThat(checkNotNull(fixture.member(5)).rect.overlaps(fixture.viewport)).isTrue()
    val ghosts = fixture.ghosts()
    assertThat(ghosts).isNotEmpty()
    assertThat(ghosts.any { it.rect.overlaps(fixture.viewport) }).isTrue()

    fixture.settle()
    assertThat(fixture.ghosts()).isEmpty()
    assertThat(checkNotNull(fixture.member(5)).rect).isEqualTo(fixture.contentBounds(5))
  }

  @Test
  fun lazyItem_matchedGeometry_keepsGrowingWhenScrollAndGapReachMergeDistance() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.MatchedGeometry)

    fixture.frames(2, scrollPerFrame = 8f)
    assertThat(checkNotNull(fixture.member(5)).link).isNotNull()

    // The 8px scroll and 8px item gap reach 16dp merge distance during partial placement only.
    fixture.frames(5, scrollPerFrame = 8f)
    val growing = checkNotNull(fixture.member(5))
    assertThat(growing.link).isNotNull()
    assertThat(growing.fading).isFalse()
    assertThat(growing.material.alpha).isEqualTo(1f)

    fixture.settle()
    assertThat(checkNotNull(fixture.member(5)).rect).isEqualTo(fixture.contentBounds(5))
  }

  @Test
  fun lazyItem_materialize_fadesWhileVisible() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.Materialize)

    fixture.scroll(frames = 6)
    fixture.frames(3)

    val entering = checkNotNull(fixture.member(5))
    assertThat(entering.fading).isTrue()
    assertThat(entering.material.alpha).isLessThan(1f)
    assertThat(entering.rect.overlaps(fixture.viewport)).isTrue()
    assertThat(fixture.ghosts()).isNotEmpty()

    fixture.settle()
    assertThat(checkNotNull(fixture.member(5)).material.alpha).isEqualTo(1f)
  }

  @Test
  fun lazyItem_none_appearsAndLeavesWithoutAnimating() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.None)

    fixture.scroll(frames = 6)
    fixture.frames(3)

    fixture.assertAppearedWithoutAnimating(5)
    assertThat(checkNotNull(fixture.member(5)).rect.overlaps(fixture.viewport)).isTrue()
    assertThat(fixture.ghosts()).isEmpty()
  }

  @Test
  fun lazyItem_withScrollState_matchedGeometry_appearsAndLeavesWithoutAnimating() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.MatchedGeometry, withScrollState = true)

    fixture.scrollInProgress(frames = 9, px = 6f)

    fixture.assertAppearedWithoutAnimating(5)
    assertThat(fixture.ghosts()).isEmpty()
  }

  @Test
  fun lazyItem_withScrollState_materialize_fastScrollDoesNotAnimate() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.Materialize, withScrollState = true)

    fixture.scrollInProgress(frames = 4, px = 40f)

    fixture.assertAppearedWithoutAnimating(5)
    assertThat(fixture.ghosts()).isEmpty()
  }

  @Test
  fun lazyItem_withScrollState_scrollToItemDoesNotAnimate() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.MatchedGeometry, withScrollState = true)

    fixture.inScroll(frames = 3) { scrollToItem(10) }

    fixture.assertAppearedWithoutAnimating(10)
    assertThat(fixture.ghosts()).isEmpty()
  }

  /** A ghost's rect is fixed in owner space, so a scroll that starts mid-fade must not leave it behind. */
  @Test
  fun lazyItem_withScrollState_scrollStartingMidFade_endsGhost() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.Materialize, withScrollState = true)
    fixture.frames(1)
    runOnIdle { fixture.keys.remove(2) }
    fixture.frames(1)
    assertThat(fixture.ghosts()).isNotEmpty()

    fixture.startScroll(count = 8, px = 6f)
    repeat(4) {
      fixture.frames(1)
      assertThat(fixture.ghosts()).isEmpty()
    }
    fixture.settle()
  }

  /** A state change is not a scroll, so an item inserted while the list is idle still animates. */
  @Test
  fun lazyItem_withScrollState_insertWhileIdle_animates() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.MatchedGeometry, withScrollState = true)

    runOnIdle { fixture.keys.add(2, 100) }
    fixture.frames(1)

    fixture.assertAnimating(100)
  }

  /** The scroll window closes when the scroll ends, so a later insertion still animates. */
  @Test
  fun lazyItem_withScrollState_insertAfterScrollEnds_animates() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.MatchedGeometry, withScrollState = true)
    fixture.scrollInProgress(frames = 3, px = 6f)
    fixture.settle()

    runOnIdle { fixture.keys.add(3, 100) }
    fixture.frames(1)

    fixture.assertAnimating(100)
  }

  /** Every member placed in a scrolling frame notifies the container, but the frame reclusters once. */
  @Test
  fun lazyContainer_scrollingMembers_reclusterOncePerFrame() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.MatchedGeometry, withScrollState = true)
    fixture.settle()
    val owner = fixture.owner()
    // Inside a scroll, as a drag is, so leaving items make no ghosts and no pass is forced early.
    fixture.startScroll(count = 16, px = 6f)
    // Item 0 has scrolled out and item 5 in by now; the next item changes at about frame 15, and
    // an item entering or leaving adds a pass for its own release or registration.
    fixture.frames(4)

    val perFrame = List(6) {
      val frameStart = owner.reclusterPasses
      fixture.frames(1)
      owner.reclusterPasses - frameStart
    }
    assertThat(perFrame).isEqualTo(List(6) { 1 })

    val visible = fixture.visibleItems()
    assertThat(visible.size).isGreaterThanOrEqualTo(5)
    assertThat(owner.model.surfaces.flatMap { it.members }.map { it.identity }.filterIsInstance<GlassMemberNode>().toSet())
      .isEqualTo(visible.mapNotNull { fixture.node(it) }.toSet())
  }

  @Test
  fun lazyContainer_idleFrame_doesNotRecluster() = runComposeUiTest {
    val fixture = lazyFixture(GlassMemberTransition.MatchedGeometry, withScrollState = true)
    fixture.settle()
    val owner = fixture.owner()
    val before = owner.reclusterPasses

    fixture.frames(1)

    assertThat(owner.reclusterPasses - before).isEqualTo(0)
  }

  /** Lazy prefetch precomposes and premeasures an item without placing it. */
  @Test
  fun prefetchedItem_registersButDoesNotAnimateUntilPlaced() = runComposeUiTest {
    val effects = mutableMapOf<Int, GlassRuntimeEffect>()
    val hazeState = HazeState()
    val subcompose = SubcomposeLayoutState()
    val placed = mutableStateOf(listOf(0, 1, 2, 3))
    val scrollX = mutableStateOf(0)

    fun content(index: Int): @Composable () -> Unit = {
      val effect = remember { effects.getOrPut(index) { GlassRuntimeEffect() } }
      Box(
        Modifier.size(80.dp).hazeGlass(
          HazeEffectFactory { effect },
          HazeInput.Sources(hazeState),
          lazyItemStyle(index),
          HazePerformanceMode.Quality,
          true,
          null,
        ),
      )
    }

    setContent {
      Box(Modifier.size(360.dp, 240.dp).background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(hazeState))
        SubcomposeLayout(
          subcompose,
          Modifier
            .offset(0.dp, 80.dp)
            .size(360.dp, 80.dp)
            .hazeGlassContainer(16.dp, transitionSpec = tween(TRANSITION_MILLIS, easing = LinearEasing), reducedMotionPolicy = GlassReducedMotionPolicy.Full),
        ) { constraints ->
          val fixed = Constraints.fixed(80.dp.roundToPx(), 80.dp.roundToPx())
          val items = placed.value.map { index -> index to subcompose(index, content(index)).map { it.measure(fixed) } }
          layout(constraints.maxWidth, constraints.maxHeight) {
            items.forEach { (index, placeables) -> placeables.forEach { it.place(index * 88 - scrollX.value, 0) } }
          }
        }
      }
    }
    waitForIdle()
    mainClock.autoAdvance = false

    // Pass content(4) straight to precompose: wrapping it makes the layout compose a second node.
    val handle = runOnIdle {
      subcompose.precompose(4, content(4)).also { it.premeasure(0, Constraints.fixed(80, 80)) }
    }
    repeat(3) {
      mainClock.advanceTimeBy(16)
      waitForIdle()
    }

    // The member registered with the container, but it has no bounds and draws nothing yet.
    val node = checkNotNull(effects.getValue(4).memberBindingForTest?.node)
    val container = checkNotNull(node.container)
    assertThat(node.binding.surface?.boundsInOwner()).isNull()
    assertThat(container.model.surfaces.flatMap { it.members }.any { it.identity === node }).isFalse()

    // Placed at the viewport edge, it animates from there, and the first frame is visible.
    runOnIdle {
      scrollX.value = 60
      placed.value = listOf(0, 1, 2, 3, 4)
    }
    repeat(3) {
      mainClock.advanceTimeBy(16)
      waitForIdle()
    }

    val entering = checkNotNull(container.model.surfaces.flatMap { it.members }.firstOrNull { it.identity === node })
    assertThat(entering.link).isNotNull()
    assertThat(entering.rect.overlaps(Rect(0f, 0f, 360f, 80f))).isTrue()

    mainClock.autoAdvance = true
    waitForIdle()
    runOnIdle { handle.dispose() }
  }

  private class LazyFixture(
    private val test: ComposeUiTest,
    private val state: LazyListState,
    private val effects: Map<Int, GlassRuntimeEffect>,
    val keys: MutableList<Int>,
    private val scope: CoroutineScope,
  ) {
    val viewport = Rect(0f, 0f, 360f, 80f)

    fun node(index: Int): GlassMemberNode? = effects[index]?.memberBindingForTest?.node

    fun owner(): GlassContainerNode = effects.values.firstNotNullOf { it.memberBindingForTest?.node?.container }

    private fun members(): List<GlassMemberSnapshot> = owner().model.surfaces.flatMap { it.members }

    /** Items whose layout bounds overlap the viewport. */
    fun visibleItems(): List<Int> = effects.keys.filter { index ->
      node(index)?.binding?.surface?.boundsInOwner()?.overlaps(viewport) == true
    }

    fun member(index: Int): GlassMemberSnapshot? = members().firstOrNull { it.identity === node(index) }

    fun ghosts(): List<GlassMemberSnapshot> = members().filter { it.identity is GlassMemberGhost }

    /** The member's layout bounds, which is where its content is drawn. */
    fun contentBounds(index: Int): Rect = checkNotNull(node(index)?.binding?.surface?.boundsInOwner())

    fun frames(count: Int, scrollPerFrame: Float = 0f) = repeat(count) {
      if (scrollPerFrame != 0f) test.runOnIdle { state.dispatchRawDelta(scrollPerFrame) }
      test.mainClock.advanceTimeBy(16)
      test.waitForIdle()
    }

    fun scroll(frames: Int) = frames(frames, scrollPerFrame = 6f)

    /** Scrolls [px] per frame inside one scroll, so the state reports a scroll in progress. */
    // The launched scroll starts on the next frame, so it needs one frame more than it scrolls.
    fun scrollInProgress(frames: Int, px: Float) = inScroll(frames + 1) {
      scroll {
        repeat(frames) {
          scrollBy(px)
          withFrameNanos {}
        }
      }
    }

    /** Launches a scroll of [count] frames at [px] each, then advances until the state reports it in progress. */
    fun startScroll(count: Int, px: Float) {
      test.runOnIdle {
        scope.launch {
          state.scroll {
            repeat(count) {
              scrollBy(px)
              withFrameNanos {}
            }
          }
        }
      }
      var waited = 0
      while (!test.runOnIdle { state.isScrollInProgress }) {
        check(waited++ < 3) { "The scroll did not start" }
        frames(1)
      }
    }

    /**
     * Runs [block] on the state, then advances [frames] frames and the two after it, so the frame
     * where the scroll stops is included. No member transitions on any of those frames.
     */
    fun inScroll(frames: Int, block: suspend LazyListState.() -> Unit) {
      test.runOnIdle { scope.launch { state.block() } }
      repeat(frames + 2) {
        frames(1)
        assertThat(members().filter { it.link != null || it.fading || it.identity is GlassMemberGhost }).isEmpty()
      }
    }

    /** The item's glass is at its content, opaque and unlinked: it did not animate in. */
    fun assertAppearedWithoutAnimating(index: Int) {
      val member = checkNotNull(member(index))
      assertThat(member.link).isNull()
      assertThat(member.fading).isFalse()
      assertThat(member.material.alpha).isEqualTo(1f)
      assertThat(member.rect).isEqualTo(contentBounds(index))
    }

    /** The item's glass is growing out of a neighbour or fading in. */
    fun assertAnimating(index: Int) {
      assertThat(checkNotNull(member(index))).matchesPredicate { it.link != null || it.fading }
    }

    /** Runs frames until every animation has finished. */
    fun settle() {
      test.mainClock.autoAdvance = true
      test.waitForIdle()
      test.mainClock.autoAdvance = false
    }
  }

  private fun ComposeUiTest.lazyFixture(
    transition: GlassMemberTransition,
    withScrollState: Boolean = false,
    splitClusters: Boolean = true,
    groupKey: (Int) -> Any? = { null },
  ): LazyFixture {
    val effects = mutableMapOf<Int, GlassRuntimeEffect>()
    val hazeState = HazeState()
    val state = LazyListState(firstVisibleItemScrollOffset = 70)
    val keys = mutableStateListOf<Int>().apply { addAll(0 until 40) }
    val spec = tween<Float>(TRANSITION_MILLIS, easing = LinearEasing)
    lateinit var scope: CoroutineScope
    setContent {
      scope = rememberCoroutineScope()
      Box(Modifier.size(360.dp, 240.dp).background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(hazeState))
        LazyRow(
          state = state,
          modifier = Modifier
            .offset(0.dp, 80.dp)
            .size(360.dp, 80.dp)
            .then(
              if (withScrollState) {
                Modifier.hazeGlassContainer(16.dp, state, spec, GlassReducedMotionPolicy.Full)
              } else {
                Modifier.hazeGlassContainer(16.dp, transitionSpec = spec, reducedMotionPolicy = GlassReducedMotionPolicy.Full)
              },
            ),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(keys, key = { it }) { index ->
            val effect = remember { GlassRuntimeEffect().also { effects[index] = it } }
            Box(
              Modifier.size(80.dp).hazeGlass(
                HazeEffectFactory { effect },
                HazeInput.Sources(hazeState),
                if (splitClusters) lazyItemStyle(index) else containerTestStyle(Color.Red),
                HazePerformanceMode.Quality,
                true,
                null,
                memberTransition = transition,
                groupKey = groupKey(index),
              ),
            )
          }
        }
      }
    }
    waitForIdle()
    mainClock.autoAdvance = false
    return LazyFixture(this, state, effects, keys, scope)
  }

  /**
   * Draws a 280x80dp `LazyRow` inset at (40, 80) in a 360x240dp root, scrolls it by 40px so the
   * first and fourth items are half out of the list, and returns the root's pixels. Items are
   * opaque red glass over a white root, so a glass pixel is [Color.Red] and an uncovered pixel is
   * [Color.White].
   */
  private fun ComposeUiTest.insetLazyRow(clipToBounds: Boolean): PixelMap {
    val hazeState = HazeState()
    val state = LazyListState()
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(hazeState).background(Color.White))
        LazyRow(
          state = state,
          modifier = Modifier
            .offset(40.dp, 80.dp)
            .size(280.dp, 80.dp)
            .then(if (clipToBounds) Modifier.clipToBounds() else Modifier)
            .hazeGlassContainer(16.dp, transitionSpec = tween(TRANSITION_MILLIS, easing = LinearEasing), reducedMotionPolicy = GlassReducedMotionPolicy.Full),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(12) {
            val effect = remember { GlassRuntimeEffect() }
            Box(
              Modifier.size(80.dp).hazeGlass(
                HazeEffectFactory { effect },
                HazeInput.Sources(hazeState),
                containerTestStyle(Color.Red),
                HazePerformanceMode.Quality,
                true,
                null,
                memberTransition = GlassMemberTransition.None,
              ),
            )
          }
        }
      }
    }
    waitForIdle()
    runOnIdle { state.dispatchRawDelta(40f) }
    waitForIdle()
    return onNodeWithTag("root").captureToImage().toPixelMap()
  }

  private fun lazyItemStyle(index: Int): GlassStyle = containerTestStyle(Color.Red).then {
    specularIntensity(if (index / 3 % 2 == 0) 0f else 0.1f)
  }
}
