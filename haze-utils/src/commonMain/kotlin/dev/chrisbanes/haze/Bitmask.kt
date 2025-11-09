// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlin.jvm.JvmInline

@JvmInline
@InternalHazeApi
public value class Bitmask(private val value: Int = 0) {
  public operator fun plus(flag: Int): Bitmask = Bitmask(value or flag)
  public operator fun minus(flag: Int): Bitmask = Bitmask(value and flag.inv())
  public operator fun contains(flag: Int): Boolean = (flag and value) == flag
  public fun any(flag: Int): Boolean = (flag and value) != 0
  public fun isEmpty(): Boolean = value == 0
}
