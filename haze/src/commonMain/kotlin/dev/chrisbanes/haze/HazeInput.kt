// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Stable

/**
 * The content consumed by a Haze visual effect.
 */
public sealed interface HazeInput {

  /**
   * Content captured by [hazeSource] modifiers associated with [state].
   *
   * @property state Shared state that identifies the available sources.
   * @property selection Policy that selects which associated sources are consumed.
   * @property retention Policy for output retained while selected sources are unavailable.
   */
  @Stable
  public data class Sources(
    public val state: HazeState,
    public val selection: HazeSourceSelection = HazeSourceSelection.Behind,
    public val retention: HazeSourceRetention = HazeSourceRetention.KeepLastFrame,
  ) : HazeInput

  /**
   * The content of the composable carrying the effect modifier.
   */
  public data object Content : HazeInput
}

/**
 * Stable source metadata exposed to [HazeSourceSelection] refinements.
 *
 * Renderer-owned geometry, content, and platform resources are intentionally not exposed.
 *
 * @property key Optional application key supplied to `hazeSource`.
 * @property zIndex Source ordering value supplied to `hazeSource`.
 */
public class HazeSourceMetadata internal constructor(
  public val key: Any?,
  public val zIndex: Float,
)

/**
 * Selects source content for [HazeInput.Sources].
 */
public sealed interface HazeSourceSelection {

  /**
   * Refines this selection using immutable source metadata.
   *
   * Repeated refinements compose with logical AND.
   */
  public fun where(
    predicate: (HazeSourceMetadata) -> Boolean,
  ): HazeSourceSelection = FilteredHazeSourceSelection(
    selection = this,
    predicate = predicate,
  )

  /**
   * Sources behind the nearest ancestor [hazeSource], or every source when there is no matching
   * ancestor.
   */
  public data object Behind : HazeSourceSelection

  /**
   * Every source associated with the input state.
   */
  public data object All : HazeSourceSelection
}

private class FilteredHazeSourceSelection(
  val selection: HazeSourceSelection,
  val predicate: (HazeSourceMetadata) -> Boolean,
) : HazeSourceSelection

internal fun HazeSourceSelection.baseSelection(): HazeSourceSelection = when (this) {
  HazeSourceSelection.All,
  HazeSourceSelection.Behind,
  -> this
  is FilteredHazeSourceSelection -> selection.baseSelection()
}

internal fun HazeSourceSelection.matches(metadata: HazeSourceMetadata): Boolean = when (this) {
  HazeSourceSelection.All,
  HazeSourceSelection.Behind,
  -> true
  is FilteredHazeSourceSelection -> selection.matches(metadata) && predicate(metadata)
}

internal fun HazeSourceSelection.hasRefinements(): Boolean =
  this is FilteredHazeSourceSelection

/**
 * Controls retained output when source content is temporarily unavailable.
 */
public sealed interface HazeSourceRetention {

  /**
   * Continue drawing the most recently rendered output.
   *
   * This visually compatible behavior is the default for source transitions.
   */
  public data object KeepLastFrame : HazeSourceRetention

  /**
   * Clear retained output as soon as no source content is available.
   *
   * Prefer this policy for privacy-sensitive surfaces where stale source pixels must not remain.
   */
  public data object ClearWhenUnavailable : HazeSourceRetention
}

internal fun HazeSourceRetention.keepsLastFrame(): Boolean = when (this) {
  HazeSourceRetention.KeepLastFrame -> true
  HazeSourceRetention.ClearWhenUnavailable -> false
}
