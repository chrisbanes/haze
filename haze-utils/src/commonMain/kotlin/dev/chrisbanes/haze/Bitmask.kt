// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlin.jvm.JvmInline

/** Immutable integer-backed set of bit flags. */
@JvmInline
@InternalHazeApi
public value class Bitmask(private val value: Int = 0) {
  /** Returns a bitmask containing [flag]. */
  public operator fun plus(flag: Int): Bitmask = Bitmask(value or flag)

  /** Returns a bitmask without [flag]. */
  public operator fun minus(flag: Int): Bitmask = Bitmask(value and flag.inv())

  /** Returns whether every bit in [flag] is present. */
  public operator fun contains(flag: Int): Boolean = (flag and value) == flag

  /** Returns whether any bit in [flag] is present. */
  public fun any(flag: Int): Boolean = (flag and value) != 0

  /** Returns whether no bits are set. */
  public fun isEmpty(): Boolean = value == 0
}
