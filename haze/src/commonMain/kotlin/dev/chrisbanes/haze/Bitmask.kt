// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlin.jvm.JvmInline

@JvmInline
internal value class Bitmask(internal val value: Int = 0) {
  operator fun plus(flag: Int): Bitmask = Bitmask(value or flag)
  operator fun minus(flag: Int): Bitmask = Bitmask(value and flag.inv())
  operator fun contains(flag: Int): Boolean = (flag and value) == flag
  fun any(flag: Int): Boolean = (flag and value) != 0
  fun isEmpty(): Boolean = value == 0
}
