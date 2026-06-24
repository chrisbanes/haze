// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.ProvidableModifierLocal
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.modifier.modifierLocalProvider
import androidx.compose.ui.node.invalidateDraw

internal enum class HazeInvalidationNodeType {
  Source,
  Effect,
}

internal enum class HazeInvalidationType {
  Draw,
  Layout,
}

internal enum class HazeInvalidationReason {
  DirtyFields,
  PreDraw,
  VisualEffect,
}

internal data class HazeInvalidationEvent(
  val tag: String?,
  val nodeType: HazeInvalidationNodeType,
  val invalidationType: HazeInvalidationType,
  val reason: HazeInvalidationReason,
)

@PublishedApi
internal class HazeInvalidationRecorder {
  val events = mutableListOf<HazeInvalidationEvent>()
}

@PublishedApi
internal var activeHazeInvalidationRecorder: HazeInvalidationRecorder? = null

internal val isHazeInvalidationTrackingActive: Boolean
  get() = activeHazeInvalidationRecorder != null

internal inline fun recordHazeInvalidation(event: () -> HazeInvalidationEvent) {
  activeHazeInvalidationRecorder?.events?.add(event())
}

private val ModifierLocalHazeInvalidationTag: ProvidableModifierLocal<String?> =
  modifierLocalOf { null }

internal fun Modifier.hazeInvalidationTag(tag: String): Modifier {
  return modifierLocalProvider(ModifierLocalHazeInvalidationTag) { tag }
}

private fun ModifierLocalModifierNode.currentHazeInvalidationTag(): String? {
  return if (isHazeInvalidationTrackingActive) {
    ModifierLocalHazeInvalidationTag.current
  } else {
    null
  }
}

internal fun HazeEffectNode.invalidateHazeDraw(reason: HazeInvalidationReason) {
  recordHazeInvalidation {
    HazeInvalidationEvent(
      tag = currentHazeInvalidationTag(),
      nodeType = HazeInvalidationNodeType.Effect,
      invalidationType = HazeInvalidationType.Draw,
      reason = reason,
    )
  }
  invalidateDraw()
}

internal fun withHazeInvalidationTracking(block: () -> Unit) {
  val previousRecorder = activeHazeInvalidationRecorder
  val recorder = HazeInvalidationRecorder()
  activeHazeInvalidationRecorder = recorder
  try {
    block()
  } finally {
    activeHazeInvalidationRecorder = previousRecorder
  }
}

internal fun clearHazeInvalidations() {
  activeHazeInvalidationRecorder?.events?.clear()
}

internal fun hazeInvalidationEvents(): List<HazeInvalidationEvent> {
  return activeHazeInvalidationRecorder?.events.orEmpty()
}
