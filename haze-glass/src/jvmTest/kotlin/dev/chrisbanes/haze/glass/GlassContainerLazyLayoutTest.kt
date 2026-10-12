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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

private const val TRANSITION_MILLIS = 150

/**
 * Characterises what a [hazeGlassContainer] does with the members of a lazy layout, to back the
 * guidance in `docs/effects/glass.md` ("Lazy layouts") and the [GlassMemberTransition] KDoc.
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
 * The tests use 6px per frame because that rate keeps item 5's grow running for its whole
 * duration. They characterise the first item only: later items depend on whether their neighbour
 * is still animating.
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

    val entering = checkNotNull(fixture.member(5))
    assertThat(entering.link).isNull()
    assertThat(entering.fading).isFalse()
    assertThat(entering.material.alpha).isEqualTo(1f)
    assertThat(entering.rect.overlaps(fixture.viewport)).isTrue()
    assertThat(entering.rect).isEqualTo(fixture.contentBounds(5))
    assertThat(fixture.ghosts()).isEmpty()
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
          containerTestStyle(Color.Red),
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
            .hazeGlassContainer(16.dp, tween(TRANSITION_MILLIS, easing = LinearEasing), GlassReducedMotionPolicy.Full),
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
  ) {
    val viewport = Rect(0f, 0f, 360f, 80f)

    private fun node(index: Int): GlassMemberNode? = effects[index]?.memberBindingForTest?.node

    private fun members(): List<GlassMemberSnapshot> {
      val container = effects.values.firstNotNullOf { it.memberBindingForTest?.node?.container }
      return container.model.surfaces.flatMap { it.members }
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

    /** Runs frames until every animation has finished. */
    fun settle() {
      test.mainClock.autoAdvance = true
      test.waitForIdle()
      test.mainClock.autoAdvance = false
    }
  }

  private fun ComposeUiTest.lazyFixture(transition: GlassMemberTransition): LazyFixture {
    val effects = mutableMapOf<Int, GlassRuntimeEffect>()
    val hazeState = HazeState()
    val state = LazyListState(firstVisibleItemScrollOffset = 70)
    setContent {
      Box(Modifier.size(360.dp, 240.dp).background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(hazeState))
        LazyRow(
          state = state,
          modifier = Modifier
            .offset(0.dp, 80.dp)
            .size(360.dp, 80.dp)
            .hazeGlassContainer(16.dp, tween(TRANSITION_MILLIS, easing = LinearEasing), GlassReducedMotionPolicy.Full),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(40, key = { it }) { index ->
            val effect = remember { GlassRuntimeEffect().also { effects[index] = it } }
            Box(
              Modifier.size(80.dp).hazeGlass(
                HazeEffectFactory { effect },
                HazeInput.Sources(hazeState),
                containerTestStyle(Color.Red),
                HazePerformanceMode.Quality,
                true,
                null,
                memberTransition = transition,
              ),
            )
          }
        }
      }
    }
    waitForIdle()
    mainClock.autoAdvance = false
    return LazyFixture(this, state, effects)
  }
}
