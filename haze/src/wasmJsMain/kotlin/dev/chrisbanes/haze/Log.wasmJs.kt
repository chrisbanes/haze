// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("NOTHING_TO_INLINE")

package dev.chrisbanes.haze

internal actual inline fun platformLog(tag: String, message: String) {
  println("[$tag] $message")
}
