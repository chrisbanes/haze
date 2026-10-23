// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeInputSharing
import dev.chrisbanes.haze.HazeInputSharing.Auto
import dev.chrisbanes.haze.HazeInputSharing.Never
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
@Config(qualifiers = "w393dp-h698dp-440dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassFeedProfilingSampleTest : ContextTest() {
  @Test
  fun scenarioIds_resolveNestedAndExistingFeedScenarios() {
    assertThat(GlassFeedProfilingScenario.entries.map { it.id })
      .containsExactlyInAnyOrder(*(FlatIds + NestedPolicies.keys).toTypedArray())
    FlatIds.forEach { id -> assertThat(resolve(id).hostSharing).isNull() }
    NestedPolicies.forEach { (id, policies) ->
      val scenario = resolve(id)
      assertThat<Pair<HazeInputSharing, HazeInputSharing?>>(scenario.inputSharing to scenario.hostSharing)
        .isEqualTo(policies)
    }
  }

  @Test
  fun nestedScenarios_headerSamplesFeedAndBottomSamplesOuterHost() {
    NestedPolicies.forEach { (id, policies) ->
      runComposeUiTest {
        val state = GlassFeedProfilingState().apply { select(resolve(id)) }
        setContent { GlassFeedProfilingSampleContent(state = state, onBack = {}) }

        val (feedSharing, hostSharing) = policies
        assertThat(onNodeWithTag("glass_feed_source").inspectable("hazeSource")["inputSharing"])
          .isEqualTo(feedSharing)
        assertThat(onNodeWithTag("glass_feed_host_source").inspectable("hazeSource")["inputSharing"])
          .isEqualTo(hostSharing)
        onNode(hasTestTag("glass_feed_source") and hasAnyAncestor(hasTestTag("glass_feed_host_source")))
          .assertExists()

        val headerInputs = List(4) { index -> controlInput("glass_feed_control_$index") }
        val hostInputs = List(2) { index -> controlInput("glass_feed_host_control_$index") }
        assertThat(headerInputs.distinct().size).isEqualTo(1)
        assertThat(hostInputs.distinct().size).isEqualTo(1)
        assertThat(headerInputs.first()).isNotNull().isInstanceOf<HazeInput.Sources>()
        assertThat(hostInputs.first()).isNotNull().isInstanceOf<HazeInput.Sources>()
        assertThat((headerInputs.first() as HazeInput.Sources).state)
          .isNotEqualTo((hostInputs.first() as HazeInput.Sources).state)
        // Controls sit outside both captured subtrees, so they never sample themselves.
        (List(4) { "glass_feed_control_$it" } + List(2) { "glass_feed_host_control_$it" }).forEach { tag ->
          onNode(
            hasTestTag(tag) and
              (hasAnyAncestor(hasTestTag("glass_feed_source")) or hasAnyAncestor(hasTestTag("glass_feed_host_source"))),
          ).assertDoesNotExist()
        }
      }
    }
  }

  @Test
  fun pickerScenarios_exposeReadyStartAndCompleteProtocol() {
    listOf("feed_scroll_blur_nested_both", "feed_video_glass_nested_unshared", "feed_video_glass").forEach { id ->
      runComposeUiTest {
        setContent {
          GlassFeedProfilingSampleContent(state = remember { GlassFeedProfilingState() }, onBack = {})
        }

        onNodeWithText(id).performScrollTo().performClick()
        onNodeWithTag("glass_profiling_selected_$id").assertIsDisplayed()
        onNodeWithTag("glass_profiling_phase_ready").assertIsDisplayed()
        onNodeWithTag("glass_profiling_start").performClick()
        onNodeWithTag("glass_profiling_phase_complete").assertIsDisplayed()
      }
    }
  }

  private fun SemanticsNodeInteractionsProvider.controlInput(tag: String): Any? =
    onNodeWithTag(tag).inspectable("HazeEffect")["input"]

  private fun SemanticsNodeInteraction.inspectable(name: String): Map<String, Any?> =
    fetchSemanticsNode().layoutInfo.getModifierInfo()
      .map { it.modifier }
      .filterIsInstance<InspectableValue>()
      .single { it.nameFallback == name }
      .inspectableElements
      .associate { it.name to it.value }

  private fun resolve(id: String) = GlassFeedProfilingScenario.entries.single { it.id == id }

  private companion object {
    val FlatIds = listOf(
      "feed_video_glass", "feed_video_glass_unshared", "feed_video_blur",
      "feed_video_blur_unshared", "feed_video_plain", "feed_scroll_glass",
      "feed_scroll_glass_unshared", "feed_scroll_blur", "feed_scroll_blur_unshared",
      "feed_scroll_plain",
    )

    // Expected IDs and policies from the #1615 plan: feed policy, then outer host policy.
    val NestedPolicies = mapOf(
      "feed_video_glass_nested_unshared" to (Never to Never),
      "feed_video_glass_nested_feed_only" to (Auto to Never),
      "feed_video_glass_nested_both" to (Auto to Auto),
      "feed_scroll_glass_nested_unshared" to (Never to Never),
      "feed_scroll_glass_nested_feed_only" to (Auto to Never),
      "feed_scroll_glass_nested_both" to (Auto to Auto),
      "feed_video_blur_nested_unshared" to (Never to Never),
      "feed_video_blur_nested_feed_only" to (Auto to Never),
      "feed_video_blur_nested_both" to (Auto to Auto),
      "feed_scroll_blur_nested_unshared" to (Never to Never),
      "feed_scroll_blur_nested_feed_only" to (Auto to Never),
      "feed_scroll_blur_nested_both" to (Auto to Auto),
    )
  }
}
