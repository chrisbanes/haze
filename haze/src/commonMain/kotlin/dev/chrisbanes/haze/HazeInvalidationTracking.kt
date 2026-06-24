// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier

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

internal fun Modifier.hazeInvalidationTag(tag: String): Modifier = this

internal fun withHazeInvalidationTracking(block: () -> Unit) {
  block()
}

internal fun clearHazeInvalidations() = Unit

internal fun hazeInvalidationEvents(): List<HazeInvalidationEvent> = emptyList()
