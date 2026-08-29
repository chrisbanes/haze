// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import kotlin.jvm.JvmInline

/** A value which is either fixed or responsive to the material's shortest dimension. */
@ExperimentalHazeApi
@Immutable
public sealed interface SizeValue<T> {
  /** A value which does not change with the material size. */
  @JvmInline
  public value class Fixed<T>(
    /** The fixed value. */
    public val value: T,
  ) : SizeValue<T>

  /** A value smoothly interpolated from shortest-dimension points. */
  @JvmInline
  public value class Responsive<T> private constructor(
    /** The ordered interpolation points. */
    public val points: List<SizePoint<T>>,
  ) : SizeValue<T> {
    /** Creates an immutable responsive value from the ordered [points]. */
    public constructor(vararg points: SizePoint<T>) : this(points.toList())

    /** Creates an immutable responsive value from a collection of ordered [points]. */
    public constructor(points: Collection<SizePoint<T>>) : this(points.toList())

    init {
      require(points.size >= 2) { "points must contain at least two values" }
      for (index in 1..<points.size) {
        require(points[index].shortestDimension > points[index - 1].shortestDimension) {
          "points shortest dimensions must be strictly increasing"
        }
      }
    }
  }
}

/**
 * Associates a value with a positive shortest dimension.
 *
 * @param shortestDimension The positive shortest dimension at which [value] applies.
 * @param value The value to resolve at [shortestDimension].
 */
@ExperimentalHazeApi
@Immutable
public data class SizePoint<T>(
  val shortestDimension: Dp,
  val value: T,
) {
  init {
    require(shortestDimension.value.isFinite()) { "shortestDimension must be finite" }
    require(shortestDimension > 0.dp) { "shortestDimension must be positive" }
  }
}
