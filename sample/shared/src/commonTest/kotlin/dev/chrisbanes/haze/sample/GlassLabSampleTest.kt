// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalHazeApi::class, ExperimentalTestApi::class)
class GlassLabSampleTest : ContextTest() {
  @Test
  fun specimenFollowsDrag() = runComposeUiTest {
    setContent {
      GlassLabSampleContent(
        state = GlassLabState(),
        recordingMode = false,
        onStateChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    val specimen = onNodeWithContentDescription("Glass specimen")
    val initialCenter = specimen.fetchSemanticsNode().boundsInRoot.center
    specimen.performTouchInput { down(center) }
    specimen.performTouchInput {
      updatePointerTo(0, center + Offset(80f, 40f))
      move()
    }
    waitForIdle()
    val draggedCenter = specimen.fetchSemanticsNode().boundsInRoot.center

    assertThat(draggedCenter.x).isGreaterThan(initialCenter.x)
    assertThat(draggedCenter.y).isGreaterThan(initialCenter.y)
    specimen.performTouchInput { cancel() }
  }

  @Test
  fun specimenSpringsBackToCenterAfterRelease() = runComposeUiTest {
    setContent {
      GlassLabSampleContent(
        state = GlassLabState(),
        recordingMode = false,
        onStateChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    val specimen = onNodeWithContentDescription("Glass specimen")
    val initialCenter = specimen.fetchSemanticsNode().boundsInRoot.center
    specimen.performTouchInput { down(center) }
    specimen.performTouchInput {
      updatePointerTo(0, center + Offset(80f, 40f))
      move()
    }
    val autoAdvance = mainClock.autoAdvance
    mainClock.autoAdvance = false
    try {
      specimen.performTouchInput { up() }
      mainClock.advanceTimeUntil(timeoutMillis = 5_000) {
        specimen.fetchSemanticsNode().boundsInRoot.center == initialCenter
      }

      assertThat(specimen.fetchSemanticsNode().boundsInRoot.center).isEqualTo(initialCenter)
    } finally {
      mainClock.autoAdvance = autoAdvance
    }
  }

  @Test
  fun specimenCenterIsClampedToViewportDuringDrag() = runComposeUiTest {
    setContent {
      GlassLabSampleContent(
        state = GlassLabState(),
        recordingMode = false,
        onStateChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    val viewport = onNodeWithTag("glass_lab_specimen_viewport")
    val specimen = onNodeWithContentDescription("Glass specimen")
    specimen.performTouchInput { down(center) }
    specimen.performTouchInput {
      updatePointerTo(0, center + Offset(10_000f, 10_000f))
      move()
    }
    waitForIdle()

    assertThat(specimen.fetchSemanticsNode().boundsInRoot.center)
      .isEqualTo(viewport.fetchSemanticsNode().boundsInRoot.bottomRight)
    specimen.performTouchInput { cancel() }
  }

  @Test
  fun specimenReturnIsClampedWhenViewportShrinks() = runComposeUiTest {
    var labSize by mutableStateOf(800.dp)
    setContent {
      GlassLabSampleContent(
        state = GlassLabState(),
        recordingMode = false,
        onStateChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
        modifier = Modifier.size(labSize, labSize / 2),
      )
    }

    val viewport = onNodeWithTag("glass_lab_specimen_viewport")
    val specimen = onNodeWithContentDescription("Glass specimen")
    specimen.performTouchInput { down(center) }
    specimen.performTouchInput {
      updatePointerTo(0, center + Offset(10_000f, 10_000f))
      move()
    }
    waitForIdle()
    assertThat(specimen.fetchSemanticsNode().boundsInRoot.center)
      .isEqualTo(viewport.fetchSemanticsNode().boundsInRoot.bottomRight)
    val autoAdvance = mainClock.autoAdvance
    mainClock.autoAdvance = false
    try {
      specimen.performTouchInput { up() }
      mainClock.advanceTimeBy(64)
      val returningCenter = specimen.fetchSemanticsNode().boundsInRoot.center
      assertThat(returningCenter.x)
        .isGreaterThan(viewport.fetchSemanticsNode().boundsInRoot.center.x)
      labSize = 300.dp
      mainClock.advanceTimeByFrame()

      val specimenCenter = specimen.fetchSemanticsNode().boundsInRoot.center
      val viewportBounds = viewport.fetchSemanticsNode().boundsInRoot
      assertThat(specimenCenter.x).isLessThanOrEqualTo(viewportBounds.right + 0.5f)
      assertThat(specimenCenter.y).isLessThanOrEqualTo(viewportBounds.bottom + 0.5f)
      val clampedOffsetX = specimenCenter.x - viewportBounds.center.x
      labSize = 800.dp
      mainClock.advanceTimeBy(64)

      val expandedViewportCenterX = viewport.fetchSemanticsNode().boundsInRoot.center.x
      assertThat(specimen.fetchSemanticsNode().boundsInRoot.center.x - expandedViewportCenterX)
        .isLessThanOrEqualTo(clampedOffsetX + 0.5f)
    } finally {
      mainClock.autoAdvance = autoAdvance
    }
  }

  @Test
  fun specimenDragEmitsPressAndReleaseInteractions() = runComposeUiTest {
    val interactionSource = MutableInteractionSource()
    val interactions = mutableListOf<Interaction>()
    val collectionScope = CoroutineScope(Dispatchers.Unconfined)
    try {
      collectionScope.launch {
        interactionSource.interactions.collect(interactions::add)
      }
      setContent {
        GlassLabSampleContent(
          state = GlassLabState(),
          recordingMode = false,
          onStateChanged = {},
          onRecordingModeChanged = {},
          onBack = {},
          specimenInteractionSource = interactionSource,
        )
      }

      val specimen = onNodeWithContentDescription("Glass specimen")
      specimen.performTouchInput { down(center) }
      specimen.performTouchInput {
        updatePointerTo(0, center + Offset(80f, 40f))
        move()
      }
      specimen.performTouchInput { up() }
      waitUntil { interactions.size >= 2 }

      assertThat(interactions[0]).isInstanceOf<PressInteraction.Press>()
      assertThat(interactions[1]).isInstanceOf<PressInteraction.Release>()
      val press = interactions[0] as PressInteraction.Press
      val release = interactions[1] as PressInteraction.Release
      assertThat(release.press).isSameInstanceAs(press)
    } finally {
      collectionScope.cancel()
    }
  }

  @Test
  fun cancelledSpecimenDragEmitsPressAndCancelInteractions() = runComposeUiTest {
    val interactionSource = MutableInteractionSource()
    val interactions = mutableListOf<Interaction>()
    val collectionScope = CoroutineScope(Dispatchers.Unconfined)
    var showSpecimen by mutableStateOf(true)
    try {
      collectionScope.launch {
        interactionSource.interactions.collect(interactions::add)
      }
      setContent {
        if (showSpecimen) {
          GlassLabSampleContent(
            state = GlassLabState(),
            recordingMode = false,
            onStateChanged = {},
            onRecordingModeChanged = {},
            onBack = {},
            specimenInteractionSource = interactionSource,
          )
        }
      }

      val specimen = onNodeWithContentDescription("Glass specimen")
      specimen.performTouchInput { down(center) }
      specimen.performTouchInput {
        updatePointerTo(0, center + Offset(80f, 40f))
        move()
      }
      showSpecimen = false
      waitUntil { interactions.size >= 2 }

      assertThat(interactions[0]).isInstanceOf<PressInteraction.Press>()
      assertThat(interactions[1]).isInstanceOf<PressInteraction.Cancel>()
      val press = interactions[0] as PressInteraction.Press
      val cancel = interactions[1] as PressInteraction.Cancel
      assertThat(cancel.press).isSameInstanceAs(press)
    } finally {
      collectionScope.cancel()
    }
  }

  @Test
  fun regrabbingSpecimenCancelsReturnAtCurrentPosition() = runComposeUiTest {
    var showSpecimen by mutableStateOf(true)
    setContent {
      if (showSpecimen) {
        GlassLabSampleContent(
          state = GlassLabState(interaction = GlassLabInteractionMode.Off),
          recordingMode = false,
          onStateChanged = {},
          onRecordingModeChanged = {},
          onBack = {},
        )
      }
    }

    val specimen = onNodeWithContentDescription("Glass specimen")
    val initialCenter = specimen.fetchSemanticsNode().boundsInRoot.center
    specimen.performTouchInput { down(center) }
    specimen.performTouchInput {
      updatePointerTo(0, center + Offset(120f, 0f))
      move()
    }
    val autoAdvance = mainClock.autoAdvance
    mainClock.autoAdvance = false
    try {
      specimen.performTouchInput { up() }
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeBy(64)
      val returningCenter = specimen.fetchSemanticsNode().boundsInRoot.center
      specimen.performTouchInput { down(center) }
      mainClock.advanceTimeBy(500)
      assertThat(specimen.fetchSemanticsNode().boundsInRoot.center).isEqualTo(returningCenter)
      specimen.performTouchInput {
        updatePointerTo(0, center + Offset(0f, 60f))
        move()
      }
      mainClock.advanceTimeByFrame()
      val regrabbedCenter = specimen.fetchSemanticsNode().boundsInRoot.center

      assertThat(regrabbedCenter.x).isGreaterThan(initialCenter.x)
      assertThat(regrabbedCenter.x).isLessThanOrEqualTo(returningCenter.x)
      assertThat(regrabbedCenter.y).isGreaterThan(returningCenter.y)
      mainClock.advanceTimeBy(500)

      assertThat(specimen.fetchSemanticsNode().boundsInRoot.center.x).isEqualTo(regrabbedCenter.x)
    } finally {
      showSpecimen = false
      mainClock.autoAdvance = autoAdvance
      waitForIdle()
    }
  }

  @Test
  fun tappingSpecimenDuringReturnResumesReturnToCenter() = runComposeUiTest {
    setContent {
      GlassLabSampleContent(
        state = GlassLabState(interaction = GlassLabInteractionMode.Off),
        recordingMode = false,
        onStateChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    val specimen = onNodeWithContentDescription("Glass specimen")
    val initialCenter = specimen.fetchSemanticsNode().boundsInRoot.center
    specimen.performTouchInput { down(center) }
    specimen.performTouchInput {
      updatePointerTo(0, center + Offset(120f, 0f))
      move()
    }
    val autoAdvance = mainClock.autoAdvance
    mainClock.autoAdvance = false
    try {
      specimen.performTouchInput { up() }
      mainClock.advanceTimeBy(64)
      specimen.performTouchInput { down(center) }
      specimen.performTouchInput { up() }
      mainClock.advanceTimeUntil(timeoutMillis = 5_000) {
        specimen.fetchSemanticsNode().boundsInRoot.center == initialCenter
      }

      assertThat(specimen.fetchSemanticsNode().boundsInRoot.center).isEqualTo(initialCenter)
    } finally {
      mainClock.autoAdvance = autoAdvance
    }
  }

  @Test
  fun recordingModeSpecimenRemainsDraggable() = runComposeUiTest {
    setContent {
      GlassLabSampleContent(
        state = GlassLabState(),
        recordingMode = true,
        onStateChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    val specimen = onNodeWithContentDescription("Glass specimen")
    val initialCenter = specimen.fetchSemanticsNode().boundsInRoot.center
    specimen.performTouchInput { down(center) }
    specimen.performTouchInput {
      updatePointerTo(0, center + Offset(80f, 40f))
      move()
    }

    assertThat(specimen.fetchSemanticsNode().boundsInRoot.center.x)
      .isGreaterThan(initialCenter.x)
    specimen.performTouchInput { cancel() }
  }

  @Test
  fun narrowLabShowsEveryPresetWithoutHorizontalScrolling() = runComposeUiTest {
    setContent {
      LabControls(
        state = GlassLabState(),
        recordingMode = false,
        onStateChanged = {},
        modifier = Modifier.width(320.dp).fillMaxSize(),
      )
    }

    SelectableGlassLabPresets.forEach { preset ->
      onNode(hasText(preset.name) and hasClickAction()).performScrollTo().assertIsDisplayed()
    }
  }

  @Test
  fun narrowLabKeepsSelectorLabelsVisibleAsControls() = runComposeUiTest {
    setContent {
      GlassLabSampleContent(
        state = GlassLabState(),
        recordingMode = false,
        onStateChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
        modifier = Modifier.width(320.dp),
      )
    }

    val optionNames = buildList {
      addAll(SelectableGlassLabPresets.map { it.name })
      addAll(GlassGalleryBackdropId.entries.map { it.name })
      addAll(GlassLabInteractionMode.entries.map { it.name })
    }
    optionNames.forEach { optionName ->
      val textLayouts = mutableListOf<TextLayoutResult>()
      onNode(hasText(optionName) and hasClickAction())
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
          action(textLayouts)
        }
      val textLayout = textLayouts.single()
      assertThat(textLayout.lineCount, optionName).isEqualTo(1)
      assertThat(
        textLayout.multiParagraph.intrinsics.maxIntrinsicWidth <= textLayout.size.width + 1f,
        "$optionName: size=${textLayout.size}, intrinsicWidth=" +
          textLayout.multiParagraph.intrinsics.maxIntrinsicWidth,
      ).isTrue()
    }
  }

  @Test
  fun controlsForwardPresetBackdropInteractionAndAdvancedEvents() = runComposeUiTest {
    var state by mutableStateOf(GlassLabState())
    setContent {
      LabControls(
        state = state,
        recordingMode = false,
        onStateChanged = { state = it },
        modifier = Modifier.fillMaxSize(),
      )
    }

    onNode(hasText("Prism") and hasClickAction())
      .performScrollTo()
      .performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
    assertThat(state.preset).isEqualTo(GlassLabPresetId.Prism)

    onNode(hasText("Grid") and hasClickAction())
      .performScrollTo()
      .performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
    assertThat(state.backdrop).isEqualTo(GlassGalleryBackdropId.Grid)

    onNode(hasText("Off") and hasClickAction())
      .performScrollTo()
      .performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
    assertThat(state.interaction).isEqualTo(GlassLabInteractionMode.Off)

    onNodeWithText("Advanced")
      .performScrollTo()
      .performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
    assertThat(state.advancedExpanded).isTrue()
  }

  @Test
  fun recordingModeKeepsSpecimenAndHidesExplanatoryCopy() = runComposeUiTest {
    setContent {
      GlassLabSampleContent(
        state = GlassLabState(),
        recordingMode = true,
        onStateChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    onNodeWithContentDescription("Glass specimen").assertIsDisplayed()
    onNodeWithText("Choose a material preset").assertDoesNotExist()
  }

  @Test
  fun recordingModeSpecimenTapDoesNotRevealChrome() = runComposeUiTest {
    var recordingModeChanged = false
    setContent {
      GlassLabSampleContent(
        state = GlassLabState(),
        recordingMode = true,
        onStateChanged = {},
        onRecordingModeChanged = { recordingModeChanged = !it },
        onBack = {},
      )
    }

    onNodeWithContentDescription("Glass specimen").performTouchInput { click() }
    assertThat(recordingModeChanged).isFalse()
  }
}
